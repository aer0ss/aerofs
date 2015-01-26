/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol.class_under_test;

import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.protocol.NewUpdates;
import com.aerofs.daemon.core.protocol.NewUpdatesSender;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.lib.id.SIndex;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.sql.SQLException;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * This class contains a NewUpdates object with its supporting mock objects
 */
public class NewUpdatesWithMocks extends AbstractClassUnderTestWithMocks
{
    public @Mock NativeVersionControl _nvc;
    public @Mock ChangeEpochDatabase _cedb;
    public @Mock MapSIndex2Store _sidx2s;
    public @Mock AntiEntropy _ae;

    public @InjectMocks NewUpdates _nu;
    public @InjectMocks NewUpdatesSender _nus;

    public NewUpdatesWithMocks()
    {
        try {
            when(_cedb.getChangeEpoch_(any(SIndex.class))).thenReturn(null);
        } catch (SQLException e) { fail(); }
    }
}
