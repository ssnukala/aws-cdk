package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class GlobalConfigModel  extends AbstractConfigModel{
    private List<S3Model> s3Buckets;
    private List<SnsModel> sns;
    private List<SqsModel> sqs;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        super.processPlaceHolders();
        s3Buckets.forEach(s3Model -> s3Model.processPlaceHolders(parameters));
        if( sns != null &&  !sns.isEmpty()) {
            sns.forEach(snsModel -> snsModel.processPlaceHolders(parameters));
        }
        if ( sqs != null && !sqs.isEmpty()) {
            sqs.forEach(sqsModel -> sqsModel.processPlaceHolders(parameters));
        }

    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = false)
    public static class S3Model extends AbstractConfigModel {
        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
        }
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = false)
    public static class SnsModel extends AbstractConfigModel {
        private List<String> emailAddress = new ArrayList<>();
        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
        }
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = false)
    public static class SqsModel extends AbstractConfigModel {
        private boolean fifo = false;
        private int retentionPeriod = 7;
        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
        }
    }
}
