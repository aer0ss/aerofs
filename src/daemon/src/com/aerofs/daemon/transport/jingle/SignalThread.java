package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.exception.ExSendFailed;
import com.aerofs.daemon.transport.exception.ExTransport;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.j.Jid;
import com.aerofs.j.Message;
import com.aerofs.j.MessageHandlerBase;
import com.aerofs.j.SWIGTYPE_p_cricket__Session;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.TunnelSessionClient;
import com.aerofs.j.TunnelSessionClient_IncomingTunnelSlot;
import com.aerofs.j.XmppClient_StateChangeSlot;
import com.aerofs.j.XmppEngine;
import com.aerofs.j.XmppMain;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.base.Preconditions.checkState;

/**
 * Code convention for the jingle package:
 *
 * 1. a class must implement IProxyObjectContainer if it contains SWIG proxy
 * objects. see the interface's source code for more comments
 *
 * 2. a class that contains IProxyObjectContainer objects but doesn't implement
 * the interface itself must call the containing objects' delete() whenever
 * they are no longer used.
 */
class SignalThread extends Thread
{
    static interface ISignalThreadListener
    {
        void onIncomingTunnel(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session session);

        void closeAllAcceptedChannels();
    }

    private static final Logger l = Loggers.getLogger(SignalThread.class);

    private volatile XmppMain _main;
    private volatile TunnelSessionClient _tsc;
    private TunnelSessionClient_IncomingTunnelSlot _incomingTunnelSlot;
    private final byte[] _logPathUTF8;
    private final Jid _xmppUsername;
    private final String _xmppPassword;
    private final XMPPConnectionService _xmppServer;
    private final InetSocketAddress _stunServerAddress;
    private final InetSocketAddress _xmppServerAddress;
    private final String _absRTRoot;
    private final ArrayList<Runnable> _postRunners = Lists.newArrayList(); // st thread only
    private final Object _mxMain = new Object(); // lock object
    private final BlockingQueue<ISignalThreadTask> _tasks = new LinkedBlockingQueue<ISignalThreadTask>(DaemonParam.QUEUE_LENGTH_DEFAULT);
    private ISignalThreadListener _listener;
    private volatile boolean _connectedToXMPP;

    public SignalThread(Jid localjid, InetSocketAddress stunServerAddress, InetSocketAddress xmppServerAddress, XMPPConnectionService xmppServer, String absRtRoot)
    {
        super(TransportThreadGroup.get(), "j-st");
        _stunServerAddress = stunServerAddress;
        _xmppServerAddress = xmppServerAddress;
        _absRTRoot = absRtRoot;
        _logPathUTF8 = getLogPath();
        _xmppServer = xmppServer;
        _xmppUsername = localjid;
        _xmppPassword = xmppServer.getXmppPassword();
    }

    void setListener(ISignalThreadListener listener)
    {
        // Note: for now, we only allow one listener to the signal thread. This is because it would
        // not make much sense to have several Netty server channels getting notified when a client
        // is accepted. However, this highlights that the coupling between the signal thread and
        // the server channel isn't very well defined. We should revisit this later.
        checkState(_listener == null);
        _listener = listener;
    }

    private byte[] getLogPath()
    {
        String ljlogpath = Util.join(_absRTRoot, "lj.log");
        try {
            return ljlogpath.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw SystemUtil.fatalWithReturn("cannot convert path:" + ljlogpath + " to UTF-8");
        }
    }

    public boolean isReady()
    {
        return _connectedToXMPP;
    }

    StreamInterface createTunnel(Jid to, String description)
    {
        l.info("create tunnel to j:{} ({})", to, description);

        return _tsc.CreateTunnel(to, description);
    }

    void checkThread()
    {
        checkState(Thread.currentThread() == this);
    }

    /**
     * run the object within the signal thread. wait until the handler
     * finishes before returning. The _main pointer is guaranteed valid within
     * runnable.run().
     *
     * N.B. runnable.run() must not block
     */
    void call(ISignalThreadTask task)
    {
        if (Thread.currentThread() == this) {
            _tasks.add(task); // throws IllegalStateException if the queue is full
        } else {
            try {
                _tasks.put(task);
            } catch (InterruptedException e) {
                task.error(e);
            }
        }

        processTasksQueue();
    }

