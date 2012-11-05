/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;

import java.sql.SQLException;
import java.util.List;

public interface IStoreDeletionListener
{
    void onStoreDeletion_(SIndex sidx, Trans t) throws SQLException;

    /**
     * A class with which store deletion listeners register, and which StoreDeleter uses to
     * notify the listeners
     * N.B. this breaks the circular dependency of StoreDeleter <-> DirectoryService
     */
    public static class StoreDeletionNotifier
    {
        private final List<IStoreDeletionListener> _storeDeletionListeners = Lists.newArrayList();

        public void addListener_(IStoreDeletionListener listener)
        {
            _storeDeletionListeners.add(listener);
        }

        void notifyListeners_(SIndex sidx, Trans t) throws SQLException
        {
            for (IStoreDeletionListener listener : _storeDeletionListeners) {
                listener.onStoreDeletion_(sidx, t);
            }
        }
    }
}
