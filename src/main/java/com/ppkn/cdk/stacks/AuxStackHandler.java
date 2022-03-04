package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.SsmConfigModel;
import com.ppkn.cdk.config.AuxConfigModel;
import com.ppkn.cdk.config.StackEnum;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.elasticache.CfnCacheClusterProps;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroupProps;
import software.amazon.awscdk.services.elasticsearch.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class AuxStackHandler implements AwsStackHandler {

    private final Parameters parameters;
    private AuxConfigModel auxConfigModel;

    public AuxStackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        String accountId = auxConfigModel.getAccountId();
        log.info("processStack::accountId : {}", accountId);
        new AuxStack(parameters.getApp(), auxConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                return new Environment() {
                    @Override
                    public String getAccount() {
                        return accountId;
                    }

                    @Override
                    public String getRegion() {
                        return auxConfigModel.getRegion();
                    }
                };
            }
        });

    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.aux);
            log.info("Reading Global stack config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            auxConfigModel = mapper.readValue(new File(configFilePath), AuxConfigModel.class);
            auxConfigModel.processPlaceHolders(parameters);

            log.debug("Done Reading config model: {}", auxConfigModel);
            log.info("Successfully loaded config model");
        } catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class AuxStack extends AbstractStack {

        public AuxStack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);
            createElasticCache();
            createElasticSearch();
            createCloudFront();
        }
        private void createCloudFront() {

            auxConfigModel.getCloudFront().forEach(cloudFrontModel -> {

                String paramValue = super.getSsmStringParameterV2( auxConfigModel.getRegion(),cloudFrontModel.getS3Bucket());

                List<SourceConfiguration> list = new ArrayList<>();
                List<Behavior> behaviors = new ArrayList<>();
                behaviors.add(Behavior.builder().isDefaultBehavior(true).build());

                IBucket sourceBucket = Bucket.fromBucketArn(this, cloudFrontModel.getName()+"-s3", paramValue);
                list.add( SourceConfiguration.builder()
                    .s3OriginSource(S3OriginConfig.builder()
                        .s3BucketSource(sourceBucket).build())
                    .behaviors(behaviors)
                    .build());


                CloudFrontWebDistribution cfn = new CloudFrontWebDistribution(this, cloudFrontModel.getName(),
                    CloudFrontWebDistributionProps.builder()
                        .originConfigs(list)
                        .build()
                    );
                super.processTags(cfn, cloudFrontModel);
                super.putSsmStringParameter(SsmConfigModel.builder()
                    .ssmKey(cloudFrontModel.getSsmKey())
                    .value(cfn.getDistributionDomainName())
                    .build());
            });
        }

        int index=0;
        private int getIndex() {
            return index++;
        }

        private void createElasticSearch() {
            auxConfigModel.getElasticSearch().forEach(elasticSearchModel -> {

                IVpc vpc = Vpc.fromLookup(this, elasticSearchModel.getVpc() + "-" + getIndex(), VpcLookupOptions.builder()
                        .vpcName(elasticSearchModel.getVpc())
                        .build()
                );
                List<ISubnet> subnetList = vpc.getPrivateSubnets();

                List<PolicyStatement> policyStatements = new ArrayList<>();
                policyStatements.add(new PolicyStatement(PolicyStatementProps.builder()
                        .effect(Effect.ALLOW)
                        .actions(Arrays.asList("es:*"))
                        .principals(Arrays.asList(new AnyPrincipal()))
                        .resources(Arrays.asList("arn:aws:es:us-west-2:555994199423:domain/opg-dev2-search-dev3"))
                        .build()) );

                Domain prodDomain = Domain.Builder.create(this, "Domain")
                    .version(ElasticsearchVersion.V7_10)
                    .domainName(elasticSearchModel.getName())
                    .capacity(CapacityConfig.builder()
                        .dataNodes(2)
                        .dataNodeInstanceType("t3.small.elasticsearch")
                        .build())
                    .ebs(EbsOptions.builder()
                        .volumeSize(10)
                        .build())
                    .zoneAwareness(ZoneAwarenessConfig.builder()
                        .availabilityZoneCount(2)
                        .build())
                    .logging(LoggingOptions.builder()
                        .slowSearchLogEnabled(elasticSearchModel.isSlowSearchLogEnabled())
                        .appLogEnabled(elasticSearchModel.isAppLogEnabled())
                        .slowIndexLogEnabled(elasticSearchModel.isSlowIndexLogEnabled())
                        .build())
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .accessPolicies(policyStatements)
                    .vpc(vpc)
                    .vpcSubnets(Arrays.asList(SubnetSelection.builder()
                                .subnets(subnetList)
                                .availabilityZones(vpc.getAvailabilityZones())
                                .build()))
                        .nodeToNodeEncryption(true)
                        .enforceHttps(true)
                        .encryptionAtRest(EncryptionAtRestOptions.builder().enabled(true).build())
                    .build();


                super.processTags(prodDomain, elasticSearchModel);
                super.putSsmStringParameter(SsmConfigModel.builder()
                    .ssmKey(elasticSearchModel.getSsmKey())
                    .value(prodDomain.getDomainEndpoint())
                    .build());

            });

        }


        private void createElasticCache() {

            auxConfigModel.getElasticCache().forEach(elastiCacheModel -> {

                String subnetGroupName = elastiCacheModel.getClusterName() + "-subnet-group";
                IVpc vpc = Vpc.fromLookup(this, elastiCacheModel.getVpc(), VpcLookupOptions.builder()
                    .vpcName(elastiCacheModel.getVpc())
                    .build()
                );
                List<String> subnetIds = new ArrayList<>();
                vpc.getIsolatedSubnets().forEach(subnetId-> {
                    subnetIds.add( subnetId.getSubnetId() );
                });

                List<String> securityGroups = new ArrayList<>();
                elastiCacheModel.getSecurityGroups().forEach(sg -> {
                    String paramValue = super.getSsmStringParameterV2(auxConfigModel.getRegion(), sg );
                    ISecurityGroup securityGroup = SecurityGroup.fromLookup(this, sg, paramValue);
                    securityGroups.add( securityGroup.getSecurityGroupId() );
                });

                CfnSubnetGroup cfnSubnetGroup = new CfnSubnetGroup(this, elastiCacheModel.getClusterName()+"subg",
                    CfnSubnetGroupProps.builder()
                        .cacheSubnetGroupName(subnetGroupName)
                        .description(subnetGroupName)
                        .subnetIds(subnetIds)
                        .build());

                CfnCacheCluster cfnCacheCluster = new CfnCacheCluster(this, elastiCacheModel.getClusterName(), CfnCacheClusterProps
                    .builder()
                    .engine(elastiCacheModel.getEngine())
                    .clusterName(elastiCacheModel.getClusterName())
                    .cacheNodeType(elastiCacheModel.getCacheNodeType())
                    .numCacheNodes(elastiCacheModel.getNumCacheNodes())
                    .cacheSubnetGroupName(subnetGroupName)
                    .vpcSecurityGroupIds(securityGroups)
                    .build());

                cfnCacheCluster.addDependsOn(cfnSubnetGroup);

                super.processTags(cfnCacheCluster, elastiCacheModel);
                super.putSsmStringParameter(SsmConfigModel.builder()
                    .ssmKey(elastiCacheModel.getSsmKey())
                    .value(cfnCacheCluster.getLogicalId())
                    .build());
            });

        }
    }

}
