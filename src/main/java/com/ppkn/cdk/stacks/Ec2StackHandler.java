package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.SsmConfigModel;
import com.ppkn.cdk.config.Ec2ConfigModel;
import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.StackEnum;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.autoscaling.*;
import software.amazon.awscdk.services.autoscaling.BlockDevice;
import software.amazon.awscdk.services.autoscaling.BlockDeviceVolume;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cloudwatch.actions.AutoScalingAction;
import software.amazon.awscdk.services.cloudwatch.actions.Ec2Action;
import software.amazon.awscdk.services.cloudwatch.actions.Ec2InstanceAction;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetGroupAttributes;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awssdk.utils.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Ec2StackHandler implements AwsStackHandler {

    private final Parameters parameters;
    private Ec2ConfigModel ec2ConfigModel;

    public Ec2StackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        String accountId = ec2ConfigModel.getAccountId();
        log.info("processStack::accountId : {}", accountId);
        new Ec2Stack(parameters.getApp(), ec2ConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                return new Environment() {
                    @Override
                    public String getAccount() {
                        return accountId;
                    }

                    @Override
                    public String getRegion() {
                        return ec2ConfigModel.getRegion();
                    }
                };
            }
        });

    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.ec2);
            log.info("Reading EC2 config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            ec2ConfigModel = mapper.readValue(new File(configFilePath), Ec2ConfigModel.class);
            ec2ConfigModel.processPlaceHolders(parameters);

            log.debug("Done Reading config model: {}", ec2ConfigModel);
            log.info("Successfully loaded config model");
        }catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class Ec2Stack extends AbstractStack {

        public Ec2Stack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);
            createInstances();
        }

        int index=0;
        private int getIndex() {
            return index++;
        }

        private void createInstances() {
            ec2ConfigModel.getEc2().forEach(instanceConfigModel -> {
                //Matching Image
                LookupMachineImage image = new LookupMachineImage(LookupMachineImageProps
                            .builder()
                            .name(instanceConfigModel.getAmi())
                            .build()
                );
                IVpc vpc = Vpc.fromLookup(this, instanceConfigModel.getVpc() + "-" + getIndex(), VpcLookupOptions.builder()
                        .vpcName(instanceConfigModel.getVpc())
                        .build()
                );
                String paramValue = super.getSsmStringParameter( SsmConfigModel.builder()
                                                                    .id(instanceConfigModel.getName())
                                                                    .ssmKey(instanceConfigModel.getSg())
                                                                .build() );
                ISecurityGroup securityGroup = SecurityGroup.fromSecurityGroupId(this, instanceConfigModel.getName() + "asg-sg", paramValue);
                paramValue = super.getSsmStringParameter( SsmConfigModel.builder()
                    .id(instanceConfigModel.getName())
                    .ssmKey(instanceConfigModel.getRole())
                    .build() );
                IRole role = Role.fromRoleArn(this, instanceConfigModel.getRole()+"-role" + "-" + getIndex(), paramValue);

                List<ISubnet> subnetList = vpc.getIsolatedSubnets();
                if( instanceConfigModel.getSubnetType().equalsIgnoreCase("PUBLIC")) {
                    subnetList = vpc.getPublicSubnets();
                }else if( instanceConfigModel.getSubnetType().equalsIgnoreCase("PRIVATE")) {
                    subnetList = vpc.getPrivateSubnets();
                }
                if( instanceConfigModel.isAutoScalingEnabled()) {
                    createEc2WithAsg(vpc, subnetList, role, securityGroup, image, instanceConfigModel);
                }
                else {
                    createEc2(vpc, subnetList, role, securityGroup, image, instanceConfigModel);
                }
            });
        }

        private void createEc2WithAsg(IVpc vpc, List<ISubnet> subnetList, IRole role, ISecurityGroup securityGroup,
                                      LookupMachineImage image, Ec2ConfigModel.Ec2Model instanceConfigModel) {

            AutoScalingGroup asg = new AutoScalingGroup(this, instanceConfigModel.getName(),
                AutoScalingGroupProps
                    .builder()
                    .autoScalingGroupName(instanceConfigModel.getName())
                    .blockDevices(getAsgVolume(instanceConfigModel))
                    .instanceType(getInstanceType(instanceConfigModel))
                    .machineImage(image)
                    .vpc(vpc)
                    .securityGroup(securityGroup)
                    .role(role)
                    .keyName(instanceConfigModel.getKeypair())
                    .vpcSubnets(SubnetSelection.builder()
                        .subnets(subnetList)
                        .availabilityZones(vpc.getAvailabilityZones())
                        .build())
                    .desiredCapacity(instanceConfigModel.getDesiredCap())
                    .minCapacity(instanceConfigModel.getMinCap())
                    .maxCapacity(instanceConfigModel.getMaxCap())
                    .userData(UserData.custom(instanceConfigModel.getUserData()))
                    .build());

            if( !StringUtils.isEmpty(instanceConfigModel.getTargetGroupSsmKey())) {

                String tgArn = super.getSsmStringParameterV2(ec2ConfigModel.getRegion(), instanceConfigModel.getTargetGroupSsmKey());
                IApplicationTargetGroup targetGroup = ApplicationTargetGroup.fromTargetGroupAttributes(this,
                        instanceConfigModel.getName()+"-tg", TargetGroupAttributes.builder()
                                .targetGroupArn(tgArn)
                                .build());
                asg.attachToApplicationTargetGroup(targetGroup);
            }


//            StepScalingAction scalingAction = new StepScalingAction(this, instanceConfigModel.getName()+"-scaling",
//                    StepScalingActionProps.builder()
//                            .autoScalingGroup(asg)
//                            .adjustmentType(AdjustmentType.EXACT_CAPACITY)
//                            .build());
//
//            scalingAction.addAdjustment(AdjustmentTier.builder()
//                            .adjustment(0)
//                            .lowerBound(0)
//                    .build());

            super.putSsmStringParameter(SsmConfigModel
                    .builder()
                    .ssmKey(instanceConfigModel.getSsmKey())
                    .value(asg.getAutoScalingGroupArn())
                    .build());
            super.processTags(asg, instanceConfigModel);
        }

        private List<BlockDevice> getAsgVolume(Ec2ConfigModel.Ec2Model instanceConfigModel) {
            return Arrays.asList(new BlockDevice() {
                @Override
                public @NotNull String getDeviceName() {
                    return instanceConfigModel.getBlockDeviceName();
                }

                @Override
                public @NotNull BlockDeviceVolume getVolume() {
                    return BlockDeviceVolume.ebs(instanceConfigModel.getBlockDeviceSize());
                }
            });
        }
        private void createEc2(IVpc vpc, List<ISubnet> subnetList, IRole role, ISecurityGroup securityGroup,
                                      LookupMachineImage image, Ec2ConfigModel.Ec2Model instanceConfigModel) {
            Instance ec2Instance = new Instance(this, instanceConfigModel.getName(),
                InstanceProps
                    .builder()
                    .instanceType(getInstanceType(instanceConfigModel))
                    .machineImage(image)
                    .blockDevices(getEc2Volume(instanceConfigModel))
                    .vpc(vpc)
                    .securityGroup(securityGroup)
                    .role(role)
                    .keyName(instanceConfigModel.getKeypair())
                    .availabilityZone(vpc.getAvailabilityZones().get(0))
                    .vpcSubnets(SubnetSelection.builder()
                        .subnets(subnetList)
                        .availabilityZones(vpc.getAvailabilityZones())
                        .build())
                    .userData(UserData.custom(instanceConfigModel.getUserData()))
                    .build());

            createAlarm(ec2Instance, instanceConfigModel);

            super.putSsmStringParameter(SsmConfigModel
                    .builder()
                    .ssmKey(instanceConfigModel.getSsmKey())
                    .value(ec2Instance.getInstanceId())
                    .build());
            super.processTags(ec2Instance, instanceConfigModel);
        }

        private List<software.amazon.awscdk.services.ec2.BlockDevice> getEc2Volume(Ec2ConfigModel.Ec2Model instanceConfigModel) {
            return Arrays.asList(new software.amazon.awscdk.services.ec2.BlockDevice() {
                @Override
                public @NotNull String getDeviceName() {
                    return instanceConfigModel.getBlockDeviceName();
                }

                @Override
                public @NotNull software.amazon.awscdk.services.ec2.BlockDeviceVolume getVolume() {
                    return software.amazon.awscdk.services.ec2.BlockDeviceVolume.ebs(instanceConfigModel.getBlockDeviceSize());
                }
            });
        }
        private InstanceType getInstanceType(Ec2ConfigModel.Ec2Model instanceConfigModel) {
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

        private void createAlarm(Instance ec2Instance, Ec2ConfigModel.Ec2Model instanceConfigModel) {
            if( instanceConfigModel.getAlarms()  == null ||  instanceConfigModel.getAlarms().isEmpty()) {
                log.info("Alarm is empty for : {} ", ec2Instance.getInstanceId());
                return;
            }
            log.info("Creating alarm for instance id : {} ", ec2Instance.getInstanceId());
            Map<String, String> dimensionMap = new HashMap<>();
            dimensionMap.putIfAbsent("InstanceId", ec2Instance.getInstanceId());

            instanceConfigModel.getAlarms().forEach(ec2AlarmModel -> {
                Metric metric = new Metric(MetricProps.builder()
                    .namespace("AWS/EC2")
                    .metricName(ec2AlarmModel.getMetricName())
                    .period(Duration.minutes(ec2AlarmModel.getMetricPeriod()))
                    .statistic(Statistic.AVERAGE.name())
                    .dimensionsMap(dimensionMap)
                    .build());

                Alarm alarm = new Alarm(this, instanceConfigModel.getName()+"-alarm", AlarmProps.builder()
                    .metric(metric)
                    .evaluationPeriods(ec2AlarmModel.getDataPoints())
                    .alarmName(ec2AlarmModel.getName())
                    .alarmDescription(ec2AlarmModel.getName())
                    .threshold(ec2AlarmModel.getThreshold())
                    .comparisonOperator(ComparisonOperator.LESS_THAN_THRESHOLD)
                    .datapointsToAlarm(ec2AlarmModel.getDataPoints())
                    .treatMissingData(TreatMissingData.MISSING)
                    .build());
                if( ec2AlarmModel.getAction().equalsIgnoreCase("STOP")) {
                    alarm.addAlarmAction(new Ec2Action(Ec2InstanceAction.STOP));
                }
                else if( ec2AlarmModel.getAction().equalsIgnoreCase("TERMINATE")) {
                    alarm.addAlarmAction(new Ec2Action(Ec2InstanceAction.TERMINATE));
                }
                else if( ec2AlarmModel.getAction().equalsIgnoreCase("REBOOT")) {
                    alarm.addAlarmAction(new Ec2Action(Ec2InstanceAction.REBOOT));
                }
            });

        }
    }

}
