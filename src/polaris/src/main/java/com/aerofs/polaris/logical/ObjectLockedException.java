package com.aerofs.polaris.logical;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public class ObjectLockedException extends PolarisException {

    private static final long serialVersionUID = -2900943994799052171L;

    private final UniqueID oid;

    public ObjectLockedException(UniqueID oid) {
        super(PolarisError.OBJECT_LOCKED);
        this.oid = oid;
    }

    @Override
    public  String getSimpleMessage() {
        return String.format("%s is locked and cannot currently be modified", oid);
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("oid", oid.toStringFormal());
    }
}
