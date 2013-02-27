/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.tap.filter.IMessageFilterListener;
import com.aerofs.daemon.tap.filter.MessageFilterRequest;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.proto.Transport.PBTPHeader;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OutgoingMessageFilterListener implements IMessageFilterListener
{
    private static final Logger l = Loggers.getLogger(OutgoingMessageFilterListener.class);

    private final ISingleThreadedPrioritizedExecutor _executor;
    private final Set<PBTPHeader.Type> _denySet = new HashSet<PBTPHeader.Type>();
    private boolean _denyAll = false;

    public OutgoingMessageFilterListener(ISingleThreadedPrioritizedExecutor executor)
    {
        _executor = executor;
    }

    public void denyAll_()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                _denyAll = true;
            }
        });
    }

    public void denyNone_()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                _denyAll = false;
                _denySet.clear();
            }
        });
    }

    public void deny_(final List<PBTPHeader.Type> denyList)
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                _denySet.addAll(denyList);
            }
        });
    }

    @Override
    public void onOutgoingMessageReceived_(MessageFilterRequest request)
    {
        // This was configured to run on _executor, so this is safe

        if (request.message instanceof OutgoingAeroFSPacket) {
            OutgoingAeroFSPacket packet = (OutgoingAeroFSPacket) request.message;
            if (_denySet.contains(packet.getHeader_().getType()) || _denyAll) {
                request.deny();
                l.info("Denied a message of type " + packet.getHeader_().getType());
            }
        }
        request.allow();
    }

    @Override
    public void onIncomingMessageReceived_(MessageFilterRequest request)
    {
        request.allow();
    }
}
