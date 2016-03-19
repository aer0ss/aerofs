/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.config.PropertiesRenderer;
import com.aerofs.defects.Defect.Priority;
import com.aerofs.defects.DryadClient.FileUploadListener;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.configuration.ClientConfigurationLoader;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.UploadLogsArgs;
import com.aerofs.proto.Cmd.UploadLogsDestination;
import com.aerofs.ui.IDaemonMonitor;
import com.google.common.collect.Queues;
import org.slf4j.Logger;

import java.io.File;
import java.util.Properties;
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

    private static final int CONNECTION_TIMEOUT = (int)(3 * C.SEC);
    private static final int READ_TIMEOUT = (int)(3 * C.SEC);

    private static final String PROPERTY_CONFIG_SERVICE_URL
            = "config.loader.configuration_service_url";
    private static final String PROPERTY_BASE_CA_CERT
            = "config.loader.base_ca_certificate";
    private static final String DRYAD_CERT
            = "base.dryad.cert";

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
                throws Exception
        {
            if (args.hasDestination()) {
                UploadLogsDestination destination = args.getDestination();
                return createPrivateDryadClient(destination.getHostname(),
                        destination.getPort(),
                        getDryadCertificate());
            } else {
                return createPublicDryadClient();
            }
        }

        // Dryad Certificate are stored on config service. Since we cache config value on client startup,
        // we will need to connect to config service to retrieve the certificate to authenticate Dryad.
        private String getDryadCertificate()
                throws Exception
        {
            ClientConfigurationLoader loader =
                    new ClientConfigurationLoader(AppRoot.abs(), Cfg.absRTRoot(), new PropertiesRenderer());

            Properties prop = loader.loadConfiguration();

            return prop.getProperty(DRYAD_CERT);
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
                    return filename.startsWith(ClientParam.OBF_CORE_DATABASE)
                            || filename.equalsIgnoreCase(ClientParam.CFG_DATABASE)
                            || filename.equalsIgnoreCase(ClientParam.CORE_DATABASE);
                }
            };
        }
    }
}