    /**
     * The _main pointer is guaranteed valid within runnable.run().
     */
    private void post(final Runnable runnable)
    {
        checkThread();
        checkState(_main != null);

        _postRunners.add(runnable);

        // post the handler if it's not been scheduled
        if (_postRunners.size() == 1) {
            _main.signal_thread().Post(_postHandler);
        }
    }

    /**
     * Delete the object (and its underlying C++ objects) in a separate
     * message handler. This is needed when the object to be deleted may be
     * referenced within the original message handling method.
     */
    void delayedDelete(final JingleStream stream)
    {
        checkThread();

        post(new Runnable()
        {
            @Override
            public void run()
            {
                stream.delete();
            }
        });
    }

    @Override
    public void run()
    {
        // noinspection InfiniteLoopStatement
        while (true) {
            runImpl();
            ThreadUtil.sleepUninterruptable(LibParam.EXP_RETRY_MIN_DEFAULT);
        }
    }

    private void runImpl()
    {
        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        // TODO (WW) XmppMain() should use int rather than short as the datatype of jingleRelayPort
        // as Java's unsigned short may overflow on big port numbers.
        _main = new XmppMain(
                _xmppServerAddress.getHostName(), _xmppServerAddress.getPort(),
                _stunServerAddress.getHostName(), _stunServerAddress.getPort(),
                true,
                _xmppUsername,
                _xmppPassword,
                _logPathUTF8);

        _slotStateChange.connect(_main.xmpp_client());

        l.debug("start main");

        // >>>> WHEE...RUNNING >>>>

        // boolean lololon = Cfg.lotsOfLotsOfLog(_absRTRoot); FIXME (AG): inject this somehow
        _main.Run(true);

        l.debug("end main - start cleanup");

        // >>>> WHEE...OHHHH....DONE :( >>>>

        synchronized (_mxMain) {
            drainTasksQueue(new ExSendFailed("transport stopped [main ended]"));
            _main.delete();
            _main = null;
        }

        l.debug("end cleanup");
    }

    /**
     * N.B. pay special attention on how runImpl() synchronizes with this class
     */
    private void processTasksQueue()
    {
        // Synchronizing on mxMain is necessary because this may be called from any thread
        synchronized (_mxMain) {
            if (_main == null) {
                drainTasksQueue(new ExTransport("transport stopped [null main]"));
                return;
            }

            _main.signal_thread().Post(_callHandler);
        }
    }

    private void drainTasksQueue(Exception reason)
    {
        ISignalThreadTask task;
        while ((task = _tasks.poll()) != null) {
            task.error(reason);
        }
    }

    // We re-use the same handler for all calls because 1) creation and deletion of C++ objects is
    // inefficient, and more importantly, 2) there's no good opportunity to delete the finished
    // handlers safely. It's not safe to delete from within OnMessage as the object may be
    // dereferenced after OnMessage returns (e.g. SWIG references director objects after the
    // callback returns). It's not safe to delete from a non-signal thread, either, because other
    // threads don't know when the object is no longer needed by the signal thread. One solution is
    // to delete the handler using yet another handler executed by the signal thread, but it's too much.
    private final MessageHandlerBase _callHandler = new MessageHandlerBase()
    {
        @Override
        public void OnMessage(Message msg)
        {
            ISignalThreadTask task = null;

            // onMessage callbacks are run by libjingle's event thread. It expects
            // _that tasks do not throw_. Throwing an exception here would result in undefined
            // behavior since this would be handled at the JNI level.
            //
            // So if you're adding new code, **make sure it is within this try-catch block.**
            try {
                checkThread();

                task = _tasks.poll();
                if (task == null) return;

                // We don't have to hold _mxMain because the OnMessage callback is being run while
                // _main is alive. If _main is alive the only person who can modify it (runImpl)
                // is still in the _main.run() call and won't be modifying the value of _main.
                try {
                    task.run();
                } catch (Exception e) {
                    l.error("t: {} run fin with unhandled err: {}", task, Util.e(e));
                    task.error(e);
                }

            } catch (Throwable t) {
                // note: task could be null here. Make sure to test for it if you need it.
                l.error("jingle task crash and burn task:{}", task, t);
                ExitCode.JINGLE_TASK_FATAL_ERROR.exit();
            }
        }
    };

