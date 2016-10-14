/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nullable;

/**
 * Helper class to integrate a nice-looking spinner in arbitrary UI components that can display an
 * Image.
 */
public abstract class AbstractSpinAnimator
{
    private int _idx;
    private boolean _animating;

    // We need to support a null widget for cases relating to the tray menu where the widget may
    // not exist.  The semantics are:
    // 1) if a widget is provided, the timer dies with it
    // 2) if a null is provided, the timer does not die until stop() is called.
    private final @Nullable Widget _w;

    public AbstractSpinAnimator(@Nullable Widget w)
    {
        _w = w;
    }

    public void start()
    {
        _animating = true;
        _animator.run();
    }

    public void stop()
    {
        _animating = false;
    }

    private final Runnable _animator = new Runnable() {
        @Override
        public void run()
        {
            if ((_w != null && _w.isDisposed()) || !_animating) return;

            setImage(Images.getSpinnerFrame(_idx++));
            GUI.get().disp().timerExec(Images.getSpinnerFrameDelay(), _animator);
        }
    };

    protected abstract void setImage(Image img);
}
