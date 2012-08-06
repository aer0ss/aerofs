package com.aerofs.ui.update;

import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExFormatError;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

public class PostUpdate
{
    public static boolean updated()
    {
        return !Cfg.ver().equals(Cfg.db().get(Key.LAST_VER));
    }

    private static Map<String, byte[]> getChecksums()
            throws IOException, ExFormatError
    {
        Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), C.VERSION)));
        try {
            // skip the version number
            s.nextLine();

            Map<String, byte[]> chksums = new HashMap<String, byte[]>();

            // checksum lines are in the format of "file=sum"
            while (s.hasNextLine()) {
                String str = s.nextLine();
                String parts[] = str.split("=");
                if (parts.length != 2) throw new IOException("ver file format error");
                chksums.put(parts[0], Util.hexDecode(parts[1]));
            }

            return chksums;

        } finally {
            s.close();
        }
    }

    /**
     * must be called *after* Cfg is initialized
     *
     * @return the failed file name if checksum fails. null if okay
     */
    public static String verifyChecksum()
            throws IOException, ExFormatError
    {
        Map<String, byte[]> chksums = getChecksums();
        for (Entry<String, byte[]> en : chksums.entrySet()) {
            File f = new File(AppRoot.abs() + File.separator + en.getKey());

            // skip non-exist files
            if (!f.exists()) continue;

            byte[] hash = SecUtil.hash(f);
            if (!Arrays.equals(hash, en.getValue())) {
                Util.l(PostUpdate.class).warn(en.getKey() + " chksum mismatch." +
                        " expected " + Util.hexEncode(en.getValue()) +
                        " actual " + Util.hexEncode(hash));
                return en.getKey();
            }
        }

        return null;
    }
}
