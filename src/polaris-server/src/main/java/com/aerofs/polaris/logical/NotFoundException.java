package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public final class NotFoundException extends PolarisException {

    private static final long serialVersionUID = 5816361021481101865L;

    private final String oid;

    public NotFoundException(String oid) {
        super(PolarisError.NO_SUCH_OBJECT);
        this.oid = oid;
    }

    @Override
    protected String getSimpleMessage() {
        return oid + " does not exist";
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("oid", oid);
    }
}
