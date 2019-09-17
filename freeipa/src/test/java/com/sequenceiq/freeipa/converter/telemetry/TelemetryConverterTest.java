package com.sequenceiq.freeipa.converter.telemetry;

import org.junit.Before;
import org.junit.Test;

import com.sequenceiq.common.api.telemetry.model.Telemetry;
import com.sequenceiq.common.api.telemetry.request.TelemetryRequest;

public class TelemetryConverterTest {

    private TelemetryConverter underTest;

    @Before
    public void setUp() {
        underTest = new TelemetryConverter(true, true, "myEndpoint");
    }

    @Test
    public void testConvert() {
        // GIVEN
        TelemetryRequest request = new TelemetryRequest();
        // WHEN
        Telemetry result = underTest.convert(request);
        // THEN
    }
}
