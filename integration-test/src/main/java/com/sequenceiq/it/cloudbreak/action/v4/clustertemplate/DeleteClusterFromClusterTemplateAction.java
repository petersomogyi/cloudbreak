package com.sequenceiq.it.cloudbreak.action.v4.clustertemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.it.cloudbreak.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.action.Action;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.clustertemplate.ClusterTemplateTestDto;
import com.sequenceiq.it.cloudbreak.dto.stack.StackTemplateTestDto;
import com.sequenceiq.it.cloudbreak.log.Log;

public class DeleteClusterFromClusterTemplateAction implements Action<ClusterTemplateTestDto, CloudbreakClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteClusterFromClusterTemplateAction.class);

    private String stackTemplateKey;

    public DeleteClusterFromClusterTemplateAction(String stackTemplateKey) {
        this.stackTemplateKey = stackTemplateKey;
    }

    public DeleteClusterFromClusterTemplateAction(Class<StackTemplateTestDto> stackTemplateKey) {
        this.stackTemplateKey = stackTemplateKey.getSimpleName();
    }

    @Override
    public ClusterTemplateTestDto action(TestContext testContext, ClusterTemplateTestDto testDto, CloudbreakClient client) throws Exception {
        if (testDto.getResponse() == null)  {
//            Log.logJSON(LOGGER, "Cluster response is null", testDto.getRequest().getStackTemplate());
            return testDto;
        }
//        Log.logJSON(LOGGER, "Stack from template post request:\n", testDto.getRequest().getStackTemplate());
        StackTemplateTestDto stackEntity = testContext.get(stackTemplateKey);
        client.getCloudbreakClient()
                .stackV4Endpoint()
                .delete(client.getWorkspaceId(), stackEntity.getResponse().getName(), false);
        Log.logJSON(LOGGER, " Stack from template created  successfully:\n", testDto.getResponse());
        Log.log(LOGGER, "Stack from template ID: " + testDto.getResponse().getId());
        return testDto;
    }
}
