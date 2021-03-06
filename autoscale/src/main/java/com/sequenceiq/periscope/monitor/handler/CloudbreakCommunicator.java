package com.sequenceiq.periscope.monitor.handler;

import javax.inject.Inject;

import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.endpoint.v4.autoscales.request.FailureReportV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackV4Response;
import com.sequenceiq.cloudbreak.client.CloudbreakInternalCrnClient;

@Service
public class CloudbreakCommunicator {

    @Inject
    private CloudbreakInternalCrnClient cloudbreakInternalCrnClient;

    public StackV4Response getByCrn(String stackCrn) {
        return cloudbreakInternalCrnClient.withInternalCrn().autoscaleEndpoint().get(stackCrn);
    }

    public void failureReport(String stackCrn, FailureReportV4Request failureReport) {
        cloudbreakInternalCrnClient.withInternalCrn().autoscaleEndpoint().failureReport(stackCrn, failureReport);
    }
}
