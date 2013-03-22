/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Helper for low-level walk of the metadatabase
 *
 * This interface should be used when a DirectoryService.walk_ is unsuitable for performance reaons
 * and fetching full OA and CA is not needed.
 *
 * On large hierarchies it can yield ~6x faster walks
 */
public interface IMetaDatabaseWalker
{
    public static class TypeNameOID
    {
        public final String _name;
        public final OID _oid;
        public final Type _type;

        TypeNameOID(String name, OID oid, Type type) { _name = name; _oid = oid; _type = type; }
    }

    Collection<TypeNameOID> getTypedChildren_(SIndex sidx, OID parent) throws SQLException;
}
