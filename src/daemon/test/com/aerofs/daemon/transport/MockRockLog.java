/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.Defect.Priority;
import com.aerofs.rocklog.RockLog;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class MockRockLog
{
    private final RockLog rockLog = mock(RockLog.class);
    private final Defect defect = mock(Defect.class);

    public MockRockLog()
    {
        when(rockLog.newDefect(anyString())).thenReturn(defect);
        when(defect.addData(anyString(), anyString())).thenReturn(defect);
        when(defect.setException(any(Throwable.class))).thenReturn(defect);
        when(defect.setMessage(anyString())).thenReturn(defect);
        when(defect.setPriority(any(Priority.class))).thenReturn(defect);
    }

    public RockLog getRockLog()
    {
        return rockLog;
    }

    public Defect getDefect()
    {
        return defect;
    }
}
