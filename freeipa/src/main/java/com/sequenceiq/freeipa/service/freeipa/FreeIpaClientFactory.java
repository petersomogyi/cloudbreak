package com.sequenceiq.freeipa.service.freeipa;

import com.sequenceiq.cloudbreak.client.HttpClientConfig;
import com.sequenceiq.cloudbreak.orchestrator.model.GatewayConfig;
import com.sequenceiq.freeipa.client.FreeIpaClient;
import com.sequenceiq.freeipa.client.FreeIpaClientBuilder;
import com.sequenceiq.freeipa.client.FreeIpaClientException;
import com.sequenceiq.freeipa.entity.FreeIpa;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.service.GatewayConfigService;
import com.sequenceiq.freeipa.service.TlsSecurityService;
import com.sequenceiq.freeipa.service.stack.StackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.inject.Inject;

@Service
public class FreeIpaClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeIpaClientFactory.class);

    private static final String ADMIN_USER = "admin";

    // TODO make this point to actual cluster proxy address
    private static final String CLUSTER_PROXY_API_ADDRESS = "localhost";
    private static final String CLUSTER_PROXY_PORT = "10080";

    // TODO pull these out into ClusterProxyConfiguration object
    @Value("${clusterProxy.enabled:false}")
    private boolean clusterProxyIntegrationEnabled;

    @Value("${clusterProxy.url:}")
    private String clusterProxyUrl;

    @Inject
    private GatewayConfigService gatewayConfigService;

    @Inject
    private StackService stackService;

    @Inject
    private FreeIpaService freeIpaService;

    @Inject
    private TlsSecurityService tlsSecurityService;

    public FreeIpaClient getFreeIpaClientForStackId(Long stackId) throws Exception {
        LOGGER.debug("Retrieving stack for stack id {}", stackId);

        Stack stack = stackService.getStackById(stackId);

        return getFreeIpaClientForStack(stack);
    }

    public FreeIpaClient getFreeIpaClientByAccountAndEnvironment(String environmentCrn, String accountId) throws FreeIpaClientException {
        Stack stack = stackService.getByEnvironmentCrnAndAccountId(environmentCrn, accountId);
        return getFreeIpaClientForStack(stack);
    }

    public FreeIpaClient getFreeIpaClientForStack(Stack stack) throws FreeIpaClientException {
        LOGGER.debug("Creating FreeIpaClient for stack {}", stack.getResourceCrn());

        try {
            clusterProxyIntegrationEnabled = true;  // TODO Remove this
            if (clusterProxyIntegrationEnabled) {
                HttpClientConfig httpClientConfig = new HttpClientConfig(CLUSTER_PROXY_API_ADDRESS);
                FreeIpa freeIpa = freeIpaService.findByStack(stack);

                String freeIpaClusterCrn = stack.getResourceCrn();
                String registeredServiceName = "freeipa-proxy";
                String clusterProxyPath = String.format("/cluster-proxy/proxy/%s/%s/ipa", freeIpaClusterCrn, registeredServiceName);

                return new FreeIpaClientBuilder(ADMIN_USER,freeIpa.getAdminPassword(), freeIpa.getDomain().toUpperCase(),
                    httpClientConfig, CLUSTER_PROXY_PORT, clusterProxyPath).build();
            } else {
                GatewayConfig gatewayConfig = gatewayConfigService.getPrimaryGatewayConfig(stack);
                HttpClientConfig httpClientConfig = tlsSecurityService.buildTLSClientConfigForPrimaryGateway(
                        stack.getId(), gatewayConfig.getPublicAddress());
                FreeIpa freeIpa = freeIpaService.findByStack(stack);
                return new FreeIpaClientBuilder(ADMIN_USER, freeIpa.getAdminPassword(), freeIpa.getDomain().toUpperCase(),
                        httpClientConfig, stack.getGatewayport().toString()).build();
            }
        } catch (Exception e) {
            throw new FreeIpaClientException("Couldn't build FreeIPA client. "
                    + "Check if the FreeIPA security rules have not changed and the instance is in running state. " + e.getLocalizedMessage(), e);
        }
    }

    public String getAdminUser() {
        return ADMIN_USER;
    }
}