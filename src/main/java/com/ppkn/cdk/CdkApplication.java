package com.ppkn.cdk;

import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.processor.StackProcessor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.core.App;

@Slf4j
public class CdkApplication {

    public CdkApplication() {
        log.info("CDK Stack create/update process started..");
        new StackProcessor(buildParameters());
    }

    private Parameters buildParameters() {
        log.info("Reading parameters..");
        return Parameters.builder(new App())
            .withEnv("env")
            .withPath("path")
            .withStack("stack")
            .build();
    }

	public static void main(String[] args) {
        new CdkApplication();
	}

}
