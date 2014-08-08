/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.controller;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.UploadLogsArgs;
import com.aerofs.proto.Cmd.UploadLogsDestination;
import com.aerofs.ui.IDaemonMonitor;
import com.aerofs.ui.defect.DryadClient;
import com.aerofs.ui.defect.DryadClient.FileUploadListener;
import com.google.common.collect.Queues;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.TimeUnit;

import static com.aerofs.ui.defect.DryadClientUtil.createDefectLogsResource;
import static com.aerofs.ui.defect.DryadClientUtil.createPrivateDryadSSLContext;
import static com.aerofs.ui.defect.DryadClientUtil.createPublicDryadSSLContext;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class CommandDefect
{
    private static final Logger l = Loggers.getLogger(CommandDefect.class);

    private static final Executor _executor = new ThreadPoolExecutor(
            0, 1,                                           // at most 1 thread
            30, TimeUnit.SECONDS,                           // idle thread TTL
            Queues.<Runnable>newLinkedBlockingQueue(5),     // bounded event queue
            new DiscardOldestPolicy());                     // discard oldest on overflow

    private final Command _command;
    private final IDaemonMonitor _dm;

    public CommandDefect(Command command, IDaemonMonitor dm)
    {
        _command = command;
        _dm = dm;
    }

    public void sendAsync()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    sendSync();
                } catch (Exception e) {
                    l.warn("Failed to upload logs.", e);
                }
            }
        });
    }

    public void sendSync()
            throws Exception
    {
        checkArgument(_command.hasUploadLogsArgs());

        UploadLogsArgs args = _command.getUploadLogsArgs();

        checkState(System.currentTimeMillis() <= args.getExpiryTime(), "The command has expired.");

        DryadClient client = createDryadClient(args);

        client.setListener(new FileUploadListener()
        {
            @Override
            public void onFileUpload(File file)
            {
                if (isDatabaseFile(file)) {
                    _dm.stopIgnoreException();
                }
            }

            @Override
            public void onFileUploaded(File file)
            {
                if (isDatabaseFile(file)) {
                    try {
                        _dm.start();
                    } catch (Exception e){
                        l.error("Failed to restart daemon.", e);
                    }
                }
            }

            private boolean isDatabaseFile(File file)
            {
                String filename = file.getName();
                return filename.startsWith(LibParam.OBF_CORE_DATABASE)
                        || filename.equalsIgnoreCase(LibParam.CFG_DATABASE)
                        || filename.equalsIgnoreCase(LibParam.CORE_DATABASE);
            }
        });

        client.uploadFiles(createDefectLogsResource(args.getDefectId(), Cfg.user(), Cfg.did()),
                new File(Cfg.absRTRoot()).listFiles());
    }

    private DryadClient createDryadClient(UploadLogsArgs args)
            throws IOException, GeneralSecurityException
    {
        if (args.hasDestination()) {
            UploadLogsDestination destination = args.getDestination();
            return new DryadClient(
                    destination.getHostname(),
                    destination.getPort(),
                    createPrivateDryadSSLContext(destination.getCert()));
        } else {
            return new DryadClient(
                    "dryad.aerofs.com",
                    443,
                    createPublicDryadSSLContext());
        }
    }
}
