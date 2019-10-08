package com.sequenceiq.cloudbreak.cloud.gcp;

import static com.sequenceiq.cloudbreak.cloud.model.Location.location;
import static com.sequenceiq.common.api.type.ResourceType.GCP_NETWORK;
import static com.sequenceiq.common.api.type.ResourceType.GCP_SUBNET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.sequenceiq.cloudbreak.cloud.NetworkConnector;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.gcp.context.GcpContext;
import com.sequenceiq.cloudbreak.cloud.gcp.context.GcpContextBuilder;
import com.sequenceiq.cloudbreak.cloud.gcp.network.GcpNetworkResourceBuilder;
import com.sequenceiq.cloudbreak.cloud.gcp.network.GcpSubnetResourceBuilder;
import com.sequenceiq.cloudbreak.cloud.gcp.util.GcpStackUtil;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.Network;
import com.sequenceiq.cloudbreak.cloud.model.Platform;
import com.sequenceiq.cloudbreak.cloud.model.Subnet;
import com.sequenceiq.cloudbreak.cloud.model.Variant;
import com.sequenceiq.cloudbreak.cloud.model.network.CreatedCloudNetwork;
import com.sequenceiq.cloudbreak.cloud.model.network.CreatedSubnet;
import com.sequenceiq.cloudbreak.cloud.model.network.NetworkCreationRequest;
import com.sequenceiq.cloudbreak.cloud.model.network.NetworkDeletionRequest;
import com.sequenceiq.cloudbreak.cloud.scheduler.SyncPollingScheduler;
import com.sequenceiq.cloudbreak.cloud.task.PollTask;
import com.sequenceiq.cloudbreak.cloud.template.task.ResourcePollTaskFactory;
import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.common.api.type.ResourceType;

