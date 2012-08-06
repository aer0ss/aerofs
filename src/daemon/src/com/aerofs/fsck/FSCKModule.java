package com.aerofs.fsck;

import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

public class FSCKModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
    }
}
