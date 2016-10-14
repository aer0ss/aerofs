/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

/**
 * A custom button. Features:
 * - It enforces a minimum button width of GUIParam.AEROFS_MIN_BUTTON_WIDTH
 */
public class AeroFSButton extends Button
{
    public AeroFSButton(Composite parent, int style)
    {
        super(parent, style);
    }

    @Override
    protected void checkSubclass()
    {
        // N.B. this method is overriden to not throw exceptions when SWT checks subclass
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed)
    {
        Point size = super.computeSize(wHint, hHint, changed);
        if (wHint == SWT.DEFAULT) size.x = Math.max(size.x, GUIParam.AEROFS_MIN_BUTTON_WIDTH);
        return size;
    }
}
