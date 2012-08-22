package com.aerofs.daemon.core.linker;

import com.aerofs.daemon.lib.IStartable;

public interface ILinker extends IStartable
{
    public void init_();

    public static class NullLinker implements ILinker
    {
        @Override
        public void init_() {}
        @Override
        public void start_() {}
    }
}
