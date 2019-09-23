package com.sequenceiq.cloudbreak.controller;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Controller;

import com.sequenceiq.cloudbreak.api.endpoint.flow.FlowEndpoint;
import com.sequenceiq.cloudbreak.api.model.flow.FlowLogResponse;
import com.sequenceiq.cloudbreak.domain.FlowLog;
import com.sequenceiq.cloudbreak.service.flowlog.FlowLogService;

@Controller
public class FlowController implements FlowEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowController.class);

    @Inject
    private FlowLogService flowLogService;

    @Inject
    @Named("conversionService")
    private ConversionService conversionService;

    @Override
    public FlowLogResponse getLastFlowById(String flowId) {
        FlowLog lastFlowLog = flowLogService.getLastFlowLog(flowId);
        if (lastFlowLog != null) {
            return conversionService.convert(lastFlowLog, FlowLogResponse.class);
        }
        throw new BadRequestException("Not found flow for this flow id!");
    }

    @Override
    public List<FlowLogResponse> getFlowLogsByFlowId(String flowId) {
        List<FlowLog> flowLogs = flowLogService.findAllByFlowIdOrderByCreatedDesc(flowId);
        return flowLogs.stream().map(flowLog -> conversionService.convert(flowLog, FlowLogResponse.class)).collect(Collectors.toList());
    }

    @Override
    public FlowLogResponse getLastFlowByResourceName(String resourceName) {
        return conversionService.convert(flowLogService.getLastFlowLogByResourcerName(resourceName), FlowLogResponse.class);
    }

    @Override
    public List<FlowLogResponse> getFlowLogsByResourceName(String resourceName) {
        List<FlowLog> flowLogs = flowLogService.getFlowLogsByResourceName(resourceName);
        return flowLogs.stream().map(flowLog -> conversionService.convert(flowLog, FlowLogResponse.class)).collect(Collectors.toList());
    }

    @Override
    public List<FlowLogResponse> getFlowLogsByResourceId(Long resourceId) {
        List<FlowLog> flowLogs = flowLogService.getFlowLogsByResourceId(resourceId);
        return flowLogs.stream().map(flowLog -> conversionService.convert(flowLog, FlowLogResponse.class)).collect(Collectors.toList());
    }
}
