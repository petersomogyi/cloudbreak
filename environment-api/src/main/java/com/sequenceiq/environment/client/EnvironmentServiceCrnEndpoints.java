package com.sequenceiq.environment.client;

import javax.ws.rs.client.WebTarget;

import com.sequenceiq.cloudbreak.client.AbstractUserCrnServiceEndpoint;
import com.sequenceiq.environment.api.v1.credential.endpoint.CredentialEndpoint;
import com.sequenceiq.environment.api.v1.environment.endpoint.EnvironmentEndpoint;
import com.sequenceiq.environment.api.v1.proxy.endpoint.ProxyEndpoint;

public class EnvironmentServiceCrnEndpoints extends AbstractUserCrnServiceEndpoint implements EnvironmentClient {

    EnvironmentServiceCrnEndpoints(WebTarget webTarget, String crn) {
        super(webTarget, crn);
    }

    @Override
    public CredentialEndpoint credentialV1Endpoint() {
        return getEndpoint(CredentialEndpoint.class);
    }

    @Override
    public ProxyEndpoint proxyV1Endpoint() {
        return getEndpoint(ProxyEndpoint.class);
    }

    @Override
    public EnvironmentEndpoint environmentV1Endpoint() {
        return getEndpoint(EnvironmentEndpoint.class);
    }
}

