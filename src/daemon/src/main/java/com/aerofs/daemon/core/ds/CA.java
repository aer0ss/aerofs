package com.aerofs.daemon.core.ds;

/**
 * Content Attribute
 *
 * Useful invariants
 *
 * 1. Presence of MASTER CA                <=> presence of valid FID
 * 2. Absence of content hash               => stale branch
 * 3. DB(length,mtime) != FS(length,mtime)  => stale branch
 *
 * A stale branch is one for which local changes are known to exist but have yet to be acknowledged.
 * A change is only acknowledged when the new content hash is computed, at which point it can be
 * propagated to other devices, either by bumping the local tick (old distributed versioning) or by
 * queueing the change for submission to polaris (new centralised versioning).
 *
 * NB: only the MASTER (aka local) branch can ever be stale (unless of course someone or something
 * mucks with the aux root but then all bets are off).
 */
public class CA
{
    private volatile long _len;
    private volatile long _mtime;

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
}
