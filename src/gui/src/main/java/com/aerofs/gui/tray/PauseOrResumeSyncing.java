package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.ui.UIGlobals;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

/**
 * TODO (WW) reissue the pause ritual command if the daemon restarts.
 */
public class PauseOrResumeSyncing
{
    private static final Logger l = Loggers.getLogger(PauseOrResumeSyncing.class);

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
        l.warn("pause syncing");
        assert !_paused;

        UIGlobals.ritual().pauseSyncing();

        _paused = true;
        final int seq = ++_pauseSeq;

        ThreadUtil.startDaemonThread("gui-res", new Runnable()
        {
            @Override
            public void run()
            {
                ThreadUtil.sleepUninterruptable(timeout);
                Util.exponentialRetry("gui-res", new Callable<Void>()
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
        l.warn("resume syncing");
        assert _paused;

        UIGlobals.ritual().resumeSyncing();

        // increment the pause sequence to cancel all pending pause timeouts
        _pauseSeq++;
        _paused = false;
    }

    public boolean isPaused()
    {
        return _paused;
    }
}
