package com.aerofs.polaris.resources;

import com.aerofs.baseline.db.Databases;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;
import com.aerofs.polaris.api.batch.BatchError;
import org.skife.jdbi.v2.exceptions.DBIException;

abstract class Resources {

    static Throwable rootCause(Throwable e) {
        if (e instanceof DBIException) {
            return Databases.findExceptionRootCause((DBIException) e);
        }
        return e;
    }

    static BatchError getBatchErrorFromThrowable(Throwable cause) {
        // first, extract the underlying cause if it's an error within DBI
        cause = rootCause(cause);

        // then, find the exact error code and message
        if (cause instanceof PolarisException) {
            PolarisException polarisException = (PolarisException) cause;
            return new BatchError(polarisException.getErrorCode(), polarisException.getSimpleMessage());
        } else {
            return new BatchError(PolarisError.UNKNOWN, cause.getMessage());
        }
    }

    private Resources() {
        // to prevent instantiation by subclasses
    }
}