@Component
public class GcpNetworkConnector extends AbstractGcpResourceBuilder implements NetworkConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcpNetworkConnector.class);

    @Inject
    private GcpCloudSubnetProvider gcpCloudSubnetProvider;

    @Inject
    private GcpNetworkResourceBuilder gcpNetworkResourceBuilder;

    @Inject
    private GcpSubnetResourceBuilder gcpSubnetResourceBuilder;

    @Inject
    private GcpContextBuilder contextBuilders;

    @Inject
    private SyncPollingScheduler<List<CloudResourceStatus>> syncPollingScheduler;

    @Inject
    private ResourcePollTaskFactory statusCheckFactory;

    @Override
    public CreatedCloudNetwork createNetworkWithSubnets(NetworkCreationRequest networkRequest, String creatorUser) {
        CloudContext cloudContext = getCloudContext(networkRequest);
        AuthenticatedContext auth = new AuthenticatedContext(cloudContext, networkRequest.getCloudCredential());
        Network network = buildNetworkForCreation(networkRequest);
        GcpContext context = contextBuilders.contextInit(cloudContext, auth, network, null, true);

        try {
            CloudResource networkResource = createNetwork(context, auth, network);
            List<CreatedSubnet> subnetList = getCloudSubNets(networkRequest);
            for (CreatedSubnet createdSubnet : subnetList) {
                createSubnet(context, auth, buildSubnetForCreation(networkRequest, createdSubnet.getCidr()), createdSubnet);
            }
            return new CreatedCloudNetwork(networkRequest.getEnvName(), networkResource.getName(), getCreatedSubnets(subnetList));
        } catch (GoogleJsonResponseException e) {
            throw new GcpResourceException(checkException(e), GCP_NETWORK, networkRequest.getEnvName());
        } catch (IOException e) {
            throw new GcpResourceException("sdfsdfsdfsdfsdf", GCP_NETWORK, networkRequest.getEnvName());
        }
    }

    @Override
    public void deleteNetworkWithSubnets(NetworkDeletionRequest networkDeletionRequest) {
        CloudContext cloudContext = getCloudContext(networkDeletionRequest);
        AuthenticatedContext auth = new AuthenticatedContext(cloudContext, networkDeletionRequest.getCloudCredential());
        Network network = buildNetworkForDeletion(networkDeletionRequest);
        GcpContext context = contextBuilders.contextInit(cloudContext, auth, network, null, true);

        try {
            for (String subnetId : networkDeletionRequest.getSubnetIds()) {
                deleteSubnet(context, auth, network, subnetId);
            }
            deleteNetwork(context, auth, network, networkDeletionRequest.getNetworkId());
        } catch (GoogleJsonResponseException e) {
            exceptionHandler(e, networkDeletionRequest.getStackName(), GCP_NETWORK);
        } catch (IOException e) {
            throw new GcpResourceException("sdfsdfsdfsdfsdf", GCP_NETWORK, networkDeletionRequest.getStackName());
        }
    }

    @Override
    public String getNetworkCidr(Network network, CloudCredential credential) {
        return null;
    }

    private CloudContext getCloudContext(NetworkCreationRequest networkRequest) {
        return new CloudContext(
                networkRequest.getEnvId(),
                networkRequest.getEnvName(),
                CloudPlatform.GCP.name(),
                CloudPlatform.GCP.name(),
                location(networkRequest.getRegion()),
                networkRequest.getUserId(),
                networkRequest.getAccountId());
    }

    private CloudContext getCloudContext(NetworkDeletionRequest networkRequest) {
        return new CloudContext(
                networkRequest.getEnvId(),
                networkRequest.getEnvName(),
                CloudPlatform.GCP.name(),
                CloudPlatform.GCP.name(),
                location(networkRequest.getRegion()),
                networkRequest.getUserId(),
                networkRequest.getAccountId());
    }

    private Network buildNetworkForCreation(NetworkCreationRequest networkRequest) {
        Subnet subnet = new Subnet(networkRequest.getNetworkCidr());
        Map<String, Object> params = new HashMap<>();
        params.put(GcpStackUtil.NETWORK_IP_RANGE, networkRequest.getNetworkCidr());
        Network result = new Network(subnet, params);
        return result;
    }

    private Network buildNetworkForDeletion(NetworkDeletionRequest networkRequest) {
        Network result = new Network(null);
        return result;
    }

    private Network buildSubnetForCreation(NetworkCreationRequest networkRequest, String cidr) {
        Network network = buildNetworkForCreation(networkRequest);
        Subnet subnet = new Subnet(cidr);
        network = new Network(subnet, network.getParameters());
        return network;
    }

    private CloudResource createSubnet(GcpContext context, AuthenticatedContext auth, Network network, CreatedSubnet subnet) {
        CloudResource cloudResource = gcpSubnetResourceBuilder.create(context, auth, network);
        try {
            cloudResource = gcpSubnetResourceBuilder.build(context, auth, network, null, cloudResource);
            PollTask<List<CloudResourceStatus>> task = statusCheckFactory.newPollResourceTask(gcpSubnetResourceBuilder,
                    auth, Collections.singletonList(cloudResource), context, true);
            subnet.setSubnetId(cloudResource.getName());
            List<CloudResourceStatus> pollerResult = syncPollingScheduler.schedule(task);
        } catch (Exception e) {
            LOGGER.debug("Skipping resource creation: {}", e.getMessage());
        }
        return cloudResource;
    }

    private CloudResource createNetwork(GcpContext context, AuthenticatedContext auth, Network network) {
        CloudResource cloudResource = gcpNetworkResourceBuilder.create(context, auth, network);
        try {
            cloudResource = gcpNetworkResourceBuilder.build(context, auth, network, null, cloudResource);
            PollTask<List<CloudResourceStatus>> task = statusCheckFactory.newPollResourceTask(gcpNetworkResourceBuilder,
                    auth, Collections.singletonList(cloudResource), context, true);
            List<CloudResourceStatus> pollerResult = syncPollingScheduler.schedule(task);
        } catch (Exception e) {
            LOGGER.debug("Skipping resource creation: {}", e.getMessage());
        }
        return cloudResource;
    }

    private void deleteNetwork(GcpContext context, AuthenticatedContext auth, Network network, String networkId) throws IOException {
        CloudResource networkResource = createNamedResource(GCP_NETWORK, networkId);
        try {
            CloudResource deletedResource = gcpNetworkResourceBuilder.delete(context, auth, networkResource, network);
            if (deletedResource != null) {
                PollTask<List<CloudResourceStatus>> task = statusCheckFactory.newPollResourceTask(
                        gcpSubnetResourceBuilder, auth, Collections.singletonList(deletedResource), context, true);
                List<CloudResourceStatus> pollerResult = syncPollingScheduler.schedule(task);
            }
        } catch (Exception e) {
            LOGGER.debug("Skipping resource creation: {}", e.getMessage());
        }
    }

    private void deleteSubnet(GcpContext context, AuthenticatedContext auth, Network network, String subnetId) throws IOException {
        CloudResource subnetResource = createNamedResource(GCP_SUBNET, subnetId);
        try {
            CloudResource deletedResource = gcpSubnetResourceBuilder.delete(context, auth, subnetResource, network);
            if (deletedResource != null) {
                PollTask<List<CloudResourceStatus>> task = statusCheckFactory.newPollResourceTask(
                        gcpSubnetResourceBuilder, auth, Collections.singletonList(deletedResource), context, true);
                List<CloudResourceStatus> pollerResult = syncPollingScheduler.schedule(task);
            }
        } catch (Exception e) {
            LOGGER.debug("Skipping resource creation: {}", e.getMessage());
        }
    }

    @Override
    public Platform platform() {
        return GcpConstants.GCP_PLATFORM;
    }

    @Override
    public Variant variant() {
        return GcpConstants.GCP_VARIANT;
    }

    private List<CreatedSubnet> getCloudSubNets(NetworkCreationRequest networkRequest) throws IOException {
        return gcpCloudSubnetProvider.provide(networkRequest, new ArrayList<>(networkRequest.getSubnetCidrs()));
    }

    protected CloudResource createNamedResource(ResourceType type, String name) {
        return new CloudResource.Builder().type(type).name(name).build();
    }

    protected String checkException(GoogleJsonResponseException execute) {
        return execute.getDetails().getMessage();
    }

    private Set<CreatedSubnet> getCreatedSubnets(List<CreatedSubnet> createdSubnetList) {
        Set<CreatedSubnet> subnets = new HashSet<>();
        for (int i = 0; i < createdSubnetList.size(); i++) {
            CreatedSubnet createdSubnetIndexed = createdSubnetList.get(i);
            CreatedSubnet createdSubnet = new CreatedSubnet();
            createdSubnet.setSubnetId(createdSubnetIndexed.getSubnetId());
            createdSubnet.setCidr(createdSubnetList.get(i).getCidr());
            createdSubnet.setAvailabilityZone(createdSubnetList.get(i).getAvailabilityZone());
            createdSubnet.setMapPublicIpOnLaunch(true);
            createdSubnet.setIgwAvailable(true);
            subnets.add(createdSubnet);
        }
        return subnets;
    }
}
