package com.aerofs.daemon.core.alias;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.IAliasDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helps add/retrieve alias to target mapping in the persistent store.
 */
public class MapAlias2Target
{
    private static final Logger l = Loggers.getLogger(MapAlias2Target.class);

    private final IAliasDatabase _aldb;

    @Inject
    public MapAlias2Target(IAliasDatabase aldb)
    {
        _aldb = aldb;
    }

    /**
     * Adds alias to target oid mapping in persistent store and resolves chaining, if any.
     */
    public void add_(SOID alias, SOID target, Trans t) throws SQLException
    {
        SIndex sidx = alias.sidx();
        assert sidx.equals(target.sidx());

        _aldb.insertAliasToTargetMapping_(sidx, alias.oid(), target.oid(), t);
        _aldb.resolveAliasChaining_(sidx, alias.oid(), target.oid(), t);
    }

    /**
     * @return Returns target object-id for given soid if one exists else returns null.
     */
    public @Nullable OID getNullable_(SOID alias) throws SQLException
    {
        return _aldb.getTargetOID_(alias.sidx(), alias.oid());
    }

    public boolean isAliased_(SOID soid) throws SQLException
    {
        return getNullable_(soid) != null;
    }

    /**
     * @return the target SOID of {@code soid} if it is aliased, otherwise return soid
     */
    public @Nonnull SOID dereferenceAliasedOID_(SOID soid) throws SQLException
    {
        final OID oidTarget = getNullable_(soid);
        if (oidTarget == null) return soid;

        SOID soidTarget = new SOID(soid.sidx(), oidTarget);
        l.debug("dereferenced alias" + soid + "->" + soidTarget);

        return soidTarget;
    }
}
