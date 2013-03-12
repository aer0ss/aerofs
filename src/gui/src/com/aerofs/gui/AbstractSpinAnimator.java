/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Widget;

/**
 * Helper class to integrate a nice-looking spinner in arbitrary UI components that can display an
 * Image.
 */
public abstract class AbstractSpinAnimator
{
    private int _idx;
    private boolean _animating;

    private final Widget _w;

    public AbstractSpinAnimator(Widget w)
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
            if (_w.isDisposed() || !_animating) return;

            setImage(Images.getSpinnerFrame(_idx++));
            _w.getDisplay().timerExec(Images.getSpinnerFrameDelay(), _animator);
        }
    };

    protected abstract void setImage(Image img);
}
