package com.aerofs.ui.update;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Properties;

import com.aerofs.controller.ControllerService;
import com.aerofs.lib.*;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerNotifications.Type;
import com.aerofs.proto.ControllerNotifications.UpdateNotification;
import com.aerofs.proto.ControllerNotifications.UpdateNotification.Builder;
import com.aerofs.proto.ControllerNotifications.UpdateNotification.Status;
import org.apache.log4j.Logger;

import com.aerofs.gui.GUI;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.Versions.CompareResult;
import com.aerofs.lib.os.OSUtil.OSArch;
import com.aerofs.ui.UI;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIParam;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

/**
 * Note: methods in this class might be called before Cfg is initialized (i.e. before setup is
 * done)
 */

// TODO (GS): This class should move to the controller package

public abstract class Updater
{
    protected static final Logger l = Util.l(Updater.class);

    private String _installationFilename = "";

    private boolean _ongoing = false;
    private boolean _confirmingUpdate;

    private boolean _skipUpdate = false;
    private int _percentDownloaded = 0;
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    private final String _installerFilenameFormat;
    private Status _status = Status.NONE;

    public static Updater getInstance_()
    {
        if (OSUtil.isOSX()) {
            return new OSXUpdater();
        } else if (OSUtil.isWindows()) {
            return new WindowsUpdater();
        } else if (OSUtil.isLinux()) {
            if (OSUtil.getOSArch() == OSArch.X86) {
                return new Linux32Updater();
            } else if (OSUtil.getOSArch() == OSArch.X86_64) {
                return new Linux64Updater();
            }
        }

        Util.fatal("Unsupported OS Family or arch");

        return null;
    }

    /**
     * @param installerFilenameFormat printf-style format of the installer filename; has a single
     * string parameter for the version
     */
    protected Updater(String installerFilenameFormat)
    {
        this._installerFilenameFormat = installerFilenameFormat;
    }

    /**
     * Update AeroFS
     *
     * @param installerFilename filename of the installer to use in the update
     * @param newVersion version number of the update
     * @param hasPermissions if AeroFS has permissions to make changes to the executable and other
     * AeroFS components
     */
    protected abstract void update(String installerFilename, String newVersion,
            boolean hasPermissions);

