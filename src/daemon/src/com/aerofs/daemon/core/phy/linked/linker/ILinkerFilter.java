/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.sql.SQLException;

/**
 * This class abstracts away children filtering logic for ScannSession/MightCreate
 */
public interface ILinkerFilter
{
    /**
     * @return whether MightCreate should ignore all children created under the given object
     */
    boolean shouldIgnoreChilren_(PathCombo pc, @Nonnull OA oaParent) throws SQLException;

    /**
     * Regular {@code LinkedStorage} does not need any filtering
     */
    static class AcceptAll implements ILinkerFilter
    {
        @Override
        public boolean shouldIgnoreChilren_(PathCombo pc, @Nonnull OA oaParent) throws SQLException
        {
            return false;
        }
    }

    /**
     * {@code FlatLinkedStorage} needs to filter out creation under anchors
     */
    static class FilterUnderAnchor implements ILinkerFilter
    {
        private final IMapSID2SIndex _sid2sidx;

        @Inject
        public FilterUnderAnchor(IMapSID2SIndex sid2sidx)
        {
            _sid2sidx = sid2sidx;
        }

        @Override
        public boolean shouldIgnoreChilren_(PathCombo pc, @Nonnull OA oaParent) throws SQLException
        {
            return oaParent.isAnchor()
                    || !oaParent.soid().sidx().equals(_sid2sidx.get_(pc._path.sid()));
        }
    }
}
