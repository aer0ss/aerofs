package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.BaseParam.Xmpp;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.daemon.transport.xmpp.XMPPServerConnection;
import com.aerofs.j.Jid;
import com.aerofs.j.Message;
import com.aerofs.j.MessageHandlerBase;
import com.aerofs.j.Status;
import com.aerofs.j.XmppClient_StateChangeSlot;
import com.aerofs.j.XmppEngine;
import com.aerofs.j.XmppMain;
import com.aerofs.j.XmppSocket_CloseEventSlot;
import com.aerofs.j.XmppSocket_ErrorSlot;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExJingle;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;

// it doesn't implements IProxyObjectContainer becasue we don't properly handle
// deletion in this class (yet)
//
public class SignalThread extends java.lang.Thread implements IDumpStatMisc
{
    static
    {
        String ljlogpath = Util.join(Cfg.absRTRoot(), "lj.log");
        byte[] temputf8 = null;
        try {
            temputf8 = ljlogpath.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            SystemUtil.fatal("cannot convert path:" + ljlogpath + " to UTF-8");
        }

        ljlogpathutf8 = temputf8; // this ridiculous indirection is done to keep the compiler happy
    }

    SignalThread(IJingle ij)
    {
        this.ij = ij;
    }

    /**
     * N.B. unsafe to call out of the signal thread cuz 1) _eng may be changed
     * by the signal thread. 2) accessing the engine's methods out of the signal
     * thread is not safe.
     */
    Engine getEngine_()
    {
        return _engineReference.get_();
    }

    boolean ready()
    {
        return _engineReference.get_() != null;
    }

    void linkStateChanged(boolean up)
    {
        synchronized (_cvLinkState) {
            _linkUp = up;
            _cvLinkState.notifyAll();
        }
    }

    void assertThread()
    {
        assert this == java.lang.Thread.currentThread();
    }

    /**
     * run the object within the signal thread. wait until the handler
     * finishes before returning. The _main pointer is guaranteed valid within
     * runnable.run().
     *
     * N.B. runnable.run() must not block
     * @param task
     */
    void call(ISignalThreadTask task)
    {
        if (java.lang.Thread.currentThread() == this) {
            l.debug("st: run task inline t:" + task);
            task.run();
        } else {
            // we use a global handler for all calls because
            // 1) creation and deletion of C++ objects are inefficient, and more
            // importantly, 2) there's no good opportunity to delete the finished
            // handlers safely. It's not safe to delete from within OnMessage
            // as the object may be dereferenced after OnMessage returns (e.g.
            // SWIG references director objects after the callback returns). It's
            // not safe to delete from a non-signal thread, either, because other
            // threads don't know when the object is no longer needed by the signal
            // thread. One solution is to delete the handler using yet another
            // handler executed by the signal thread, but it's too much.
            //
            // call() will synchronize appropriately
            // internally to ensure that only one task will be run at a time
            l.debug("st: queue task for callhandler t:" + task);
            _callHandler.call(task);
        }
    }

    /**
     * The _main pointer is guaranteed valid within runnable.run().
     */
    private void post_(final Runnable runnable)
    {
        assertThread();
        assert _main != null;

        _postRunners.add(runnable);

        // post the handler if it's not been scheduled
        if (_postRunners.size() == 1) {
            _main.getSignalThread().Post(_postHandler);
        }
    }

    /**
     * call delete_() in a separate message handler. this is needed sometimes
     * when the object to be deleted may be referred to by the rest of the
     * message handling.
     */
    void delayedDelete_(final IProxyObjectContainer poc)
    {
        assertThread();

        post_(new Runnable() {
            @Override
            public void run()
            {
                poc.delete_();
            }
        });
    }

    @Override
    public void run()
    {
        while (true) {
            runImpl_();
            ThreadUtil.sleepUninterruptable(Param.EXP_RETRY_MIN_DEFAULT);
        }
    }

    private void runImpl_()
    {
        l.debug("st: check ls");

        synchronized (_cvLinkState) {
            while (!_linkUp) ThreadUtil.waitUninterruptable(_cvLinkState);
        }

        l.debug("st: links up");

        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        InetSocketAddress address = Xmpp.xmppAddress();
        // TODO (WW) XmppMain() should use int rather than short as the datatype of jingleRelayPort
        // as Java's unsigned short may overflow on big port numbers.
        _main = new XmppMain(address.getHostName(), address.getPort(),
                true, _jidSelf, XMPPServerConnection.shaedXMPP(),
                DaemonParam.Jingle.RELAY_HOST, (short) DaemonParam.Jingle.RELAY_PORT,
                ljlogpathutf8);

        l.debug("st: created xmppmain");

        _slotStateChange.connect(_main.getXmppClient());

        _slotSocketCloseEvent.connect(_main.getSocket());

        _slotSocketError.connect(_main.getSocket());

        l.debug("st: connected slots - starting main");

        // >>>> WHEE...RUNNING >>>>

        boolean lolon = Cfg.lotsOfLog(Cfg.absRTRoot());
        _main.run(lolon, lolon);

        l.debug("st: main completed");

        // >>>> WHEE...OHHHH....DONE :( >>>>

        if (_engineReference.get_() != null) {
            _engineReference.get_().delete_();
            _engineReference.set_(null);
        }

        synchronized (_mxMain) {
            _main.delete();
            _main = null;
            _callHandler.wake(); // also holds _mxMain internally
        }

        l.debug("st: cleanup completed, callhandler woken");
    }

