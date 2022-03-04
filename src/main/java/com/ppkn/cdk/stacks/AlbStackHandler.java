package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;

import java.io.File;
import java.util.*;

@Slf4j
public class AlbStackHandler implements AwsStackHandler {

    private Parameters parameters;
    private AlbConfigModel albConfigModel;

    public AlbStackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        String accountId = albConfigModel.getAccountId();
        log.info("processStack::accountId : {}", accountId);
        new AlbStack(parameters.getApp(), albConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                return new Environment() {
                    @Override
                    public String getAccount() {
                        return accountId;
                    }

                    @Override
                    public String getRegion() {
                        return albConfigModel.getRegion();
                    }
                };
            }
        });

    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.alb);
            log.info("Reading ALB config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            albConfigModel = mapper.readValue(new File(configFilePath), AlbConfigModel.class);
            albConfigModel.processPlaceHolders(parameters);
            log.debug("Done Reading config model: {}", albConfigModel);
            log.info("Successfully loaded config model");
        } catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class AlbStack extends AbstractStack {

        private Map<String, ManagedPolicy> policyMap = new HashMap<>();
        private List<Role> roleList = new ArrayList<>();

        public AlbStack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);
            albConfigModel.getAlbs().forEach(albModel -> {
                //Certificate cert = new Certificate(this, "dev2Cecrt", CertificateProps.builder().build());
                IVpc vpc = Vpc.fromLookup(this, albModel.getVpc() + "-" + albModel.getName(), VpcLookupOptions.builder()
                    .vpcName(albModel.getVpc())
                    .build()
                );
                Map<String, ApplicationTargetGroup> targetGroups = createTargetGroups(albModel, vpc);
                ApplicationLoadBalancer alb = createAlb(albModel, vpc);
                createAlbListener(albModel, vpc, alb, targetGroups);
            });
        }

        private Map<String, ApplicationTargetGroup> createTargetGroups(final AlbModel albModel, final IVpc vpc) {
            Map<String, ApplicationTargetGroup> targetGroups = new HashMap<>();
            albModel.getTargetGroups().forEach(albTargetGroupModel -> {
                ApplicationTargetGroup targetGroup = new ApplicationTargetGroup(this, albTargetGroupModel.getName(),
                    ApplicationTargetGroupProps
                        .builder()
                        .vpc(vpc)
                        .targetGroupName(albTargetGroupModel.getName())
                        .port(albTargetGroupModel.getPort())
                        .healthCheck(HealthCheck.builder()
                            .path(albTargetGroupModel.getHealthCheck())
                            .port(String.valueOf(albTargetGroupModel.getPort()))
                            .interval(Duration.seconds(albTargetGroupModel.getIntervalInSec()))
                            .build())
                        .protocol(ApplicationProtocol.HTTP)
                        .targetType(TargetType.IP)
                        .build());

                Tags.of(targetGroup).add("Name", albTargetGroupModel.getName());
                albConfigModel.getTags().forEach((key, value) -> {
                    log.debug("key : {}, value: {}", key, value);
                    Tags.of(targetGroup).add(key, value);
                });
                super.putSsmStringParameter(SsmConfigModel
                    .builder()
                    .ssmKey(albTargetGroupModel.getSsmKey())
                    .value(targetGroup.getTargetGroupArn())
                    .build());
                targetGroups.put(albTargetGroupModel.getName(), targetGroup);

            });

            return targetGroups;
        }

        private ApplicationLoadBalancer createAlb(final AlbModel albModel, final IVpc vpc) {
            boolean internetFacing = false;
            List<ISubnet> subnetList = vpc.getIsolatedSubnets();
            if( albModel.getSubnetType().equalsIgnoreCase("PUBLIC")) {
                subnetList = vpc.getPublicSubnets();
                internetFacing = true;
            }else if( albModel.getSubnetType().equalsIgnoreCase("PRIVATE")) {
                subnetList = vpc.getPrivateSubnets();
            }
            log.info("subnetList size : {} ", subnetList.size());
            if( subnetList.isEmpty()) {
                throw new RuntimeException("Subnets cannot be empty");
            }
            ApplicationLoadBalancer alb = new ApplicationLoadBalancer(this, albModel.getName(),
                ApplicationLoadBalancerProps.builder()
                    .loadBalancerName(albModel.getName())
                    .vpc(vpc)
                    .internetFacing(internetFacing)
                    .ipAddressType(IpAddressType.IPV4)
                    .vpcSubnets(SubnetSelection.builder()
                        .subnets(subnetList)
                        .availabilityZones(vpc.getAvailabilityZones())
                        .build())
                    .build());


            List<String> listOfSgIds = new ArrayList<>();  // Simple list of security group ids as strings
            albModel.getSecurityGroups().forEach(sg -> {
                String paramValue = super.getSsmStringParameterV2(albConfigModel.getRegion(), sg);
                ISecurityGroup securityGroup = SecurityGroup.fromLookup(this, sg + "-alb-sg", paramValue);
                listOfSgIds.add(securityGroup.getSecurityGroupId());
            });

            CfnLoadBalancer cfnLb = (CfnLoadBalancer) alb.getNode().getDefaultChild();
            cfnLb.addPropertyOverride("SecurityGroups", listOfSgIds);

            Tags.of(alb).add("Name", albModel.getName());
            albConfigModel.getTags().forEach((key, value) -> {
                log.debug("key : {}, value: {}", key, value);
                Tags.of(alb).add(key, value);
            });

            super.putSsmStringParameter(SsmConfigModel
                .builder()
                .ssmKey(albModel.getSsmKey())
                .value(alb.getLoadBalancerDnsName())
                .build());

            return alb;
        }

        private void createAlbListener(final AlbModel albModel, final IVpc vpc,
                      final ApplicationLoadBalancer alb, final Map<String, ApplicationTargetGroup> targetGroups) {

            albModel.getListeners().forEach(albListenerModel -> {

                ApplicationTargetGroup defaultTargetGroup = targetGroups.get(albListenerModel.getDefaultTargetGroup());
                List<ApplicationTargetGroup> defaultTargetGroupList = Collections.singletonList(defaultTargetGroup);

                ApplicationProtocol applicationProtocol = ApplicationProtocol.HTTP;
                if(albListenerModel.getProtocol().equalsIgnoreCase("https")) {
                    applicationProtocol = ApplicationProtocol.HTTPS;
                }
                List<IListenerCertificate> certificates = new ArrayList<>();
                boolean isRedirect = albListenerModel.isRedirect();
                if( isRedirect) {
                    alb.addListener(albListenerModel.getName(), BaseApplicationListenerProps
                        .builder()
                        .open(true)
                        .protocol(applicationProtocol)
                        .defaultAction(ListenerAction.redirect(RedirectOptions.builder()
                            .permanent(true)
                            .protocol(ApplicationProtocol.HTTPS.name())
                            .port("443")
                            .build()))
                        .port(albListenerModel.getPort())
                        .build());

                    return;
                }

                if(  applicationProtocol == ApplicationProtocol.HTTPS ) {
                    albListenerModel.getCertificates().forEach(certificate -> {
                        String paramValue = super.getSsmStringParameterV2( albConfigModel.getRegion(),certificate);
                        certificates.add(ListenerCertificate.fromArn(paramValue));
                    });
                }

                ApplicationListener listener = alb.addListener(albListenerModel.getName(), BaseApplicationListenerProps
                    .builder()
                    .open(true)
                    .protocol(applicationProtocol)
                    .defaultAction(ListenerAction.forward(defaultTargetGroupList))
                    .port(albListenerModel.getPort())
                    .build());

                if( !certificates.isEmpty()) {
                    listener.addCertificates(albListenerModel.getName(), certificates);
                }

                albListenerModel.getRules().forEach(albListenerRuleModel -> {
                    ApplicationTargetGroup targetGroup = targetGroups.get(albListenerRuleModel.getTargetGroup());
                    List<ApplicationTargetGroup> targetGroupList = Collections.singletonList(targetGroup);
                    List<ListenerCondition> conditions = new ArrayList<>();
                    conditions.add(ListenerCondition.pathPatterns(Collections.singletonList(albListenerRuleModel.getPath())));

                    new ApplicationListenerRule(this, albListenerRuleModel.getName(),
                        ApplicationListenerRuleProps
                            .builder()
                            .listener(listener)
                            .conditions(conditions)
                            .priority(albListenerRuleModel.getPriority())
                            .action(ListenerAction.forward(targetGroupList))
                            .build()
                    );
                });

            });

        }
    }

}
