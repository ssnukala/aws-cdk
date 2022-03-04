package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.SsmConfigModel;
import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.StackEnum;
import com.ppkn.cdk.config.VpcConfigModel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.Tags;
import software.amazon.awscdk.services.ec2.*;

import java.io.File;
import java.util.*;

@Slf4j
public class VpcStackHandler implements AwsStackHandler {

    private final Parameters parameters;
    private VpcConfigModel vpcConfigModel;

    public VpcStackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        new VpcStackHandler.VpcStack(parameters.getApp(), vpcConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                if( vpcConfigModel.getAccountId() != null && vpcConfigModel.getRegion() != null) {
                    return new Environment() {
                        @Override
                        public String getAccount() {
                            return vpcConfigModel.getAccountId();
                        }

                        @Override
                        public String getRegion() {
                            return vpcConfigModel.getRegion();
                        }
                    };
                }
                return null;
            }
        });
    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.vpc);
            log.info("Reading vpc config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            vpcConfigModel = mapper.readValue(new File(configFilePath), VpcConfigModel.class);
            vpcConfigModel.processPlaceHolders(parameters);

            log.debug("Done Reading config model: {}", vpcConfigModel);
            log.info("Successfully loaded config");
        } catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class VpcStack extends AbstractStack {

        public VpcStack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);
            Vpc vpc = createVpc();
            createIGW(vpc);
            Map<String, SecurityGroup> sgMap = createSecurityGroups(vpc);
            createVpcEndpoints(vpc, sgMap);

        }

        private Vpc createVpc() {
            Vpc vpc = new Vpc(this, vpcConfigModel.getName(),
                VpcProps.builder()
                    .cidr(vpcConfigModel.getCidr())
                    .maxAzs(vpcConfigModel.getMaxAzs())
                    .natGateways(vpcConfigModel.getNatGW())
                    .enableDnsHostnames(true)
                    .enableDnsSupport(true)
                    .subnetConfiguration(createSubnets())
                    .build()
            );
            super.processTags(vpc, vpcConfigModel);
            super.putSsmStringParameter(SsmConfigModel
                .builder()
                .ssmKey(vpcConfigModel.getSsmKey())
                .value(vpc.getVpcId())
                .build());
            //Subnet tage
            subnetTag(vpc.getPublicSubnets(), SubnetType.PUBLIC);
            subnetTag(vpc.getPrivateSubnets(), SubnetType.PRIVATE);
            subnetTag(vpc.getIsolatedSubnets(), SubnetType.ISOLATED);
            return vpc;
        }

        private void subnetTag(List<ISubnet> subnets, SubnetType subnetType) {
            for (ISubnet iSubnet : subnets) {
                Tags.of(iSubnet).add("Name", getSubnetTagName(subnetType) + "-" + iSubnet.getAvailabilityZone());
                vpcConfigModel.getTags().forEach((key, value) -> {
                    log.debug("key : {}, value: {}", key, value);
                    Tags.of(iSubnet).add(key, value);
                });
            }
        }

        private String getSubnetTagName(SubnetType subnetType) {

            for (VpcConfigModel.SubnetsConfigModel subnetsConfigModel : vpcConfigModel.getSubnets()) {
                if (subnetsConfigModel.getType().equalsIgnoreCase(subnetType.name())) {
                    return subnetsConfigModel.getName();
                }
            }
            return "";
        }

        private void createIGW(Vpc vpc) {
            CfnVPCGatewayAttachment igw = new CfnVPCGatewayAttachment(vpc, "GatewayStack", new CfnVPCGatewayAttachmentProps() {
                @NotNull
                public String getVpcId() {
                    return vpc.getVpcId();
                }

                public String getInternetGatewayId() {
                    return vpc.getInternetGatewayId();
                }
            });
            super.processTags(igw, vpcConfigModel);
        }

        private List<SubnetConfiguration> createSubnets() {
            List<SubnetConfiguration> configurations = new ArrayList<>();
            int index = 1;
            for(VpcConfigModel.SubnetsConfigModel subnetsConfigModel : vpcConfigModel.getSubnets()) {
                SubnetType subnetType = SubnetType.ISOLATED;
                if ("PUBLIC".equalsIgnoreCase(subnetsConfigModel.getType())) {
                    subnetType = SubnetType.PUBLIC;
                } else if ("PRIVATE".equalsIgnoreCase(subnetsConfigModel.getType())) {
                    subnetType = SubnetType.PRIVATE;
                }
                log.info("createSubnets::subnetType : {}", subnetType);
                SubnetConfiguration subnet = SubnetConfiguration
                    .builder()
                    .name(subnetsConfigModel.getName() + (index++) )
                    .subnetType(subnetType)
                    .cidrMask(subnetsConfigModel.getCidrMask())
                    .build();
                configurations.add(subnet);
            }
            return configurations;
        }

        private Map<String, SecurityGroup> createSecurityGroups(Vpc vpc) {
            Map<String, SecurityGroup> sgMap = new HashMap<>();
            vpcConfigModel.getSecurityGroups().forEach(securityGroupConfigModel -> {
                SecurityGroup securityGroup = new SecurityGroup(this, securityGroupConfigModel.getName(),
                    SecurityGroupProps.builder()
                        .vpc(vpc)
                        .allowAllOutbound(true)
                        .securityGroupName(securityGroupConfigModel.getName())
                        .description(securityGroupConfigModel.getName())
                        .build()
                );
                super.processTags(securityGroup, securityGroupConfigModel);
                super.putSsmStringParameter(SsmConfigModel
                    .builder()
                    .ssmKey(securityGroupConfigModel.getSsmKey())
                    .value(securityGroup.getSecurityGroupId())
                    .build());

                sgMap.put(securityGroupConfigModel.getName(), securityGroup);
                securityGroupConfigModel.getSecurityGroupIngress().forEach(ingressConfigModel -> {
                    IPeer source = null;
                    if ("0.0.0.0/0".equalsIgnoreCase(ingressConfigModel.getSource())) {
                        source = Peer.anyIpv4();
                    } else if (sgMap.get(ingressConfigModel.getSource()) != null) {
                        source = sgMap.get(ingressConfigModel.getSource());
                    }else {
                        source = Peer.ipv4(ingressConfigModel.getSource());
                    }
                    if( source != null) {
                        securityGroup.addIngressRule(source,
                            Port.tcp(ingressConfigModel.getFromPort()));
                    }else {
                        throw new RuntimeException("For securityGroup source cannot be null for source : " + ingressConfigModel.getSource());
                    }

                });

            });
            return sgMap;
        }

        private void createVpcEndpoints(final Vpc vpc, final Map<String, SecurityGroup> sgMap) {

            List<ISubnet> subnets = vpc.getPrivateSubnets();

            SubnetSelection privateSubnetsSelection = SubnetSelection.builder()
                .subnets(subnets)
                .availabilityZones(vpc.getAvailabilityZones())
                .build();
            vpcConfigModel.getVpcInterfaceEndPoints().forEach(vpcEndpointsConfigModel -> {
                List<SecurityGroup> sgList = Collections.singletonList(sgMap.get(vpcEndpointsConfigModel.getSecurityGroups()));

                InterfaceVpcEndpoint endPoint = new InterfaceVpcEndpoint(this, vpcEndpointsConfigModel.getName(),
                    InterfaceVpcEndpointProps.builder()
                        .vpc(vpc)
                        .privateDnsEnabled(true)
                        .subnets(privateSubnetsSelection)
                        .securityGroups(sgList)
                        .service(vpcEndpointsConfigModel.getVpcEndpointAwsService())
                        .build());
                Tags.of(endPoint).add("Name", vpcEndpointsConfigModel.getName());
                vpcConfigModel.getTags().forEach((key, value) -> {
                    log.debug("key : {}, value: {}", key, value);
                    Tags.of(endPoint).add(key, value);
                });
            });

            vpcConfigModel.getVpcGatewayEndPoints().forEach(vpcEndpointsConfigModel -> {
                GatewayVpcEndpoint endPoint = new GatewayVpcEndpoint(this, vpcEndpointsConfigModel.getName(),
                    GatewayVpcEndpointProps.builder()
                        .vpc(vpc)
                        .subnets(Collections.singletonList(privateSubnetsSelection))
                        .service(vpcEndpointsConfigModel.getVpcEndpointAwsService())
                        .build());
                Tags.of(endPoint).add("Name", vpcEndpointsConfigModel.getName());
                vpcConfigModel.getTags().forEach((key, value) -> {
                    log.debug("key : {}, value: {}", key, value);
                    Tags.of(endPoint).add(key, value);
                });
            });
        }
    }

}
