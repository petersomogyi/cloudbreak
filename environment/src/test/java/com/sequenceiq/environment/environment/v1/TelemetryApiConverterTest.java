package com.sequenceiq.environment.environment.v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import com.sequenceiq.common.api.cloudstorage.old.S3CloudStorageV1Parameters;
import com.sequenceiq.common.api.telemetry.request.FeaturesRequest;
import com.sequenceiq.common.api.telemetry.request.LoggingRequest;
import com.sequenceiq.common.api.telemetry.request.TelemetryRequest;
import com.sequenceiq.common.api.telemetry.request.WorkloadAnalyticsRequest;
import com.sequenceiq.common.api.telemetry.response.TelemetryResponse;
import com.sequenceiq.common.api.type.FeatureSetting;
import com.sequenceiq.environment.environment.dto.telemetry.EnvironmentLogging;
import com.sequenceiq.environment.environment.dto.telemetry.EnvironmentTelemetry;
import com.sequenceiq.environment.environment.dto.telemetry.S3CloudStorageParameters;

public class TelemetryApiConverterTest {

    private static final String INSTANCE_PROFILE_VALUE = "myInstanceProfile";

    private TelemetryApiConverter underTest;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        underTest = new TelemetryApiConverter(true, "http://mydatabus.endpoint.com");
    }

    @Test
    public void testConvert() {
        // GIVEN
        TelemetryRequest telemetryRequest = new TelemetryRequest();
        LoggingRequest loggingRequest = new LoggingRequest();
        S3CloudStorageV1Parameters s3Params = new S3CloudStorageV1Parameters();
        s3Params.setInstanceProfile(INSTANCE_PROFILE_VALUE);
        loggingRequest.setS3(s3Params);
        telemetryRequest.setLogging(loggingRequest);
        telemetryRequest.setWorkloadAnalytics(new WorkloadAnalyticsRequest());
        FeaturesRequest fr = new FeaturesRequest();
        FeatureSetting fs = new FeatureSetting();
        fs.setEnabled(true);
        fr.setReportDeploymentLogs(fs);
        telemetryRequest.setFeatures(fr);
        // WHEN
        EnvironmentTelemetry result = underTest.convert(telemetryRequest);
        // THEN
        assertEquals(result.getDatabusEndpoint(), "http://mydatabus.endpoint.com");
        assertEquals(INSTANCE_PROFILE_VALUE, result.getLogging().getS3().getInstanceProfile());
        assertTrue(result.getFeatures().getReportDeploymentLogs().isEnabled());
    }

    @Test
    public void testConvertToResponse() {
        // GIVEN
        EnvironmentLogging logging = new EnvironmentLogging();
        S3CloudStorageParameters s3Params = new S3CloudStorageParameters();
        s3Params.setInstanceProfile(INSTANCE_PROFILE_VALUE);
        logging.setS3(s3Params);
        EnvironmentTelemetry telemetry = new EnvironmentTelemetry(logging, null, null, null);
        // WHEN
        TelemetryResponse result = underTest.convert(telemetry);
        // THEN
        assertEquals(INSTANCE_PROFILE_VALUE, result.getLogging().getS3().getInstanceProfile());
        assertNull(result.getWorkloadAnalytics());
    }

}
