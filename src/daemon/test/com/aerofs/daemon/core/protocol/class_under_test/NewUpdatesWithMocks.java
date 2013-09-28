/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.protocol.NewUpdates;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class NewUpdatesWithMocks extends AbstractClassUnderTestWithMocks
{
    public @Mock NativeVersionControl _nvc;
    public @Mock MapSIndex2Store _sidx2s;
    public @Mock AntiEntropy _ae;

    public @InjectMocks NewUpdates _nu;
}
