/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Base64;
import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.RevisionInfo;
import com.aerofs.defects.Defect;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static com.aerofs.defects.Defects.newFrequentDefect;

/**
 * Rename files in revision tree to use new suffix encoding scheme
 * old rev suffix : -<kidx>_<mtime>
 * new rev suffix : .<encoded> where encoded is URL-safe base64 encoding of <kidx><mtime><time>
 * for the suffix conversion we arbitrarily set <time> to 0
 */
public class DPUTMigrateRevisionSuffixToBase64 implements IDaemonPostUpdateTask
{
    private final String DATE_FORMAT = "yyyyMMdd_HHmmss_SSS";
    private final DateFormat _dateFormat = new SimpleDateFormat(DATE_FORMAT);
    private final Defect _defect = newFrequentDefect("dput.migrate_revision_suffix");

    // separates file name from encoded revision suffix
    private static final char REVISION_SUFFIX_SEPARATOR = '.';

    /**
     * Return true if the input file is a valid revision file in the old scheme
     */
    boolean fixFile(File f)
    {
        String name = f.getName();
        int posHyphen = name.lastIndexOf('-');
        if (posHyphen <= 0) return false;
        int posUnderscoreAfterHyphen = name.indexOf('_', posHyphen);
        if (posUnderscoreAfterHyphen < 0
                || posUnderscoreAfterHyphen + DATE_FORMAT.length() > name.length()) return false;
        int kidx;
        try {
            kidx = Integer.parseInt(name.substring(posHyphen + 1, posUnderscoreAfterHyphen));
        } catch (NumberFormatException e) {
            return false;
        }
        long mtime;
        try {
            mtime = _dateFormat.parse(name.substring(posUnderscoreAfterHyphen + 1)).getTime();
        } catch (ParseException e) {
            return false;
        }
        try {
            ByteBuffer buf = ByteBuffer.allocate(RevisionInfo.DECODED_LENGTH);
            buf.putInt(kidx);
            buf.putLong(mtime);
            buf.putLong(0);
            String encoded = Base64.encodeBytes(buf.array(), Base64.URL_SAFE);
            String newName = name.substring(0, posHyphen)
                    + REVISION_SUFFIX_SEPARATOR + encoded;
            FileUtil.moveInSameFileSystem(f, new File(f.getParent(), newName));
        } catch (IOException e) {
            _defect.setMessage("Failed to fix revision: " + f.getAbsolutePath())
                    .setException(e)
                    .sendAsync();
        }
        return true;
    }

    void fixFolder(File d)
    {
        File children[] = d.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isFile()) {
                if (!fixFile(c)) {
                    _defect.setMessage("Invalid revision file: " + c.getAbsolutePath())
                            .sendAsync();
                }
            } else if (c.isDirectory()) {
                fixFolder(c);
            }
        }
    }

    @Override
    public void run() throws Exception
    {
        fixFolder(new File(Util.join(DPUTMigrateAuxRoot.getOldAuxRoot(), "r")));
    }
}
