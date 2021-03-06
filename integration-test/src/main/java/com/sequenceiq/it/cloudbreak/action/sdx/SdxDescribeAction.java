package com.sequenceiq.it.cloudbreak.action.sdx;

import static java.lang.String.format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.it.cloudbreak.SdxClient;
import com.sequenceiq.it.cloudbreak.action.Action;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.sdx.SdxTestDto;
import com.sequenceiq.it.cloudbreak.log.Log;

public class SdxDescribeAction implements Action<SdxTestDto, SdxClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SdxDescribeAction.class);

    @Override
    public SdxTestDto action(TestContext testContext, SdxTestDto testDto, SdxClient client) throws Exception {
        Log.log(LOGGER, format(" Environment: %s", testDto.getRequest().getEnvironment()));
        Log.logJSON(LOGGER, " SDX describe request: ", testDto.getRequest());
        client.getSdxClient()
                .sdxEndpoint()
                .get(testDto.getName());
        Log.logJSON(LOGGER, " SDX describe response: ", client.getSdxClient().sdxEndpoint().get(testDto.getName()));
        Log.log(LOGGER, " SDX name: %s", client.getSdxClient().sdxEndpoint().get(testDto.getName()).getName());
        return testDto;
    }
}
