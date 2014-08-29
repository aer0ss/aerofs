/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.tc.CoreLockReleasingExecutor;
import com.aerofs.daemon.event.IEBIMC;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FileUtil.FileName;
import com.aerofs.lib.os.IOSUtil;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

/**
 * Base class for event handlers dealing with file export
 *
 */
public abstract class AbstractHdExport<T extends IEBIMC> extends AbstractHdIMC<T>
{
    private final IOSUtil _os;
    private final CoreLockReleasingExecutor _coreLockReleasingExecutor;

    protected AbstractHdExport(IOSUtil os, CoreLockReleasingExecutor coreLockReleasingExecutor)
    {
        _os = os;
        _coreLockReleasingExecutor = coreLockReleasingExecutor;
    }

    /**
     * Create a temp file that has the same extension has the original file
     * This is important so that we can open the temp file using the appropriate program
     */
    protected File createTempFileWithSameExtension(String fileName) throws IOException
    {
        FileName file = FileName.fromBaseName(_os.cleanFileName(fileName));
        l.info("temp {} {}", file.base, file.extension);
        return FileUtil.createTempFile(file.base, file.extension, null);
    }

    protected void exportOrDeleteDest_(InputStream is, File dst) throws Exception
    {
        boolean ok = false;
        try {
            export_(is, dst);
            ok = true;
        } finally {
            if (!ok) dst.delete();
        }
    }

    protected void export_(final InputStream is, File dst) throws Exception
    {
        try {
            try (OutputStream os = new FileOutputStream(dst)) {
                _coreLockReleasingExecutor.execute_(() -> {
                    ByteStreams.copy(is, os);
                    os.flush();
                    return null;
                }, "export");
            }
        } finally {
            is.close();
        }
    }
}
