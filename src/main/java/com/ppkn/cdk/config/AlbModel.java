package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
@EqualsAndHashCode(callSuper = false)
public class AlbModel extends AbstractConfigModel{
    private String vpc;
    private String subnetType;
    private List<String> securityGroups;
    private List<AlbTargetGroupModel> targetGroups;
    private List<AlbListenerModel> listeners;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        this.vpc = replacePlaceHolders(this.vpc);
        List<String> sgList = new ArrayList<>();
        securityGroups.forEach(sg -> {
            sgList.add(replacePlaceHolders(sg));
        });
        securityGroups = sgList;
        targetGroups.forEach(albTargetGroupModel -> {
            albTargetGroupModel.processPlaceHolders(parameters);
        });
        listeners.forEach(albListenerModel -> {
            albListenerModel.processPlaceHolders(parameters);
        });
    }


    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class AlbTargetGroupModel extends AbstractConfigModel {
        private int port;
        private String protocol;
        private String healthCheck;
        private int intervalInSec;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
        }
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class AlbListenerModel extends AbstractConfigModel {
        private String protocol;
        private int port;
        private String defaultTargetGroup;
        private List<AlbListenerRuleModel> rules;
        private List<String> certificates;
        private boolean redirect = false;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            if( this.defaultTargetGroup != null) {
                this.defaultTargetGroup = replacePlaceHolders(this.defaultTargetGroup);
            }
            if( rules != null && !rules.isEmpty()) {
                rules.forEach(albListnerRuleModel -> {
                    albListnerRuleModel.processPlaceHolders(parameters);
                });
            }
            if( certificates != null && !certificates.isEmpty()) {
                List<String> certList = new ArrayList<>();
                certificates.forEach(s -> {
                    certList.add( replacePlaceHolders(s) );
                });
                certificates = certList;
            }

        }
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class AlbListenerRuleModel extends AbstractConfigModel {
        private String path;
        private int priority;
        private String targetGroup;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.path = replacePlaceHolders(this.path);
            this.targetGroup = replacePlaceHolders(this.targetGroup);
        }
    }
}
