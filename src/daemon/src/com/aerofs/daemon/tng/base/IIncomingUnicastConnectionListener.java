/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.id.DID;
import com.aerofs.lib.notifier.IListenerVisitor;

public interface IIncomingUnicastConnectionListener
{
    /**
     * Triggers on connections that are not explicitly requested by you
     */
    void onNewIncomingConnection_(DID did, IUnicastConnection unicast);

    public static class Visitor implements IListenerVisitor<IIncomingUnicastConnectionListener>
    {
        private final IUnicastConnection _unicast;
        private final DID _did;

        public Visitor(DID did, IUnicastConnection unicast)
        {
            this._did = did;
            this._unicast = unicast;
        }

        @Override
        public void visit(IIncomingUnicastConnectionListener listener)
        {
            listener.onNewIncomingConnection_(_did, _unicast);
        }
    }
}
