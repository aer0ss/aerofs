/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

/**
 * The database that persists the state of collectors. Only collector classes e.g.
 * {@link com.aerofs.daemon.core.collector.CollectorIterator} should use this interface.
 *
 * Note that there is no method to create or delete entries. This is because this interface is
 * backed by the store database and store creation and deletion naturally creates and deletes the
 * entries.
 */
public interface ICollectorStateDatabase
{
    /**
     * Return whether the collector iterator of the given store includes content components
     */
    boolean isCollectingContent_(SIndex sidx) throws SQLException;

    /**
     * Set whether the collector iterator of the given store includes content components
     */
    void setCollectingContent_(SIndex sidx, boolean collectingContent, Trans t) throws SQLException;
}
