package com.aerofs.daemon.core.ds;

import com.aerofs.daemon.core.phy.IPhysicalFile;

/**
 * Content Attribute
 */
public class CA
{
    private volatile long _len;
    private volatile long _mtime;
    private IPhysicalFile _pf;

    public CA(long len, long mtime)
    {
        _len = len;
        _mtime = mtime;
    }

    @Override
    public String toString()
    {
        return "l " + _len + " mt " + _mtime;
    }

    // the content length may not be accurate since when the file is off cache,
    // it's the length on the remote device when the metadata was downloaded, or
    // the local length when it's evicted, depending on which happened last;
    // if it's in cache, new lengths made by file writes via FSI is not propagated
    // to ObjectAttr until ComMonitor.endWrite() is called.
    //
    public long length()
    {
        return _len;
    }

    public void length(long len)
    {
        _len = len;
    }

    public void mtime(long mtime)
    {
        _mtime = mtime;
    }

    public long mtime()
    {
        return _mtime;
    }

    /**
     * internal use. only DirectoryService should call this.
     */
    void setPhysicalFile_(IPhysicalFile pf)
    {
        assert _pf == null;
        _pf = pf;
    }

    public IPhysicalFile physicalFile()
    {
        return _pf;
    }
}
