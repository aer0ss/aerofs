/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SIndex;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static com.aerofs.daemon.core.protocol.ProtocolTestUtil.newUser;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.when;

/**
 * A base class to construct protocol objects under test as well as their supporting mock objects.
 * Subclasses holds protocol objects. This class include commonly used mock objects. Subclasses can
 * add more mock objects as necessary.
 */
public abstract class AbstractClassUnderTestWithMocks
{
    public @Spy Metrics _m = new Metrics();
    public @Mock LocalACL _lacl;
    public @Mock CfgLocalUser _cfgLocalUser;
    public @Mock TransportRoutingLayer _trl;
    public @Mock TransManager _tm;
    public @Mock IMapSIndex2SID _sidx2sid;
    public @Mock IMapSID2SIndex _sid2sidx;
    public @Mock RPC _rpc;

    // These values are shared across all AbstractClassUnderTestWithMocks instances
    public static final SIndex[] SINDEXES = { new SIndex(1), new SIndex(2), new SIndex(3) };
    public static final SID[] SIDS = { SID.generate(), SID.generate(), SID.generate() };

    public AbstractClassUnderTestWithMocks()
    {
        // Initialze mocks in the constructor.
        //
        // Ideally we can base this class off AbstractTest to automatically initialize mocks.
        // However, a typical use of this class (or more precisely, concrete subclasses of this
        // class) is that a test class refers to one or more instances of this class as field
        // members. Therefore, there is no chance for the @Before method of AbstractTest to be
        // invoked.
        MockitoAnnotations.initMocks(this);

        try {
            setupMocks();
        } catch (Exception e) {
            // Catch the exception here rather than throw it out to simpilify code for subclasses
            // and test classes.
            fail();
        }
    }

    private void setupMocks()
            throws Exception
    {
        when(_tm.begin_()).then(RETURNS_MOCKS);

        // Mock the local user
        when(_cfgLocalUser.get()).thenReturn(newUser());

        mockSID2SIndexMapping();

        // Make a pass-through LocalACL. Test cases can customize it by adding more rules using
        // when().
        when(_lacl.check_(any(UserID.class), any(SIndex.class), any(Permissions.class)))
                .thenReturn(true);
    }

    private void mockSID2SIndexMapping()
            throws ExNotFound
    {
        for (int i = 0; i < SIDS.length; i++) {
            SID sid = SIDS[i];
            SIndex sidx = SINDEXES[i];
            when(_sidx2sid.getNullable_(eq(sidx))).thenReturn(sid);
            when(_sidx2sid.getThrows_(eq(sidx))).thenReturn(sid);
            when(_sidx2sid.get_(eq(sidx))).thenReturn(sid);
            when(_sid2sidx.getNullable_(eq(sid))).thenReturn(sidx);
            when(_sid2sidx.getThrows_(eq(sid))).thenReturn(sidx);
            when(_sid2sidx.get_(eq(sid))).thenReturn(sidx);
        }
    }

    public UserID user()
    {
        return _cfgLocalUser.get();
    }
}
