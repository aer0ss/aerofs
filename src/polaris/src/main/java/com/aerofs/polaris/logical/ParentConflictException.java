package com.aerofs.polaris.logical;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public final class ParentConflictException extends PolarisException {

    private static final long serialVersionUID = -3167870149030796031L;

    private final UniqueID child;
    private final UniqueID requestedParent;
    private final UniqueID conflictingParent;

    public ParentConflictException(UniqueID child, UniqueID requestedParent, UniqueID conflictingParent) {
        super(PolarisError.PARENT_CONFLICT);
        this.child = child;
        this.requestedParent = requestedParent;
        this.conflictingParent = conflictingParent;
    }

    @Override
    public String getSimpleMessage() {
        return String.format("%s is already a child of %s", child, conflictingParent);
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("child", child.toStringFormal());
        errorFields.put("requested_parent", requestedParent.toStringFormal());
        errorFields.put("conflicting_parent", conflictingParent.toStringFormal());
    }

    @Override
    public String typeForAPIException() {
        return "CONFLICT";
    }
}
