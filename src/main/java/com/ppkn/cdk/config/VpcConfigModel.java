package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import software.amazon.awscdk.services.ec2.GatewayVpcEndpointAwsService;
import software.amazon.awscdk.services.ec2.InterfaceVpcEndpointAwsService;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class VpcConfigModel extends AbstractConfigModel{
    private String cidr;
    private int maxAzs;
    private int natGW;
    private List<SubnetsConfigModel> subnets = new ArrayList<>();
    private List<SecurityGroupConfigModel> securityGroups = new ArrayList<>();
    private List<VpcInterfaceEndpointsConfigModel> vpcInterfaceEndPoints = new ArrayList<>();
    private List<VpcGatewayEndpointsConfigModel> vpcGatewayEndPoints = new ArrayList<>();

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        super.processPlaceHolders();
        subnets.forEach(subnetsConfigModel -> subnetsConfigModel.processPlaceHolders(parameters));
        securityGroups.forEach(securityGroupConfigModel -> securityGroupConfigModel.processPlaceHolders(parameters));
        vpcInterfaceEndPoints.forEach(vpcEndPointsGroupConfigModel -> vpcEndPointsGroupConfigModel.processPlaceHolders(parameters));
        vpcGatewayEndPoints.forEach(vpcEndPointsGroupConfigModel -> vpcEndPointsGroupConfigModel.processPlaceHolders(parameters));
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @ToString(callSuper = true)
    public static class SubnetsConfigModel extends AbstractConfigModel {
        private String type;
        private int cidrMask;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @ToString(callSuper = true)
    public static class SecurityGroupConfigModel extends AbstractConfigModel {
        private String type;
        private String cidrMask;
        private List<SecurityGroupIngressConfigModel> securityGroupIngress = new ArrayList<>();

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            securityGroupIngress.forEach(ingressConfigModel -> ingressConfigModel.processPlaceHolders(parameters));
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @ToString(callSuper = true)
    public static class SecurityGroupIngressConfigModel extends AbstractConfigModel {
        private String source;
        private int fromPort;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.source = replacePlaceHolders(this.source);
        }
    }


    @Data
    @EqualsAndHashCode(callSuper = false)
    @ToString(callSuper = true)
    public static class VpcInterfaceEndpointsConfigModel extends AbstractConfigModel {
        private String service;
        private String securityGroups;
        private String subnetsType;

        public InterfaceVpcEndpointAwsService getVpcEndpointAwsService() {
            switch(service) {
                case "ecr":
                    return InterfaceVpcEndpointAwsService.ECR_DOCKER;
                case "ecr-api":
                    return InterfaceVpcEndpointAwsService.ECR;
                case "secretsmanager":
                    return InterfaceVpcEndpointAwsService.SECRETS_MANAGER;
                case "logs":
                    return InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS;
            }
            return null;
        }
        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.securityGroups = replacePlaceHolders(this.securityGroups);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @ToString(callSuper = true)
    public static class VpcGatewayEndpointsConfigModel extends AbstractConfigModel {
        private String service;
        private String securityGroups;
        private String subnetsType;

        public GatewayVpcEndpointAwsService getVpcEndpointAwsService() {
            switch(service) {
                case "S3":
                    return GatewayVpcEndpointAwsService.S3;
            }
            return null;
        }
        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.securityGroups = replacePlaceHolders(this.securityGroups);
        }
    }
}
