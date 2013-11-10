package com.aerofs.daemon.core.phy;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;

import com.aerofs.base.BaseUtil;
import com.aerofs.lib.Path;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.proto.Ritual.PBRevChild;
import com.aerofs.proto.Ritual.PBRevision;
import com.google.protobuf.ByteString;

/**
 * a revision provider provides revision history, storation of a revision, etc
 */
public interface IPhysicalRevProvider
{
    public static class Child
    {
        public final String _name;
        public final boolean _dir;

        public Child(String name, boolean dir)
        {
            _name = name;
            _dir = dir;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Child))
                return false;
            Child c = (Child)obj;
            return c._name.equals(_name) && c._dir == _dir;
        }

        @Override
        public int hashCode()
        {
            return _name.hashCode();
        }

        public PBRevChild toPB()
        {
            return PBRevChild.newBuilder()
                    .setName(_name)
                    .setIsDir(_dir)
                    .build();
        }
    }

    public static class Revision implements Comparable<Revision>
    {
        public final byte[] _index; // index is a cookie uniquely identifying the
                                    // revision (e.g. file,version pair)
        public final long _mtime;
        public final long _length;

        /**
         * @param index an opaque field used by the implementing class to
         * uniquely identify a revision in revGetInputStream()
         */
        public Revision(byte[] index, long mtime, long length)
        {
            _index = index;
            _mtime = mtime;
            _length = length;
        }

        public PBRevision toPB()
        {
            return PBRevision.newBuilder()
                    .setIndex(ByteString.copyFrom(_index))
                    .setMtime(_mtime)
                    .setLength(_length)
                    .build();
        }

        @Override
        public int compareTo(Revision r)
        {
            return BaseUtil.compare(_mtime, r._mtime);
        }

        @Override
        public String toString()
        {
            return "Rev(" + BaseUtil.utf2string(_index) + "," + _mtime + "," + _length + ")";
        }
    }

    public static class RevInputStream
    {
        public final long _length;
        public final InputStream _is;
        public final long _mtime;

        public RevInputStream(InputStream is, long length, long mtime)
        {
            _is = is;
            _length = length;
            _mtime = mtime;
        }
    }

    public class ExInvalidRevisionIndex extends ExNotFound
    {
        private static final long serialVersionUID = 0L;
    }

    Collection<Child> listRevChildren_(Path path) throws IOException, SQLException;

    /**
     * Return the revision list for a given path
     * The list must be sorted. One can use Collections.sort(revisions) for this purpose
     */
    Collection<Revision> listRevHistory_(Path path) throws IOException, SQLException;

    /**
     * N.B don't forget to close the returned stream after use
     */
    RevInputStream getRevInputStream_(Path path, byte[] index)
            throws IOException, SQLException, ExInvalidRevisionIndex;

    void deleteRevision_(Path path, byte[] index)
            throws IOException, SQLException, ExInvalidRevisionIndex;

    void deleteAllRevisionsUnder_(Path path) throws IOException, SQLException;
}
