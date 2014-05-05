/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.controller;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.sv.client.SVClient;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import org.slf4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class DryadUploadService
{
    private static final Logger l = Loggers.getLogger(DryadUploadService.class);

    private final String _rtroot;
    private final ExecutorService _executor;

    public DryadUploadService(String rtroot, ExecutorService executor)
    {
        _rtroot = rtroot;
        _executor = executor;
    }

    public void submit(String dryadID, String customerID)
    {
        _executor.submit(new DryadUploadTask(_rtroot, dryadID, customerID));
    }

    public static class Factory
    {
        public static DryadUploadService create(String rtroot)
        {
            return new DryadUploadService(rtroot, new DryadExecutor());
        }
    }

    private static class DryadExecutor extends ThreadPoolExecutor implements
            RejectedExecutionHandler
    {
        // use at most 1 thread because we don't want to deal with managing multiple concurrent
        // archives and uploads at the same time.
        public static final int CORE_POOL_SIZE = 0;
        public static final int MAX_POOL_SIZE = 1;

        // requests should be rare and keep alive only helps when there are multiple requests
        // queued up. So 10 seconds should be more than sufficient.
        public static final int KEEP_ALIVE_TIME = 10; // seconds

        // Since the queue is not persisted, 10 is more than enough.
        // TODO (AT): find out what's the failure mode when the queue overflows?
        public static final int TASK_QUEUE_SIZE = 10;

        public DryadExecutor()
        {
            super(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(TASK_QUEUE_SIZE));

            setRejectedExecutionHandler(this);
        }

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor)
        {
            l.warn("Request rejected. The task queue has {} tasks.", getQueue().size());
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t)
        {
            if (r instanceof FutureTask) {
                FutureTask task = (FutureTask) r;

                try {
                    task.get();
                } catch (Exception e) {
                    l.warn("Encountered an error", e);
                    return;
                }
            }

            l.info("Finished executing a request");
        }
    }

    private static class DryadUploadTask implements Callable<Void>
    {
        private static final int CHUNK_SIZE = 4 * C.MB;

        private final String _rtroot;
        private final String _dryadID;
        private final String _customerID;

        public DryadUploadTask(String rtroot, String dryadID, String customerID)
        {
            _rtroot = rtroot;
            _dryadID = dryadID;
            _customerID = customerID;
        }

        @Override
        public Void call()
                throws Exception
        {
            File archive = createLogsArchive(_rtroot);
            URL url = createResourceURL(_dryadID, _customerID, Cfg.user().getString(),
                    Cfg.did().toStringFormal());
            SSLContext ssl = createSSLContext();

            uploadFile(archive, url, ssl);

            FileUtil.deleteIgnoreErrorRecursively(archive);

            return null;
        }

        private File createLogsArchive(String rtroot)
                throws IOException
        {
            File archive = FileUtil.createTempFile("aerofs", ".zip", null);
            File[] logs = new File(rtroot).listFiles(new FilenameFilter()
            {
                @Override
                public boolean accept(File parent, String filename)
                {
                    return filename.contains(LibParam.LOG_FILE_EXT)
                            || filename.endsWith(LibParam.HPROF_FILE_EXT);
                }
            });

            OutputStream os = null;
            try {
                os = new FileOutputStream(archive);

                // TODO (AT): look into extracting this out of SVClient and into an utility class.
                SVClient.compress(logs, os);
            } finally {
                if (os != null) { os.close(); }
            }

            return archive;
        }

        private URL createResourceURL(String dryadID, String customerID, String username,
                String deviceID) throws MalformedURLException
        {
            return new URL(format("%s/v1.0/client/%s/%s/%s/%s/logs", "https://dryad.aerofs.com",
                    customerID, dryadID, username, deviceID));
        }

        private SSLContext createSSLContext()
                throws GeneralSecurityException, IOException
        {
            String dryadCertPath = new File(AppRoot.abs(), "dryad.pem").getAbsolutePath();
            ICertificateProvider certProvider = new FileBasedCertificateProvider(dryadCertPath);

            return new SSLEngineFactory(Mode.Client, Platform.Desktop, null, certProvider, null)
                    .getSSLContext();
        }

        private void uploadFile(File file, URL url, SSLContext ssl)
                throws Exception
        {
            FileInputStream is = null;
            try {
                is = new FileInputStream(file);
                long fileSize = file.length(),
                        uploaded = 0;

                // the intention here is to upload the archived logs by making multiple https
                // requests where each request contains at most CHUNK_SIZE bytes in the request
                // body.
                while (uploaded < fileSize) {
                    long chunkSize = Math.min(fileSize - uploaded, CHUNK_SIZE);

                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

                    // setup connection
                    conn.setSSLSocketFactory(ssl.getSocketFactory());
                    conn.setDoOutput(true);
                    conn.setUseCaches(false);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
                    conn.setRequestProperty(HttpHeaders.CONTENT_RANGE, format("bytes %d-%d/%d",
                            uploaded, uploaded + chunkSize - 1, fileSize));
                    conn.setRequestProperty(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunkSize));
                    conn.connect();

                    // send request body
                    OutputStream os = null;
                    try {
                        os = conn.getOutputStream();
                        ByteStreams.copy(ByteStreams.limit(is, chunkSize), os);
                    } finally {
                        if (os != null) { os.close(); }
                    }

                    // process response
                    int response = conn.getResponseCode();

                    if (response != HttpURLConnection.HTTP_OK
                            && response != HttpURLConnection.HTTP_NO_CONTENT) {
                        throw new Exception(format("Unexpected response code: %d", response));
                    }

                    uploaded += chunkSize;
                }
            } finally {
                if (is != null) { is.close(); }
            }
        }
    }
}
