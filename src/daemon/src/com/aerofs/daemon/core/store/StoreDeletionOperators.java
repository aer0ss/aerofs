/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;

import java.sql.SQLException;
import java.util.List;

/**
 * See IStoreDeletionOperator
 */
public class StoreDeletionOperators
{
    private final List<IStoreDeletionOperator> _operators = Lists.newArrayList();

    public void add_(IStoreDeletionOperator operator)
    {
        _operators.add(operator);
    }

    void runAll_(SIndex sidx, Trans t) throws SQLException
    {
        for (IStoreDeletionOperator operator : _operators) {
            operator.deleteStore_(sidx, t);
        }
    }
}
