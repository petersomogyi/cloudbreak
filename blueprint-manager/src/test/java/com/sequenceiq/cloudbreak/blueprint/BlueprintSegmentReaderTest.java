package com.sequenceiq.cloudbreak.blueprint;

import java.io.IOException;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.sequenceiq.cloudbreak.blueprint.templates.ServiceName;
import com.sequenceiq.cloudbreak.blueprint.templates.TemplateFiles;

@RunWith(MockitoJUnitRunner.class)
public class BlueprintSegmentReaderTest {

    @InjectMocks
    private final BlueprintSegmentReader underTest = new BlueprintSegmentReader();

    private AnnotationConfigEmbeddedWebApplicationContext annotationConfigEmbeddedWebApplicationContext = new AnnotationConfigEmbeddedWebApplicationContext();

    @Before
    public void setup() throws IOException {
        ReflectionTestUtils.setField(underTest, "resourceLoader", annotationConfigEmbeddedWebApplicationContext);
        ReflectionTestUtils.setField(underTest, "blueprintTemplatePath", "blueprints/configurations");
        ReflectionTestUtils.setField(underTest, "basicTemplatePath", "blueprints/basics");
        ReflectionTestUtils.setField(underTest, "settingsTemplatePath", "blueprints/settings");
    }

    @Test
    public void testThatAllFileIsReadableShouldVerifyThatFileCountMatch() {
        Map<ServiceName, TemplateFiles> configFiles = underTest.collectAllConfigFile();
        Map<ServiceName, TemplateFiles> serviceFiles = underTest.collectAllServiceFile();
        Map<ServiceName, TemplateFiles> settingsFiles = underTest.collectAllSettingsFile();

        Assert.assertEquals(1, configFiles.size());
        Assert.assertEquals(18, serviceFiles.size());
        Assert.assertEquals(1, settingsFiles.size());
    }
}