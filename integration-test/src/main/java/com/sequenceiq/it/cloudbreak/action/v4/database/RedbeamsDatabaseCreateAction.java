package com.sequenceiq.it.cloudbreak.action.v4.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.it.cloudbreak.RedbeamsClient;
import com.sequenceiq.it.cloudbreak.action.Action;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.database.RedbeamsDatabaseTestDto;
import com.sequenceiq.it.cloudbreak.log.Log;

public class RedbeamsDatabaseCreateAction implements Action<RedbeamsDatabaseTestDto, RedbeamsClient> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedbeamsDatabaseCreateAction.class);

    @Override
    public RedbeamsDatabaseTestDto action(TestContext testContext, RedbeamsDatabaseTestDto testDto, RedbeamsClient client) throws Exception {
        Log.logJSON(LOGGER, " Database register request:\n", testDto.getRequest());
        testDto.setResponse(
                client.getEndpoints()
                        .databaseV4Endpoint()
                        .register(testDto.getRequest()));
        Log.logJSON(LOGGER, " Database registered successfully:\n", testDto.getResponse());
        Log.log(LOGGER, String.format(" CRN: %s", testDto.getResponse().getCrn()));

        return testDto;
    }

}
