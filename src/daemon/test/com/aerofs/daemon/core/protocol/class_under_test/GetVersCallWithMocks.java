/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.net.OutgoingStreams;
import com.aerofs.daemon.core.protocol.GetVersCall;
import com.aerofs.daemon.core.protocol.GetVersReply;
import com.aerofs.daemon.core.store.MapSIndex2Contributors;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class GetVersCallWithMocks extends AbstractClassUnderTestWithMocks
{
    public @Mock NativeVersionControl _nvc;
    public @Mock ImmigrantVersionControl _ivc;
    public @Mock GetVersReply _pgvr;
    public @Mock IncomingStreams _iss;
    public @Mock OutgoingStreams _oss;
    public @Mock MapSIndex2Store _sidx2s;
    public @Mock IPulledDeviceDatabase _pulleddb;
    public @Mock TokenManager _tokenManager;
    public @Mock DirectoryService _ds;
    public @Mock MapSIndex2Contributors _sidx2contrib;

    public @InjectMocks GetVersCall _gvc;
}
