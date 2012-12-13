package com.aerofs.gui.tray;

import java.util.concurrent.Callable;

import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;

/**
 * TODO (WW) reissue the pause ritual command if the daemon restarts.
 */
public class PauseOrResumeSyncing
{
    // access to all member variables should be protected by synchronized (this)
    private int _pauseSeq;
    private boolean _paused;

    public synchronized void pause(long timeout) throws Exception
    {
        // The test is needed in case the GUI shows a "Pause" menu item when a pause operation is
        // ongoing (since isPaused() returns false before the operation completes), and the user
        // clicks on the menu item.
        if (!_paused) pause_(timeout);
    }

    public synchronized void resume() throws Exception
    {
        // see comments in pause() for detail.
        if (_paused) resume_();
    }

    private void pause_(final long timeout) throws Exception
    {
        Util.l(this).warn("pause syncing");
        assert !_paused;

        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            ritual.pauseSyncing();
        } finally {
            ritual.close();
        }

        _paused = true;
        final int seq = ++_pauseSeq;

        ThreadUtil.startDaemonThread("resume-syncing", new Runnable()
        {
            @Override
            public void run()
            {
                ThreadUtil.sleepUninterruptable(timeout);
                Util.exponentialRetry("resume-syncing", new Callable<Void>()
                {
                    @Override
                    public Void call()
                            throws Exception
                    {
                        synchronized (PauseOrResumeSyncing.this) {
                            // no-op if another pause timeout has been scheduled or syncing has been
                            // resumed.
                            if (seq == _pauseSeq) resume_();
                        }
                        return null;
                    }
                });
            }
        });
    }

    private void resume_() throws Exception
    {
        Util.l(this).warn("resume syncing");
        assert _paused;

        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            ritual.resumeSyncing();
        } finally {
            ritual.close();
        }

        // increment the pause sequence to cancel all pending pause timeouts
        _pauseSeq++;
        _paused = false;
    }

    public boolean isPaused()
    {
        return _paused;
    }
}
