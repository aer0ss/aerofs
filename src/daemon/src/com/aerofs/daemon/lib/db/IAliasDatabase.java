package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;

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
    void addAliasToTargetMapping_(SIndex sidx, OID alias, OID target, Trans t) throws SQLException;

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
}

