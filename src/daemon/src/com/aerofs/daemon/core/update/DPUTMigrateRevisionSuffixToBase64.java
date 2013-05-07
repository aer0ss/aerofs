/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.phy.linked.LinkedRevProvider.RevisionSuffix;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

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
    private final FrequentDefectSender _fds = new FrequentDefectSender();

    DPUTMigrateRevisionSuffixToBase64()
    {
    }

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
        RevisionSuffix suffix = new RevisionSuffix(kidx, mtime, 0);
        try {
            String newName = name.substring(0, posHyphen)
                    + RevisionSuffix.SEPARATOR + suffix.encoded();
            FileUtil.moveInSameFileSystem(f, new File(f.getParent(), newName));
        } catch (IOException e) {
            _fds.logSendAsync("Failed to fix revision: " + f.getAbsolutePath(), e);
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
                    _fds.logSendAsync("Invalid revision file: " + c.getAbsolutePath());
                }
            } else if (c.isDirectory()) {
                fixFolder(c);
            }
        }
    }

    @Override
    public void run() throws Exception
    {
        fixFolder(new File(Util.join(DPUTMigrateAuxRoot.getOldAuxRoot(),
                LibParam.AuxFolder.REVISION._name)));
    }
}
