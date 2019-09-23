package com.sequenceiq.cloudbreak.api.endpoint.flow;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.sequenceiq.cloudbreak.api.model.flow.FlowLogResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Path("/flow_logs")
@Consumes(MediaType.APPLICATION_JSON)
@Api(value = "/flow_logs", description = "Operations on flow logs", protocols = "http,https")
public interface FlowEndpoint {

    @GET
    @Path("{flowId}/last")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get last flow log by flow id", produces = "application/json", notes = "Flow log operations",
            nickname = "getLastFlowById")
    FlowLogResponse getLastFlowById(@PathParam("flowId") String flowId);

    @GET
    @Path("{flowId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get flow logs by flow id", produces = "application/json", notes = "Flow log operations",
            nickname = "getFlowLogsByFlowId")
    List<FlowLogResponse> getFlowLogsByFlowId(@PathParam("flowId") String flowId);

    @GET
    @Path("/resource/name/{resourceName}/last")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get last flow log for resource by resource name", produces = "application/json", notes = "Flow log operations",
            nickname = "getLastFlowByResourceName")
    FlowLogResponse getLastFlowByResourceName(@PathParam("resourceName") String resourceName);

    @GET
    @Path("/resource/name/{resourceName}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get flow logs for resource by resource name", produces = "application/json", notes = "Flow log operations",
            nickname = "getFlowLogsByResourceName")
    List<FlowLogResponse> getFlowLogsByResourceName(@PathParam("resourceName") String resourceName);

    @GET
    @Path("/resource/id/{resourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get flow logs for resource by resource id", produces = "application/json", notes = "Flow log operations",
            nickname = "getFlowLogsByResourceId")
    List<FlowLogResponse> getFlowLogsByResourceId(@PathParam("resourceId") Long resourceId);
}