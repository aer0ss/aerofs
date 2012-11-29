/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.umdc;

import com.aerofs.lib.IProgram;

public class UMDCProgram implements IProgram
{
    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        UMDC umdc = new UMDC();
        umdc.run_(args);
    }
}