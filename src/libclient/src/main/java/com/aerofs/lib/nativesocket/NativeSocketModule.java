/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import com.google.inject.AbstractModule;

public class NativeSocketModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(AbstractNativeSocketPeerAuthenticator.class).toInstance(
                NativeSocketAuthenticatorFactory.create());
    }
}
