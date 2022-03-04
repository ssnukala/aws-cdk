package com.ppkn.cdk.config;

import com.ppkn.cdk.util.KeystoreHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.core.App;

import java.util.Objects;

@Data
@Slf4j
public class Parameters {
    protected String env;
    protected String path;
    protected String stack;
    protected String accountId;
    protected String version;
    protected String logLevel;
    protected String region;
    protected String prefix;
    protected String orgName;
    protected String keystorePath;
    protected String keystorePwd;
    protected String keystoreKeyPwd;

    protected StackEnum stackEnum;
    protected App app;
    protected KeystoreHandler keystoreHandler;
    private static final String FILE_SEP = System.getProperty("file.separator");

    private Parameters(){}
    private Parameters(final App app) {
        this.app = app;
    }

    public String getConfigPath(StackEnum stackEnum) {
        StringBuilder builder = new StringBuilder(path);
        builder.append(FILE_SEP)
                .append(env).append(FILE_SEP)
                .append(stackEnum.name() + "-stack.yml");
        return builder.toString();
    }

    public static Parameters.Builder builder(final App app)  {
        return new Parameters.Builder(app);
    }

    public static class Builder extends Parameters {

        public Builder(final App stackApp) {
            app = stackApp;
            env = getContextValue("env");
            path = getContextValue("path");
            stack = getContextValue("stack");
            version = getContextValue("version");
            logLevel = getContextValue("logLevel");
            region = getContextValue("region");
            prefix = getContextValue("prefix");
            orgName = getContextValue("org");
            keystorePath = getContextValue("keystorePath");
            keystorePwd = getContextValue("keystorePwd");
            keystoreKeyPwd = getContextValue("keystoreKeyPwd");
            keystoreHandler = new KeystoreHandler(keystorePath, keystorePwd, keystoreKeyPwd);
        }

        public Parameters.Builder withEnv(String paramName) {
            log.info("Env param : {}, value: {}", paramName, this.env);
            Objects.requireNonNull(env, "Environment variable must be supplied");
            this.accountId = keystoreHandler.getValueFromKeystore(this.env + ".accountid");
            log.info("accountId : {}", this.accountId);
            return this;
        }

        public Parameters.Builder withPath(String paramName) {
            log.info("path param : {}, value: {}", paramName, this.path);
            Objects.requireNonNull(path, "Path variable must be supplied");
            return this;
        }

        public Parameters.Builder withStack(String paramName) {
            log.info("stack param : {}, value: {}", paramName, this.stack);
            Objects.requireNonNull(stack, "Stack variable must be supplied");
            this.stackEnum = StackEnum.valueOf(stack);
            return this;
        }

        private String getContextValue(String paramName) {
            return (String) app
                .getNode()
                .tryGetContext(paramName);
        }

        public Parameters build() {
            Parameters parameters = new Parameters(app);
            parameters.setEnv(this.env);
            parameters.setPath(this.path);
            parameters.setStackEnum(this.stackEnum);
            parameters.setAccountId(this.accountId);
            parameters.setStack(this.stack);
            parameters.setVersion(this.version);
            parameters.setLogLevel(this.logLevel);
            parameters.setRegion(this.region);
            parameters.setPrefix(this.prefix);
            parameters.setOrgName(this.orgName);

            log.info("build parameters : {}", parameters);
            return parameters;
        }
    }
}
