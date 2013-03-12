package com.aerofs.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

public class CompSpin extends Composite
{
    private final CLabel _label;
    private final AbstractSpinAnimator _animator = new AbstractSpinAnimator(this) {
        @Override
        protected void setImage(Image img)
        {
            _label.setImage(img);
        }
    };

    /**
     * Create the composite.
     */
    public CompSpin(Composite parent, int style)
    {
        super(parent, style);

        setLayout(new FillLayout(SWT.HORIZONTAL));
        _label = new CLabel(this, SWT.NONE);
        stop();
    }

    public void start()
    {
        _animator.start();
    }

    public void stop()
    {
        stop(Images.get(Images.ICON_NIL));
    }

    public void error()
    {
        stop(Images.get(Images.ICON_WARNING));
    }

    public void warning()
    {
        stop(Images.get(Images.ICON_WARNING));
    }

    /**
     * @param img may not be null
     */
    public void stop(Image img)
    {
        assert img != null;

        _animator.stop();
        _label.setImage(img);
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }
}
