package com.sequenceiq.datalake.service.sdx.stop;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.AVAILABLE;
import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.REQUESTED;
import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.STOPPED;
import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.STOP_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dyngr.core.AttemptResult;
import com.dyngr.core.AttemptState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.StackV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.cluster.ClusterV4Response;
import com.sequenceiq.cloudbreak.cloud.scheduler.PollGroup;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.datalake.entity.DatalakeStatusEnum;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.flow.SdxReactorFlowManager;
import com.sequenceiq.datalake.flow.statestore.DatalakeInMemoryStateStore;
import com.sequenceiq.datalake.service.sdx.SdxService;
import com.sequenceiq.datalake.service.sdx.status.SdxStatusService;

@ExtendWith(MockitoExtension.class)
public class SdxStopServiceTest {

    private static final String CLUSTER_NAME = "clusterName";

    private static final Long CLUSTER_ID = 1L;

    @InjectMocks
    private SdxStopService underTest;

    @Mock
    private SdxReactorFlowManager sdxReactorFlowManager;

    @Mock
    private SdxService sdxService;

    @Mock
    private StackV4Endpoint stackV4Endpoint;

    @Mock
    private SdxStatusService sdxStatusService;

    @Test
    public void testTriggerStop() {

        underTest.triggerStop(sdxCluster());

        verify(sdxReactorFlowManager).triggerSdxStopFlow(CLUSTER_ID);
    }

    @Test
    public void testStopWhenNotFoundException() {
        SdxCluster sdxCluster = sdxCluster();
        doThrow(NotFoundException.class).when(stackV4Endpoint).putStop(0L, CLUSTER_NAME);
        when(sdxService.getById(CLUSTER_ID)).thenReturn(sdxCluster);

        underTest.stop(CLUSTER_ID);

        verify(sdxStatusService, times(0)).setStatusForDatalakeAndNotify(DatalakeStatusEnum.STOP_IN_PROGRESS, ResourceEvent.SDX_STOP_STARTED,
                "Datalake stop in progress", sdxCluster);
    }

    @Test
    public void testStopWheClientErrorException() {
        SdxCluster sdxCluster = sdxCluster();
        ClientErrorException clientErrorException = mock(ClientErrorException.class);
        Response response = mock(Response.class);
        when(clientErrorException.getResponse()).thenReturn(response);
        when(response.readEntity(String.class)).thenReturn(Json.silent(Map.of("message", "error")).getValue());
        doThrow(clientErrorException).when(stackV4Endpoint).putStop(0L, CLUSTER_NAME);
        when(sdxService.getById(CLUSTER_ID)).thenReturn(sdxCluster);

        RuntimeException exception = Assertions.assertThrows(RuntimeException.class, () -> underTest.stop(CLUSTER_ID));
        assertEquals("Can not stop stack, client error happened on Cloudbreak side: Error message: \"error\"", exception.getMessage());
    }

    @Test
    public void testStopWhenWebApplicationException() {
        SdxCluster sdxCluster = sdxCluster();
        WebApplicationException clientErrorException = mock(WebApplicationException.class);
        when(clientErrorException.getMessage()).thenReturn("error");
        doThrow(clientErrorException).when(stackV4Endpoint).putStop(0L, CLUSTER_NAME);
        when(sdxService.getById(CLUSTER_ID)).thenReturn(sdxCluster);

        RuntimeException exception = Assertions.assertThrows(RuntimeException.class, () -> underTest.stop(CLUSTER_ID));
        assertEquals("Can not stop stack, web application error happened on Cloudbreak side: error", exception.getMessage());
    }

    @Test
    public void testCheckClusterStatusDuringStopWhenCancelled() throws JsonProcessingException {
        DatalakeInMemoryStateStore.put(2L, PollGroup.CANCELLED);
        SdxCluster sdxCluster = sdxCluster();
        sdxCluster.setId(2L);
        AttemptResult<StackV4Response> actual = underTest.checkClusterStatusDuringStop(sdxCluster);

        assertEquals("Stop polling cancelled in inmemory store, id: " + 2L, actual.getMessage());
    }

    @Test
    public void testCheckClusterStatusDuringStopWhenStackAndClusterAvailable() throws JsonProcessingException {
        SdxCluster sdxCluster = sdxCluster();

        StackV4Response stackV4Response = new StackV4Response();
        stackV4Response.setStatus(STOPPED);
        ClusterV4Response clusterV4Response = new ClusterV4Response();
        clusterV4Response.setStatus(STOPPED);
        stackV4Response.setCluster(clusterV4Response);

        when(stackV4Endpoint.get(0L, sdxCluster.getClusterName(), Collections.emptySet())).thenReturn(stackV4Response);

        AttemptResult<StackV4Response> actual = underTest.checkClusterStatusDuringStop(sdxCluster);

        assertEquals(stackV4Response, actual.getResult());
    }

    @Test
    public void testCheckClusterStatusDuringStopWhenStackAvailableOnly() throws JsonProcessingException {
        SdxCluster sdxCluster = sdxCluster();

        StackV4Response stackV4Response = new StackV4Response();
        stackV4Response.setStatus(AVAILABLE);
        ClusterV4Response clusterV4Response = new ClusterV4Response();
        clusterV4Response.setStatus(REQUESTED);
        stackV4Response.setCluster(clusterV4Response);

        when(stackV4Endpoint.get(0L, sdxCluster.getClusterName(), Collections.emptySet())).thenReturn(stackV4Response);

        AttemptResult<StackV4Response> actual = underTest.checkClusterStatusDuringStop(sdxCluster);

        assertEquals(AttemptState.CONTINUE, actual.getState());
    }

    @Test
    public void testCheckClusterStatusDuringStopWhenStackFailed() throws JsonProcessingException {
        SdxCluster sdxCluster = sdxCluster();

        StackV4Response stackV4Response = new StackV4Response();
        stackV4Response.setStatusReason("reason");
        stackV4Response.setStatus(STOP_FAILED);

        when(stackV4Endpoint.get(0L, sdxCluster.getClusterName(), Collections.emptySet())).thenReturn(stackV4Response);

        AttemptResult<StackV4Response> actual = underTest.checkClusterStatusDuringStop(sdxCluster);

        assertEquals(AttemptState.BREAK, actual.getState());
        assertEquals("SDX stop failed 'clusterName', reason", actual.getMessage());
    }

    @Test
    public void testCheckClusterStatusDuringStopWhenClusterFailed() throws JsonProcessingException {
        SdxCluster sdxCluster = sdxCluster();

        StackV4Response stackV4Response = new StackV4Response();
        stackV4Response.setStatusReason("reason");
        stackV4Response.setStatus(AVAILABLE);
        ClusterV4Response clusterV4Response = new ClusterV4Response();
        clusterV4Response.setStatus(STOP_FAILED);
        clusterV4Response.setStatusReason("cluster reason");
        stackV4Response.setCluster(clusterV4Response);

        when(stackV4Endpoint.get(0L, sdxCluster.getClusterName(), Collections.emptySet())).thenReturn(stackV4Response);

        AttemptResult<StackV4Response> actual = underTest.checkClusterStatusDuringStop(sdxCluster);

        assertEquals(AttemptState.BREAK, actual.getState());
        assertEquals("SDX stop failed 'clusterName', cluster reason", actual.getMessage());
    }

    private SdxCluster sdxCluster() {
        SdxCluster sdxCluster = new SdxCluster();
        sdxCluster.setId(CLUSTER_ID);
        sdxCluster.setClusterName(CLUSTER_NAME);
        return sdxCluster;
    }

}
