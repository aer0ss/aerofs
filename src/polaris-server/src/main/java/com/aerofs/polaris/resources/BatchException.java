package com.aerofs.polaris.resources;

import com.aerofs.polaris.PolarisException;

public final class BatchException extends PolarisException {

    private static final long serialVersionUID = 3500966318187392676L;

    BatchException(int failedOperationNumber, Throwable cause) {
        super("failed batch operation " + failedOperationNumber, cause);
    }
}
