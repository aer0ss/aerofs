/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.id.UserID;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// perhaps one day I'll find a better way than this. Until then, this will have to do
public class MockDefects
{
    public static void init()
    {
        init(mock(DefectFactory.class), mock(AutoDefect.class));
    }

    public static void init(DefectFactory factory, AutoDefect defect)
    {
        when(defect.addData(anyString(), anyObject())).thenReturn(defect);
        when(defect.setException(any(Throwable.class))).thenReturn(defect);
        when(defect.setMessage(anyString())).thenReturn(defect);

        when(factory.newAutoDefect(anyString())).thenReturn(defect);
        when(factory.newAutoDefect(anyString(), any(DryadClient.class))).thenReturn(defect);

        when(factory.newMetric(anyString())).thenReturn(defect);
        when(factory.newDefect(anyString())).thenReturn(defect);
        when(factory.newDefectWithLogs(anyString())).thenReturn(defect);
        when(factory.newDefectWithLogsNoCfg(anyString(), any(UserID.class), anyString()))
                .thenReturn(defect);
        when(factory.newFrequentDefect(anyString())).thenReturn(defect);
        when(factory.newUploadCoreDatabase()).thenReturn(defect);

        Defects.setFactory(factory);
    }
}
