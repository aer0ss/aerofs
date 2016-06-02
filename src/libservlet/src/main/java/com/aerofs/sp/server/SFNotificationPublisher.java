package com.aerofs.sp.server;

import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.ThreadLocalSFNotifications;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPIdentifier;
import com.aerofs.ssmp.SSMPRequest;

import javax.inject.Inject;

import java.util.Collection;

import static com.aerofs.sp.server.LipwigUtil.lipwigFutureGet;

public class SFNotificationPublisher {
    private final SQLThreadLocalTransaction _sqlTrans;
    private final SSMPConnection _ssmp;
    private final SSMPIdentifier polaris = SSMPIdentifier.fromInternal("polaris");

    @Inject
    public SFNotificationPublisher(SSMPConnection ssmp, SQLThreadLocalTransaction sqlTrans) {
        _ssmp = ssmp;
        _sqlTrans = sqlTrans;
    }

    public void sendNotification(ThreadLocalSFNotifications.SFNotification notif) {
        Runnable send = () -> send_(notif.sid, notif.uid, notif.msg);

        // schedule send on commit if we're in a transaction, otherwise send now
        if (_sqlTrans.isInTransaction()) {
            _sqlTrans.onCommit(send);
        } else {
            // if we're not in a transaction, run it now
            send.run();
        }
    }

    public void sendNotifications(Collection<ThreadLocalSFNotifications.SFNotification> notifs) {
        notifs.forEach(n -> sendNotification(n));
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
