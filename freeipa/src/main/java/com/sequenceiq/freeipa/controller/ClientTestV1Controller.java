package com.sequenceiq.freeipa.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import com.cloudera.thunderhead.service.usermanagement.UserManagementProto.GetRightsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.altus.GrpcUmsClient;
import com.sequenceiq.freeipa.api.v1.freeipa.test.ClientTestV1Endpoint;
import com.sequenceiq.freeipa.client.FreeIpaClient;
import com.sequenceiq.freeipa.client.model.User;
import com.sequenceiq.freeipa.entity.FreeIpa;
import com.sequenceiq.freeipa.service.freeipa.FreeIpaClientFactory;
import com.sequenceiq.freeipa.service.freeipa.FreeIpaService;
import com.sequenceiq.freeipa.service.stack.ClusterProxyService;
import com.sequenceiq.freeipa.service.stack.StackService;

@Controller
public class ClientTestV1Controller implements ClientTestV1Endpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTestV1Controller.class);

    @Inject
    private FreeIpaClientFactory freeIpaClientFactory;

    @Inject
    private GrpcUmsClient umsClient;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private ClusterProxyService clusterProxyService;

    @Inject
    private FreeIpaService freeIpaService;

    @Inject
    private StackService stackService;

    @Inject
    private ThreadBasedUserCrnProvider threadBasedUserCrnProvider;

    @Override
    public String userShow(Long id, String name) {
        FreeIpaClient freeIpaClient;
        try {
            freeIpaClient = freeIpaClientFactory.getFreeIpaClientForStackId(id);
        } catch (Exception e) {
            LOGGER.error("Error creating FreeIpaClient", e);
            return "FAILED TO CREATE CLIENT";
        }

        try {
            User user = freeIpaClient.userShow(name);
            LOGGER.info("Groups: {}", user.getMemberOfGroup());
            LOGGER.info("Success: {}", user);
        } catch (Exception e) {
            LOGGER.error("Error showing user {}", name, e);
            return "FAILED TO SHOW USER";
        }
        return "END";
    }

    @Override
    public String getRight(String crn) {
        LOGGER.debug("Getting rights for crn [{}]", crn);
        try {
            Map<String, Object> rights = new HashMap<>();
            GetRightsResponse rightsResponse = umsClient.getRightsForUser(crn, crn, null, Optional.empty());
            rights.put("thunderheadAdmin", rightsResponse.getThunderheadAdmin());
            rights.put("groupCrn", rightsResponse.getGroupCrnList());
            rights.put("resourceRoleAssignments", rightsResponse.getResourceRolesAssignmentList().stream()
                    .map(r -> {
                        Map<String, Object> resourceRoleAssignment = new HashMap<>();
                        resourceRoleAssignment.put("ResourceRole", r.getResourceRole());
                        resourceRoleAssignment.put("resources", r.getResourceList());
                        return resourceRoleAssignment;
                    })
                    .collect(Collectors.toList())
            );
            rights.put("roleAssignments", rightsResponse.getRoleAssignmentList().stream()
                    .map(r -> {
                        Map<String, Object> roleAssignment = new HashMap<>();
                        roleAssignment.put("role", r.getRole());
                        return roleAssignment;
                    })
                    .collect(Collectors.toList())
            );
            return objectMapper.writeValueAsString(rights);
        } catch (Exception e) {
            LOGGER.error("Error getting rights.", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String registerWithClusterProxy(String envCrn) {
        FreeIpa freeIpa = freeIpaService.findByStack(stackService.getByEnvironmentCrnAndAccountId(envCrn, threadBasedUserCrnProvider.getAccountId()));
        return clusterProxyService.registerFreeIpa(freeIpa).toString();
    }

    @Override
    public void deregisterWithClusterProxy(String envCrn) {
        FreeIpa freeIpa = freeIpaService.findByStack(stackService.getByEnvironmentCrnAndAccountId(envCrn, threadBasedUserCrnProvider.getAccountId()));
        clusterProxyService.deregisterFreeIpa(freeIpa);
    }
}
