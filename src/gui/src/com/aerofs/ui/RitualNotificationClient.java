package com.aerofs.ui;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import com.aerofs.lib.ThreadUtil;
import org.apache.log4j.Logger;

import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;
import com.aerofs.proto.RitualNotifications.PBNotification;

public class RitualNotificationClient
{
    private final static Logger l = Util.l(RitualNotificationClient.class);

    /**
     * N.B. methods of this interface are called in an independent notification thread
     */
    public static interface IListener
    {
        void onNotificationReceived(PBNotification pb);
    }

    private boolean _started; // for debugging only

    private volatile boolean _stopping;

    // access protected by synchronized (_ls)
    private final ConcurrentlyModifiableListeners<IListener> _ls = ConcurrentlyModifiableListeners.create();

    /**
     * @pre this method hasn't been called before.
     */
    public void start()
    {
        assert !_started;
        assert !_stopping;
        _started = true;

        ThreadUtil.startDaemonThread("rnc", new Runnable()
        {
            @Override
            public void run()
            {
                while (true) {
                    try {
                        thdRecv_();
                    } catch (IOException e) {
                        l.warn(Util.e(e, IOException.class));
                    }

                    if (_stopping) break;

                    l.info("reconnect in " + UIParam.DAEMON_CONNECTION_RETRY_INTERVAL + " ms");
                    ThreadUtil.sleepUninterruptable(UIParam.DAEMON_CONNECTION_RETRY_INTERVAL);
                }
            }
        });
    }

    public void stop()
    {
        _stopping = true;
    }

    private void thdRecv_() throws IOException
    {
        Socket s = new Socket(C.LOCALHOST_ADDR, Cfg.port(PortType.RITUAL_NOTIFICATION));
        try {
            DataInputStream is = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            while (true) {
                byte[] bs = Util.readMessage(is, C.RITUAL_NOTIFICATION_MAGIC, Integer.MAX_VALUE);

                if (_stopping) return;

                synchronized (_ls) {
                    try {
                        for (IListener l : _ls.beginIterating_()) {
                            l.onNotificationReceived(PBNotification.parseFrom(bs));
                        }
                    } finally {
                        _ls.endIterating_();
                    }
                }
            }
        } finally {
            s.close();
        }
    }

    /**
     * Call this method _before_ starting the daemon or _before_ calling start() to avoid missing
     * events.
     */
    public void addListener(IListener l)
    {
        synchronized (_ls) { _ls.addListener_(l); }
    }

    public void removeListener(IListener l)
    {
        synchronized (_ls) { _ls.removeListener_(l); }
    }
}
