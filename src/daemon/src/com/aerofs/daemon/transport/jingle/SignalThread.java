package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.lib.IUnicastListener;
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
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkState;

/**
 * Event loop for the Jingle subsystem.
 */
class SignalThread extends Thread
{
    /**
     * Implemented by classes that want to be notified
     * when remote devices want to connect to the local device.
     */
    static interface IIncomingTunnelListener
    {
        /**
         * Called by the {@code SignalThread} when a remote
         * device wants to connect to the local device.
         *
         * @param client {@code TunnelSessionClient} that can be used to create a Jingle tunnel to the remote device
         * @param jid Jabber ID of the remote device
         * @param session C++ Session object associated with this potential connection
         *        (<strong>do not</strong> call any methods on this object)
         */
        void onIncomingTunnel(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session session);
    }

    /**
     * Implemented by classes that want to be notified
     * when of {@code SignalThread} lifecycle events.
     *
     * @see com.aerofs.daemon.transport.jingle.SignalThread
     */
    static interface ISignalThreadListener
    {
        /**
         * Called when the {@code SignalThread} is ready to process incoming and outgoing events.
         */
        void onSignalThreadReady();

        /**
         * Called when the {@code SignalThread} is about to shut down.
         */
        void onSignalThreadClosing();
    }

    private static final Logger l = Loggers.getLogger(SignalThread.class);

    private volatile XmppMain main;
    private volatile TunnelSessionClient tunnelSessionClient;
    private TunnelSessionClient_IncomingTunnelSlot incomingTunnelSlot;
    private final byte[] logPathUTF8;
    private final Jid xmppUsername;
    private final String xmppPassword;
    private final InetSocketAddress stunServerAddress;
    private final InetSocketAddress xmppServerAddress;
    private final String libjingleWorkingDirectory;
    private final boolean enableJingleLibraryLogging;
    private final ArrayList<Runnable> postHandlerTasks = Lists.newArrayList(); // st thread only // FIXME (AG): remove these
    private final Object mxMain = new Object(); // lock object
    private final BlockingQueue<ISignalThreadTask> signalThreadTasks = new LinkedBlockingQueue<ISignalThreadTask>(DaemonParam.QUEUE_LENGTH_DEFAULT);
    private final Set<ISignalThreadListener> signalThreadListeners = Sets.newHashSet();
    private final Object mxListeners = new Object(); // protects signalThreadListeners;
    private final Semaphore stoppedSemaphore = new Semaphore(0);

    private volatile boolean running;
    private IIncomingTunnelListener incomingTunnelListener;
    private IUnicastListener unicastListener;

    public SignalThread(String transportId, Jid xmppUsername, String xmppPassword, InetSocketAddress stunServerAddress, InetSocketAddress xmppServerAddress, String libjingleWorkingDirectory, boolean enableJingleLibraryLogging)
    {
        super(TransportThreadGroup.get(), transportId + "-st"); // FIXME (AG): remove direct call to TransportThreadGroup.get()
        setDaemon(true);

        this.stunServerAddress = stunServerAddress;
        this.xmppServerAddress = xmppServerAddress;
        this.libjingleWorkingDirectory = libjingleWorkingDirectory;
        this.logPathUTF8 = getLogPath();
        this.xmppUsername = xmppUsername;
        this.xmppPassword = xmppPassword;
        this.enableJingleLibraryLogging = enableJingleLibraryLogging;
    }

    void setIncomingTunnelListener(IIncomingTunnelListener listener)
    {
        // Note: for now, we only allow one listener to the signal thread. This is because it would
        // not make much sense to have several Netty server channels getting notified when a client
        // is accepted. However, this highlights that the coupling between the signal thread and
        // the server channel isn't very well defined. We should revisit this later.
        checkState(incomingTunnelListener == null);
        incomingTunnelListener = listener;
    }

    void setUnicastListener(IUnicastListener listener)
    {
        checkState(unicastListener == null);
        unicastListener = listener;
    }

