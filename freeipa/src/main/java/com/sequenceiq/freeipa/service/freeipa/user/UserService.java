package com.sequenceiq.freeipa.service.freeipa.user;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.auth.altus.CrnParseException;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.cloudbreak.logger.LoggerContextKey;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.freeipa.api.v1.freeipa.user.model.FailureDetails;
import com.sequenceiq.freeipa.api.v1.freeipa.user.model.SuccessDetails;
import com.sequenceiq.freeipa.api.v1.freeipa.user.model.SyncOperationStatus;
import com.sequenceiq.freeipa.api.v1.freeipa.user.model.SyncOperationType;
import com.sequenceiq.freeipa.api.v1.freeipa.user.model.SynchronizationStatus;
import com.sequenceiq.freeipa.client.FreeIpaClient;
import com.sequenceiq.freeipa.client.FreeIpaClientException;
import com.sequenceiq.freeipa.client.model.Config;
import com.sequenceiq.freeipa.client.model.RPCResponse;
import com.sequenceiq.freeipa.controller.exception.BadRequestException;
import com.sequenceiq.freeipa.controller.exception.NotFoundException;
import com.sequenceiq.freeipa.converter.freeipa.user.SyncOperationToSyncOperationStatus;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.entity.SyncOperation;
import com.sequenceiq.freeipa.entity.UserSyncStatus;
import com.sequenceiq.freeipa.service.freeipa.FreeIpaClientFactory;
import com.sequenceiq.freeipa.service.freeipa.user.model.FmsGroup;
import com.sequenceiq.freeipa.service.freeipa.user.model.FmsUser;
import com.sequenceiq.freeipa.service.freeipa.user.model.SyncStatusDetail;
import com.sequenceiq.freeipa.service.freeipa.user.model.UsersState;
import com.sequenceiq.freeipa.service.freeipa.user.model.UsersStateDifference;
import com.sequenceiq.freeipa.service.freeipa.user.model.WorkloadCredential;
import com.sequenceiq.freeipa.service.stack.StackService;
import com.sequenceiq.freeipa.util.KrbKeySetEncoder;

