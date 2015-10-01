package com.sequenceiq.cloudbreak.service.stack.event;

import com.sequenceiq.cloudbreak.common.type.CloudPlatform;

public class PrepareImageComplete extends ProvisionEvent {

    public PrepareImageComplete(CloudPlatform cloudPlatform, Long stackId) {
        super(cloudPlatform, stackId);
    }

}
