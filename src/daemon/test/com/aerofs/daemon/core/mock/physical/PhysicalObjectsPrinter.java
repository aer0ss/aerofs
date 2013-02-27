package com.aerofs.daemon.core.mock.physical;

import com.aerofs.lib.Util;
import com.aerofs.lib.injectable.InjectableFile;
import org.slf4j.Logger;

import java.io.File;

/**
 * See MockPhysicalDir for usage
 */
public class PhysicalObjectsPrinter
{
    private static final Logger l = Util.l(PhysicalObjectsPrinter.class);

    public static void printRecursively(InjectableFile f)
    {
        if (f.isDirectory()) {
            l.info(f.getPath() + File.separator);
            for (InjectableFile child : f.listFiles()) {
                printRecursively(child);
            }
        } else {
            l.info(f.getPath());
        }
    }
}
