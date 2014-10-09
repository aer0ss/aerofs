package com.aerofs.polaris.acl;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public final class AccessException extends PolarisException {

    private static final long serialVersionUID = -3547417330189536355L;

    private final String user;
    private final String oid;
    private final Access access;

    public AccessException(String user, String oid, Access access) {
        super(PolarisError.INSUFFICIENT_PERMISSIONS);

        this.user = user;
        this.oid = oid;
        this.access = access;
    }

    @Override
    protected String getSimpleMessage() {
        return String.format("user %s cannot access %s for %s", user, oid, access.name());
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("user", user);
        errorFields.put("oid", oid);
        errorFields.put("access", access);
    }
}
