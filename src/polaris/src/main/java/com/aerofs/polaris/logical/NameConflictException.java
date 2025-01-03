package com.aerofs.polaris.logical;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.PolarisUtilities;

import java.util.Map;

public final class NameConflictException extends PolarisException {

    private static final long serialVersionUID = -397290595244198563L;

    private final UniqueID parent;
    private final String childName;
    private final UniqueID conflictingObject;

    public NameConflictException(UniqueID parent, byte[] childName, UniqueID conflictingObject) {
        super(PolarisError.NAME_CONFLICT);
        this.parent = parent;
        this.childName = PolarisUtilities.stringFromUTF8Bytes(childName);
        this.conflictingObject = conflictingObject;
    }

    @Override
    public String getSimpleMessage() {
        return parent.toStringFormal() + "/" + childName + ":" + conflictingObject.toStringFormal();
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("parent", parent.toStringFormal());
        errorFields.put("child_name", childName);
        errorFields.put("conflicting_object", conflictingObject.toStringFormal());
    }

    @Override
    public String typeForAPIException() {
        return "CONFLICT";
    }
}
