package com.aerofs.daemon.core.status;

import com.aerofs.base.Loggers;

import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class SyncStatusOnline implements PauseSync.Listener
{
    private final static Logger l = Loggers.getLogger(SyncStatusOnline.class);

    private final PauseSync _pauseSync;
    private final AtomicBoolean _storageAgentReachable;
    private final List<Listener> _listeners;

    @Inject
    public SyncStatusOnline(PauseSync pauseSync) {
        _storageAgentReachable = new AtomicBoolean(false);
        _listeners = new ArrayList<>(1);
        _pauseSync = pauseSync;
        _pauseSync.addListener_(this);
    }

    public interface Listener
    {
        void onSyncStatusOnline_(boolean online);
    }

    /**
     * @param storageAgentReachable
     * @return true if value was changed
     */
    protected boolean set(boolean storageAgentReachable) {
        l.trace("set: {}", storageAgentReachable, _storageAgentReachable.get(), _pauseSync.isPaused());
        boolean changed = _storageAgentReachable.compareAndSet(!storageAgentReachable,
                storageAgentReachable);
        if (changed && !_pauseSync.isPaused()) {
            notifyListeners_();
        }
        return changed;
    }

    public boolean get() {
        l.trace("get");
        return !_pauseSync.isPaused() && _storageAgentReachable.get();
    }

    public void addListener(Listener listener) {
        l.trace("addListener");
        _listeners.add(listener);
    }

    private void notifyListeners_() {
        l.trace("notifyListeners_: {}", _listeners.size());
        boolean online = get();
        _listeners.forEach(l -> l.onSyncStatusOnline_(online));
    }

    @Override
    public void onPauseSync_() {
        l.trace("onPauseSync_");
        if (_storageAgentReachable.get()) {
            notifyListeners_();
        }
    }

    @Override
    public void onResumeSync_() {
        l.trace("onResumeSync_");
        if (_storageAgentReachable.get()) {
            notifyListeners_();
        }
    }
}
