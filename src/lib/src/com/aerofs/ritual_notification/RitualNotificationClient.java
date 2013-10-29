package com.aerofs.ritual_notification;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import static com.aerofs.lib.LibParam.RitualNotification.NOTIFICATION_SERVER_CONNECTION_RETRY_INTERVAL;

public class RitualNotificationClient
{
    private final static Logger l = Loggers.getLogger(RitualNotificationClient.class);

    private boolean _started; // for debugging only

    private volatile boolean _paused;
    private volatile boolean _stopping;

    // access protected by synchronized (_ls)
    private final ConcurrentlyModifiableListeners<IRitualNotificationListener> _ls = ConcurrentlyModifiableListeners.create();

    private final RitualNotificationSystemConfiguration _config;

    public RitualNotificationClient(RitualNotificationSystemConfiguration config)
    {
        _config = config;
    }

    /**
     * @pre this method hasn't been called before.
     */
    public void start()
    {
        Preconditions.checkState(!_started);
        Preconditions.checkState(!_stopping);
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

                    // ugh

                    synchronized (_ls) {
                        try {
                            for (IRitualNotificationListener l : _ls.beginIterating_()) {
                                l.onNotificationChannelBroken();
                            }
                        } finally {
                            _ls.endIterating_();
                        }
                    }

                    if (_stopping) break;

                    l.info("reconnect in " + NOTIFICATION_SERVER_CONNECTION_RETRY_INTERVAL + " ms");

                    do {
                        ThreadUtil.sleepUninterruptable(NOTIFICATION_SERVER_CONNECTION_RETRY_INTERVAL);
                    } while (_paused);
                }
            }
        });
    }

    public void stop()
    {
        if (!_started) l.warn("closing rnc before starting");
        _stopping = true;
    }

    public void pause()
    {
        _paused = true;
    }

    public void resume()
    {
        _paused = false;
    }

    private void thdRecv_() throws IOException
    {
        Socket s = new Socket(_config.getAddress(), _config.getPort());
        try {
            DataInputStream is = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            while (true) {
                byte[] bs = readMessage(is, LibParam.RITUAL_NOTIFICATION_MAGIC, Integer.MAX_VALUE);

                if (_stopping) return;

                synchronized (_ls) {
                    try {
                        for (IRitualNotificationListener l : _ls.beginIterating_()) {
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
    public void addListener(IRitualNotificationListener l)
    {
        synchronized (_ls) { _ls.addListener_(l); }
    }

    public void removeListener(IRitualNotificationListener l)
    {
        synchronized (_ls) { _ls.removeListener_(l); }
    }

    private static byte[] readMessage(DataInputStream is, int magic, int maxSize)
            throws IOException
    {
        int m = is.readInt();
        if (m != magic) {
            throw new IOException("Magic number doesn't match. Expect 0x" +
                    String.format("%1$08x", magic) + " received 0x" +
                    String.format("%1$08x", m));
        }
        int size = is.readInt();

        if (size > maxSize) {
            throw new IOException("Message too large (" + size + " > " + maxSize + ")");
        }
        byte[] bs = new byte[size];
        is.readFully(bs);
        return bs;
    }
}
