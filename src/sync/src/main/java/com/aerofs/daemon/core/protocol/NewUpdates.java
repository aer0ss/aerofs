package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.net.CoreProtocolReactor;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.inject.Inject;
import org.slf4j.Logger;

/**
 * This class is responsible for reacting to NEW_UPDATE messages
 */
public class NewUpdates implements CoreProtocolReactor.Handler
{
    private static final Logger l = Loggers.getLogger(NewUpdates.class);

    private final IMapSID2SIndex _sid2sidx;
    private final Impl _impl;

    public interface Impl {
        void handle_(SIndex sidx, DID did, PBNewUpdates pb) throws Exception;
    }

    @Inject
    public NewUpdates(IMapSID2SIndex sid2sidx, Impl impl)
    {
        _sid2sidx = sid2sidx;
        _impl = impl;
    }

    @Override
    public Type message() {
        return Type.NEW_UPDATES;
    }

    @Override
    public void handle_(DigestedMessage msg) throws Exception
    {
        l.debug("{} process incoming nu over {}", msg.did(), msg.tp());

        if (!msg.pb().hasNewUpdates()) throw new ExProtocolError();
        PBNewUpdates pb = msg.pb().getNewUpdates();
        SIndex sidx = _sid2sidx.getThrows_(new SID(BaseUtil.fromPB(pb.getStoreId())));

        // NB: no ACL check on incoming mcast
        //  - TCP mcast can be forged trivially since the messages are not signed
        //  - mcast do not contain any state, they are pure signals to take further action
        //    like GetVersion or GetFilter, which will do appropriate ACL checks
        //  - race between ACL propagation and mcast could lead to mcast being discarded

        _impl.handle_(sidx, msg.did(), pb);
    }
}
