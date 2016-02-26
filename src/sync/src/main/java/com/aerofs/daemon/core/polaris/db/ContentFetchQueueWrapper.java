package com.aerofs.daemon.core.polaris.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;

import javax.inject.Inject;

import java.sql.SQLException;
import java.util.List;

public class ContentFetchQueueWrapper
{
    private final ContentFetchQueueDatabase _cfqdb;
    private final List<IContentFetchQueueListener> _listeners;

    @Inject
    public ContentFetchQueueWrapper(ContentFetchQueueDatabase cfqdb) {
        this._cfqdb = cfqdb;
        this._listeners = Lists.newArrayList();
    }

    public void addListener(IContentFetchQueueListener listener) {
        _listeners.add(listener);
    }

    private void notifyListenersOnInsert_(SIndex sidx, OID oid, Trans t) throws SQLException {
        for (IContentFetchQueueListener listener : _listeners) {
            listener.onInsert_(sidx, oid, t);
        }
    }

    public void insert_(SIndex sidx, OID oid, Trans t) throws SQLException {
        _cfqdb.insert_(sidx, oid, t);
        notifyListenersOnInsert_(sidx, oid, t);
    }
}
