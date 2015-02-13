/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.PathType;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.RevisionInfo;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.cfg.Cfg;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map.Entry;

import static com.aerofs.daemon.core.phy.linked.LinkedRevProvider.encode;

/**
 * Old history structure:
 *
 * {AuxFolder}/r/path/to/file.{base64(revinfo)}
 *
 * New history structure (details in {@link com.aerofs.daemon.core.phy.linked.LinkedRevProvider}
 *
 * {AuxFolder}/h/D{hex(path)}/D{hex(to)}/F{hex(file)}/{hex(revinfo)}
 *
 * Benefits:
 *      - supports non-representable names
 *      - case-sensitive
 *      - more scalable version listing
 * Drawbacks:
 *      - roughly halves effective max path length if there is one
 */
public class DPUTMigrateHistoryToHex implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTMigrateHistoryToHex.class);

    private static final char REVISION_SUFFIX_SEPARATOR = '.';

    private static @Nullable RevisionInfo fromSuffixNullable(String suffix)
    {
        byte[] decoded;
        try {
            decoded = Base64.decode(suffix, Base64.URL_SAFE);
        } catch (Throwable e) {
            l.warn("unable to decode {}", suffix, BaseLogUtil.suppress(e));
            return null;
        }
        if (decoded.length != RevisionInfo.DECODED_LENGTH) return null;
        ByteBuffer buf = ByteBuffer.wrap(decoded);
        int kidx = buf.getInt();
        long mtime = buf.getLong();
        long rtime = buf.getLong();
        return new RevisionInfo(kidx, mtime, rtime);
    }

    private void migrate(File oldHistory, File newHistory) throws IOException
    {
        File[] children = oldHistory.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                migrate(child, new File(newHistory, encode(name, PathType.DIR)));
            } else if (child.isFile()) {
                int pos = name.lastIndexOf(REVISION_SUFFIX_SEPARATOR);
                RevisionInfo info = pos > 0 ? fromSuffixNullable(name.substring(pos + 1)) : null;
                if (info != null) {
                    File d = new File(newHistory, encode(name.substring(0, pos), PathType.FILE));
                    d.mkdirs();
                    child.renameTo(new File(d, info.hexEncoded()));
                } else {
                    l.warn("invalid rev file {}", child.getAbsolutePath());
                    child.delete();
                }
            }
        }
    }

    @Override
    public void run() throws Exception
    {
        for (Entry<SID, String> e: Cfg.getRoots().entrySet()) {
            String auxRoot = Cfg.absAuxRootForPath(e.getValue(), e.getKey());
            File oldHistory = new File(auxRoot, "r");
            File newHistory = new File(auxRoot, AuxFolder.HISTORY._name);
            if (oldHistory.exists() && oldHistory.isDirectory()) {
                newHistory.mkdir();
                migrate(oldHistory, newHistory);
                oldHistory.delete();
            }
        }
    }
}
