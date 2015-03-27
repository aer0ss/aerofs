/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.Loggers;
import com.aerofs.defects.Defect.Priority;
import com.aerofs.defects.DryadClient.FileUploadListener;
import com.aerofs.lib.LibParam;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.UploadLogsArgs;
import com.aerofs.proto.Cmd.UploadLogsDestination;
import com.aerofs.ui.IDaemonMonitor;
import com.google.common.collect.Queues;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.TimeUnit;

import static com.aerofs.defects.Defects.getFactory;
import static com.aerofs.defects.DryadClientUtil.createPrivateDryadClient;
import static com.aerofs.defects.DryadClientUtil.createPublicDryadClient;
import static com.google.common.base.Preconditions.*;

public class CommandDefect
{
    private static final Logger l = Loggers.getLogger(CommandDefect.class);

    private final String _defectID;
    private final DryadClient _dryad;
    private final Executor _executor;

    public CommandDefect(String defectID, DryadClient dryad, Executor executor)
    {
        _defectID = defectID;
        _dryad = dryad;
        _executor = executor;
    }

    public void sendAsync()
    {
        _executor.execute(this::sendSyncIgnoreErrors);
    }

    private void sendSyncIgnoreErrors()
    {
        try {
            sendSync();
        } catch (Exception e) {
            l.warn("Failed to upload logs on command.", e);
            // ignored
        }
    }

    private void sendSync()
            throws Exception
    {
        l.info("Sending command defect: {}", _defectID);
        getFactory().newAutoDefect("defect.command", _dryad)
                .setDefectID(_defectID)
                .setPriority(Priority.Command)
                .setFilesToUpload(AutoDefect.UPLOAD_ALL_FILES)
                .sendSync();
    }

    public static class Factory
    {
        private final Executor _executor = new ThreadPoolExecutor(
                0, 1,                                           // at most 1 thread
                30, TimeUnit.SECONDS,                           // idle thread TTL
                Queues.<Runnable>newLinkedBlockingQueue(5),     // bounded event queue
                new DiscardOldestPolicy());
        private final IDaemonMonitor _dm;

        public Factory(IDaemonMonitor dm)
        {
            _dm = dm;
        }

        public CommandDefect newCommandDefect(Command command)
                throws Exception
        {
            checkArgument(command.hasUploadLogsArgs());
            UploadLogsArgs args = checkNotNull(command.getUploadLogsArgs());
            checkState(System.currentTimeMillis() <= args.getExpiryTime(), "The command has expired.");

            l.info("Processing command defect; {}", args.getDefectId());

            DryadClient dryad = createDryadClient(args)
                    .setFileUploadListener(createListener());

            return new CommandDefect(args.getDefectId(), dryad, _executor);
        }

        private DryadClient createDryadClient(UploadLogsArgs args)
                throws IOException, GeneralSecurityException
        {
            if (args.hasDestination()) {
                UploadLogsDestination destination = args.getDestination();
                return createPrivateDryadClient(destination.getHostname(),
                        destination.getPort(),
                        destination.getCert());
            } else {
                return createPublicDryadClient();
            }
        }

        private FileUploadListener createListener()
        {
            return new FileUploadListener()
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
                        } catch (Exception e) {
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
            };
        }
    }
}
