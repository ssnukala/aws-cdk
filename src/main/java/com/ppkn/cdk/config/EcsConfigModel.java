package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class EcsConfigModel extends AbstractConfigModel{
    private EcsModel ecs;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        super.processPlaceHolders();
        ecs.processPlaceHolders(parameters);
    }

}

