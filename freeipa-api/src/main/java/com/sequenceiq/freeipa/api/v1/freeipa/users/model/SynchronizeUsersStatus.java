package com.sequenceiq.freeipa.api.v1.freeipa.users.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.ApiModel;

@ApiModel("SynchronizeUsersV1Status")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SynchronizeUsersStatus {
    private final String value;

    public SynchronizeUsersStatus(String value) {
        this.value = requireNonNull(value);
    }

    public String getValue() {
        return value;
    }
}
