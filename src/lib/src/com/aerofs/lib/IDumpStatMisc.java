/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import java.io.PrintStream;

public interface IDumpStatMisc
{
    /**
     * must be thread-safe for non-core components
     */
    void dumpStatMisc(String indent, String indentUnit, PrintStream ps) throws Exception;
}
