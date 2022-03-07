package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ToString
@EqualsAndHashCode(callSuper = false)
public class EcsModel extends AbstractConfigModel{
    private String name;
    private String vpc;
    private List<EcsServiceModel> services;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        this.name = replacePlaceHolders(this.name);
        this.vpc = replacePlaceHolders(this.vpc);
        services.forEach(ecsServiceModel -> {
            ecsServiceModel.processPlaceHolders(parameters);
        });
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class EcsServiceModel extends AbstractConfigModel {
        private String name;
        private String targetGroup;
        private String subnetType;
        private List<String> securityGroups;
        private EcsTaskModel task;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.name = replacePlaceHolders(this.name);
            this.targetGroup = replacePlaceHolders(this.targetGroup);
            List<String> sgList = new ArrayList<>();
            securityGroups.forEach(s -> {
                sgList.add( replacePlaceHolders(s) );
            });
            securityGroups = sgList;
            task.processPlaceHolders(parameters);
        }
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class EcsTaskModel extends AbstractConfigModel {
        private String name;
        private int cpu;
        private int memory;
        private RetentionDays retentionDays;
        private String role;
        private List<EcsContainerModel> containers;
        private EcsTaskAutoScalingModel autoScaling;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.name = replacePlaceHolders(this.name);
            this.role = replacePlaceHolders(this.role);
            containers.forEach(ecsContainerModel -> ecsContainerModel.processPlaceHolders(parameters));
        }
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class EcsContainerModel extends AbstractConfigModel {
        private String name;
        private String ecrRepoName;
        private String tag;
        private int containerPort;
        private int hostPort;
        private String logGroup;
        private String streamPrefix;
        private String cpu;
        private String memory;
        private List<String> entryPoint;
        private Map<String, String> environment = new HashMap<>();

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.name = replacePlaceHolders(this.name);
            this.ecrRepoName = replacePlaceHolders(this.ecrRepoName);
            this.logGroup = replacePlaceHolders(this.logGroup);
            this.streamPrefix = replacePlaceHolders(this.streamPrefix);
        }
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class EcsTaskAutoScalingModel extends AbstractConfigModel {
        private int memUtilizationPercent;
        private int cpuUtilizationPercent;
        private int minCapacity;
        private int maxCapacity;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
        }
    }
}
