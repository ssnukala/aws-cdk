package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.RdsConfigModel;
import com.ppkn.cdk.config.SsmConfigModel;
import com.ppkn.cdk.config.StackEnum;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretProps;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;

import java.io.File;
import java.util.Collections;

@Slf4j
public class RdsStackHandler implements AwsStackHandler {

    private final Parameters parameters;
    private RdsConfigModel rdsConfigModel;

    public RdsStackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        String accountId = rdsConfigModel.getAccountId();
        log.info("processStack::accountId : {}", accountId);
        new RdsStack(parameters.getApp(), rdsConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                return new Environment() {
                    @Override
                    public String getAccount() {
                        return accountId;
                    }

                    @Override
                    public String getRegion() {
                        return rdsConfigModel.getRegion();
                    }
                };
            }
        });

    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.rds);
            log.info("Reading ASG config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            rdsConfigModel = mapper.readValue(new File(configFilePath), RdsConfigModel.class);
            rdsConfigModel.processPlaceHolders(parameters);

            log.debug("Done Reading config model: {}", rdsConfigModel);
            log.info("Successfully loaded config model");
        }catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class RdsStack extends AbstractStack {

        public RdsStack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);
            rdsConfigModel.getRds().forEach(rdsModel -> {
                Secret dbSecret = createDBSecret(rdsModel);
                createSnapshotDB(rdsModel, dbSecret);
            });
        }

        private Secret createDBSecret(RdsConfigModel.RdsModel rdsModel) {
            Secret secret =  new Secret(this, rdsModel.getName()+"-secret", SecretProps.builder()
                .secretName(rdsModel.getSecretName())
                .generateSecretString(SecretStringGenerator.builder()
                    .secretStringTemplate("{\"username\":\"postgres\"}")
                    .excludePunctuation(true)
                    .excludeCharacters("),-?[")
                    .generateStringKey("password")
                    .build())
                .build());
            super.processTags(secret, rdsModel);
            return secret;
        }

        private void createSnapshotDB(RdsConfigModel.RdsModel rdsModel, Secret dbSecret) {
            IVpc vpc = Vpc.fromLookup(this, rdsModel.getVpc(), VpcLookupOptions.builder()
                .vpcName(rdsModel.getVpc())
                .build()
            );
            String paramValue = super.getSsmStringParameter( SsmConfigModel.builder()
                .id(rdsModel.getName())
                .ssmKey(rdsModel.getSg())
                .build() );
            ISecurityGroup securityGroup = SecurityGroup.fromSecurityGroupId(this, "rds-sg", paramValue);

            DatabaseInstanceFromSnapshot db = new DatabaseInstanceFromSnapshot(this, rdsModel.getName() + "-db",
                DatabaseInstanceFromSnapshotProps.builder()
                    .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
                        .version(PostgresEngineVersion.VER_13_3)
                        .build()))
                    .instanceIdentifier(rdsModel.getName())
                    .instanceType(getInstanceType(rdsModel))
                    .vpc(vpc)
                    .vpcSubnets(SubnetSelection.builder()
                        .subnets(vpc.getIsolatedSubnets())
                        .build())
                    .securityGroups(Collections.singletonList(securityGroup))
                    .port(rdsModel.getPort())
                    .allocatedStorage(rdsModel.getAllocatedStorage())
                    .backupRetention(Duration.days(rdsModel.getBackupRetention()))
                    .snapshotIdentifier(rdsModel.getSnapshotIdentifier())
                    .credentials(SnapshotCredentials.fromGeneratedSecret("admin"))
                    .build());

            super.processTags(db, rdsModel);
        }

        private InstanceType getInstanceType(RdsConfigModel.RdsModel instanceConfigModel) {
            InstanceClass iClass = null;
            for(InstanceClass instanceClass : InstanceClass.values()) {
                if( instanceClass.name().equalsIgnoreCase(instanceConfigModel.getInstanceType()) ) {
                    iClass = instanceClass;
                }
            }
            InstanceSize iSize = null;
            for(InstanceSize instanceSize : InstanceSize.values()) {
                if(instanceSize.name().equalsIgnoreCase(instanceConfigModel.getInstanceSize())) {
                    iSize = instanceSize;
                }
            }
            log.info("instanceConfigModel name : {}, instanceClass : {}, InstanceSize : {}", instanceConfigModel.getName(), iClass, iSize);
            if( iClass == null || iSize == null) {
                throw new RuntimeException("Instance Type /Size should not be null. Please check");
            }
            return InstanceType.of(iClass, iSize);
        }
    }

}
