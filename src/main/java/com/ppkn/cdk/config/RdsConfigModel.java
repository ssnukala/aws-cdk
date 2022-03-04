package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@EqualsAndHashCode(callSuper = false)
public class RdsConfigModel extends AbstractConfigModel{

    private List<RdsModel> rds;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        rds.forEach(rdsModel -> {
            rdsModel.processPlaceHolders(parameters);
        });
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class RdsModel extends AbstractConfigModel{
        private String vpc;
        private String subnetType;
        private String sg;
        private String instanceType;
        private String instanceSize;
        private String secretName;
        private int port;
        private int allocatedStorage;
        private int backupRetention;
        private String snapshotIdentifier;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.vpc = replacePlaceHolders(vpc);
            this.sg = replacePlaceHolders(sg);
            this.secretName = replacePlaceHolders(secretName);
        }
    }
}
