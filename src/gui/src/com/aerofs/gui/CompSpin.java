package com.aerofs.gui;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

public class CompSpin extends Composite {

    private int _idx;
    private final CLabel _label;
    private boolean _animating;

    private static Image[] s_imgs;
    private static int s_delay;

    /**
     * Create the composite.
     * @param parent
     * @param style
     */
    public CompSpin(Composite parent, int style)
    {
        super(parent, style);

        if (s_imgs == null) {
            // image downloaded from http://ajaxload.info/
            ImageLoader loader = new ImageLoader();
            loader.load(AppRoot.abs() + C.ICONS_DIR + Images.ICON_SPIN);
            s_delay = loader.data[0].delayTime;
            s_imgs = new Image[loader.data.length];
            for (int i = 0; i < loader.data.length; i++) {
                s_imgs[i] = new Image(getDisplay(), loader.data[i]);
            }
        }

        setLayout(new FillLayout(SWT.HORIZONTAL));
        _label = new CLabel(this, SWT.NONE);
        stop();
    }

    public void start()
    {
        _animating = true;
        _animator.run();
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

        _animating = false;
        _label.setImage(img);
    }

    private final Runnable _animator = new Runnable() {
        @Override
        public void run()
        {
            if (isDisposed() || !_animating) return;

            _label.setImage(s_imgs[_idx++ % s_imgs.length]);
            getDisplay().timerExec(s_delay * 5, _animator);
        }
    };

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }
}
