package com.aerofs.ui.update;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import static com.aerofs.lib.cfg.CfgDatabase.LAST_VER;

public class PostUpdate
{
    public static boolean updated()
    {
        return !Cfg.ver().equals(Cfg.db().get(LAST_VER));
    }

    private static Map<String, byte[]> getChecksums()
            throws IOException, ExFormatError
    {
        try (Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), ClientParam.VERSION)))) {
            // skip the version number
            s.nextLine();

            Map<String, byte[]> chksums = new HashMap<>();

            // checksum lines are in the format of "file=sum"
            while (s.hasNextLine()) {
                String str = s.nextLine();
                String parts[] = str.split("=");
                if (parts.length != 2) throw new IOException("ver file format error");
                chksums.put(parts[0], BaseUtil.hexDecode(parts[1]));
            }
            return chksums;
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

            byte[] hash = BaseSecUtil.hash(f);
            if (!Arrays.equals(hash, en.getValue())) {
                Loggers.getLogger(PostUpdate.class)
                        .warn("{} chksum mismatch. expected {} actual {}", en.getKey(),
                                BaseUtil.hexEncode(en.getValue()), BaseUtil.hexEncode(hash));
                return en.getKey();
            }
        }

        return null;
    }
}
