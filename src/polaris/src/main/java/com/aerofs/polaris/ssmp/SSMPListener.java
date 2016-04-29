package com.aerofs.polaris.ssmp;

import com.aerofs.base.Loggers;
import com.aerofs.baseline.Managed;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.logical.SFMemberChangeListener;
import com.aerofs.ssmp.*;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static com.aerofs.polaris.Constants.SFNOTIF_PREFIX;
import static com.aerofs.polaris.Constants.SFNOTIF_JOIN;
import static com.aerofs.polaris.Constants.SFNOTIF_LEAVE;
import static com.aerofs.polaris.Constants.SFNOTIF_CHANGE;

@Singleton
public class SSMPListener implements EventHandler, Managed {
    private final SFMemberChangeListener listener;
    private final AccessManager access;
    private final SSMPConnectionWrapper wrapper;
    private final static Logger l = Loggers.getLogger(SSMPListener.class);

    // TODO sp and sparta both use the anonymous ssmp id
    private final List<SSMPIdentifier> VALID_SENDERS = Lists.newArrayList(SSMPIdentifier.ANONYMOUS);

    @Inject
    public SSMPListener(SSMPConnectionWrapper ssmpConnectionWrapper, SFMemberChangeListener listener, AccessManager access)
    {
        wrapper = ssmpConnectionWrapper;
        this.listener = listener;
        this.access = access;
    }

    @Override
    public void eventReceived(SSMPEvent e) {
        l.info("received ssmp event {}", e);
        if (e.payload == null || !VALID_SENDERS.contains(e.from)) {
            l.info("empty message {} or invalid sender {}", e.payload, e.from);
            return;
        }
        // strip off prefix and space
        String body = PolarisUtilities.stringFromUTF8Bytes(e.payload).substring(SFNOTIF_PREFIX.length() + 1);
        String[] parts = body.split(" ");
        if (parts.length != 3) {
            l.info("ssmp body not recognized: {}", body);
            return;
        }
        try {
            SID store = SID.fromStringFormal(parts[0]);
            UserID user = UserID.fromExternal(parts[1]);
            String msg = parts[2];
            switch (msg) {
                // keep these messages in sync with the Enum defined in ThreadLocalSFNotifications.java
                case SFNOTIF_JOIN:
                    listener.userJoinedStore(user, store);
                    break;
                case SFNOTIF_LEAVE:
                    // notify that access changed first, since the auto-leave operation takes longer
                    access.accessChanged(user, store);
                    listener.userLeftStore(user, store);
                    break;
                case SFNOTIF_CHANGE:
                    access.accessChanged(user, store);
                    break;
                default:
                    l.error("unrecognized message type {}", msg);
            }
        } catch (ExInvalidID ex) {
            l.warn("invalid id sent {}", body);
        }
    }

    @Override
    public void start() throws Exception {
        l.info("starting the ssmp listener");
        wrapper.setup(SSMPIdentifier.fromInternal("polaris"));
        wrapper.getConn().addUcastHandler("sf", this);
    }

    @Override
    public void stop() {
        wrapper.teardown();
    }
}
