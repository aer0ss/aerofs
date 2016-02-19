/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class PauseSync
{
    private final static Logger l = Loggers.getLogger(PauseSync.class);

    private final AtomicBoolean _paused = new AtomicBoolean(true);
    private final HashSet<Listener> _listeners = new HashSet<>();

    public interface Listener
    {
        void onPauseSync_();
        void onResumeSync_();
    }

    public void addListener_(Listener l)
    {
        _listeners.add(l);
    }

    public void removeListener_(Listener l)
    {
        _listeners.remove(l);
    }

    public void pause_()
    {
        if (_paused.compareAndSet(false, true)) {
            _listeners.forEach(PauseSync.Listener::onPauseSync_);
        } else {
            l.warn("already paused");
        }
    }

    public void resume_()
    {
        if (_paused.compareAndSet(true, false)) {
            _listeners.forEach(PauseSync.Listener::onResumeSync_);
        } else {
            l.warn("already resumed");
        }
    }

    public boolean isPaused()
    {
        return _paused.get();
    }
}
