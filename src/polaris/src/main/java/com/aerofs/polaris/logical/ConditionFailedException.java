package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public class ConditionFailedException extends PolarisException {

    private static final long serialVersionUID = 2060157192676717488L;

    public ConditionFailedException() {
        super(PolarisError.CONDITION_FAILED);
    }

    @Override
    public String getSimpleMessage() {
        return "fail condition";
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        // empty on purpose
    }

    @Override
    public String typeForAPIException() {
        return "CONFLICT";
    }
}
