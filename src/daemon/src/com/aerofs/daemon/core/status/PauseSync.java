/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.daemon.core.status;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class PauseSync
{
    private AtomicBoolean _paused = new AtomicBoolean(false);

    public void pause()
    {
        _paused.set(true);
    }

    public void resume()
    {
        _paused.set(false);
    }

    public boolean isPaused()
    {
        return _paused.get();
    }
}
