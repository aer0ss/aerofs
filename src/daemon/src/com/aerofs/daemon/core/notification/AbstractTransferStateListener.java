/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.daemon.core.net.ITransferStateListener;
import com.aerofs.lib.KeyBasedThrottler;
import com.aerofs.lib.KeyBasedThrottler.Factory;
import com.aerofs.proto.RitualNotifications.PBNotification;

/**
 * Base class for transfer state listeners emitting Ritual notifications
 */
public abstract class AbstractTransferStateListener implements ITransferStateListener
{
    private final RitualNotificationServer _notifier;
    private final Factory _factory;

    private KeyBasedThrottler<Key> _throttler;
    private boolean _enableFilter = true;

    protected AbstractTransferStateListener(RitualNotificationServer notifier, Factory factory)
    {
        _notifier = notifier;
        _factory = factory;
    }

    public void enableFilter(boolean enable)
    {
        _enableFilter = enable;
    }

    @Override
    public void stateChanged_(Key key, Value value)
    {
        if (_throttler == null) {
            _throttler = _factory.<Key>create();
            _throttler.setDelay(1 * C.SEC);
        }

        if (_enableFilter && key._socid.cid().isMeta()) return;

        if (value._done < value._total) {
            if (_throttler.shouldThrottle(key)) return;
        } else {
            _throttler.untrack(key);
        }

        _notifier.sendEvent_(notificationForState(key, value));
    }

    protected abstract PBNotification notificationForState(Key key, Value value);
}
