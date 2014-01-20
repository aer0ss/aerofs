/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.tray;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * This class is WIP. The long term goal is to have it directly accessible from UIGlobals and used
 * in both GUI and CLI.
 */
public class Progresses
{
    /**
     * The callback will be made from the thread that triggers the callback, and each listener is
     * responsible for switching the thread context to the desired context (e.g. GUI).
     */
    public interface ProgressUpdatedListener
    {
        // called when the progress was added and notify was true
        void onProgressAdded(Progresses progresses, String message);

        // called whenever progresses have changed including after a progress is added
        void onProgressChanged(Progresses progresses);
    }

    private final List<String> _progresses = Lists.newLinkedList();
    private final List<ProgressUpdatedListener> _listeners = Lists.newLinkedList();

    public List<String> getProgresses()
    {
        return _progresses;
    }

    public void addProgress(String message, boolean notify)
    {
        _progresses.add(0, message);
        if (notify) notifyListenersOnProgressAdded(message);
        notifyListenersOnProgressChanged();
    }

    public void removeProgress(String message)
    {
        if (_progresses.remove(message)) notifyListenersOnProgressChanged();
    }

    public void removeAllProgresses()
    {
        _progresses.clear();
        notifyListenersOnProgressChanged();
    }

    public void addListener(ProgressUpdatedListener listener)
    {
        _listeners.add(listener);
    }

    public void removeListener(ProgressUpdatedListener listener)
    {
        _listeners.remove(listener);
    }

    private void notifyListenersOnProgressChanged()
    {
        for (ProgressUpdatedListener listener : _listeners) {
            listener.onProgressChanged(this);
        }
    }

    private void notifyListenersOnProgressAdded(String message)
    {
        for (ProgressUpdatedListener listener : _listeners) {
            listener.onProgressAdded(this, message);
        }
    }
}
