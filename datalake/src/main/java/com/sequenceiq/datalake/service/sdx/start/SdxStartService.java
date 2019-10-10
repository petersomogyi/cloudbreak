package com.sequenceiq.datalake.service.sdx.start;

import java.util.Collections;

import javax.inject.Inject;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.dyngr.Polling;
import com.dyngr.core.AttemptResult;
import com.dyngr.core.AttemptResults;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.StackV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.cluster.ClusterV4Response;
import com.sequenceiq.cloudbreak.cloud.scheduler.PollGroup;
import com.sequenceiq.cloudbreak.common.exception.ClientErrorExceptionHandler;
import com.sequenceiq.cloudbreak.common.json.JsonUtil;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.datalake.entity.DatalakeStatusEnum;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.flow.SdxReactorFlowManager;
import com.sequenceiq.datalake.flow.statestore.DatalakeInMemoryStateStore;
import com.sequenceiq.datalake.service.sdx.PollingConfig;
import com.sequenceiq.datalake.service.sdx.SdxService;
import com.sequenceiq.datalake.service.sdx.status.SdxStatusService;

@Component
public class SdxStartService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SdxStartService.class);

    @Inject
    private SdxReactorFlowManager sdxReactorFlowManager;

    @Inject
    private SdxService sdxService;

    @Inject
    private StackV4Endpoint stackV4Endpoint;

    @Inject
    private SdxStatusService sdxStatusService;

    public void triggerStart(SdxCluster cluster) {
        MDCBuilder.buildMdcContext(cluster);
        sdxReactorFlowManager.triggerSdxStartFlow(cluster.getId());
    }

    public void start(Long sdxId) {
        SdxCluster sdxCluster = sdxService.getById(sdxId);
        try {
            LOGGER.info("Triggering start flow for cluster {}", sdxCluster.getClusterName());
            stackV4Endpoint.putStart(0L, sdxCluster.getClusterName());
            sdxStatusService.setStatusForDatalakeAndNotify(DatalakeStatusEnum.START_IN_PROGRESS, ResourceEvent.SDX_START_STARTED,
                    "Datalake start in progress", sdxCluster);
        } catch (NotFoundException e) {
            LOGGER.info("Can not find stack on cloudbreak side {}", sdxCluster.getClusterName());
        } catch (ClientErrorException e) {
            String errorMessage = ClientErrorExceptionHandler.getErrorMessage(e);
            LOGGER.info("Can not start stack {} from cloudbreak: {}", sdxCluster.getStackId(), errorMessage, e);
            throw new RuntimeException("Can not start stack, client error happened on Cloudbreak side: " + errorMessage);
        } catch (WebApplicationException e) {
            LOGGER.info("Can not start stack {} from cloudbreak: {}", sdxCluster.getStackId(), e.getMessage(), e);
            throw new RuntimeException("Can not start stack, web application error happened on Cloudbreak side: " + e.getMessage());
        }
    }

    public void waitCloudbreakCluster(Long sdxId, PollingConfig pollingConfig) {
        SdxCluster sdxCluster = sdxService.getById(sdxId);
        Polling.waitPeriodly(pollingConfig.getSleepTime(), pollingConfig.getSleepTimeUnit())
                .stopIfException(pollingConfig.getStopPollingIfExceptionOccured())
                .stopAfterDelay(pollingConfig.getDuration(), pollingConfig.getDurationTimeUnit())
                .run(() -> checkClusterStatusDuringStart(sdxCluster));
    }

    protected AttemptResult<StackV4Response> checkClusterStatusDuringStart(SdxCluster sdxCluster) throws JsonProcessingException {
        LOGGER.info("Start polling cloudbreak for stack status: '{}' in '{}' env", sdxCluster.getClusterName(), sdxCluster.getEnvName());
        try {
            if (PollGroup.CANCELLED.equals(DatalakeInMemoryStateStore.get(sdxCluster.getId()))) {
                LOGGER.info("Start polling cancelled in inmemory store, id: " + sdxCluster.getId());
                return AttemptResults.breakFor("Start polling cancelled in inmemory store, id: " + sdxCluster.getId());
            }
            return getStackResponseAttemptResult(sdxCluster);
        } catch (NotFoundException e) {
            LOGGER.debug("Stack not found on CB side " + sdxCluster.getClusterName(), e);
            return AttemptResults.breakFor("Stack not found on CB side " + sdxCluster.getClusterName());
        }
    }

    private AttemptResult<StackV4Response> getStackResponseAttemptResult(SdxCluster sdxCluster) throws JsonProcessingException {
        StackV4Response stackV4Response = stackV4Endpoint.get(0L, sdxCluster.getClusterName(), Collections.emptySet());
        LOGGER.info("Response from cloudbreak: {}", JsonUtil.writeValueAsString(stackV4Response));
        ClusterV4Response cluster = stackV4Response.getCluster();
        if (stackAndClusterAvailable(stackV4Response, cluster)) {
            return AttemptResults.finishWith(stackV4Response);
        } else {
            if (Status.START_FAILED.equals(stackV4Response.getStatus())) {
                LOGGER.info("Stack start failed for Stack {} with status {}, reason: {}", stackV4Response.getName(), stackV4Response.getStatus(),
                        stackV4Response.getStatusReason());
                return sdxStartFailed(sdxCluster, stackV4Response.getStatusReason());
            } else if (Status.START_FAILED.equals(stackV4Response.getCluster().getStatus())) {
                LOGGER.info("Cluster start failed for Cluster {} status {}, reason: {}", stackV4Response.getCluster().getName(),
                        stackV4Response.getCluster().getStatus(), stackV4Response.getStatusReason());
                return sdxStartFailed(sdxCluster, stackV4Response.getCluster().getStatusReason());
            } else {
                return AttemptResults.justContinue();
            }
        }
    }

    private AttemptResult<StackV4Response> sdxStartFailed(SdxCluster sdxCluster, String statusReason) {
        return AttemptResults.breakFor("SDX start failed '" + sdxCluster.getClusterName() + "', " + statusReason);
    }

    private boolean stackAndClusterAvailable(StackV4Response stackV4Response, ClusterV4Response cluster) {
        return stackV4Response.getStatus().isAvailable()
                && cluster != null
                && cluster.getStatus() != null
                && cluster.getStatus().isAvailable();
    }
}
