/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.tng.xmpp.ID;
import com.aerofs.j.Jid;
import com.aerofs.j.Message;
import com.aerofs.j.MessageHandlerBase;
import com.aerofs.j.Status;
import com.aerofs.j.XmppClient_StateChangeSlot;
import com.aerofs.j.XmppEngine;
import com.aerofs.j.XmppMain;
import com.aerofs.j.XmppSocket_CloseEventSlot;
import com.aerofs.j.XmppSocket_ErrorSlot;
import com.aerofs.lib.L;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.lib.id.DID;
import org.apache.log4j.Logger;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;

// it doesn't implements IProxyObjectContainer becasue we don't properly handle
// deletion in this class (yet)
//
final class SignalThread extends java.lang.Thread implements IDumpStatMisc
{
    static {
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
     * N.B. unsafe to call out of the signal thread cuz 1) _eng may be changed by the signal thread.
     * 2) accessing the engine's methods out of the signal thread is not safe.
     */
    Engine getEngine_()
    {
        return _eng;
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

    void assertNotThread()
    {
        assert this != java.lang.Thread.currentThread();
    }

    /**
     * run the object within the signal thread. wait until the handler finishes before returning.
     * The _main pointer is guaranteed valid within runnable.run().
     * <p/>
     * N.B. runnable.run() must not block
     *
     * @param task The task to run on the SignalThread
     */
    void call(ISignalThreadTask task)
    {
        if (java.lang.Thread.currentThread() == this) {
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
            // it's okay to synchronize all _callHandler.call()s as the method
            // should not block for long any way. However we can't rely on
            // call()'s own synchronized primitive because call() releases the
            // lock when waiting for notification.
            //
            synchronized (_mxCall) {
                _callHandler.call(task);
            }
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
     * call delete_() in a separate message handler. this is needed sometimes when the object to be
     * deleted may be referred to by the rest of the message handling.
     */
    void delayedDelete_(final IProxyObjectContainer poc)
    {
        assertThread();

        post_(new Runnable()
        {
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
        while (true) { thdLoop_(); }
    }

    private void thdLoop_()
    {
        synchronized (_cvLinkState) {
            while (!_linkUp) { ThreadUtil.waitUninterruptable(_cvLinkState); }
        }


        l.info("create_ xmppmain");

        // The xmpp server address is an unresolved hostname.
        // We avoid resolving the hostname ourselves and let
        // SMACK do the DNS query on its thread.
        InetSocketAddress address = Param.xmppAddress();
        _main = new XmppMain(address.getHostName(), address.getPort(), true, _jidSelf,
                ID.getShaedXMPP(), L.get().jingleRelayHost(), L.get().jingleRelayPort(),
                ljlogpathutf8);

        l.info("connect slots");

        _slotStateChange.connect(_main.getXmppClient());

        _slotSocketCloseEvent.connect(_main.getSocket());

        _slotSocketError.connect(_main.getSocket());

        // >>>> WHEE...RUNNING >>>>

        l.info("before run");

        boolean lolon = Cfg.lotsOfLog(Cfg.absRTRoot());
        _main.run(lolon, lolon);

        l.info("after run");

        // >>>> WHEE...OHHHH....DONE :( >>>>

        if (_eng != null) {
            _eng.delete_();
            _eng = null;
        }

        synchronized (_callHandler) {
            _main.delete();
            _main = null;
            _callHandler.wake_();
        }

        ThreadUtil.sleepUninterruptable(Param.EXP_RETRY_MIN_DEFAULT);
        l.info("attempt connect");
    }

    // this method must be called within the signal thread, and may be called
    // several times on each XmppMain object
    void close_(Exception e)
    {
        assertThread();

        l.info("close st: " + Util.e(e, ExJingle.class));

        if (_eng != null) _eng.close_(e);

        _main.quit();
    }

    void close_(DID did, Exception e)
    {
        assertThread();

        if (_eng != null) _eng.close_(did, e);
    }

    Collection<DID> getConnections_()
    {
        assertThread();

        InOutArg<Collection<DID>> res = new InOutArg<Collection<DID>>(new ArrayList<DID>());
        if (_eng != null) res.set(_eng.getConnections_());
        return res.get();
    }

    String diagnose_()
    {
        assertThread();

        final InOutArg<String> res = new InOutArg<String>("call not executed");

        if (_eng == null) res.set("engine closed");
        else res.set(_eng.diagnose_());

        return res.get();
    }

    @Override
    public void dumpStatMisc(final String indent, final String indentUnit, final PrintStream ps)
    {
        ps.println(indent + "engine " + (_eng == null ? "closed" : "open"));
    }

    /**
     * N.B. pay special attention on how thdLoop_() synchronizes with this class
     */
    private class CallHandler extends MessageHandlerBase
    {
        synchronized void wake()
        {
            wake_();
        }

        void wake_()
        {
            _wake = true;
            notify();
        }

        synchronized void call(ISignalThreadTask task)
        {
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

            main.getSignalThread().Post(this);

            _wake = false;

            // we must wait here to avoid requests from queued up inside the
            // jingle engine. it's problematic otherwise, cause 1) jingle
            // queue is unbounded, and 2) jingle queue is oblivious to
            // our priority system
            //
            // wake up either the call finishes or main quits
            //
            ThreadUtil.waitUninterruptable(this, DaemonParam.Jingle.CALL_TIMEOUT);

            if (!_wake) ExitCode.JINGLE_CALL_TOO_LONG.exit();
        }

        @Override
        public void OnMessage(Message msg)
        {
            _task.run();

            wake();
        }

        private ISignalThreadTask _task;
        private boolean _wake;
    }

    MessageHandlerBase _postHandler = new MessageHandlerBase()
    {
        @Override
        public void OnMessage(Message msg)
        {
            for (Runnable r : _postRunners) r.run();
            _postRunners.clear();
        }
    };

    private final XmppClient_StateChangeSlot _slotStateChange = new XmppClient_StateChangeSlot()
    {
        @Override
        public void onStateChange(XmppEngine.State state)
        {
            l.info("engine state: " + state);

            if (state == XmppEngine.State.STATE_OPEN) {
                _main.sendPresence(true, Status.Show.SHOW_ONLINE);
                _main.initSessionManagerTask();
                l.info("create_ engine");
                _eng = new Engine(ij, _main, SignalThread.this);

            } else if (state == XmppEngine.State.STATE_CLOSED) {
                int[] subcode = {0};
                XmppEngine.Error error = _main.getXmppClient().engine().GetError(subcode);
                close_(new ExJingle("engine state changed to closed." +
                        " error " + error + " subcode " + subcode[0]));
                // Previously we had code that tried to create_ a new account
                // if login failed because of a "not authorized" message. This
                // call would race against the Multicast thread to create_ an
                // account on the XMPPBasedTransportFactory server. The winner would succeed, but
                // the loser would get a conflict(409). This led to a lot of
                // thrashing. The better solution is to simply bail out here,
                // and let the Multicast thread alone attempt to create_ the
                // account. This thread will simply reattempt to log in. At
                // some point at time the Multicast thread will succeed, at
                // which time we will be able to log in here as well.
            }
        }
    };

    private final XmppSocket_CloseEventSlot _slotSocketCloseEvent = new XmppSocket_CloseEventSlot()
    {
        @Override
        public void onCloseEvent(int error)
        {
            close_(new ExJingle("socket closed. error " + error));
        }
    };

    private final XmppSocket_ErrorSlot _slotSocketError = new XmppSocket_ErrorSlot()
    {
        @Override
        public void onError()
        {
            close_(new ExJingle("socket error"));
        }
    };

    private boolean _linkUp;    // protected by _cvLinkState

    private volatile XmppMain _main;
    private Engine _eng; // _eng's presence indicates _main is alive

    private final IJingle ij;
    private final ArrayList<Runnable> _postRunners = new ArrayList<Runnable>(); // st thread only
    private final CallHandler _callHandler = new CallHandler();
    private final Object _mxCall = 0; // lock object
    private final Object _cvLinkState = 0; // lock object
    private final Jid _jidSelf = JingleUnicastConnectionService.did2jid(Cfg.did());

    private static final byte[] ljlogpathutf8;
    private static final Logger l = Util.l(SignalThread.class);
}
