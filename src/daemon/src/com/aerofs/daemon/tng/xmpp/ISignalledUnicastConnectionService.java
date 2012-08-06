/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.daemon.tng.base.IUnicastConnectionService;

/**
 * Implemented by {@link com.aerofs.daemon.tng.diagnosis.TransportDiagnosisState.IUnicastIOLayer}
 * instances whose implementation depends on an out-of-band signalling channel. This is a
 * meta-interface that combines both {@link com.aerofs.daemon.tng.diagnosis.TransportDiagnosisState.IUnicastIOLayer}
 * and {@link ISignallingClient}. <br/> <br/> Implementation note: I could have simply moved the
 * methods from <code>ISignallingClient</code> into the definition of
 * <code>ISignalledUnicastConnectionService</code>. However, the current approach preserves the
 * relationship between <code>ISignallingService</code> and <code>ISignallingClient</code>. It also
 * allows <code>ISignallingService</code> instances to callback instances of
 * <code>ISignallingClient</code> that are <em>not</em> also <code>IUnicastConnectionService</code>
 * instances. I believe the added conceptual weight is extremely low.
 */
public interface ISignalledUnicastConnectionService
        extends IUnicastConnectionService, ISignallingClient
{
}
