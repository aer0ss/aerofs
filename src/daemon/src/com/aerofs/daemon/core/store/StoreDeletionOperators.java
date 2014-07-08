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
 * When deleting a store we want all subsystems to be able to cleanup related data.
 *
 * To avoid introducing explicit outward dependency from the store subsystem to other subsystems
 * we use a listener pattern.
 *
 * With the advent of incremental deletion (know to friends and family as scalable deletion) we
 * have to distinguish between "immediate" and "deferred" listeners.
 *
 * "immediate" deletion operators are executed immediately after the store is removed from the
 * local store hierarchy, as part of the small transaction that effectively marks the store as
 * absent.
 *
 * At that point the database may still contain stale information relative to that store (OAs,
 * CAs, version vectors, ...) which are cleaned incrementally by the LogicalStagingArea.
 *
 * "deferred" deletion operators are called at the end of this incremental cleanup, as part
 * of the last transaction that finalizes the deletion.
 */
public class StoreDeletionOperators
{
    private final List<IStoreDeletionOperator> _immediate = Lists.newArrayList();
    private final List<IStoreDeletionOperator> _deferred = Lists.newArrayList();

    public void addImmediate_(IStoreDeletionOperator operator)
    {
        _immediate.add(operator);
    }

    public void addDeferred_(IStoreDeletionOperator operator)
    {
        _deferred.add(operator);
    }

    void runAllImmediate_(SIndex sidx, Trans t) throws SQLException
    {
        for (IStoreDeletionOperator operator : _immediate) {
            operator.deleteStore_(sidx, t);
        }
    }

    public void runAllDeferred_(SIndex sidx, Trans t) throws SQLException
    {
        for (IStoreDeletionOperator operator : _deferred) {
            operator.deleteStore_(sidx, t);
        }
    }
}