    // this method must be called within the signal thread, and may be called
    // several times on each XmppMain object
    void close_(Exception e)
    {
        assertThread();

        l.debug("st: close: cause: " + e);

        if (_engineReference.get_() != null) {
            _engineReference.get_().close_(e);
        } else {
            l.warn("st: null engine");
        }

        _main.quit();
    }

    void close_(DID did, Exception e)
    {
        assertThread();

        l.debug("st: close: cause: " + e + " d:" + did);

        if (_engineReference.get_() != null) {
            _engineReference.get_().close_(did, e);
        }
    }

    Collection<DID> getConnections_()
    {
        assertThread();

        InOutArg<Collection<DID>> res = new InOutArg<Collection<DID>>(new ArrayList<DID>());
        if (_engineReference.get_() != null) {
            res.set(_engineReference.get_().getConnections_());
        }
        return res.get();
    }

    String diagnose_()
    {
        assertThread();

        final InOutArg<String> res = new InOutArg<String>("call not executed");

        if (_engineReference.get_() == null) {
            res.set("engine closed");
        } else {
            res.set(_engineReference.get_().diagnose_());
        }

        return res.get();
    }

    @Override
    public void dumpStatMisc(final String indent, final String indentUnit, final PrintStream ps)
    {
        ps.println(indent + "engine " + (_engineReference.get_() == null ? "closed" : "open"));
    }

    /**
     * N.B. pay special attention on how runImpl_() synchronizes with this class
     */
    private class CallHandler extends MessageHandlerBase
    {
        private void wake()
        {
            l.debug("st: wake callhandler");

            synchronized (_mxMain) {
                _wake = true;
                _mxMain.notifyAll();
            }
        }

        //
        // IMPORTANT: synchronizing on CallHandler itself ensures that only _1_ task will run
        // at a time. It essentially acts as an unordered task list (it's unordered because
        // mutexes don't guarantee FIFO order on who accesses the protected section)
        //
        // IMPORTANT: because of the way we're using call() it's possible to remove this
        // synchronized block. This is because call() is only called via Jingle, which is driven
        // only by the single-threaded XMPP event queue. This means that there is only ever one
        // task waiting at a time
        //
        synchronized void call(ISignalThreadTask task)
        {
            //
            // synchronizing on mxMain is necessary because we need to ensure that _mxMain is
            // valid while the task is running
            //
            synchronized (_mxMain) {
                _task = task;

                // because changing _main from a non-null reference to null is
                // always done within the lock, it's safe to cache it and check
                // for nullness in non-atomic operations
                //
                XmppMain main = _main;
                if (main == null) {
                    _task.error(new ExJingle("null main"));
                    return;
                }

                long postMessageId = _messageId++;
                main.getSignalThread().Post(this, postMessageId);
                l.debug("st: post to jingle m_id:" + postMessageId + " t:" + task);

                _wake = false;

                // we must wait here to avoid requests from queued up inside the
                // jingle engine. it's problematic otherwise, cause 1) jingle
                // queue is unbounded, and 2) jingle queue is oblivious to
                // our priority system
                //
                // wake up either the call finishes or main quits

                ThreadUtil.waitUninterruptable(_mxMain, DaemonParam.Jingle.CALL_TIMEOUT);

                if (!_wake) {
                    // I'm paranoid
                    l.fatal("call() too long. failed m_id:" + postMessageId + " t:" + task);
                    Util.logAllThreadStackTraces();
                    ExitCode.JINGLE_CALL_TOO_LONG.exit();
                }
            }
        }

        //
        // we don't have to hold _mxMain for the entire lifetime of OnMessage because the
        // OnMessage callback is being run while _main is alive. If _main is alive the only person
        // who can modify it (runImpl_) is still in the _main.run() call and won't be modifying
        // the value of _main
        //