    private final MessageHandlerBase _postHandler = new MessageHandlerBase()
    {
        @Override
        public void OnMessage(Message msg)
        {
            // See comment in CallHandler.OnMessage(). Basically, don't throw anything here.
            try {
                for (Runnable r : _postRunners) r.run();
                _postRunners.clear();
            } catch (Throwable t) {
                l.error("caught throwable while handling post task", t);
                ExitCode.JINGLE_TASK_FATAL_ERROR.exit();
            }
        }
    };

    private final XmppClient_StateChangeSlot _slotStateChange = new XmppClient_StateChangeSlot()
    {
        @Override
        public void onStateChange(XmppEngine.State state)
        {
            try {
                l.info("engine state:{}", state);

                if (state == XmppEngine.State.STATE_OPEN) {
                    handleXMPPEngineOpen();
                } else if (state == XmppEngine.State.STATE_CLOSED) {
                    handleXMPPEngineClosed();
                }
            } catch (Throwable t) {
                l.error("caught throwable while handling XMPP engine state change", t);
                ExitCode.JINGLE_TASK_FATAL_ERROR.exit();
            }
        }

        private void handleXMPPEngineClosed()
        {
            _connectedToXMPP = false;

            int[] subcode = {0};
            XmppEngine.Error error = _main.xmpp_client().engine().GetError(subcode);

            l.warn("engine state changed to closed. err:{} subcode:{}", error, subcode[0]);

            _xmppServer.linkStateChanged(false);

            if (_tsc != null) {
                _tsc.delete();
                _tsc = null;
            }

            _listener.closeAllAcceptedChannels();

            if (_incomingTunnelSlot != null) {
                _incomingTunnelSlot.delete();
                _incomingTunnelSlot = null;
            }

            _main.ShutdownSignalThread();
            // Previously we had code that tried to create a new account
            // if login failed because of a "not authorized" message. This
            // call would race against the Multicast thread to create an
            // account on the XMPP server. The winner would succeed, but
            // the loser would get a conflict(409). This led to a lot of
            // thrashing. The better solution is to simply bail out here,
            // and let the Multicast thread alone attempt to create the
            // account. This thread will simply reattempt to log in. At
            // some point at time the Multicast thread will succeed, at
            // which time we will be able to log in here as well.
        }

        private void handleXMPPEngineOpen()
        {
            l.debug("create engine");

            checkState(_tsc == null);
            checkState(_incomingTunnelSlot == null);

            _main.StartHandlingSessions();

            Preconditions.checkState(_tsc == null);
            _tsc = new TunnelSessionClient(_main.xmpp_client().jid(), _main.session_manager());

            _incomingTunnelSlot = new TunnelSessionClient_IncomingTunnelSlot()
            {
                @Override
                public void onIncomingTunnel(TunnelSessionClient client, Jid jid, String desc, SWIGTYPE_p_cricket__Session sess)
                {
                    try {
                        l.info("handle incoming tunnel j:{}", jid);

                        if (_listener != null) {
                            _listener.onIncomingTunnel(client, jid, sess);
                        } else {
                            l.warn("no listener for incoming tunnel j:{}", jid);
                        }
                    } catch (Throwable t) {
                        l.error("caught throwable while handling incoming tunnel j:{}", jid.Str(), t);
                        ExitCode.JINGLE_TASK_FATAL_ERROR.exit();
                    }
                }
            };

            _incomingTunnelSlot.connect(_tsc);

            // Now that Jingle is initialized and ready, we start the connection to the xmpp
            // server. If we started it earlier the core would get presence information and try
            // to connect to peers before jingle is ready.
            _xmppServer.linkStateChanged(true);

            _connectedToXMPP = true;
        }
    };
}
