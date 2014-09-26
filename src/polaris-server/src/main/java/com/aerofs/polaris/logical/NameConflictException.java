package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.ErrorCode;
import com.aerofs.polaris.api.LogicalObject;

import java.util.Map;

public final class NameConflictException extends PolarisException {

    private static final long serialVersionUID = -397290595244198563L;

    private final String parent;
    private final String childName;
    private final LogicalObject conflictingObject;

    public NameConflictException(String parent, String childName, LogicalObject conflictingObject) {
        super(ErrorCode.NAME_CONFLICT);

        this.parent = parent;
        this.childName = childName;
        this.conflictingObject = conflictingObject;
    }

    @Override
    protected String getSimpleMessage() {
        return String.format("child with name %s already exists in %s", childName, parent);
    }

    @Override
    protected void addProperties(Map<String, Object> errorProperties) {
        errorProperties.put("parent", parent);
        errorProperties.put("child_name", childName);
        errorProperties.put("conflicting_object", conflictingObject);
    }
}
