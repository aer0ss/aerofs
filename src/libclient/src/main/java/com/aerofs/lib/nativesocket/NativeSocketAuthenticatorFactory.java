/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import com.aerofs.lib.os.OSUtil;

public class NativeSocketAuthenticatorFactory
{
    public static AbstractNativeSocketPeerAuthenticator create()
    {
        if (OSUtil.isWindows()) {
            return new WinNamedPipePeerAuthenticator();
        }
        return new UnixDomainSockPeerAuthenticator(OSUtil.get());
    }
}
