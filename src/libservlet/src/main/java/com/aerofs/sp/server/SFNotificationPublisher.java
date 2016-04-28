package com.aerofs.sp.server;

import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.ThreadLocalSFNotifications;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPIdentifier;
import com.aerofs.ssmp.SSMPRequest;

import javax.inject.Inject;
import java.util.Collection;

import static com.aerofs.sp.server.LipwigUtil.lipwigFutureGet;

public class SFNotificationPublisher {
    SSMPConnection _ssmp;
    SSMPIdentifier polaris = SSMPIdentifier.fromInternal("polaris");

    @Inject
    public SFNotificationPublisher(SSMPConnection ssmp) {
        _ssmp = ssmp;
    }

    public void sendNotification(ThreadLocalSFNotifications.SFNotification notif) {
        send_(notif.sid, notif.uid, notif.msg);
    }

    public void sendNotifications(Collection<ThreadLocalSFNotifications.SFNotification> notifs) {
        for (ThreadLocalSFNotifications.SFNotification notif : notifs) {
            sendNotification(notif);
        }
    }

    private void send_(SID sid, UserID uid, ThreadLocalSFNotifications.SFNotificationMessage action) {
        String msg = String.format("sf %s %s %s", sid.toStringFormal(), uid.getString(), action.toString());
        try {
            lipwigFutureGet(_ssmp.request(SSMPRequest.ucast(polaris, msg)));
        } catch (Exception e) {
            throw new RuntimeException("could not send lipwig message", e);
        }
    }
}
