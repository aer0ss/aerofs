package com.aerofs.daemon.core.migration;

import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.lib.LRUCache;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class RemoteTreeCache {
    private final static Logger l = LoggerFactory.getLogger(RemoteTreeCache.class);

    private final SIndex sidx;
    private final OID root;

    private final RemoteLinkDatabase rldb;

    private final LRUCache<OID, Boolean> inSharedTree;

    public RemoteTreeCache(SIndex sidx, OID root, RemoteLinkDatabase rldb) {
        this.sidx = sidx;
        this.root = root;
        this.rldb = rldb;
        inSharedTree = new LRUCache<>(10000);
    }

    public boolean isInSharedTree(OID oid) throws SQLException {
        if (root.equals(oid)) return true;
        Boolean r = inSharedTree.get_(oid);
        if (r == null) {
            RemoteLink lnk = rldb.getParent_(sidx, oid);
            r = lnk != null && isInSharedTree(lnk.parent);
            inSharedTree.put_(oid, r);
        }
        l.debug("{} under {}{}: {}", oid, sidx, root, r);
        return r;
    }
}
