package com.sequenceiq.cloudbreak.service.flowlog;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.cedarsoftware.util.io.JsonWriter;
import com.google.common.collect.Lists;
import com.sequenceiq.cloudbreak.cloud.event.Payload;
import com.sequenceiq.cloudbreak.cloud.event.setup.CheckImageRequest;
import com.sequenceiq.cloudbreak.controller.exception.NotFoundException;
import com.sequenceiq.cloudbreak.domain.FlowLog;
import com.sequenceiq.cloudbreak.domain.StateStatus;
import com.sequenceiq.cloudbreak.repository.FlowChainLogRepository;
import com.sequenceiq.cloudbreak.repository.FlowLogRepository;

@RunWith(MockitoJUnitRunner.class)
public class FlowLogServiceTest {

    private static final String FLOW_ID = "flowId";

    private static final long ID = 1L;

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @InjectMocks
    private FlowLogService underTest;

    @Mock
    private FlowLogRepository flowLogRepository;

    @Mock
    private ResourceIdProvider resourceIdProvider;

    @Mock
    private FlowChainLogRepository flowLogChainRepository;

    @Test
    public void updateLastFlowLogStatus() throws Exception {
        runUpdateLastFlowLogStatusTest(false, StateStatus.SUCCESSFUL);
    }

    @Test
    public void updateLastFlowLogStatusFailure() throws Exception {
        runUpdateLastFlowLogStatusTest(true, StateStatus.FAILED);
    }

    private void runUpdateLastFlowLogStatusTest(boolean failureEvent, StateStatus successful) throws Exception {
        FlowLog flowLog = new FlowLog();
        flowLog.setId(ID);

        underTest.updateLastFlowLogStatus(flowLog, failureEvent);

        verify(flowLogRepository, times(1)).updateLastLogStatusInFlow(ID, successful);
    }

    @Test
    public void getLastFlowLog() {
        FlowLog flowLog = new FlowLog();
        flowLog.setId(ID);

        when(flowLogRepository.findFirstByFlowIdOrderByCreatedDesc(FLOW_ID)).thenReturn(flowLog);

        FlowLog lastFlowLog = underTest.getLastFlowLog(FLOW_ID);
        assertEquals(flowLog, lastFlowLog);
    }

    @Test
    public void updateLastFlowLogPayload() {
        FlowLog flowLog = new FlowLog();
        flowLog.setId(ID);

        Payload payload = new CheckImageRequest<>(null, null, null, null);
        Map<Object, Object> variables = Map.of("repeated", 2);

        underTest.updateLastFlowLogPayload(flowLog, payload, variables);

        ArgumentCaptor<FlowLog> flowLogCaptor = ArgumentCaptor.forClass(FlowLog.class);
        verify(flowLogRepository, times(1)).save(flowLogCaptor.capture());

        FlowLog savedFlowLog = flowLogCaptor.getValue();
        assertEquals(flowLog.getId(), savedFlowLog.getId());

        String payloadJson = JsonWriter.objectToJson(payload, Map.of());
        String variablesJson = JsonWriter.objectToJson(variables, Map.of());
        assertEquals(payloadJson, savedFlowLog.getPayload());
        assertEquals(variablesJson, savedFlowLog.getVariables());
    }

    @Test
    public void testGetFlowLogs() {
        when(resourceIdProvider.getResourceIdByResourceName(anyString())).thenReturn(1L);
        when(flowLogRepository.findAllByStackIdOrderByCreatedDesc(anyLong())).thenReturn(Lists.newArrayList(new FlowLog()));

        assertEquals(1, underTest.getFlowLogsByResourceName("stackName").size());

        verify(resourceIdProvider, times(1)).getResourceIdByResourceName(anyString());
    }

    @Test
    public void testGetLastFlowLogWhenThereIsNoFlow() {
        when(resourceIdProvider.getResourceIdByResourceName(anyString())).thenReturn(1L);
        when(flowLogRepository.findAllByStackIdOrderByCreatedDesc(anyLong())).thenReturn(Lists.newArrayList());

        thrown.expect(NotFoundException.class);
        thrown.expectMessage("Flow log for resource not found!");

        underTest.getLastFlowLogByResourcerName("stackName");
    }

    @Test
    public void testGetLastFlowLog() {
        when(resourceIdProvider.getResourceIdByResourceName(anyString())).thenReturn(1L);
        when(flowLogRepository.findAllByStackIdOrderByCreatedDesc(anyLong())).thenReturn(Lists.newArrayList(createFlowLog("1"), createFlowLog("2")));

        assertEquals("1", underTest.getLastFlowLogByResourcerName("stackName").getFlowId());

        verify(resourceIdProvider, times(1)).getResourceIdByResourceName(anyString());
    }

    private FlowLog createFlowLog(String flowId) {
        FlowLog flowLog = new FlowLog();
        flowLog.setFlowId(flowId);
        flowLog.setFlowChainId(flowId + "chain");
        return flowLog;
    }
}