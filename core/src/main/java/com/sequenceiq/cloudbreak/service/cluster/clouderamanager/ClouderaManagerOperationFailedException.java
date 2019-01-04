package com.sequenceiq.cloudbreak.service.cluster.clouderamanager;

import com.sequenceiq.cloudbreak.service.CloudbreakServiceException;

public class ClouderaManagerOperationFailedException extends CloudbreakServiceException {

    public ClouderaManagerOperationFailedException(String message) {
        super(message);
    }

    public ClouderaManagerOperationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
