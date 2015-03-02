/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;

import java.sql.SQLException;
import java.util.List;

/**
 *
 */
public class StoreCreationOperators
{
    private final List<IStoreCreationOperator> _ops = Lists.newArrayList();

    public void add_(IStoreCreationOperator operator)
    {
        _ops.add(operator);
    }

    void runAll_(SIndex sidx, boolean usePolaris, Trans t) throws SQLException
    {
        for (IStoreCreationOperator operator : _ops) {
            operator.createStore_(sidx, usePolaris, t);
        }
    }
}
