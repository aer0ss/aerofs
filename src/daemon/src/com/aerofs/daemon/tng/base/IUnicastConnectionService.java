/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.core.net.link.INetworkLinkStateListener;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.lib.id.DID;

import java.util.concurrent.Executor;

public interface IUnicastConnectionService extends INetworkLinkStateListener, IStartable, IDebug
{
    /**
     * Only one listener can be set
     *
     * @param listener {@code IIncomingUnicastConnectionListener} to be notified when an incoming
     * connection is established
     * @param notificationExecutor {@code Executor} on which the notification will be delivered
     */
    void setListener_(IIncomingUnicastConnectionListener listener, Executor notificationExecutor);

    /**
     * Implementers can choose to return a proxy, as long as {@code connect_()} can be safely called
     * on it
     */
    IUnicastConnection createConnection_(DID did);
}