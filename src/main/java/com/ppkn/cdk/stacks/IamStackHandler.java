package com.ppkn.cdk.stacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ppkn.cdk.config.IamConfigModel;
import com.ppkn.cdk.config.Parameters;
import com.ppkn.cdk.config.SsmConfigModel;
import com.ppkn.cdk.config.StackEnum;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.core.Tags;
import software.amazon.awscdk.services.iam.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class IamStackHandler implements AwsStackHandler {

    private final Parameters parameters;
    private IamConfigModel iamConfigModel;

    public IamStackHandler(final Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void processStack() {
        readConfigModel();
        String accountId = iamConfigModel.getAccountId();
        log.info("processStack::accountId : {}", accountId);
        new IamStack(parameters.getApp(), iamConfigModel.getStackName(), new StackProps() {
            @Override
            public @Nullable Environment getEnv() {
                return new Environment() {
                    @Override
                    public String getAccount() {
                        return accountId;
                    }

                    @Override
                    public String getRegion() {
                        return iamConfigModel.getRegion();
                    }
                };
            }
        });

    }

    private void readConfigModel() {
        try {
            String configFilePath = parameters.getConfigPath(StackEnum.iam);
            log.info("Reading config model : {} ", configFilePath);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.findAndRegisterModules();
            iamConfigModel = mapper.readValue(new File(configFilePath), IamConfigModel.class);
            iamConfigModel.processPlaceHolders(parameters);

            log.debug("Done Reading config model: {}", iamConfigModel);
            log.info("Successfully loaded config model");
        }catch (Exception e) {
            log.error("Error in populateStackMap : ", e);
            throw new RuntimeException(e);
        }
    }

    public class IamStack extends AbstractStack {

        private final Map<String, ManagedPolicy> policyMap = new HashMap<>();

        public IamStack(final Construct scope, final String id, final StackProps props) {
            super(scope, id, props);

            createPolicies();
            createRoles();
            createGroups();
        }

        private void createPolicies() {
            Map<String, IamConfigModel.PolicyConfigModel> policies = iamConfigModel.getPolicies();

            policies.forEach((mapKey, policyConfigModel) -> {
                List<PolicyStatement> policyStatementList = new ArrayList<>();
                policyConfigModel.getDocuments().forEach((docName, documentConfigModel)->{

                    PolicyStatement policyStatement = new PolicyStatement(PolicyStatementProps
                        .builder()
                        .sid(docName + "" +(policyStatementList.size()))
                        .effect( "Deny".equalsIgnoreCase(documentConfigModel.getEffect()) ? Effect.DENY : Effect.ALLOW )
                        .actions(documentConfigModel.getActions())
                        .resources(documentConfigModel.getResources())
                        .conditions(documentConfigModel.getConditions())
                        .build());
                    if( documentConfigModel.getNotActions() != null &&  !documentConfigModel.getNotActions().isEmpty()) {
                        documentConfigModel.getNotActions().forEach(notAction->policyStatement.addNotActions(notAction));
                    }
                    policyStatementList.add(policyStatement);
                });

                ManagedPolicy managedPolicy = new ManagedPolicy(this, policyConfigModel.getName(), ManagedPolicyProps
                    .builder()
                    .managedPolicyName(policyConfigModel.getName())
                    .statements(policyStatementList)
                    .description(policyConfigModel.getName())
                    .build());

                super.processTags(managedPolicy, policyConfigModel);
                policyMap.put(policyConfigModel.getName(), managedPolicy);
            });
        }

        private void createRoles() {

            List<IamConfigModel.RoleConfigModel> roles = iamConfigModel.getRoles();
            roles.forEach(roleConfigModel -> {
                CompositePrincipal compositePrincipal = null;
                for(String assumeRole : roleConfigModel.getAssumedBy()) {
                    if(compositePrincipal == null) {
                        compositePrincipal = new CompositePrincipal(new ServicePrincipal(assumeRole));
                    }else {
                        compositePrincipal.addPrincipals(new ServicePrincipal(assumeRole));
                    }
                }
                Role role = new Role(this, roleConfigModel.getName(), RoleProps.builder()
                    .roleName(roleConfigModel.getName())
                    .assumedBy(compositePrincipal)
                    .build());
                roleConfigModel.getPolicies().forEach(policyName -> role.addManagedPolicy(policyMap.get(policyName)));
                Tags.of(role).add("Name", roleConfigModel.getName());
                iamConfigModel.getTags().forEach((key, value) -> {
                    log.info("key : {}, value: {}", key, value);
                    Tags.of(role).add(key, value);
                });
                super.processTags(role, roleConfigModel );
                super.putSsmStringParameter(SsmConfigModel
                    .builder()
                    .ssmKey(roleConfigModel.getSsmKey())
                    .value(role.getRoleArn())
                    .build());
            });

        }


        private void createGroups() {

            List<IamConfigModel.GroupConfigModel> groups = iamConfigModel.getGroups();
            log.info("createGroups groups size : {}", groups.size());
            log.info("createGroups policyMap size : {}", policyMap.size());
            groups.forEach(groupConfigModel -> {

                Group group = new Group(this, groupConfigModel.getName(), GroupProps
                    .builder()
                    .groupName(groupConfigModel.getName())
                    .build() );

                groupConfigModel.getPolicies().forEach(policyName -> {
                    log.info("createGroups policyName : {}", policyName);
                    group.addManagedPolicy(policyMap.get(policyName));
                    }
                );
                Tags.of(group).add("Name", groupConfigModel.getName());
                iamConfigModel.getTags().forEach((key, value) -> {
                    log.info("key : {}, value: {}", key, value);
                    Tags.of(group).add(key, value);
                });
                super.processTags(group, groupConfigModel );
                super.putSsmStringParameter(SsmConfigModel
                    .builder()
                    .ssmKey(groupConfigModel.getSsmKey())
                    .value(group.getGroupArn())
                    .build());
            });

        }
    }

}
