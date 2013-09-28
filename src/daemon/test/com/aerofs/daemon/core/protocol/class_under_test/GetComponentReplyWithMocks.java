/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.alias.Aliasing;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.protocol.GetComponentReply;
import com.aerofs.daemon.core.protocol.MetaDiff;
import com.aerofs.daemon.core.protocol.ReceiveAndApplyUpdate;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class GetComponentReplyWithMocks extends AbstractClassUnderTestWithMocks
{
    // For GetComponentReply
    public @Mock DirectoryService _ds;
    public @Mock IncomingStreams _iss;
    public @Mock ReceiveAndApplyUpdate _ru;
    public @Mock MetaDiff _mdiff;
    public @Mock Aliasing _al;
    public @Mock MapAlias2Target _a2t;
    public @Mock IEmigrantDetector _emd;

    public @InjectMocks GetComponentReply _gcr;
}
