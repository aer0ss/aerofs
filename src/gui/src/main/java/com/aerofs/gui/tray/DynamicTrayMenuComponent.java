package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.gui.common.RateLimitedTask;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.eclipse.swt.widgets.UbuntuTrayItem;

import java.util.List;

import static com.aerofs.ui.UIParam.SLOW_REFRESH_DELAY;

/**
 * Abstract base class for all tray menu components that need to be dynamically
 * refreshed to display new information (e.g. in reaction to a Ritual notification)
 *
 * This class makes it easy to rate limit refresh operations, which is particularly
 * important to properly support systems using libappindicator
 */
public abstract class DynamicTrayMenuComponent implements ITrayMenuComponent
{
    private final List<ITrayMenuComponentListener> _listeners = Lists.newArrayList();

    // We need to refresh slowly on Ubuntu, to avoid making the menu unnavigable
    private final RateLimitedTask _refreshTask = new RateLimitedTask(SLOW_REFRESH_DELAY * 2)
    {
        @Override
        protected void workImpl()
        {
            for (ITrayMenuComponentListener listener : _listeners) {
                listener.onTrayMenuComponentChange();
            }
        }
    };

    /**
     * Force a refresh of the whole menu
     *
     * @pre must be called from the UI thread
     */
    protected synchronized void refresh()
    {
        Preconditions.checkState(GUI.get().isUIThread());
        _refreshTask.run();
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
                    _refreshTask.schedule();
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
