/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.IEBIMC;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FileUtil.FileName;
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
    protected final TC _tc;

    public AbstractHdExport(TC tc)
    {
        _tc = tc;
    }

    /**
     * Create a temp file that has the same extension has the original file
     * This is important so that we can open the temp file using the appropriate program
     */
    protected File createTempFileWithSameExtension(String fileName) throws IOException
    {
        FileName file = FileName.fromBaseName(fileName);
        return FileUtil.createTempFile(file.base, file.extension, null, false);
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
            final OutputStream os = new FileOutputStream(dst);
            try {
                callWithCoreLockReleased_(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception
                    {
                        ByteStreams.copy(is, os);
                        os.flush();
                        return null;
                    }
                }, "export");
            } finally {
                os.close();
            }
        } finally {
            is.close();
        }
    }

    // TODO: move to helper class?
    private <V> V callWithCoreLockReleased_(Callable<V> c, String reason) throws Exception
    {
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, reason);
        try {
            TCB tcb = tk.pseudoPause_(reason);
            try {
                return c.call();
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }
}
