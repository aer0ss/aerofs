package com.aerofs.daemon.core.polaris;

import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class RemoteLinkProxy {
    private final SIndex _sidx;
    private final RemoteLinkDatabase _rpdb;
    private final Map<OID, RemoteLink> _overlay = new HashMap<>();

    public RemoteLinkProxy(RemoteLinkDatabase rpdb, SIndex sidx) {
        _rpdb = rpdb;
        _sidx = sidx;
    }

    public RemoteLink getParent_(OID oid) throws SQLException {
        RemoteLink lnk = _overlay.get(oid);
        return lnk != null ? lnk : _rpdb.getParent_(_sidx, oid);
    }

    public void setParent(OID oid, OID newParent, String newName) {
        _overlay.put(oid, new RemoteLink(newParent, newName, -1));
    }
}
