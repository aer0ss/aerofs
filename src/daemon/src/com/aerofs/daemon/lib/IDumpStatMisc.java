package com.aerofs.daemon.lib;

import java.io.PrintStream;

public interface IDumpStatMisc
{
    /**
     * must be thread-safe for non-core components
     */
    void dumpStatMisc(String indent, String indentUnit, PrintStream ps) throws Exception;
}
