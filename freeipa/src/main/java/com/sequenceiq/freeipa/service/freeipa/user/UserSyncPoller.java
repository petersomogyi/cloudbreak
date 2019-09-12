package com.sequenceiq.freeipa.service.freeipa.user;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.auth.security.InternalCrnBuilder;
import com.sequenceiq.freeipa.api.v1.freeipa.user.model.SyncOperationStatus;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.service.stack.StackService;

@Service
public class UserSyncPoller {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserSyncPoller.class);

    private static final String INTERNAL_ACTOR_CRN = new InternalCrnBuilder(Crn.Service.IAM).getInternalCrnForServiceAsString();

    @Inject
    private ThreadBasedUserCrnProvider threadBasedUserCrnProvider;

    @Inject
    private StackService stackService;

    @Inject
    private UserService userService;

    @Value("${freeipa.syncoperation.poller.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${freeipa.syncoperation.poller.fixed-delay-millis:60000}",
            initialDelayString = "${freeipa.syncoperation.poller.initial-delay-millis:300000}")
    public void pollUms() {
        try {
            if (enabled) {
                syncFreeIpaStacks();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to automatically sync users to FreeIPA stacks", e);
        }
    }

    private void syncFreeIpaStacks() {
        LOGGER.debug("Attempting to sync users to FreeIPA stacks");
        List<Stack> stackList = stackService.findAllRunning();
        LOGGER.debug("Found {} active stacks", stackList.size());
        stackList.stream()
                .filter(this::isStale)
                .forEach(stack -> {
                    threadBasedUserCrnProvider.setUserCrn(INTERNAL_ACTOR_CRN);
                    try {
                        LOGGER.debug("Environment {} in Account {} is stale.", stack.getEnvironmentCrn(), stack.getAccountId());
                        SyncOperationStatus status = userService.synchronizeUsers(stack.getAccountId(), threadBasedUserCrnProvider.getUserCrn(),
                                Set.of(stack.getEnvironmentCrn()), Set.of(), Set.of());
                        LOGGER.debug("Sync request resulted in operation {}", status);
                    } finally {
                        threadBasedUserCrnProvider.removeUserCrn();
                    }
                });
    }

    private boolean isStale(Stack stack) {
        // TODO implement CDPCP-720 after CDPCP-719 updates the UMS API
        return true;
    }
}
