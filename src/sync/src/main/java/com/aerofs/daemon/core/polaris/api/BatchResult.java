/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.daemon.core.polaris.api;

import java.util.List;

public class BatchResult
{
    public enum PolarisError
    {
        VERSION_CONFLICT(800),
        NAME_CONFLICT(801),
        INVALID_OPERATION_ON_TYPE(802),
        NO_SUCH_OBJECT(803),
        INSUFFICIENT_PERMISSIONS(804),
        PARENT_CONFLICT(805),
        BATCH_DEPENDENCY(806),
        UNKNOWN(888);

        private final int code;

        PolarisError(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public static class BatchOpResult
    {
        public boolean successful;
        public List<UpdatedObject> updated;
        public PolarisError errorCode;
        public String errorMessage;
    }

    public List<BatchOpResult> results;
}
