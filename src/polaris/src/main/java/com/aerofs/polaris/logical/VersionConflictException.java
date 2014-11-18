package com.aerofs.polaris.logical;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;

import java.util.Map;

public final class VersionConflictException extends PolarisException {

    private static final long serialVersionUID = -7931192284107460967L;

    private final String oid;
    private final long expectedVersion;
    private final long actualVersion;

    public VersionConflictException(String oid, long expectedVersion, long actualVersion) {
        super(PolarisError.VERSION_CONFLICT);

        this.oid = oid;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    @Override
    protected String getSimpleMessage() {
        return String.format("%s has version %d instead of expected version %d", oid, actualVersion, expectedVersion);
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("oid", oid);
        errorFields.put("actual_version", actualVersion);
        errorFields.put("expected_version", expectedVersion);
    }
}