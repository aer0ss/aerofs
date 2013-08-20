package com.aerofs.gui.tray;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.gui.GUI;
import com.aerofs.ui.UIParam;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.eclipse.swt.widgets.UbuntuTrayItem;

import java.util.List;

/**
 * Abstract base class for all tray menu components that need to be dynamically
 * refreshed to display new information (e.g. in reaction to a Ritual notification)
 *
 * This class makes it easy to rate limit refresh operations, which is particularly
 * important to properly support systems using libappindicator
 */
public abstract class DynamicTrayMenuComponent implements ITrayMenuComponent
{
    // We need to refresh slowly on Ubuntu, to avoid making the menu unnavigable
    private static final long REFRESH_TIME = UIParam.SLOW_REFRESH_DELAY * 2;

    private final long _rate;
    private ElapsedTimer _timer;
    private boolean _scheduled;

    private final Runnable _notifier;

    private final List<ITrayMenuComponentListener> _listeners = Lists.newArrayList();

    public DynamicTrayMenuComponent()
    {
        _rate = REFRESH_TIME;
        _timer = new ElapsedTimer();
        _notifier = new Runnable() {
            @Override
            public void run()
            {
                refresh();
            }
        };
    }

    /**
     * Force a refresh of the whole menu
     *
     * @pre must be called from the UI thread
     */
    protected synchronized void refresh()
    {
        Preconditions.checkState(GUI.get().isUIThread());

        _timer.restart();
        _scheduled = false;

        for (ITrayMenuComponentListener listener : _listeners) {
            listener.onTrayMenuComponentChange();
        }
    }

    /**
     * Schedule an update to be run on the UI thread. Can be called from any thread.
     */
    public synchronized void scheduleUpdate()
    {
        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                if (UbuntuTrayItem.supported()) {
                    // throttled refresh when using libappindicator
                    if (!_scheduled) {
                        _scheduled = true;
                        GUI.get().timerExec(Math.max(0, _rate - _timer.elapsed()), _notifier);
                    }
                } else {
                    // non-throttled async update when not using libappindicator
                    updateInPlace();
                }
            }
        });
    }

    @Override
    public void addListener(ITrayMenuComponentListener l)
    {
        _listeners.add(l);
    }
}
