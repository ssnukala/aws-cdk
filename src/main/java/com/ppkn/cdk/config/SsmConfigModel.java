package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString
@EqualsAndHashCode(callSuper = false)
public class SsmConfigModel extends AbstractConfigModel{
    private String id;
    private String region;
    private String ssmKey;
    private String value;
    private String desc;
    private static SsmConfigModel.Builder builder;


    public static SsmConfigModel.Builder builder() {
        builder = new SsmConfigModel.Builder();
        return builder;
    }

    @Data
    @ToString
    public static class Builder {
        private String id;
        private String ssmKey;
        private String value;
        private String desc;

        public SsmConfigModel.Builder id(String id) {
            this.id = id;
            return this;
        }

        public SsmConfigModel.Builder ssmKey(String ssmKey) {
            this.ssmKey = ssmKey;
            return this;
        }

        public SsmConfigModel.Builder value(String value) {
            this.value = value;
            return this;
        }

        public SsmConfigModel.Builder desc(String desc) {
            this.desc = desc;
            return this;
        }

        public SsmConfigModel build() {
            this.id = this.id != null ? this.id : this.ssmKey + "-id";
            this.value = this.value != null ? this.value : "";
            this.desc = this.desc != null ? this.desc : this.ssmKey;
            SsmConfigModel model = new SsmConfigModel();
            model.setId(this.id);
            model.setSsmKey(this.ssmKey);
            model.setValue(this.value);
            model.setDesc(this.desc);
            return model;
        }
    }
}
