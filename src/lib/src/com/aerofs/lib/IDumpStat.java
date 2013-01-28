/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.proto.Files.PBDumpStat;

public interface IDumpStat {

    /**
     * must be thread-safe for non-core components
     */
    void dumpStat(PBDumpStat template, PBDumpStat.Builder bd) throws Exception;
}
