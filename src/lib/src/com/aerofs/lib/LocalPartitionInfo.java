package com.aerofs.lib;

import java.io.File;

public class LocalPartitionInfo {

    private final String _absPath;
    private final File _f;
    private final long _quota;
    private final long _total;

    // @param absPath must be absolute
    // @param quota >0: absolute quota in bytes
    //              =0: unlimited or unspecified. will be converted to total space
    //                  of the local filesystem.
    //              <0: % of free space. -15 means 15%.
    //
    public LocalPartitionInfo(String absPath, long quota)
    {
        _absPath = absPath;
        _f = new File(absPath);
        _quota = quota;
        _total = _f.getTotalSpace();

        assert _f.isAbsolute();
    }

    /**
     * @return the absolute path
     */
    public String absPath()
    {
        return _absPath;
    }

    public long quota()
    {
        return _quota;
    }

    public long totalSpace()
    {
        return _total;
    }

    public long getFreeSpace()
    {
        return _f.getFreeSpace();
    }

}
