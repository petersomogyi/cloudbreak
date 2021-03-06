package com.sequenceiq.cloudbreak.controller.mapper;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import com.sequenceiq.cloudbreak.common.exception.ExceptionResponse;
import com.sequenceiq.cloudbreak.service.StackUnderOperationService;
import com.sequenceiq.cloudbreak.structuredevent.event.CloudbreakEventService;

abstract class SendNotificationExceptionMapper<E extends Throwable> extends BaseExceptionMapper<E> {

    @Inject
    private StackUnderOperationService stackUnderOperationService;

    @Inject
    private CloudbreakEventService eventService;

    @Override
    public Response toResponse(E exception) {
        Long stackId = stackUnderOperationService.get();
        Response response = super.toResponse(exception);
        if (stackId != null) {
            String message = response.readEntity(ExceptionResponse.class).getMessage();
            eventService.fireCloudbreakEvent(stackId, "BAD_REQUEST", message);
        }
        return response;
    }
}
