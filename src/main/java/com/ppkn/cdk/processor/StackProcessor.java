package com.ppkn.cdk.processor;

import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.StackEnum;
import com.ppkn.cdk.stacks.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class StackProcessor {

    private Map<StackEnum, AwsStackHandler> stackMap = new HashMap<>();

    public StackProcessor(Parameters parameters) {
        log.info("Initializing stacks....");
        stackMap.put(StackEnum.iam, new IamStackHandler(parameters));
        stackMap.put(StackEnum.vpc, new VpcStackHandler(parameters));
        stackMap.put(StackEnum.ec2, new Ec2StackHandler(parameters));
        stackMap.put(StackEnum.ecs, new EcsStackHandler(parameters));
        stackMap.put(StackEnum.global, new GlobalStackHandler(parameters));
        stackMap.put(StackEnum.rds, new RdsStackHandler(parameters));
        stackMap.put(StackEnum.alb, new AlbStackHandler(parameters));
        stackMap.put(StackEnum.aux, new AuxStackHandler(parameters));

        log.info("Processing stack started : {}", parameters.getStack());
        stackMap.get(parameters.getStackEnum()).processStack();
        log.info("Processing stack ended : {}", parameters.getStack());
        parameters.getApp().synth();
    }
}

