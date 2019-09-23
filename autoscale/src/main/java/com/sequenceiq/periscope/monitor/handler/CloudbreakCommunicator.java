package com.sequenceiq.periscope.monitor.handler;

import static com.sequenceiq.cloudbreak.api.model.flow.StateStatus.PENDING;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.model.FailureReport;
import com.sequenceiq.cloudbreak.api.model.flow.FlowLogResponse;
import com.sequenceiq.cloudbreak.api.model.stack.StackResponse;
import com.sequenceiq.cloudbreak.client.CloudbreakClient;
import com.sequenceiq.periscope.service.configuration.CloudbreakClientConfiguration;

@Service
public class CloudbreakCommunicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudbreakCommunicator.class);

    @Inject
    private CloudbreakClientConfiguration cloudbreakClientConfiguration;

    public StackResponse getById(long cloudbreakStackId) {
        CloudbreakClient cloudbreakClient = cloudbreakClientConfiguration.cloudbreakClient();
        return cloudbreakClient.autoscaleEndpoint().get(cloudbreakStackId);
    }

    public void failureReport(long stackId, FailureReport failureReport) {
        CloudbreakClient cloudbreakClient = cloudbreakClientConfiguration.cloudbreakClient();
        if (hasStackActiveFlow(stackId)) {
            LOGGER.info("Skip failureReport, cluster has an active flow in progress in Cloudbreak!");
        } else {
            cloudbreakClient.autoscaleEndpoint().failureReport(stackId, failureReport);
        }
    }

    private boolean hasStackActiveFlow(Long stackId) {
        CloudbreakClient cloudbreakClient = cloudbreakClientConfiguration.cloudbreakClient();
        List<FlowLogResponse> flowLogsByResourceName = cloudbreakClient.flowEndpoint().getFlowLogsByResourceId(stackId);
        return flowLogsByResourceName.stream().anyMatch(flowLog -> !flowLog.getFinalized() || flowLog.getStateStatus().equals(PENDING));
    }
}
