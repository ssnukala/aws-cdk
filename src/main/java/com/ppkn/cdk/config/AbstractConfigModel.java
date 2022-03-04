package com.ppkn.cdk.config;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Data
@ToString
public abstract class AbstractConfigModel implements ConfigModel {
    private String name;
    private String ssmKey;
    private String stackName;
    private String region;
    private String accountId;
    private Map<String, String> tags = new HashMap<>();
    protected Parameters parameters;

    protected String replacePlaceHolders(String stringTobReplaced) {
        if( stringTobReplaced != null) {
            stringTobReplaced = stringTobReplaced.replaceAll("\\$\\{CDK_ENV\\}", parameters.getEnv());
            stringTobReplaced = stringTobReplaced.replaceAll("\\$\\{CDK_ORG\\}", parameters.getOrgName());
            stringTobReplaced = stringTobReplaced.replaceAll("\\$\\{CDK_PFIX\\}", parameters.getPrefix());
            stringTobReplaced = stringTobReplaced.replaceAll("\\$\\{CDK_REGION\\}", parameters.getRegion());
            stringTobReplaced = stringTobReplaced.replaceAll("\\$\\{accountId\\}", parameters.getAccountId());
        }
        return stringTobReplaced;
    }

    public String getRegion() {
        region = region.replaceAll("\\$\\{CDK_REGION\\}", parameters.getRegion());
        return region;
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
        processPlaceHolders();
    }
    protected void processPlaceHolders() {
        this.name = replacePlaceHolders(name);
        this.ssmKey = replacePlaceHolders(ssmKey);
        this.stackName = replacePlaceHolders(stackName);
        this.accountId = parameters.getAccountId();

        tags.forEach((key,value) -> {
            tags.put(key, replacePlaceHolders(value));
        });
    }

    public String getName() {
        return this.name;
    }
    public Map<String, String> getTags() {
        return tags;
    }
}
