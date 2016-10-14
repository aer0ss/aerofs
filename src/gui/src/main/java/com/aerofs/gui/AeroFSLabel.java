/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * SWT treats ampersands in text as an indicator for mnemonics. However, we do not use
 * mnemonics. On the other hand, sometimes we show user inputs in labels' text and user inputs
 * may contain ampersands.
 *
 * To prevent repeating this mistake in the future, we now roll our custom label specifically to
 * guard against accidentally treating ampersands as mnemonics indicator.
 */
public class AeroFSLabel extends Label
{
    public AeroFSLabel(Composite parent, int style)
    {
        super(parent, style);
    }

    @Override
    protected void checkSubclass()
    {
        // overridden as SWT does not recommend subclass-ing labels
    }

    @Override
    public void setText(String text)
    {
        super.setText(text.replaceAll("&", "&&"));
    }
}
