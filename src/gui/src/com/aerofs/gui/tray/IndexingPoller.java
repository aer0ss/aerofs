/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.tray;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.ex.ExIndexing;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.proto.Common;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIParam;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import org.newsclub.net.unix.AFUNIXSocketException;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.util.List;

import static com.google.common.util.concurrent.Futures.addCallback;

/**
 * Simple helper class that polls the daemon in the background to determine when the initial
 * indexing (on first launch) is done
 */
public class IndexingPoller
{
    private static final Logger l = Loggers.getLogger(IndexingPoller.class);
    private final IScheduler _sched;

    private volatile boolean _isIndexingDone;

    public interface IIndexingCompletionListener
    {
        void onIndexingDone();
    }

    private final List<IIndexingCompletionListener> _listeners = Lists.newArrayList();

    public IndexingPoller(IScheduler sched)
    {
        _sched = sched;
        schedulePingDaemon();
    }

    public synchronized boolean isIndexingDone()
    {
        return _isIndexingDone;
    }

    public synchronized void addListener(IIndexingCompletionListener listener)
    {
        _listeners.add(listener);
        if (_isIndexingDone) listener.onIndexingDone();
    }

    private void schedulePingDaemon()
    {
        l.debug("sched ping");
        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                pingDaemon();
            }
        }, UIParam.DAEMON_CONNECTION_RETRY_INTERVAL);
    }

    private void pingDaemon()
    {
        l.debug("ping");
        addCallback(UIGlobals.ritualNonBlocking().heartbeat(), new FutureCallback<Common.Void>() {
            @Override
            public void onSuccess(Common.Void aVoid)
            {
                l.info("indexing done");
                GUI.get().asyncExec(() -> {
                    _isIndexingDone = true;
                    for (IIndexingCompletionListener listener : _listeners) {
                        listener.onIndexingDone();
                    }
                });
            }

            @Override
            public void onFailure(Throwable t)
            {
                if (!(t instanceof ExIndexing)) {
                    l.warn("failed to ping daemon", BaseLogUtil.suppress(t,
                            AFUNIXSocketException.class,
                            ClosedChannelException.class));
                }
                schedulePingDaemon();
            }
        });
    }
}
