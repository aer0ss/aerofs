package com.aerofs.daemon.core.mock.physical;

import com.aerofs.lib.Util;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.File;

/**
 * See MockPhysicalDir for usage
 */
public class PhysicalObjectsPrinter
{
    public static void printRecursively(InjectableFile f)
    {
        if (f.isDirectory()) {
            Util.l().info(f.getPath() + File.separator);
            for (InjectableFile child : f.listFiles()) {
                printRecursively(child);
            }
        } else {
            Util.l().info(f.getPath());
        }
    }
}
