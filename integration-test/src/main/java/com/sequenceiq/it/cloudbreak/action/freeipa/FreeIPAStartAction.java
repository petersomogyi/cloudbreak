package com.sequenceiq.it.cloudbreak.action.freeipa;

import static java.lang.String.format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.it.cloudbreak.FreeIPAClient;
import com.sequenceiq.it.cloudbreak.action.Action;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIPATestDto;
import com.sequenceiq.it.cloudbreak.log.Log;

public class FreeIPAStartAction implements Action<FreeIPATestDto, FreeIPAClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeIPAStartAction.class);

    public FreeIPATestDto action(TestContext testContext, FreeIPATestDto testDto, FreeIPAClient client) throws Exception {
        Log.log(LOGGER, format(" Name: %s", testDto.getRequest().getName()));
        Log.logJSON(LOGGER, format(" FreeIPA start request: %n"), testDto.getRequest());
        client.getFreeIpaClient()
                .getFreeIpaV1Endpoint()
                .start(testDto.getRequest().getEnvironmentCrn());
        return testDto;
    }
}
