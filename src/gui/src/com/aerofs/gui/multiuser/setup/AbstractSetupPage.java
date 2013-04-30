/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import org.eclipse.swt.widgets.Composite;

public abstract class AbstractSetupPage extends Composite
{
    protected SetupModel _model;

    protected AbstractSetupPage(Composite parent, int style)
    {
        super(parent, style);
    }

    public void setModel(SetupModel model)
    {
        _model = model;
        readFromModel(model);
    }

    protected abstract void readFromModel(SetupModel model);
    protected abstract void writeToModel(SetupModel model);
}
