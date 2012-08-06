/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.core.net.link.INetworkLinkStateListener;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.tng.IUnicast;

public interface IUnicastService extends IUnicast, INetworkLinkStateListener, IStartable, IDebug
{
}
