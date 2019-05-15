package com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.region;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.annotations.ApiModel;

@ApiModel("PlacementV1Request")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class PlacementRequest extends PlacementBase {
}
