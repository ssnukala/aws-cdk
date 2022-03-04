package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class AuxConfigModel extends AbstractConfigModel{
    private List<ElasticCacheModel> elasticCache;
    private List<ElasticSearchModel> elasticSearch;
    private List<CloudFrontModel> cloudFront;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        super.processPlaceHolders();
        elasticCache.forEach(elasticCacheModel -> elasticCacheModel.processPlaceHolders(parameters));
        elasticSearch.forEach(elasticSearchModel -> elasticSearchModel.processPlaceHolders(parameters));
        cloudFront.forEach(cloudFrontModel -> cloudFrontModel.processPlaceHolders(parameters));
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = false)
    public static class ElasticCacheModel extends AbstractConfigModel {
        private String vpc;
        private String engine;
        private String cacheNodeType;
        private int numCacheNodes;
        private String clusterName;
        private List<String> securityGroups;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.vpc = replacePlaceHolders(this.vpc);
            this.clusterName = replacePlaceHolders(this.clusterName);
            List<String> sgList = new ArrayList<>();
            securityGroups.forEach(sg -> {
                sgList.add(replacePlaceHolders(sg));
            });
            securityGroups = sgList;
        }
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = false)
    public static class ElasticSearchModel extends AbstractConfigModel {
        private String vpc;
        private boolean slowSearchLogEnabled;
        private boolean appLogEnabled;
        private boolean slowIndexLogEnabled;
        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.vpc = replacePlaceHolders(this.vpc);
        }
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = false)
    public static class CloudFrontModel extends AbstractConfigModel {
        private String s3Bucket;
        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            this.s3Bucket = replacePlaceHolders(this.s3Bucket);
        }
    }
}
