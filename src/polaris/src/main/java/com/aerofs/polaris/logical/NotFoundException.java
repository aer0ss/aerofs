package com.aerofs.polaris.logical;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public final class NotFoundException extends PolarisException {

    private static final long serialVersionUID = 5816361021481101865L;

    private final UniqueID oid;

    public NotFoundException(UniqueID oid) {
        super(PolarisError.NO_SUCH_OBJECT);
        this.oid = oid;
    }

    @Override
    public  String getSimpleMessage() {
        return String.format("%s does not exist", oid);
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("oid", oid.toStringFormal());
    }

    @Override
    public String typeForAPIException() {
        return "NOT_FOUND";
    }
}
