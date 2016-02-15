/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import java.io.IOException;

class NativeSocketUnknownPeerException extends IOException
{
    private static final long serialVersionUID = 0L;
    public NativeSocketUnknownPeerException(String s) { super(s); }
}
