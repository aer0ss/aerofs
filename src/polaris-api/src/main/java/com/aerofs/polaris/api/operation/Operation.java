package com.aerofs.polaris.api.operation;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.NotNull;

@JsonPropertyOrder({"type"}) // this field is always serialized first
public abstract class Operation {

    static final String TYPE_FIELD_NAME = "type";

    @NotNull
    public final OperationType type;

    protected Operation(OperationType type) {
        this.type = type;
    }
}
