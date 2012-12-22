package com.aerofs.daemon.mobile;

import javax.annotation.Nullable;

import com.aerofs.base.id.UserID;
import org.jboss.netty.buffer.ChannelBuffer;

import com.aerofs.daemon.event.fs.AbstractEIFS;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.Version;

public class EIDownloadPacket extends AbstractEIFS
{
    public static final long UNDEFINED_LENGTH = -1;
    public static final long UNDEFINED_MOD_TIME = -1;

    public @Nullable Path _path;
    public final long _offset;
    public final int _packetSize;

    public long _inFileLength = UNDEFINED_LENGTH;
    public long _inFileModTime = UNDEFINED_MOD_TIME;
    public @Nullable Version _inVersion;

    public long _fileLength = UNDEFINED_LENGTH;
    public long _fileModTime = UNDEFINED_MOD_TIME;
    public @Nullable Version _localVersion;

    public boolean _done;
    public @Nullable ChannelBuffer _data;

    public EIDownloadPacket(UserID user, IIMCExecutor imce, Path path, long offset, int packetSize)
    {
        super(user, imce);
        _path = path;
        _offset = offset;
        _packetSize = packetSize;
    }

}
