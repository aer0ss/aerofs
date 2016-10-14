/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * See class-level comments in IStores for details.
 */
public class SingleuserStoreHierarchy extends StoreHierarchy
{
    @Inject
    public SingleuserStoreHierarchy(IStoreDatabase sdb)
    {
        super(sdb);
    }

    @Override
    public @Nonnull Set<SIndex> getParents_(SIndex sidx) throws SQLException
    {
        Set<SIndex> ret = super.getParents_(sidx);
        assert ret.size() <= 1;
        return ret;
    }

    @Override
    public SIndex getPhysicalRoot_(SIndex sidx) throws SQLException
    {
        Set<SIndex> parents = getParents_(sidx);
        checkArgument(parents.size() <= 1, "%s %s", sidx, parents);
        return parents.isEmpty() ? sidx : getPhysicalRoot_(Iterables.getOnlyElement(parents));
    }

    /**
     * @return the parent of the given store. For single-user systems, a non-root store has exactly
     * one parent.
     *
     * @pre The store is not a root store
     */
    public @Nonnull SIndex getParent_(SIndex sidx) throws SQLException
    {
        Collection<SIndex> ret = getParents_(sidx);
        checkArgument(ret.size() == 1);
        return ret.iterator().next();
    }
}
