package com.aerofs.polaris.api.operation;

import com.aerofs.ids.UniqueID;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

@JsonPropertyOrder({"type"}) // this field is always serialized first
public abstract class Operation {

    static final String TYPE_FIELD_NAME = "type";

    @NotNull
    public final OperationType type;

    protected Operation(OperationType type) {
        this.type = type;
    }

    public List<UniqueID> affectedOIDs() {
        return Collections.emptyList();
    }
}
