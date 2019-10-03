package com.sequenceiq.environment.environment.service;

import static com.sequenceiq.environment.environment.service.EnvironmentTestData.CRN;
import static com.sequenceiq.environment.environment.service.EnvironmentTestData.ENVIRONMENT_NAME;
import static com.sequenceiq.environment.environment.service.EnvironmentTestData.USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sequenceiq.cloudbreak.api.endpoint.v4.clustertemplate.ClusterTemplateV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.DatalakeV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackViewV4Responses;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.exception.UnableToDeleteClusterDefinitionException;
import com.sequenceiq.distrox.api.v1.distrox.endpoint.DistroXV1Endpoint;
import com.sequenceiq.environment.environment.domain.Environment;
import com.sequenceiq.environment.exception.EnvironmentServiceException;
import com.sequenceiq.flow.reactor.api.event.EventSender;
import com.sequenceiq.sdx.api.endpoint.SdxEndpoint;

@ExtendWith(SpringExtension.class)
class EnvironmentResourceDeletionServiceTest {

    private static final Long WORKSPACE_ID = 0L;

    private static final String ENVIRONMENT_CRN = "someEnvCrn";

    @MockBean
    private SdxEndpoint sdxEndpoint;

    @MockBean
    private ThreadBasedUserCrnProvider userCrnProvider;

    @MockBean
    private EventSender eventSender;

    @MockBean
    private DistroXV1Endpoint distroXEndpoint;

    @MockBean
    private DatalakeV4Endpoint datalakeEndpoint;

    @MockBean
    private ClusterTemplateV4Endpoint clusterTemplateV4Endpoint;

    @Inject
    private EnvironmentResourceDeletionService environmentResourceDeletionServiceUnderTest;

    private Environment environment;

    @BeforeEach
    void setup() {
        environment = new Environment();
        environment.setId(1L);
        environment.setCreator(CRN);
        environment.setName(ENVIRONMENT_NAME);
    }

    @Test
    void getAttachedSdxClusterNames() {
        environmentResourceDeletionServiceUnderTest.getAttachedSdxClusterNames(environment);
        verify(sdxEndpoint).list(eq(ENVIRONMENT_NAME));
    }

    @Test
    void getDatalakeClusterNames() {
        when(datalakeEndpoint.list(anyString())).thenReturn(new StackViewV4Responses());
        environmentResourceDeletionServiceUnderTest.getDatalakeClusterNames(environment);
        verify(datalakeEndpoint).list(eq(ENVIRONMENT_NAME));
    }

    @Test
    void getAttachedDistroXClusterNames() {
        when(distroXEndpoint.list(anyString(), anyObject())).thenReturn(new StackViewV4Responses());
        environmentResourceDeletionServiceUnderTest.getAttachedDistroXClusterNames(environment);
        verify(distroXEndpoint, times(1)).list(eq(ENVIRONMENT_NAME), isNull());
    }

    @Test
    void triggerDeleteFlow() {
        when(userCrnProvider.getUserCrn()).thenReturn(USER);
        environmentResourceDeletionServiceUnderTest.triggerDeleteFlow(environment);
        verify(eventSender).sendEvent(any(), any());
    }

    @Test
    void testWhenDeleteClusterDefinitionsOnCloudbreakThrowsWebApplicationExceptionThenItShouldBeCatchedAndEnvironmentServiceExceptionShouldBeThrown() {
        doThrow(WebApplicationException.class).when(clusterTemplateV4Endpoint).deleteMultipleByEnvironmentCrn(WORKSPACE_ID, ENVIRONMENT_CRN);

        Assertions.assertThrows(EnvironmentServiceException.class,
                () -> environmentResourceDeletionServiceUnderTest.deleteClusterDefinitionsOnCloudbreak(ENVIRONMENT_CRN));

        verify(clusterTemplateV4Endpoint, times(1)).deleteMultipleByEnvironmentCrn(anyLong(), anyString());
        verify(clusterTemplateV4Endpoint, times(1)).deleteMultipleByEnvironmentCrn(WORKSPACE_ID, ENVIRONMENT_CRN);
    }

    @Test
    void testWhenDeleteClusterDefinitionsOnCloudbreakThrowsProcessingExceptionThenItShouldBeCatchedAndEnvironmentServiceExceptionShouldBeThrown() {
        doThrow(ProcessingException.class).when(clusterTemplateV4Endpoint).deleteMultipleByEnvironmentCrn(WORKSPACE_ID, ENVIRONMENT_CRN);

        Assertions.assertThrows(EnvironmentServiceException.class,
                () -> environmentResourceDeletionServiceUnderTest.deleteClusterDefinitionsOnCloudbreak(ENVIRONMENT_CRN));

        verify(clusterTemplateV4Endpoint, times(1)).deleteMultipleByEnvironmentCrn(anyLong(), anyString());
        verify(clusterTemplateV4Endpoint, times(1)).deleteMultipleByEnvironmentCrn(WORKSPACE_ID, ENVIRONMENT_CRN);
    }

    @Test
    void testWhenDeleteClusterDefinitionsThrowsUnableToDeleteClusterDefinitionExceptionThenItShouldBeCatchedAndEnvironmentServiceExceptionShouldBeThrown() {
        doThrow(UnableToDeleteClusterDefinitionException.class).when(clusterTemplateV4Endpoint).deleteMultipleByEnvironmentCrn(WORKSPACE_ID, ENVIRONMENT_CRN);

        Assertions.assertThrows(EnvironmentServiceException.class,
                () -> environmentResourceDeletionServiceUnderTest.deleteClusterDefinitionsOnCloudbreak(ENVIRONMENT_CRN));

        verify(clusterTemplateV4Endpoint, times(1)).deleteMultipleByEnvironmentCrn(anyLong(), anyString());
        verify(clusterTemplateV4Endpoint, times(1)).deleteMultipleByEnvironmentCrn(WORKSPACE_ID, ENVIRONMENT_CRN);
    }

    @Configuration
    @Import(EnvironmentResourceDeletionService.class)
    static class Config {
    }

}