    void addSignalThreadListener(ISignalThreadListener listener)
    {
        synchronized (mxListeners) {
            signalThreadListeners.add(listener);
        }
    }

    void removeSignalThreadListener(ISignalThreadListener listener)
    {
        synchronized (mxListeners) {
            signalThreadListeners.remove(listener);
        }
    }

    private byte[] getLogPath()
    {
        String ljlogpath = Util.join(libjingleWorkingDirectory, "lj.log");
        try {
            return ljlogpath.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw SystemUtil.fatalWithReturn("cannot convert path:" + ljlogpath + " to UTF-8");
        }
    }

    public void shutdown()
    {
        running = false;

        call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                main.Stop();
            }

            @Override
            public void error(Exception e)
            {
                l.warn("cannot shutdown signal thread");
            }
        });

        try {
            stoppedSemaphore.acquire();
        } catch (InterruptedException e) {
            l.warn("interrupted during shutdown wait");
        }
    }

    StreamInterface createTunnel(Jid to, String description)
            throws ExTransportUnavailable
    {
        if (tunnelSessionClient == null) {
            throw new ExTransportUnavailable("signal thread stopped [null tsc]");
        }

        l.info("create tunnel to j:{} ({})", to, description);

        return tunnelSessionClient.CreateTunnel(to, description);
    }

    void assertSignalThread()
    {
        checkState(Thread.currentThread() == this, "signal thread method called on %s thread", Thread.currentThread().getName());
    }

    /**
     * run the object within the signal thread. wait until the handler
     * finishes before returning. The main pointer is guaranteed valid within
     * runnable.run().
     *
     * N.B. runnable.run() must not block
     */
    void call(ISignalThreadTask task)
    {
        if (Thread.currentThread() == this) {
            signalThreadTasks.add(task); // throws IllegalStateException if the queue is full
        } else {
            try {
                signalThreadTasks.put(task);
            } catch (InterruptedException e) {
                task.error(e);
            }
        }

        processTasksQueue();
    }

    /**
     * The main pointer is guaranteed valid within runnable.run().
     */
    private void post(final Runnable runnable)
    {
        assertSignalThread();
        checkState(main != null);

        postHandlerTasks.add(runnable);

        // post the handler if it's not been scheduled
        if (postHandlerTasks.size() == 1) {
            main.signal_thread().Post(postHandler);
        }
    }

    /**
     * Delete the object (and its underlying C++ objects) in a separate
     * message handler. This is needed when the object to be deleted may be
     * referenced within the original message handling method.
     */
    void delayedDelete(final JingleStream stream)
    {
        assertSignalThread();

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
        running = true;

        while (running) {
            runImpl();
            ThreadUtil.sleepUninterruptable(LibParam.EXP_RETRY_MIN_DEFAULT);
        }

        stoppedSemaphore.release();
    }

    private void runImpl()
    {
        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        // TODO (WW) XmppMain() should use int rather than short as the datatype of jingleRelayPort
        // as Java's unsigned short may overflow on big port numbers.

        main = new XmppMain(
                xmppServerAddress.getHostName(), xmppServerAddress.getPort(),
                stunServerAddress.getHostName(), stunServerAddress.getPort(),
                true,
                xmppUsername,
                xmppPassword,
                logPathUTF8);

        slotStateChange.connect(main.xmpp_client());

        l.debug("start main");

        // >>>> WHEE...RUNNING >>>>

        main.Run(enableJingleLibraryLogging);

        l.debug("end main - start cleanup");

        // >>>> WHEE...OHHHH....DONE :( >>>>

        synchronized (mxMain) {
            drainTasksQueue(new ExTransportUnavailable("signal thread stopped [main ended]"));
            main.delete();
            main = null;
        }

        l.debug("end cleanup");
    }

    /**
     * N.B. pay special attention on how runImpl() synchronizes with this class
     */
    private void processTasksQueue()
    {
        // Synchronizing on mxMain is necessary because this may be called from any thread
        synchronized (mxMain) {
            if (main == null) {
                drainTasksQueue(new ExTransportUnavailable("signal thread stopped [null main]"));
                return;
            }

            main.signal_thread().Post(callHandler);
        }
    }

    private void drainTasksQueue(Exception reason)
    {
        ISignalThreadTask task;
        while ((task = signalThreadTasks.poll()) != null) {
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
    private final MessageHandlerBase callHandler = new MessageHandlerBase()
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
                assertSignalThread();

                task = signalThreadTasks.poll();
                if (task == null) return;

                // We don't have to hold mxMain because the OnMessage callback is being run while
                // main is alive. If main is alive the only person who can modify it (runImpl)
                // is still in the main.run() call and won't be modifying the value of main.
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

    private final MessageHandlerBase postHandler = new MessageHandlerBase()
    {
        @Override
        public void OnMessage(Message msg)
        {
            // See comment in CallHandler.OnMessage(). Basically, don't throw anything here.
            try {
                for (Runnable r : postHandlerTasks) r.run();
                postHandlerTasks.clear();
            } catch (Throwable t) {
                l.error("caught throwable while handling post task", t);
                ExitCode.JINGLE_TASK_FATAL_ERROR.exit();
            }
        }
    };

    private final XmppClient_StateChangeSlot slotStateChange = new XmppClient_StateChangeSlot()
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
            int[] subcode = {0};
            XmppEngine.Error error = main.xmpp_client().engine().GetError(subcode);

            l.warn("engine state changed to closed. err:{} subcode:{}", error, subcode[0]);

            unicastListener.onUnicastUnavailable();

            // notify all the listeners that the signal thread is closing
            Collection<ISignalThreadListener> listenersToNotify = getSnapshotOfSignalThreadListenersList();
            for (ISignalThreadListener listener : listenersToNotify) {
                listener.onSignalThreadClosing();
            }

            if (tunnelSessionClient != null) {
                tunnelSessionClient.delete();
                tunnelSessionClient = null;
            }

            if (incomingTunnelSlot != null) {
                incomingTunnelSlot.delete();
                incomingTunnelSlot = null;
            }

            main.ShutdownSignalThread();
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

            checkState(tunnelSessionClient == null);
            checkState(incomingTunnelSlot == null);

            main.StartHandlingSessions();

            Preconditions.checkState(tunnelSessionClient == null);
            tunnelSessionClient = new TunnelSessionClient(main.xmpp_client().jid(), main.session_manager());

            incomingTunnelSlot = new TunnelSessionClient_IncomingTunnelSlot()
            {
                @Override
                public void onIncomingTunnel(TunnelSessionClient client, Jid jid, String desc, SWIGTYPE_p_cricket__Session sess)
                {
                    try {
                        l.info("handle incoming tunnel j:{}", jid);

                        if (incomingTunnelListener != null) {
                            incomingTunnelListener.onIncomingTunnel(client, jid, sess);
                        } else {
                            l.warn("no listener for incoming tunnel j:{}", jid);
                        }
                    } catch (Throwable t) {
                        l.error("caught throwable while handling incoming tunnel j:{}", jid.Str(), t);
                        ExitCode.JINGLE_TASK_FATAL_ERROR.exit();
                    }
                }
            };

            incomingTunnelSlot.connect(tunnelSessionClient);

            // notify all the listeners that the signal thread is now ready
            Collection<ISignalThreadListener> listenersToNotify = getSnapshotOfSignalThreadListenersList();
            for (ISignalThreadListener listener : listenersToNotify) {
                listener.onSignalThreadReady();
            }

            // notify the unicast listener that we're good to go
            unicastListener.onUnicastReady();
        }
    };

    private Collection<ISignalThreadListener> getSnapshotOfSignalThreadListenersList()
    {
        Set<ISignalThreadListener> listenersToNotify;

        synchronized (mxListeners) {
            listenersToNotify = Sets.newHashSet(signalThreadListeners);
        }

        return listenersToNotify;
    }
}
