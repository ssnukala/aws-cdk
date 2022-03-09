package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class Ec2ConfigModel extends AbstractConfigModel{
    private List<Ec2Model> ec2;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        super.processPlaceHolders();
        ec2.forEach(instanceConfigModel -> instanceConfigModel.processPlaceHolders(parameters));
    }


    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class Ec2Model extends AbstractConfigModel {
        private String vpc;
        private String subnetType;
        private String sg;
        private String role;
        private String ami;
        private String instanceType;
        private String instanceSize;
        private String blockDeviceName;
        private int blockDeviceSize;
        private String keypair;
        private String userData;
        private int desiredCap;
        private int minCap;
        private int maxCap;
        private boolean autoScalingEnabled = true;
        private List<Ec2AlarmModel> alarms;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.vpc = replacePlaceHolders(this.vpc);
            this.subnetType = replacePlaceHolders(this.subnetType);
            this.sg = replacePlaceHolders(this.sg);
            this.role = replacePlaceHolders(this.role);
            this.keypair = replacePlaceHolders(this.keypair);

            if( alarms != null && !alarms.isEmpty()) {
                alarms.forEach(ec2AlarmModel -> ec2AlarmModel.processPlaceHolders(parameters));
            }
        }
    }


    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class Ec2AlarmModel extends AbstractConfigModel {
        private String name;
        private int threshold;
        private int dataPoints;
        private String action;
        private String metricName;
        private int metricPeriod;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.name = replacePlaceHolders(this.name);
        }
    }
}

