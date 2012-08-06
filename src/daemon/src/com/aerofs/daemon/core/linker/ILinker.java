package com.aerofs.daemon.core.linker;

import java.io.IOException;

public interface ILinker
{
    public void init_();
    public void start_() throws IOException;

    public static class NullLinker implements ILinker
    {
        public void init_() {}
        public void start_() throws IOException {}
    }
}
