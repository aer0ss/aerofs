package com.aerofs.daemon.core.phy;

import java.io.InputStream;
import java.util.Collection;

import com.aerofs.lib.Path;
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

    public static final String REV_INDEX_TEXT_ENCODING = "UTF-8";

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
            return (_mtime < r._mtime) ? -1 : (_mtime > r._mtime) ? 1 : 0;
        }
    }

    public static class RevInputStream
    {
        public final long _length;
        public final InputStream _is;

        public RevInputStream(InputStream is, long length)
        {
            _is = is;
            _length = length;
        }
    }

    Collection<Child> listRevChildren_(Path path) throws Exception;

    /**
     * Return the revision list for a given path
     * The list must be sorted. One can use Collections.sort(revisions) for this purpose
     */
    Collection<Revision> listRevHistory_(Path path) throws Exception;

    /**
     * N.B don't forget to close the returned stream after use
     */
    RevInputStream getRevInputStream_(Path path, byte[] index) throws Exception;
}
