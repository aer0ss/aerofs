package com.aerofs.lib.cfg;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.Versions;

import java.io.*;
import java.util.Scanner;

public class CfgUtils {

    private CfgUtils()
    {
        // Private ctor to disallow instantiation.
    }

    static boolean disabledByFile(String rtRoot, String filename)
    {
        return !new File(rtRoot, filename).exists();
    }

    static boolean enabledByFile(String rtRoot, String filename)
    {
        return new File(rtRoot, filename).exists();
    }

    public static String getVersion()
    {
        try {
            try (Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), LibParam.VERSION)))) {
                    return s.nextLine();
            }
        } catch (FileNotFoundException e) {
                return Versions.ZERO;
        }
    }

    public static InputStream cacertReaderInputStream() throws FileNotFoundException
    {
        return new ByteArrayInputStream(LibParam.DeploymentConfig.BASE_CA_CERTIFICATE.getBytes());
    }

    public static boolean lotsOfLog(String rtRoot)
    {
        return new File(Util.join(rtRoot, LibParam.LOL)).exists();
    }

    public static boolean lotsOfLotsOfLog(String rtRoot)
    {
        return new File(Util.join(rtRoot, LibParam.LOLOL)).exists();
    }
}
