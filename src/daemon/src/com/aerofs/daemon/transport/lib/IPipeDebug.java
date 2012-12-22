/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.IDebug;

/**
 * Implemented by {@link IPipe} implementations that want to supplement the
 * standard {@link IDebug} interface methods with additional ones that supply
 * per-<code>DID</code> information. Like <code>IDebug</code>, implementations of
 * these methods are permitted to block <em>briefly</em>.
 */
public interface IPipeDebug extends IDebug
{
    /**
     * Call to get the number of bytes received from a peer
     *
     * @param did {@link DID} of the peer for which we want the byte-count
     * @return number of bytes that have been received from this peer so far.
     * <strong>IMPORTANT:</strong> the returned value may be <= 0 if the peer is
     * disconnected or there was an error servicing this call
     */
    long getBytesRx(DID did);
}