        @Override
        public void OnMessage(Message msg)
        {
            //
            // IMPORTANT: I know that _main is valid and running because it's the one making the
            // OnMessage callback. That said, I sync to ensure the latest value of task
            //

            ISignalThreadTask task;
            synchronized (_mxMain) {
                task = _task;
            }

            assert task != null;

            //
            // IMPORTANT: READ THIS COMMENT CAREFULLY!
            //
            // the try...catch block below is _crucial_
            //
            // onMessage callbacks are run by libjingle's event-thread. It expects
            // _that tasks do not throw_. This is obviously untrue for the way in which we write
            // Java code. Previously a task that threw an exception would end up...well...I
            // don't know, but I'm pretty sure it did nothing good in the C++ side of the world.
            // Either way, on an exception two things happened:
            //
            // 1) libjingle _probably_ ended up in a weird state (or SWIG ignored the exception)
            // 2) More importantly, wake() was never called!
            //
            // 2) would lead to the infamous code 88 because the task caller would _never_ be
            // notified that the task had completed (erroneously or not). What made the situation
            // worse was because it's the SWIG/C library call into which the exception was
            // being thrown, the actual exception wasn't getting printed out! As a result,
            // it looked like libjingle was silently locking up, when (more likely) it was some
            // Java code tripping an exception, NPE or AE
            //
            // To work around this I do two things:
            //
            // 1) for all exceptions I simply run the ISignalTask object's error() method
            // 2) for all remaining throwables I assume the worst and simply terminate the process
            //

            try {
                l.debug("st: run beg after onmessage m_id:" + msg.getMessage_id() + " t:" + task);
                task.run();
                l.debug("st: run fin after onmessage m_id:" + msg.getMessage_id() + " t:" + task);
            } catch (Exception e) {
                l.error("st: t:" + task + " run fin with unhandled err: "+ Util.e(e));
                task.error(e);
            } catch (Throwable t) {
                l.fatal("jingle task crash and burn");
                l.fatal(Util.e(t));
                ExitCode.JINGLE_TASK_FATAL_ERROR.exit();
            }

            wake();
        }

        private long _messageId = 0; // protected by _mxMain
        private ISignalThreadTask _task; // protected by _mxMain
        private boolean _wake; // protected by _mxMain
    }

    MessageHandlerBase _postHandler = new MessageHandlerBase() {
        @Override
        public void OnMessage(Message msg)
        {
            for (Runnable r : _postRunners) r.run();
            _postRunners.clear();
        }
    };

    private final XmppClient_StateChangeSlot _slotStateChange =
        new XmppClient_StateChangeSlot() {
            @Override
            public void onStateChange(XmppEngine.State state)
            {
                l.debug("st: engine state:" + state);

                if (state == XmppEngine.State.STATE_OPEN) {
                    _main.sendPresence(true, Status.Show.SHOW_ONLINE);
                    _main.initSessionManagerTask();
                    l.debug("st: create engine");
                    _engineReference.set_(new Engine(ij, _main, SignalThread.this));
                } else if (state == XmppEngine.State.STATE_CLOSED) {
                    int[] subcode = { 0 };
                    XmppEngine.Error error = _main.getXmppClient().engine()
                        .GetError(subcode);
                    close_(new ExJingle("engine state changed to closed." +
                                " error " + error + " subcode " + subcode[0]));
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
            }
        };

    private final XmppSocket_CloseEventSlot _slotSocketCloseEvent =
        new XmppSocket_CloseEventSlot() {
            @Override
            public void onCloseEvent(int error)
            {
                close_(new ExJingle("socket closed. error " + error));
            }
        };

    private final XmppSocket_ErrorSlot _slotSocketError =
        new XmppSocket_ErrorSlot() {
            @Override
            public void onError()
            {
                close_(new ExJingle("socket error"));
            }
        };

    private final class EngineReference
    {
        /**
         * @return the current value of the {@link Engine} reference (may be null)
         * <br/>
         * NOTE: if this returns a non-null value, then _main <i>must</i> be valid (the reverse is
         * not the case)
         */
        public @Nullable Engine get_()
        {
            return _engine;
        }

        public void set_(@Nullable Engine engine)
        {
            assertThread();

            l.debug("st: set eng old:" + _engine + " new:" + engine);
            _engine = engine;
        }

        private Engine _engine;
    }

    private boolean _linkUp;    // protected by _cvLinkState

    private volatile XmppMain _main;

    private final IJingle ij;
    private final EngineReference _engineReference = new EngineReference();
    private final ArrayList<Runnable> _postRunners =  new ArrayList<Runnable>(); // st thread only
    private final CallHandler _callHandler = new CallHandler();
    private final Object _mxMain = new Object(); // lock object
    private final Object _cvLinkState = new Object(); // lock object
    private final Jid _jidSelf = Jingle.did2jid(Cfg.did());

    private static final byte[] ljlogpathutf8;
    private static final Logger l = Util.l(SignalThread.class);
}
