package com.sequenceiq.freeipa.service.stack;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.clusterproxy.ClusterProxyRegistrationClient;
import com.sequenceiq.cloudbreak.clusterproxy.ClusterServiceConfig;
import com.sequenceiq.cloudbreak.clusterproxy.ConfigRegistrationRequest;
import com.sequenceiq.cloudbreak.clusterproxy.ConfigRegistrationResponse;
import com.sequenceiq.cloudbreak.orchestrator.model.GatewayConfig;
import com.sequenceiq.freeipa.entity.FreeIpa;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.service.GatewayConfigService;
import com.sequenceiq.freeipa.service.freeipa.FreeIpaService;

@Service
public class ClusterProxyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterProxyService.class);

    @Inject
    private FreeIpaService freeIpaService;

    @Inject
    private GatewayConfigService gatewayConfigService;

    @Inject
    private ClusterProxyRegistrationClient clusterProxyRegistrationClient;

    public ConfigRegistrationResponse registerFreeIpa(Long stackId) {
        LOGGER.debug("Registering cluster with cluster-proxy: StackId = [{}]", stackId);
        return registerFreeIpa(freeIpaService.findByStackId(stackId));
    }

    public ConfigRegistrationResponse registerFreeIpa(FreeIpa freeIpa) {
        Stack stack = freeIpa.getStack();
        LOGGER.debug("Registering cluster with cluster-proxy: Environment CRN = [{}], Stack CRN = [{}]", stack.getEnvironmentCrn(), stack.getResourceCrn());
        List<ClusterServiceConfig> serviceConfigs = List.of(createServiceConfig(stack));
        LOGGER.debug("Registering service configs [{}]", serviceConfigs);
        ConfigRegistrationRequest request = new ConfigRegistrationRequest(stack.getResourceCrn(), List.of(), serviceConfigs);
        return clusterProxyRegistrationClient.registerConfig(request);
    }

    public void deregisterFreeIpa(Long stackId) {
        LOGGER.debug("Deregistering cluster with cluster-proxy: StackId = [{}]", stackId);
        deregisterFreeIpa(freeIpaService.findByStackId(stackId));
    }

    public void deregisterFreeIpa(FreeIpa freeIpa) {
        Stack stack = freeIpa.getStack();
        LOGGER.debug("Deregistering cluster with cluster-proxy: Environment CRN = [{}], Stack CRN = [{}]", stack.getEnvironmentCrn(), stack.getResourceCrn());
        clusterProxyRegistrationClient.deregisterConfig(stack.getResourceCrn());
    }

    private ClusterServiceConfig createServiceConfig(Stack stack) {
        return new ClusterServiceConfig("freeipa", List.of(freeipaUrl(stack)), List.of());
    }

    private String freeipaUrl(Stack stack) {
        GatewayConfig primaryGatewayConfig = gatewayConfigService.getPrimaryGatewayConfig(stack);
        return primaryGatewayConfig.getGatewayUrl();
    }
}