    /**
     * @return the version of the available update file (IMPORTANT: side effect: updates
     * _installationFilename); returns {@code null} if no update file is downloaded
     */
    @Nullable private String checkForDownloadedUpdate()
    {
        setUpdateStatus(Status.ONGOING, -1);

        l.info("checking for downloaded update");

        File updateVersionFile = new File(Util.join(Cfg.absRTRoot(), C.UPDATE_DIR, C.UPDATE_VER));
        if (!updateVersionFile.exists()) {
            l.info("no pending updates found");
            setUpdateStatus(Status.ERROR, -1);
            return null;
        }

        String downloadedVersion = null;
        Status currentStatus = Status.ERROR;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(updateVersionFile));
            try {
                String knownVersion = reader.readLine();
                if (knownVersion == null) {
                    l.warn("update version file is empty");
                    setUpdateStatus(Status.ERROR, -1);
                    return null;
                }

                CompareResult cr = Versions.compare(Cfg.ver(), knownVersion);

                if (cr == CompareResult.NO_CHANGE) {
                    currentStatus = Status.LATEST;
                } else if (doesUpdateFileExist(knownVersion)) {
                    l.info("update exists ver:" + knownVersion + " file:" + _installationFilename);
                    currentStatus = Status.ONGOING;
                    downloadedVersion = knownVersion;
                }
            } finally {
                reader.close();
            }
        } catch (FileNotFoundException e) {
            l.warn("update exists but file is not found"); // FIXME: how can this happen?
        } catch (IOException e) {
            l.warn("update version file exists, but could not be read");
        }

        setUpdateStatus(currentStatus, -1);

        return downloadedVersion;
    }

    private boolean doesUpdateFileExist(String wantedVersion)
    {
        String installationFilename = createFilename(_installerFilenameFormat, wantedVersion);
        File f = new File(Util.join(Cfg.absRTRoot(), C.UPDATE_DIR, installationFilename));
        if (f.exists()) {
            _installationFilename = installationFilename;
            return true;
        }

        return false;
    }

    /**
     * Executed once on startup to perform any pending patches
     * <strong>IMPORTANT:</strong> will restart AeroFS to apply the patch if conditions are met
     * (sorry - conditions are specified deep in the code, and I don't want to list them out here)
     */
    public void onStartup()
    {
        String downloadedVersion = checkForDownloadedUpdate();
        if (downloadedVersion != null) {
            applyUpdate(downloadedVersion, true);
        } else {
            // Remove the update folder if it exists, since the downloaded update version is either
            // already applied or there is no downloaded update.
            InjectableFile f = _factFile.create(Util.join(Cfg.absRTRoot(), C.UPDATE_DIR));
            f.deleteIgnoreErrorRecursively();
        }
    }

    /**
     * <strong>IMPORTANT:</strong> will restart AeroFS to apply the patch if its necessary (and
     * possible) to do so
     */
    public void onStartupFailed()
    {
        CheckAndDownloadResult cdr = checkAndDownload();
        if (cdr != null) applyUpdate(cdr._downloadedVersion, hasPermissions());
    }

    /**
     * <strong>IMPORTANT:</strong> will restart AeroFS to apply the patch if its necessary (and
     * possible) to do so
     */
    private void checkForUpdateImpl()
    {
        CheckAndDownloadResult cdr = checkAndDownload();

        // force update on all changes
        if (cdr != null) applyUpdate(cdr._downloadedVersion, true /* cdr._cr != CompareResult.BUILD_CHANGE*/);
    }

    /**
     * Starts the updater thread that will periodically check for new updates
     */
    public void start()
    {
        Util.startDaemonThread("autoupdate-worker", new Runnable()
        {
            @Override
            public void run()
            {
                /*
                * run() is executed _after_ onStartup.
                * If a user clicks the skip button in onStartup->applyUpdate, then run() needs to
                * disable the menu icons, because it will remove the ~/.aerofs/update folder and
                * re-add it in an hour.
                */
                setUpdateStatus(Status.LATEST, -1);

                if (UI.isGUI() && GUI.get().st() != null) {
                    GUI.get().st().getIcon().showNotification(false);
                }

                Util.sleepUninterruptable(UIParam.UPDATE_CHECKER_INITIAL_DELAY);

                while (true) {
                    checkForUpdate(false);
                    Util.sleepUninterruptable(UIParam.UPDATE_CHECKER_INTERVAL);
                }
            }
        });
    }

    public static String getServerVersion()
            throws IOException
    {
        URL url = new URL(SV.NOCACHE_DOWNLOAD_BASE + "/" + "current.ver");
        URLConnection conn = newAWSConnection(url);
        BufferedInputStream in = new BufferedInputStream(conn.getInputStream());

        Properties props = new Properties();
        props.load(in);

        return props.getProperty("Version");
    }

    /**
     * Download if a new version of the product is available on server.
     *
     * @return true if there's a new version downloaded.
     */
    private boolean downloadUpdate(String filename, String ver)
    {
        assert filename != null;

        String dirName = Util.join(Cfg.absRTRoot(), "tmp");
        InjectableFile dir = _factFile.create(dirName);
        dir.mkdirIgnoreError(); // start by trying to create the temporary download folder

        try {
            URLConnection conn = newAWSConnection(new URL(SV.DOWNLOAD_BASE + "/" + filename));
            conn.setReadTimeout((int) Cfg.timeout());

            int updateFileExpectedSize = conn.getContentLength();
            l.info("update size: " + updateFileExpectedSize);

            String localFilePath = Util.join(dirName, filename);
            InputStream downloadStream = conn.getInputStream();
            try {
                FileOutputStream out = new FileOutputStream(localFilePath);
                try {
                    int bytesRead = 0;
                    int downloadedFileSize = 0;
                    int oldPercent = 0;
                    byte[] buf = new byte[Param.FILE_BUF_SIZE];
                    while ((bytesRead = downloadStream.read(buf)) >= 0) {
                        out.write(buf, 0, bytesRead);
                        downloadedFileSize += bytesRead;

                        _percentDownloaded = Math.round(
                                (downloadedFileSize / (float) updateFileExpectedSize) * 100);

                        if (oldPercent < _percentDownloaded) {
                            oldPercent = _percentDownloaded;
                            setUpdateStatus(Status.ONGOING, _percentDownloaded);
                        }
                    }

                    // we should have downloaded the exact expected number of bytes; if not, assert
                    // and in doing so delete the directory into which we downloaded the update

                    assert downloadedFileSize == updateFileExpectedSize :
                            dir.deleteIgnoreErrorRecursively();
                } finally {
                    out.close();
                }
            } finally {
                downloadStream.close();
            }

            // create the ./ver file
            File temp = new File(Util.join(dirName, C.UPDATE_VER));
            BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
            try {
                bw.write(ver);
            } finally {
                bw.close();
            }

            // rename the tmp download directory to be the update directory

            dirName = Util.join(Cfg.absRTRoot(), C.UPDATE_DIR);
            InjectableFile updateDirFolder = _factFile.create(dirName);

            updateDirFolder.deleteOrThrowIfExistRecursively();

            if (!dir.moveInSameFileSystemIgnoreError(updateDirFolder)) {
                l.warn("download: could not rename tmp dir: " +
                       dir.getAbsolutePath() + "->" + updateDirFolder.getAbsolutePath());
                //test whether directory is readable or writeable
                // AAG FIXME: I'm almost 100% sure we should throw an exception here and let it
                // be cleaned up (or change installation filename to be the correct path)
            }

            // _installationFilename should be file name only, to be passed to Updater.
            _installationFilename = filename;

        } catch (Exception e) {
            l.warn("download err: " + Util.e(e));

            // in the case of errors I think we should _always_ remove the tmp folder because we
            // don't know what condition it's in
            boolean deleted = dir.deleteIgnoreErrorRecursively();
            if (!deleted) {
                l.warn("could not delete temp folder:" + dir.toString());
            }

            return false;
        }

        return true;
    }

    /**
     * Because the download URLs redirect (as CNAMEs) to AWS servers, which have their own SSL
     * certificate, we need to work around for cert verification to pass.
     *
     * HOWEVER, an attacker can still hijack the DNS and redirect the URLs to their own AWS servers.
     * The ultimate solution is to sign installer binaries.
     *
     * N.B. This method must be identical to the same method in downloader.Main
     */
    private static URLConnection newAWSConnection(URL url) throws IOException
    {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        conn.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {

                try {
                    X509Certificate[] x509 = session.getPeerCertificateChain();
                    for (int i = 0; i < x509.length; i++) {
                        String str = x509[i].getSubjectDN().toString();
                        // this is for URLs pointing to S3
                        if (str.startsWith("CN=*.s3.amazonaws.com")) return true;
                        // this is for URLs pointing to CloudFront
                        if (str.startsWith("CN=*.cloudfront.net")) return true;
                    }
                    l.warn("expected CN not found");
                    return false;
                } catch (SSLPeerUnverifiedException e) {
                    l.warn(Util.e(e));
                    return false;
                }
            }
        });

        return conn;
    }

    public void checkForUpdate(boolean newThread)
    {
        // retrieve server update.properties
        l.info("checking for update");

        synchronized (this) {
            if (_ongoing) return;
            _ongoing = true;
        }

        if (!newThread) {
            checkForUpdateImpl();
        } else {
            // user requested a check for update, let them know
            new Thread(new Runnable()
            {
                @Override
                public synchronized void run()
                {
                    checkForUpdateImpl();
                }
            }).start();
        }
    }

    // Note the caller needs to maintain the update status (setUpdateStatus())
    // if an update is found

    private class CheckAndDownloadResult
    {
        final String _downloadedVersion;
        @SuppressWarnings("unused")
        final CompareResult _cr;

        CheckAndDownloadResult(String downloadedVersion, CompareResult cr)
        {
            _downloadedVersion = downloadedVersion;
            _cr = cr;
        }
    }

    /**
     * @return {@code null} if no update is available, or no update can be downloaded
     */
    @Nullable private CheckAndDownloadResult checkAndDownload()
    {
        l.info("check and download update");

        _percentDownloaded = 0; //reset downloaded statistics
        setUpdateStatus(Status.ONGOING, -1); // signal that the process is ongoing

        try {
            String verServer = Updater.getServerVersion();
            CompareResult cr = verServer == null ? null : Versions.compare(Cfg.ver(), verServer);

            if (cr == null || cr == CompareResult.NO_CHANGE) {
                l.info("no update available");

                setUpdateStatus(Status.LATEST, -1);
                return null;
            }

            if (!doesUpdateFileExist(verServer)) {
                // no update is available locally

                // remove the update directory if it exists
                _factFile.create(Util.join(Cfg.absRTRoot(), C.UPDATE_DIR))
                         .deleteOrThrowIfExistRecursively();

                // IMPORTANT: _installationFilename is set by checkForDownloadedUpdate()
                String installationFilename = createFilename(_installerFilenameFormat, verServer);
                if (!downloadUpdate(installationFilename, verServer)) {
                    throw new Exception("cannot download installer");
                }
            }

            l.info("updating to ver:" + verServer + " file:" + _installationFilename);

            return new CheckAndDownloadResult(verServer, cr);

        } catch (Exception e) {
            l.warn("update failed: " + Util.e(e, UnknownHostException.class));
            setUpdateStatus(Status.ERROR, -1);
            return null;

        } finally {
            synchronized (this) { _ongoing = false; }
        }
    }

    /**
     * This method kills the AeroFS process
     * @param force set to {@code true} if you want the user to be prompted to apply the update,
     * (if we don't have permissions) {@code false} if the we don't want to prompt the user
     */
    private void applyUpdate(@Nonnull String downloadedVersion, boolean force)
    {
        assert downloadedVersion != null;

        setUpdateStatus(Status.APPLY, -1);

        boolean hasPermissions = hasPermissions();

        if (!hasPermissions) {
            // updater doesn't have root permission
            confirmUpdate(downloadedVersion, force, hasPermissions);
        } else {
            // updater has root permission and can actually execute
            if (!UI.isGUI() || !GUI.get().isOpen()) {
                // not GUI, or GUI window is't open --> can always apply update
                execUpdate(downloadedVersion, hasPermissions);
            } else {
                confirmUpdate(downloadedVersion, force, hasPermissions);
            }
        }

        if (UI.isGUI() && GUI.get().st() != null) {
            GUI.get().st().getIcon().showNotification(true);
        }
    }

    private void confirmUpdate(String newVersion, boolean force, boolean hasPermissions)
    {
        if (_skipUpdate) return;

        if (force) {
            if (_confirmingUpdate) return;

            _confirmingUpdate = true;

            try {
                try {
                    final long duration = 60;
                    _skipUpdate = !((GUI)UI.get())
                                     .ask(MessageType.INFO,
                                             S.IMPORTANT_UPDATE_DOWNLOADED + " Apply it now?\n" +
                                             "Skipping this version may cause " + S.PRODUCT +
                                             " to stop syncing with other computers.\n\n" +
                                             S.PRODUCT + " is automatically going " +
                                             "to update in %d seconds.\n",
                                             "Apply Update", "Not Now (files may stop syncing)",
                                             duration);
                    if (_skipUpdate) return;
                } catch (ExNoConsole e) {
                    UI.get()
                      .show(MessageType.WARN, "Could not confirm with the user. Force update.");
                }
                // call it before resetting _confirmingUpdate, as the method may
                // bring up more dialogs
                execUpdate(newVersion, hasPermissions);
            } finally {
                _confirmingUpdate = false;
            }
        }
    }

    private void execUpdate(String newVersion, boolean hasPermissions)
    {
        if (!UI.isGUI()) {
            UI.get().show(MessageType.WARN, S.PRODUCT +
                                            " may shut down to apply an update. A new process will" +
                                            " be started at the background after the update.");
        }
        update(_installationFilename, newVersion, hasPermissions);
    }

    public void execUpdateFromMenu()
    {
        assert UI.isGUI();

        String newVersion = checkForDownloadedUpdate();
        if (newVersion != null) {
            execUpdate(newVersion, hasPermissions());
        } else {
            l.warn("execUpdateFromMenu() was called, but no update was found");
        }
    }

    public Status getUpdateStatus()
    {
        return _status;
    }

    private void setUpdateStatus(Status status, int progress)
    {
        _status = (status == Status.ERROR || status == Status.LATEST) ? Status.NONE : status;

        Builder notification = UpdateNotification.newBuilder().setStatus(status);
        if (progress != -1) notification.setProgress(progress);
        ControllerService.get().notifyUI(Type.UPDATE_NOTIFICATION, notification.build());
    }

    private boolean hasPermissions()
    {
        InjectableFile f = _factFile.create(Util.join(AppRoot.abs(), ".tmp" + Math.random()));
        if (!f.mkdirIgnoreError()) return false;
        if (!f.deleteIgnoreError()) return false;
        return true;
    }

    private static String createFilename(String filenameFormat, String version)
    {
        return String.format(filenameFormat, version);
    }
}
