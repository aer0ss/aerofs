/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp;

/**
 * Implemented by {@link IPipe} instances whose implementation depends on an
 * out-of-band signalling channel. This is a meta-interface that combines both
 * {@link IPipe} and {@link ISignallingClient}.
 * <br/>
 * <br/>
 * Implementation note: I could have simply moved the methods from
 * <code>ISignallingClient</code> into the definition of <code>ISignalledPipe</code>.
 * However, the current approach preserves the relationship between
 * <code>ISignallingChannel</code> and <code>ISignallingClient</code>. It also
 * allows <code>ISignallingChannel</code> instances to callback instances of
 * <code>ISignallingClient</code> that are <em>not</em> also <code>IPipe</code>
 * instances. I believe the added conceptual weight is extremely low.
 */
public interface ISignalledPipe extends IPipe, ISignallingClient
{
}
