/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.jingle.routing;

import com.aerofs.daemon.transport.jingle.routing.IConnectionService;
import com.aerofs.daemon.transport.xmpp.ISignallingServiceListener;

/**
 * Implemented by {@link com.aerofs.daemon.transport.jingle.routing.IConnectionService} instances whose implementation depends on an
 * out-of-band signalling channel. This is a meta-interface that combines both
 * {@link com.aerofs.daemon.transport.jingle.routing.IConnectionService} and {@link com.aerofs.daemon.transport.xmpp.ISignallingServiceListener}.
 * <br/>
 * <br/>
 * Implementation note: I could have simply moved the methods from
 * <code>ISignallingServiceListener</code> into the definition of <code>ISignalledConnectionService</code>.
 * However, the current approach preserves the relationship between
 * <code>ISignallingService</code> and <code>ISignallingServiceListener</code>. It also
 * allows <code>ISignallingService</code> instances to callback instances of
 * <code>ISignallingServiceListener</code> that are <em>not</em> also <code>IConnectionService</code>
 * instances. I believe the added conceptual weight is extremely low.
 */
public interface ISignalledConnectionService extends IConnectionService, ISignallingServiceListener
{
}
