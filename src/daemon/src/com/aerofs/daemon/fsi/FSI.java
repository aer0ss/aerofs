package com.aerofs.daemon.fsi;

import com.aerofs.daemon.IModule;
import com.aerofs.daemon.fsi.protobuf.FSIProtobuf;

public class FSI implements IModule
{
    private final FSIProtobuf _pb = new FSIProtobuf();

    @Override
    public void init_() throws Exception
    {
        _pb.init_();
    }

    @Override
    public void start_()
    {
        _pb.start_();
    }
}
