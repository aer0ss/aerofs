/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.daemon.core.polaris.api;

import java.util.List;

public class Batch
{
    public static class BatchOp
    {
        public final String oid;
        public final LocalChange operation;

        public BatchOp(String oid, LocalChange op)
        {
            this.oid = oid;
            this.operation = op;
        }
    }

    public final List<BatchOp> operations;

    public Batch(List<BatchOp> ops)
    {
        operations = ops;
    }
}
