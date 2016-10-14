package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;

import java.sql.SQLException;

/**
 * When possible, use the MapAlias2Target class which is a high-level wrapper around this low-level
 * database class.
 */
public interface IAliasDatabase
{
    /**
     * Adds alias to target object mapping in the persistent store.
     */
    void insertAliasToTargetMapping_(SIndex sidx, OID alias, OID target, Trans t) throws SQLException;

    /**
     * @return target object-id for given soid if one exists else returns null.
     */
    OID getTargetOID_(SIndex sidx, OID alias) throws SQLException;

    /**
     * Resolves chaining, if any, among specified oids in the persistent store.
     *
     * For e.g. If persistent stores contains mapping a --> b and new alias to target mapping
     * b --> c is added by aliasing operation then this method will change mapping a --> b as
     * a --> c and hence resolve chaining.
     */
    void resolveAliasChaining_(SIndex sidx, OID alias, OID target, Trans t) throws SQLException;

    /**
     * NB: this method is purely for test purposes: do not use in production code
     */
    IDBIterator<OID> getAliases_(SOID soid) throws SQLException;
}

