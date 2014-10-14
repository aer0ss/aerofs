package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public final class ParentConflictException extends PolarisException {

    private static final long serialVersionUID = -3167870149030796031L;

    private final String child;
    private final String requestedParent;
    private final LogicalObject conflictingParent;

    public ParentConflictException(String child, String requestedParent, LogicalObject conflictingParent) {
        super(PolarisError.PARENT_CONFLICT);
        this.child = child;
        this.requestedParent = requestedParent;
        this.conflictingParent = conflictingParent;
    }

    @Override
    protected String getSimpleMessage() {
        return String.format("%s is already a child of %s", child, conflictingParent.oid);
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("conflicting_parent", conflictingParent);
        errorFields.put("requested_parent", requestedParent);
        errorFields.put("child", child);
    }
}