package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.EcsConfigModel;
import com.ppkn.cdk.config.EcsModel;
import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.StackEnum;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetGroupAttributes;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.LogGroupProps;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class EcsStackHandler implements AwsStackHandler {

    private Parameters parameters;
    private EcsConfigModel ecsConfigModel;

    public EcsStackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        String accountId = ecsConfigModel.getAccountId();
        log.info("processStack::accountId : {}", accountId);
        new EcsStack(parameters.getApp(), ecsConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                return new Environment() {
                    @Override
                    public String getAccount() {
                        return accountId;
                    }

                    @Override
                    public String getRegion() {
                        return ecsConfigModel.getRegion();
                    }
                };
            }
        });

    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.ecs);
            log.info("Reading ECS config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            ecsConfigModel = mapper.readValue(new File(configFilePath), EcsConfigModel.class);
            ecsConfigModel.processPlaceHolders(parameters);

            log.debug("Done Reading config model: {}", ecsConfigModel);
            log.info("Successfully loaded config model");
        } catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class EcsStack extends AbstractStack {

        private Map<String, ManagedPolicy> policyMap = new HashMap<>();
        private List<Role> roleList = new ArrayList<>();

        public EcsStack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);
            EcsModel ecsModel = ecsConfigModel.getEcs();

            //Certificate cert = new Certificate(this, "dev2Cecrt", CertificateProps.builder().build());
            IVpc vpc = Vpc.fromLookup(this, ecsModel.getVpc(), VpcLookupOptions.builder()
                .vpcName(ecsModel.getVpc())
                .build()
            );

            Cluster ecsCluster = createEcsCluster(ecsModel, vpc);

        }


        private Cluster createEcsCluster(final EcsModel ecsModel, final IVpc vpc) {
            // Create the ECS cluster
            Cluster cluster = new Cluster(this, ecsModel.getName(),
                ClusterProps.builder()
                    .clusterName(ecsModel.getName())
                    .vpc(vpc)
                    .build());

            Tags.of(cluster).add("Name", ecsModel.getName());
            ecsConfigModel.getTags().forEach((key, value) -> {
                log.debug("key : {}, value: {}", key, value);
                Tags.of(cluster).add(key, value);
            });

            ecsModel.getServices().forEach(ecsServiceModel -> {

                List<ISecurityGroup> securityGroups = new ArrayList<>();
                ecsServiceModel.getSecurityGroups().forEach(sg -> {
                    String paramValue = super.getSsmStringParameterV2(ecsConfigModel.getRegion(), sg);
                    ISecurityGroup securityGroup = SecurityGroup.fromLookup(this, sg + "-" + ecsServiceModel.getName(), paramValue);
                    securityGroups.add(securityGroup);
                });

                boolean assignPublicIP = false;
                List<ISubnet> subnetList = vpc.getIsolatedSubnets();
                if("PUBLIC".equalsIgnoreCase(ecsServiceModel.getSubnetType()) ) {
                    subnetList = vpc.getPublicSubnets();
                    assignPublicIP = true;
                    log.info("public subnets list size : {} ", subnetList.size());
                }else if( "PRIVATE".equalsIgnoreCase(ecsServiceModel.getSubnetType())) {
                    subnetList = vpc.getPrivateSubnets();
                    log.info("private subnets list size : {} ", subnetList.size());
                }

                TaskDefinition taskDefinition = createTaskDefinition(ecsModel, ecsServiceModel, vpc);
                FargateService fargateService = new FargateService(this, ecsServiceModel.getName(),
                    FargateServiceProps.builder()
                        .assignPublicIp(assignPublicIP)
                        .cluster(cluster)
                        .serviceName(ecsServiceModel.getName())
                        .desiredCount(1)
                        .taskDefinition(taskDefinition)
                        .securityGroups(securityGroups)
                        .vpcSubnets(SubnetSelection.builder()
                            .subnets(subnetList)
                            .availabilityZones(vpc.getAvailabilityZones())
                            .build())
                        .build());

                String paramValue = super.getSsmStringParameterV2(ecsConfigModel.getRegion(), ecsServiceModel.getTargetGroup() );
                IApplicationTargetGroup targetGroup = ApplicationTargetGroup.
                            fromTargetGroupAttributes(this, ecsServiceModel.getName() + "-tg",
                                TargetGroupAttributes.builder()
                                    .targetGroupArn(paramValue)
                                    .build());
                fargateService.attachToApplicationTargetGroup(targetGroup );

                ScalableTaskCount scalableTaskCount = fargateService.autoScaleTaskCount(EnableScalingProps.builder()
                        .minCapacity(ecsServiceModel.getTask().getAutoScaling().getMinCapacity())
                        .maxCapacity(ecsServiceModel.getTask().getAutoScaling().getMaxCapacity())
                    .build());

                scalableTaskCount.scaleOnCpuUtilization(ecsServiceModel.getName() +"-CpuAutoScaling",
                    CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(ecsServiceModel.getTask().getAutoScaling().getCpuUtilizationPercent())
                        .build());
                scalableTaskCount.scaleOnMemoryUtilization(ecsServiceModel.getName() +"-MemoryAutoScaling",
                    MemoryUtilizationScalingProps.builder()
                        .targetUtilizationPercent(ecsServiceModel.getTask().getAutoScaling().getMemUtilizationPercent())
                        .build());

                Tags.of(fargateService).add("Name", ecsServiceModel.getName());
                ecsConfigModel.getTags().forEach((key, value) -> {
                    log.debug("key : {}, value: {}", key, value);
                    Tags.of(fargateService).add(key, value);
                });


            });
            return cluster;
        }


        private TaskDefinition createTaskDefinition(final EcsModel ecsModel, final EcsModel.EcsServiceModel ecsServiceModel, final IVpc vpc) {

            EcsModel.EcsTaskModel ecsTaskModel = ecsServiceModel.getTask();
            IRole taskRole = Role.fromRoleArn(this, ecsServiceModel.getTask().getName(), ecsServiceModel.getTask().getRole());

            FargateTaskDefinition taskDefinition = new FargateTaskDefinition(this, ecsTaskModel.getName() + "-task",
                FargateTaskDefinitionProps.builder()
                    .executionRole(taskRole)
                    .cpu(ecsServiceModel.getTask().getCpu())
                    .memoryLimitMiB(ecsServiceModel.getTask().getMemory())
                    .taskRole(taskRole)
                    .family(ecsTaskModel.getName())
                    .build());

             ecsTaskModel.getContainers().forEach(ecsContainerModel -> {
                 IRepository repository = Repository.fromRepositoryName(this, ecsContainerModel.getName() + "-repo",
                         ecsContainerModel.getEcrRepoName());
                 EcrImage ecrImage = RepositoryImage.fromEcrRepository(repository, ecsContainerModel.getTag());
                 LogDriver logDriver = LogDriver.awsLogs(
                         AwsLogDriverProps.builder()
                                 .logGroup(new LogGroup(this, ecsContainerModel.getName()+"-log",
                                         LogGroupProps.builder()
                                                 .logGroupName(ecsContainerModel.getLogGroup())
                                                 .retention(ecsTaskModel.getRetentionDays())
                                                 .removalPolicy(RemovalPolicy.DESTROY)
                                                 .build()))
                                 .streamPrefix(ecsContainerModel.getStreamPrefix())
                                 .build());

                 ContainerDefinition containerDefinition = new ContainerDefinition(this,
                                            ecsContainerModel.getName(),
                         ContainerDefinitionProps
                         .builder()
                         .taskDefinition(taskDefinition)
                         .containerName(ecsContainerModel.getName())
                         .image(ecrImage)
                         .cpu(Integer.valueOf(ecsContainerModel.getCpu()))
                         .memoryLimitMiB(Integer.valueOf(ecsContainerModel.getMemory()))
                         .logging(logDriver)
                         .entryPoint(ecsContainerModel.getEntryPoint())
                         .environment(ecsContainerModel.getEnvironment())
                         .build());


                 if( ecsContainerModel.getContainerPort() > 0 && ecsContainerModel.getHostPort() > 0) {
                     log.info("inside IF.. name : {}, getHostPort : {}" , ecsContainerModel.getName(),  ecsContainerModel.getHostPort());
                     containerDefinition.addPortMappings(new PortMapping() {
                         @Override
                         public @NotNull Number getContainerPort() {
                             return ecsContainerModel.getContainerPort();
                         }
                         @Override
                         public @Nullable Number getHostPort() {
                             return ecsContainerModel.getHostPort();
                         }
                     });
                     taskDefinition.setDefaultContainer(containerDefinition);
                 }

            });

            Tags.of(taskDefinition).add("Name", ecsTaskModel.getName() + "-task");
            ecsConfigModel.getTags().forEach((key, value) -> {
                log.debug("key : {}, value: {}", key, value);
                Tags.of(taskDefinition).add(key, value);
            });

            return taskDefinition;
        }

    }

}
