package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.core.collector.CollectorSeq;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;

import javax.annotation.Nullable;
import java.sql.SQLException;

public interface ICollectorSequenceDatabase
{
    /**
     * no-op if the component already exists in the CS table.
     */
    void insertCS_(SOCID socid, Trans t) throws SQLException;

    void deleteCS_(CollectorSeq cs, Trans t) throws SQLException;

    public static class OCIDAndCS
    {
        public final OCID _ocid;
        public final CollectorSeq _cs;

        public OCIDAndCS(OCID ocid, CollectorSeq cs)
        {
            _ocid = ocid;
            _cs = cs;
        }

        @Override
        public String toString()
        {
            return _ocid + "," + _cs;
        }
    }

    /**
     * @return all the kml components with cs strictly greater than csStart,
     * if csStart != null; otherwise return all the kml components. The
     * components are ordered by cs
     */
    IDBIterator<OCIDAndCS> getCS_(SIndex sidx, @Nullable CollectorSeq csStart, int limit)
            throws SQLException;

    IDBIterator<OCIDAndCS> getMetaCS_(SIndex sidx, @Nullable CollectorSeq csStart, int limit)
            throws SQLException;

    void deleteCSsForStore_(SIndex sidx, Trans t) throws SQLException;
}