@Service
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    @Inject
    private StackService stackService;

    @Inject
    private FreeIpaClientFactory freeIpaClientFactory;

    @Inject
    private UmsUsersStateProvider umsUsersStateProvider;

    @Inject
    private FreeIpaUsersStateProvider freeIpaUsersStateProvider;

    @Inject
    private AsyncTaskExecutor asyncTaskExecutor;

    @Inject
    private SyncOperationStatusService syncOperationStatusService;

    @Inject
    private SyncOperationToSyncOperationStatus syncOperationToSyncOperationStatus;

    @Inject
    private UmsEventGenerationIdsProvider umsEventGenerationIdsProvider;

    @Inject
    private UserSyncStatusService userSyncStatusService;

    public SyncOperationStatus synchronizeUsers(String accountId, String actorCrn, Set<String> environmentCrnFilter,
                                                Set<String> userCrnFilter, Set<String> machineUserCrnFilter) {

        validateParameters(accountId, actorCrn, environmentCrnFilter, userCrnFilter, machineUserCrnFilter);
        LOGGER.debug("Synchronizing users in account {} for environmentCrns {}, userCrns {}, and machineUserCrns {}",
                accountId, environmentCrnFilter, userCrnFilter, machineUserCrnFilter);

        List<Stack> stacks = getStacks(accountId, environmentCrnFilter);
        LOGGER.debug("Found {} stacks", stacks.size());
        if (stacks.isEmpty()) {
            throw new NotFoundException(String.format("No matching FreeIPA stacks found for account %s with environment crn filter %s",
                    accountId, environmentCrnFilter));
        }

        SyncOperation syncOperation = syncOperationStatusService
                .startOperation(accountId, SyncOperationType.USER_SYNC, environmentCrnFilter, union(userCrnFilter, machineUserCrnFilter));

        LOGGER.info("Starting operation [{}] with status [{}]", syncOperation.getOperationId(), syncOperation.getStatus());

        if (syncOperation.getStatus() == SynchronizationStatus.RUNNING) {
            MDCBuilder.addFlowId(syncOperation.getOperationId());
            asyncTaskExecutor.submit(() -> asyncSynchronizeUsers(
                syncOperation.getOperationId(), accountId, actorCrn, stacks, userCrnFilter, machineUserCrnFilter));
        }

        return syncOperationToSyncOperationStatus.convert(syncOperation);
    }

    private void asyncSynchronizeUsers(
        String operationId, String accountId, String actorCrn, List<Stack> stacks,
        Set<String> userCrnFilter, Set<String> machineUserCrnFilter) {
        try {
            Set<String> environmentCrns = stacks.stream().map(Stack::getEnvironmentCrn).collect(Collectors.toSet());

            Optional<String> requestIdOptional = Optional.of(
                    Optional.ofNullable(MDCBuilder.getMdcContextMap().get(LoggerContextKey.REQUEST_ID.toString()))
                            .orElseGet(() -> {
                                String requestId = UUID.randomUUID().toString();
                                LOGGER.debug("No requestId found. Setting request id to new UUID [{}]", requestId);
                                MDCBuilder.addRequestId(requestId);
                                return requestId;
                            }));

            boolean fullSync = userCrnFilter.isEmpty() && machineUserCrnFilter.isEmpty();
            Json umsEventGenerationIdsJson =  fullSync ?
                    new Json(umsEventGenerationIdsProvider.getEventGenerationIds(accountId, requestIdOptional)) :
                    null;

            Map<String, UsersState> envToUmsStateMap = umsUsersStateProvider
                .getEnvToUmsUsersStateMap(accountId, actorCrn, environmentCrns, userCrnFilter, machineUserCrnFilter, requestIdOptional);

            Collection<SuccessDetails> success = new ConcurrentLinkedQueue<>();
            Collection<FailureDetails> failure = new ConcurrentLinkedQueue<>();

            Map<String, Future<SyncStatusDetail>> statusFutures = stacks.stream()
                    .collect(Collectors.toMap(Stack::getEnvironmentCrn,
                            stack -> asyncTaskExecutor.submit(() -> {
                                MDCBuilder.buildMdcContext(stack);
                                String envCrn = stack.getEnvironmentCrn();
                                SyncStatusDetail statusDetail =
                                        synchronizeStack(stack, envToUmsStateMap.get(stack.getEnvironmentCrn()), userCrnFilter, machineUserCrnFilter);
                                switch (statusDetail.getStatus()) {
                                    case COMPLETED:
                                        success.add(new SuccessDetails(envCrn));
                                        if (umsEventGenerationIdsJson != null) {
                                            UserSyncStatus userSyncStatus = userSyncStatusService.getOrCreateForStack(stack);
                                            userSyncStatus.setUmsEventGenerationIds(umsEventGenerationIdsJson);
                                            userSyncStatusService.save(userSyncStatus);
                                        }
                                        break;
                                    case FAILED:
                                        failure.add(new FailureDetails(envCrn, statusDetail.getDetails()));
                                        break;
                                    default:
                                        failure.add(new FailureDetails(envCrn, "Unknown status"));
                                        break;
                                }
                                return statusDetail;
                            })));

            statusFutures.forEach((envCrn, statusFuture) -> {
                try {
                    statusFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    failure.add(new FailureDetails(envCrn, e.getLocalizedMessage()));
                }
            });
            syncOperationStatusService.completeOperation(operationId, List.copyOf(success), List.copyOf(failure));
        } catch (RuntimeException e) {
            LOGGER.error("User sync operation {} failed with error:", operationId, e);
            syncOperationStatusService.failOperation(operationId, e.getLocalizedMessage());
            throw e;
        }
    }

    private SyncStatusDetail synchronizeStack(Stack stack, UsersState umsUsersState, Set<String> userCrnFilter, Set<String> machineUserCrnFilter) {
        String environmentCrn = stack.getEnvironmentCrn();
        try {
            FreeIpaClient freeIpaClient = freeIpaClientFactory.getFreeIpaClientForStack(stack);
            UsersState ipaUsersState = getIpaUserState(freeIpaClient, umsUsersState, userCrnFilter, machineUserCrnFilter);
            LOGGER.debug("IPA UsersState, found {} users and {} groups", ipaUsersState.getUsers().size(), ipaUsersState.getGroups().size());

            applyStateDifferenceToIpa(stack.getEnvironmentCrn(), freeIpaClient, UsersStateDifference.fromUmsAndIpaUsersStates(umsUsersState, ipaUsersState));

            // Check for the password related attribute (cdpUserAttr) existence and go for password sync.
            processUsersWorkloadCredentials(environmentCrn, umsUsersState, freeIpaClient);

            return SyncStatusDetail.succeed(environmentCrn, "TODO- collect detail info");
        } catch (Exception e) {
            LOGGER.warn("Failed to synchronize environment {}", environmentCrn, e);
            return SyncStatusDetail.fail(environmentCrn, e.getLocalizedMessage());
        }
    }

    @VisibleForTesting
    UsersState getIpaUserState(FreeIpaClient freeIpaClient, UsersState umsUsersState, Set<String> userCrnFilter, Set<String> machineUserCrnFilter)
            throws FreeIpaClientException {
        boolean fullSync = userCrnFilter.isEmpty() && machineUserCrnFilter.isEmpty();
        return fullSync ? freeIpaUsersStateProvider.getUsersState(freeIpaClient) :
                freeIpaUsersStateProvider.getFilteredFreeIPAState(freeIpaClient, umsUsersState.getRequestedWorkloadUsers());
    }

    private void applyStateDifferenceToIpa(String environmentCrn, FreeIpaClient freeIpaClient, UsersStateDifference stateDifference)
            throws FreeIpaClientException {
        LOGGER.info("Applying state difference to environment {}.", environmentCrn);

        addGroups(freeIpaClient, stateDifference.getGroupsToAdd());
        addUsers(freeIpaClient, stateDifference.getUsersToAdd());
        addUsersToGroups(freeIpaClient, stateDifference.getGroupMembershipToAdd());

        removeUsersFromGroups(freeIpaClient, stateDifference.getGroupMembershipToRemove());
        removeUsers(freeIpaClient, stateDifference.getUsersToRemove());
    }

    private void processUsersWorkloadCredentials(
        String environmentCrn, UsersState umsUsersState, FreeIpaClient freeIpaClient) throws IOException, FreeIpaClientException {
        Config config = freeIpaClient.getConfig();
        if (config.getIpauserobjectclasses() == null || !config.getIpauserobjectclasses().contains(Config.CDP_USER_ATTRIBUTE)) {
            LOGGER.debug("Doesn't seems like having config attribute, no credentials sync required for env:{}", environmentCrn);
            return;
        }

        // found the attribute, password sync can be performed
        LOGGER.debug("Having config attribute, going for credentials sync");

        // Should sync for all users and not just diff. At present there is no way to identify that there is a change in password for a user
        for (FmsUser u : umsUsersState.getUsers()) {
            WorkloadCredential workloadCredential = umsUsersState.getUsersWorkloadCredentialMap().get(u.getName());
            if (workloadCredential == null
                || StringUtils.isEmpty(workloadCredential.getHashedPassword())
                || CollectionUtils.isEmpty(workloadCredential.getKeys())) {
                continue;
            }

            // Call ASN_1 Encoder for encoding hashed password and then call user mod for password
            LOGGER.debug("Found Credentials for user {}", u.getName());
            String ansEncodedKrbPrincipalKey = KrbKeySetEncoder.getASNEncodedKrbPrincipalKey(workloadCredential.getKeys());

            Map<String, Object> params =
                ImmutableMap.of("setattr", ImmutableList.of(
                    "cdpHashedPassword=" + workloadCredential.getHashedPassword(),
                    "cdpUnencryptedKrbPrincipalKey=" + ansEncodedKrbPrincipalKey));

            freeIpaClient.userMod(u.getName(), params);
            LOGGER.debug("Password synced for the user:{}, for the environment: {}", u.getName(), environmentCrn);
        }
    }

    private void addGroups(FreeIpaClient freeIpaClient, Set<FmsGroup> fmsGroups) throws FreeIpaClientException {
        for (FmsGroup fmsGroup : fmsGroups) {
            LOGGER.debug("adding group {}", fmsGroup.getName());
            try {
                com.sequenceiq.freeipa.client.model.Group groupAdd = freeIpaClient.groupAdd(fmsGroup.getName());
                LOGGER.debug("Success: {}", groupAdd);
            } catch (FreeIpaClientException e) {
                // TODO propagate this information out to API
                LOGGER.error("Failed to add group {}", fmsGroup.getName(), e);
            }
        }
    }

    private void addUsers(FreeIpaClient freeIpaClient, Set<FmsUser> fmsUsers) throws FreeIpaClientException {
        for (FmsUser fmsUser : fmsUsers) {
            String username = fmsUser.getName();

            LOGGER.debug("adding user {}", username);

            try {
                com.sequenceiq.freeipa.client.model.User userAdd = freeIpaClient.userAdd(
                        username, fmsUser.getFirstName(), fmsUser.getLastName());
                LOGGER.debug("Success: {}", userAdd);
            } catch (FreeIpaClientException e) {
                // TODO propagate this information out to API
                LOGGER.error("Failed to add {}", username, e);
            }
        }
    }

    private void removeUsers(FreeIpaClient freeIpaClient, Set<String> fmsUsers) throws FreeIpaClientException {
        for (String username : fmsUsers) {
            LOGGER.debug("Removing user {}", username);
            try {
                com.sequenceiq.freeipa.client.model.User userRemove = freeIpaClient.deleteUser(username);
                LOGGER.debug("Success: {}", userRemove);
            } catch (FreeIpaClientException e) {
                LOGGER.error("Failed to delete {}", username, e);
            }
        }
    }

    private void removeGroups(FreeIpaClient freeIpaClient, Set<FmsGroup> fmsGroups) throws FreeIpaClientException {
        for (FmsGroup fmsGroup : fmsGroups) {
            String groupname = fmsGroup.getName();

            LOGGER.debug("Removing group {}", groupname);

            try {
                freeIpaClient.deleteGroup(groupname);
                LOGGER.debug("Success: {}", groupname);
            } catch (FreeIpaClientException e) {
                LOGGER.error("Failed to delete {}", groupname, e);
            }
        }

    }

    private void addUsersToGroups(FreeIpaClient freeIpaClient, Multimap<String, String> groupMapping) throws FreeIpaClientException {
        LOGGER.debug("adding users to groups: [{}]", groupMapping);
        for (String group : groupMapping.keySet()) {
            Set<String> users = Set.copyOf(groupMapping.get(group));
            LOGGER.debug("adding users [{}] to group [{}]", users, group);

            try {
                // TODO specialize response object
                RPCResponse<Object> groupAddMember = freeIpaClient.groupAddMembers(group, users);
                LOGGER.debug("Success: {}", groupAddMember.getResult());
            } catch (FreeIpaClientException e) {
                // TODO propagate this information out to API
                LOGGER.error("Failed to add [{}] to group [{}]", users, group, e);
            }
        }
    }

    private void removeUsersFromGroups(FreeIpaClient freeIpaClient, Multimap<String, String> groupMapping) throws FreeIpaClientException {
        for (String group : groupMapping.keySet()) {
            Set<String> users = Set.copyOf(groupMapping.get(group));
            LOGGER.debug("removing users {} from group {}", users, group);

            try {
                // TODO specialize response object
                RPCResponse<Object> groupRemoveMembers = freeIpaClient.groupRemoveMembers(group, users);
                LOGGER.debug("Success: {}", groupRemoveMembers.getResult());
            } catch (FreeIpaClientException e) {
                // TODO propagate this information out to API
                LOGGER.error("Failed to add [{}] to group [{}]", users, group, e);
            }
        }
    }

    private List<Stack> getStacks(String accountId, Set<String> environmentCrnFilter) {
        if (environmentCrnFilter.isEmpty()) {
            LOGGER.debug("Retrieving all stacks for account {}", accountId);
            return stackService.getAllByAccountId(accountId);
        } else {
            LOGGER.debug("Retrieving stacks for account {} that match environment crns {}", accountId, environmentCrnFilter);
            return stackService.getMultipleByEnvironmentCrnAndAccountId(environmentCrnFilter, accountId);
        }
    }

    @VisibleForTesting
    void validateParameters(String accountId, String actorCrn, Set<String> environmentCrnFilter,
            Set<String> userCrnFilter, Set<String> machineUserCrnFilter) {
        requireNonNull(accountId, "accountId must not be null");
        requireNonNull(actorCrn, "actorCrn must not be null");
        requireNonNull(environmentCrnFilter, "environmentCrnFilter must not be null");
        requireNonNull(userCrnFilter, "userCrnFilter must not be null");
        requireNonNull(machineUserCrnFilter, "machineUserCrnFilter must not be null");
        validateCrnFilter(environmentCrnFilter, Crn.ResourceType.ENVIRONMENT);
        validateCrnFilter(userCrnFilter, Crn.ResourceType.USER);
        validateCrnFilter(machineUserCrnFilter, Crn.ResourceType.MACHINE_USER);
    }

    @VisibleForTesting
    void validateCrnFilter(Set<String> crnFilter, Crn.ResourceType resourceType) {
        crnFilter.forEach(crnString -> {
            try {
                Crn crn = Crn.safeFromString(crnString);
                if (crn.getResourceType() != resourceType) {
                    throw new BadRequestException(String.format("Crn %s is not of expected type %s", crnString, resourceType));
                }
            } catch (CrnParseException e) {
                throw new BadRequestException(e.getMessage(), e);
            }
        });
    }

    private Set<String> union(Collection<String> collection1, Collection<String> collection2) {
        return Stream.concat(collection1.stream(), collection2.stream()).collect(Collectors.toSet());
    }
}
