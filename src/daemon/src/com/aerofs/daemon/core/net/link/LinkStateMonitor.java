/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.link;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.Util;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import java.net.NetworkInterface;

public class LinkStateMonitor extends AbstractNetworkLinkStateService
{
    private final CoreScheduler _sched;
    private final TokenManager _tokenManager;

    /**
     * whether the LSM was manually set to say that all interfaces on the machine are down
     */
    private boolean _markedDown;

    @Inject
    public LinkStateMonitor(TokenManager tokenManager, CoreScheduler sched)
    {
        _tokenManager = tokenManager;
        _sched = sched;
    }

    @Override
    public void start_()
    {
        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {

                try {
                    checkLinkState_();
                } catch (Exception e) {
                    Util.l(this).warn("retry later: " + e);
                }

                _sched.schedule(this, DaemonParam.LINK_STATE_MONITOR_INTERVAL);
            }
        }, 0);
    }

    @Override
    protected ImmutableSet<NetworkInterface> getActiveInterfaces_()
            throws Exception
    {
        if (_markedDown) return ImmutableSet.of();

        // on some Windows machines actually getting the network interfaces is a very slow
        // operation and can take a long time to finish. To prevent the daemon from hanging we'll
        // execute it out of the core context

        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "get active interfaces");
        try {
            TC.TCB tcb = tk.pseudoPause_("get active interfaces");
            try {
                return getActiveInterfacesImpl();
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    /**
     * Mark all the links as if they are down. This method is used to pause syncing activities
     */
    public void markLinksDown_()
            throws Exception
    {
        l.warn("mark down");
        _markedDown = true;
        checkLinkState_();
    }

    /**
     * Undo markLinksUp_().
     */
    public void markLinksUp_()
            throws Exception
    {
        l.warn("mark up");
        _markedDown = false;
        checkLinkState_();
    }
}
