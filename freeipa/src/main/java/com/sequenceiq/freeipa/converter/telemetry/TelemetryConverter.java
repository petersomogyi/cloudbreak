package com.sequenceiq.freeipa.converter.telemetry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.common.api.cloudstorage.old.S3CloudStorageV1Parameters;
import com.sequenceiq.common.api.cloudstorage.old.WasbCloudStorageV1Parameters;
import com.sequenceiq.common.api.telemetry.model.Features;
import com.sequenceiq.common.api.telemetry.model.Logging;
import com.sequenceiq.common.api.telemetry.model.Telemetry;
import com.sequenceiq.common.api.telemetry.request.LoggingRequest;
import com.sequenceiq.common.api.telemetry.request.TelemetryRequest;
import com.sequenceiq.common.api.telemetry.response.FeaturesResponse;
import com.sequenceiq.common.api.telemetry.response.LoggingResponse;
import com.sequenceiq.common.api.telemetry.response.TelemetryResponse;
import com.sequenceiq.common.api.type.FeatureSetting;

@Component
public class TelemetryConverter {

    private final boolean freeIpaTelemetryEnabled;

    private final boolean reportDeplymentLogs;

    private final String databusEndpoint;

    public TelemetryConverter(@Value("${freeipa.telemetry.enabled:false}") boolean freeIpaTelemetryEnabled,
            @Value("${cluster.deployment.logs.report:false}") boolean reportDeplymentLogs,
            @Value("${altus.databus.endpoint:}") String databusEndpoint) {
        this.freeIpaTelemetryEnabled = freeIpaTelemetryEnabled;
        this.reportDeplymentLogs = reportDeplymentLogs;
        this.databusEndpoint = databusEndpoint;
    }

    public Telemetry convert(TelemetryRequest request) {
        Telemetry telemetry = null;
        if (freeIpaTelemetryEnabled && request != null) {
            telemetry = new Telemetry();
            LoggingRequest loggingRequest = request.getLogging();
            Logging logging = null;
            if (loggingRequest != null) {
                logging = new Logging();
                logging.setStorageLocation(loggingRequest.getStorageLocation());
                if (loggingRequest.getS3() != null) {
                    S3CloudStorageV1Parameters s3Params = new S3CloudStorageV1Parameters();
                    s3Params.setInstanceProfile(loggingRequest.getS3().getInstanceProfile());
                    logging.setS3(s3Params);
                } else if (loggingRequest.getWasb() != null) {
                    WasbCloudStorageV1Parameters wasbParams = new WasbCloudStorageV1Parameters();
                    wasbParams.setAccountKey(loggingRequest.getWasb().getAccountKey());
                    wasbParams.setAccountName(loggingRequest.getWasb().getAccountName());
                    wasbParams.setSecure(loggingRequest.getWasb().isSecure());
                    logging.setWasb(wasbParams);
                }
            }
            telemetry.setLogging(logging);
            if (reportDeplymentLogs) {
                Features features = new Features();
                if (request.getFeatures() != null && request.getFeatures().getReportDeploymentLogs() != null) {
                    features.setReportDeploymentLogs(request.getFeatures().getReportDeploymentLogs());
                } else {
                    FeatureSetting reportDeploymentLogsFeature = new FeatureSetting();
                    reportDeploymentLogsFeature.setEnabled(true);
                    features.setReportDeploymentLogs(reportDeploymentLogsFeature);
                }
                telemetry.setFeatures(features);
            }
            telemetry.setDatabusEndpoint(databusEndpoint);
            telemetry.setFluentAttributes(request.getFluentAttributes());
        }
        return telemetry;
    }

    public TelemetryResponse convert(Telemetry telemetry) {
        TelemetryResponse response = null;
        if (freeIpaTelemetryEnabled && telemetry != null) {
            response = new TelemetryResponse();
            response.setFluentAttributes(telemetry.getFluentAttributes());
            response.setDatabusEndpoint(databusEndpoint);
            Logging logging = telemetry.getLogging();
            LoggingResponse loggingResponse = null;
            if (logging != null) {
                loggingResponse = new LoggingResponse();
                loggingResponse.setStorageLocation(logging.getStorageLocation());
                if (logging.getS3() != null) {
                    S3CloudStorageV1Parameters s3Params = new S3CloudStorageV1Parameters();
                    s3Params.setInstanceProfile(logging.getS3().getInstanceProfile());
                    loggingResponse.setS3(s3Params);
                } else if (logging.getWasb() != null) {
                    WasbCloudStorageV1Parameters wasbParams = new WasbCloudStorageV1Parameters();
                    wasbParams.setAccountKey(logging.getWasb().getAccountKey());
                    wasbParams.setAccountName(logging.getWasb().getAccountName());
                    wasbParams.setSecure(logging.getWasb().isSecure());
                    loggingResponse.setWasb(wasbParams);
                }
            }
            response.setLogging(loggingResponse);
            Features features = telemetry.getFeatures();
            FeaturesResponse featuresResponse = null;
            if (features != null && features.getReportDeploymentLogs() != null) {
                featuresResponse = new FeaturesResponse();
                featuresResponse.setReportDeploymentLogs(features.getReportDeploymentLogs());
            }
            response.setFeatures(featuresResponse);
        }
        return response;
    }

}
