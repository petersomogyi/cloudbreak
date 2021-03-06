package com.sequenceiq.cloudbreak.cmtemplate.configproviders.nifi;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cloudera.api.swagger.model.ApiClusterTemplateConfig;
import com.sequenceiq.cloudbreak.cmtemplate.configproviders.AbstractRoleConfigProvider;
import com.sequenceiq.cloudbreak.template.TemplatePreparationObject;

@Component
public class NifiConfigProvider extends AbstractRoleConfigProvider {

    @Override
    public List<ApiClusterTemplateConfig> getRoleConfigs(String roleType, TemplatePreparationObject source) {
        return List.of();
    }

    @Override
    public String getServiceType() {
        return "NIFI";
    }

    @Override
    public List<String> getRoleTypes() {
        return List.of("NIFI_NODE");
    }

}
