package com.ppkn.cdk.stacks;

import com.ppkn.cdk.config.SsmConfigModel;
import com.ppkn.cdk.config.ConfigModel;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.Tags;
import software.amazon.awscdk.services.ssm.ParameterTier;
import software.amazon.awscdk.services.ssm.ParameterType;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.ssm.StringParameterProps;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import java.util.Base64;

@Slf4j
public abstract class AbstractStack  extends Stack {

    public AbstractStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
    }

    protected void processTags(final Construct scope, final ConfigModel model) {
        log.debug("processTags key map : {}, {}, {}", model.getName(), model.getTags(), model);
        Tags.of(scope).add("Name", model.getName());
        model.getTags().forEach((key, value) -> {
            log.debug("key : {}, value: {}", key, value);
            Tags.of(scope).add(key, value);
        });
    }

    protected void putSsmStringParameter( final SsmConfigModel model) {
        StringParameter param = new StringParameter(this, model.getId(), StringParameterProps
            .builder()
            .parameterName(model.getSsmKey())
            .stringValue(model.getValue())
            .description(model.getDesc())
            .type(ParameterType.STRING)
            .tier(ParameterTier.STANDARD)
            .allowedPattern(".*")
            .build());
        model.setName(model.getSsmKey());
        this.processTags(param, model);
    }

    protected String getSsmStringParameter(final SsmConfigModel model) {
        String stringParameter = StringParameter.valueForStringParameter(this, model.getSsmKey());
        log.info("getSsmStringParameter : {}, {}, {}", stringParameter, this.resolve( stringParameter), model);
        return stringParameter;
    }

    protected String getSsmStringParameterV2(final String regionName, final String ssmKey) {
        try {
            log.info("getSsmStringParameterV2::region : {}", regionName );
            Region region = Region.of( regionName );
            SsmClient ssmClient = SsmClient.builder()
                .region(region)
                .build();
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(ssmKey)
                .build();
            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            log.info("getSsmStringParameterV2::parameterResponse : {}", parameterResponse);
            String paramValue = parameterResponse.parameter().value();
            log.info("getSsmStringParameterV2::paramValue : {}", paramValue);
            return paramValue;
        }catch (ParameterNotFoundException ex) {
            throw new RuntimeException("Ssm parameter not found : " + ssmKey);
        }catch (Exception ex) {
            log.error("Error while accessing Ssm parameter ", ex.getMessage() );
            throw new RuntimeException("Error while accessing Ssm parameter  : " + ssmKey);
        }
    }

    protected String getSecretValue(final SsmConfigModel model, String secretName) {
        Region region = Region.of( model.getRegion() );

        // Create a Secrets Manager client
        SecretsManagerClient client = SecretsManagerClient.builder()
            .region(region)
            .build();

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretName).build();
        GetSecretValueResponse getSecretValueResponse = null;

        try {
            getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
        } catch (Exception ex ) {
            // Secrets Manager can't decrypt the protected secret text using the provided KMS key.
            // Deal with the exception here, and/or rethrow at your discretion.
            throw new RuntimeException("Secret name not found : " + secretName);
        }

        String secret = null, decodedBinarySecret;
        // Decrypts secret using the associated KMS key.
        // Depending on whether the secret is a string or binary, one of these fields will be populated.
        if (getSecretValueResponse.secretString() != null) {
            secret = getSecretValueResponse.secretString();
        }
        else {
            decodedBinarySecret = new String(Base64.getDecoder().decode(getSecretValueResponse.secretBinary().asByteBuffer()).array());
        }
        return secret;
    }

}
