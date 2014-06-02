/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.protocol.GetVersionsResponse;
import com.aerofs.daemon.core.protocol.UpdateSenderFilter;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class GetVersionsResponseWithMocks extends AbstractClassUnderTestWithMocks
{
    public @Mock IncomingStreams _iss;
    public @Mock UpdateSenderFilter _pusf;
    public @Mock NativeVersionControl _nvc;
    public @Mock ImmigrantVersionControl _ivc;
    public @Mock MapSIndex2Store _sidx2s;
    public @Mock IPulledDeviceDatabase _pulleddb;

    public @InjectMocks GetVersionsResponse _gvr;
}
