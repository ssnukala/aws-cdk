package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class AlbConfigModel extends AbstractConfigModel{
    private List<AlbModel> albs;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        super.processPlaceHolders();
        albs.forEach(alb-> alb.processPlaceHolders(parameters));
    }

}

