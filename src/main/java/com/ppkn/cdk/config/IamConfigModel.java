package com.ppkn.cdk.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class IamConfigModel extends AbstractConfigModel{
    private Map<String, PolicyConfigModel> policies = new HashMap<>();
    private List<RoleConfigModel> roles;
    private List<GroupConfigModel> groups;

    public void processPlaceHolders(final Parameters parameters) {
        super.setParameters(parameters);
        super.processPlaceHolders();
        updatePolicies(parameters);
        updateRoles(parameters);
        updateGroups(parameters);
    }

    public void updatePolicies(final Parameters parameters) {
        policies.forEach((key, value) -> value.processPlaceHolders(parameters));
    }

    public void updateRoles(final Parameters parameters) {
        roles.forEach(roleConfigModel -> roleConfigModel.processPlaceHolders(parameters));
    }

    public void updateGroups(final Parameters parameters) {
        groups.forEach(groupConfigModel -> groupConfigModel.processPlaceHolders(parameters));
    }

    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class RoleConfigModel extends AbstractConfigModel {
        private List<String> policies;
        private List<String> assumedBy;

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            List<String> policiesList = new ArrayList<>();
            policies.forEach(policy -> policiesList.add(replacePlaceHolders(policy)));
            policies = policiesList;
        }
    }
    @Data
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static class GroupConfigModel extends RoleConfigModel {
        public void processPlaceHolders(final Parameters parameters) {
            super.processPlaceHolders(parameters);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @ToString
    public static class DocumentConfigModel extends AbstractConfigModel {
        private String effect;
        private List<String> actions;
        private List<String> notActions;
        private List<String> resources;
        private Map<String, Map<String, Object >> conditions = new HashMap<>();

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            List<String> resourcesList = new ArrayList<>();
            resources.forEach(resource -> resourcesList.add(replacePlaceHolders(resource)));
            resources = resourcesList;
            conditions.forEach((oKey, oValue) -> {
                oValue.forEach((iKey, iValues) -> {
                    if( iValues instanceof List) {
                        List<String> contds = new ArrayList<>();
                        ((List<String>)iValues).forEach(iValue -> contds.add(replacePlaceHolders(iValue)));
                        oValue.put(iKey, contds);
                    }else {
                        oValue.put(iKey, replacePlaceHolders((String)iValues));
                    }
                } );
            });
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @ToString
    public static class PolicyConfigModel extends AbstractConfigModel {
        private Map<String, DocumentConfigModel> documents = new HashMap<>();

        public void processPlaceHolders(final Parameters parameters) {
            super.setParameters(parameters);
            documents.forEach((oKey, oValue) -> oValue.processPlaceHolders(parameters));
        }
    }
}

