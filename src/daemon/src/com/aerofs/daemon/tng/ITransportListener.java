/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

// FIXME: do I have to explicitly list the transport on all of these?
// FIXME: where do I do the mapping from sender stream ids to those that are unique on my device?
public interface ITransportListener extends IPresenceListener, IMaxcastListener, IUnicastListener
{
}
