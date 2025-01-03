package com.aerofs.ui.update;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.controller.IViewNotifier.Type;
import com.aerofs.defects.Defects;
import com.aerofs.gui.GUI;
import com.aerofs.gui.tray.TrayIcon.NotificationReason;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.Versions.CompareResult;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.configuration.EnterpriseCertificateProvider;
import com.aerofs.lib.ex.ExFileIO;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.OSArch;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIParam;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Properties;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Note: methods in this class might be called before Cfg is initialized (i.e. before setup is
 * done)
 */

public abstract class Updater
{
    public enum Status
    {
        NONE,
        ONGOING,
        APPLY,
        LATEST,
        ERROR,
        DISABLED
    }

    public static class UpdaterNotification
    {
        public final Status status;
        public final int progress;

        UpdaterNotification(Status s, int p)
        {
            status = s;
            progress = p;
        }
    }

    protected static final Logger l = Loggers.getLogger(Updater.class);

    private static final String INSTALLER_URL = getStringProperty("updater.installer.url");

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
        if (isBlank(INSTALLER_URL)) {
            l.info("Update is disabled.");

            // return a disabled updater if either URL is invalid
            Updater disabled = new DisabledUpdater();
            // N.B. do not use setUpdateStatus here because notifier is not ready yet.
            disabled._status = Status.DISABLED;
            return disabled;
        }

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

        SystemUtil.fatal("Unsupported OS Family or arch");

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

        File updateVersionFile = new File(Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR, ClientParam.UPDATE_VER));
        if (!updateVersionFile.exists()) {
            l.info("no pending updates found");
            setUpdateStatus(Status.ERROR, -1);
            return null;
        }

        String downloadedVersion = null;
        Status currentStatus = Status.ERROR;

        try {
            try (BufferedReader reader = new BufferedReader(new FileReader(updateVersionFile))) {
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
                    l.info("update exists ver:{} file:{}", knownVersion, _installationFilename);
                    currentStatus = Status.ONGOING;
                    downloadedVersion = knownVersion;
                }
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
        File f = new File(Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR, installationFilename));
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
            InjectableFile f = _factFile.create(Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR));
            f.deleteIgnoreErrorRecursively();
        }
    }

    /**
     * <strong>IMPORTANT:</strong> will restart AeroFS to apply the patch if its necessary (and
     * possible) to do so
     */
    public void onStartupFailed()
    {
        String downloadedVersion = checkAndDownload();
        if (downloadedVersion != null) applyUpdate(downloadedVersion, hasPermissions());
    }

    /**
     * <strong>IMPORTANT:</strong> will restart AeroFS to apply the patch if its necessary (and
     * possible) to do so
     */
    private void checkForUpdateImpl()
    {
        String downloadedVersion = checkAndDownload();

        // force update on all changes
        if (downloadedVersion != null) applyUpdate(downloadedVersion, true);
    }

    /**
     * Starts the updater thread that will periodically check for new updates
     */
    public void start()
    {
        ThreadUtil.startDaemonThread("updater", () -> {
            /*
            * run() is executed _after_ onStartup.
            * If a user clicks the skip button in onStartup->applyUpdate, then run() needs to
            * disable the menu icons, because it will remove the ~/.aerofs/update folder and
            * re-add it in an hour.
            */
            setUpdateStatus(Status.LATEST, -1);

            if (UI.isGUI() && GUI.get().st() != null) {
                GUI.get().st().getIcon().showNotification(NotificationReason.UPDATE, false);
            }

            ThreadUtil.sleepUninterruptable(UIParam.UPDATE_CHECKER_INITIAL_DELAY);

            //noinspection InfiniteLoopStatement
            while (true) {
                checkForUpdate(false);
                ThreadUtil.sleepUninterruptable(UIParam.UPDATE_CHECKER_INTERVAL);
            }
        });
    }

    public String getServerVersion()
            throws IOException
    {
        String versionURL = getStringProperty("updater.version.url", "https://nocache.client.aerofs.com/current.ver");

        try {
            l.debug("Reading version from {}", versionURL);

            final URL url = URI.create(versionURL).toURL();
            final URLConnection conn = newUpdaterConnection(url);
            final BufferedInputStream in = new BufferedInputStream(conn.getInputStream());

            final Properties props = new Properties();
            props.load(in);

            final String version = props.getProperty("Version");
            l.debug("Server Version Response " + version);

            return version;
        } catch (final IOException e) {
            l.error("Error reading version from {}", versionURL, e);
            Defects.newMetric("updater.readServerVersion")
                    .setException(e)
                    .sendAsync();
            throw e;
        }
    }

    /**
     * Download if a new version of the product is available on server.
     *
     * @return true if there's a new version downloaded.
     */
    private boolean downloadUpdate(final String installerUrl, String filename, String ver)
            throws IOException
    {
        assert filename != null;

        String dirName = Util.join(Cfg.absRTRoot(), "tmp");
        InjectableFile dir = _factFile.create(dirName);
        dir.mkdirIgnoreError(); // start by trying to create the temporary download folder

        try {
            // N.B. since we intended for installerUrl to be a path to a directory of installers,
            //   we need to add a trailing slash.
            // This affected private deployment where installerURL is "https://*/path_to_installers"
            URLConnection conn = newUpdaterConnection(
                    URI.create(installerUrl + '/' + filename).toURL());
            conn.setReadTimeout((int) Cfg.timeout());

            int updateFileExpectedSize = conn.getContentLength();
            l.info("update size: {}", updateFileExpectedSize);

            int downloadedFileSize = 0;
            String localFilePath = Util.join(dirName, filename);
            try (InputStream downloadStream = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(localFilePath)) {
                int bytesRead;
                int oldPercent = 0;
                byte[] buf = new byte[BaseUtil.FILE_BUF_SIZE];
                while ((bytesRead = downloadStream.read(buf)) >= 0) {
                    out.write(buf, 0, bytesRead);
                    downloadedFileSize += bytesRead;

                    _percentDownloaded = Math.round(
                            (downloadedFileSize / (float)updateFileExpectedSize) * 100);

                    if (oldPercent < _percentDownloaded) {
                        oldPercent = _percentDownloaded;
                        setUpdateStatus(Status.ONGOING, _percentDownloaded);
                    }
                }
            }

            // we should have downloaded the exact expected number of bytes; if not, assert
            // and in doing so delete the directory into which we downloaded the update
            if (downloadedFileSize != updateFileExpectedSize) {
                throw new EOFException();
            }

            // create the ./ver file
            File temp = new File(Util.join(dirName, ClientParam.UPDATE_VER));
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(temp))) {
                bw.write(ver);
            }

            // rename the tmp download directory to be the update directory

            dirName = Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR);
            InjectableFile updateDirFolder = _factFile.create(dirName);

            updateDirFolder.deleteOrThrowIfExistRecursively();

            if (!dir.moveInSameFileSystemIgnoreError(updateDirFolder)) {
                l.warn("download: could not rename tmp dir. hasPermission? {}", hasPermissions());

                throw new ExFileIO("download: could not rename tmp dir {} -> {}",
                        dir.getImplementation(), updateDirFolder.getImplementation());
            }

            // _installationFilename should be file name only, to be passed to Updater.
            _installationFilename = filename;
        } catch (Exception e) {
            l.error("Error downloading update from {}", installerUrl, e);
            Defects.newMetric("updater.downloadUpdate")
                    .setException(e)
                    .sendAsync();
            removeTempDownloadDirectory(dir);
            return false;
        }

        return true;
    }

    private void removeTempDownloadDirectory(InjectableFile dir)
    {
        // in the case of errors I think we should _always_ remove the tmp folder because we
        // don't know what condition it's in
        boolean deleted = dir.deleteIgnoreErrorRecursively();
        if (!deleted) {
            l.warn("could not delete temp folder:" + dir.toString());
        }
    }

    private static URLConnection newUpdaterConnection(URL url) throws IOException
    {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        conn.setConnectTimeout((int) Cfg.timeout());

        try {
            SSLEngineFactory factory = new SSLEngineFactory(Mode.Client, Platform.Desktop, null,
                    new EnterpriseCertificateProvider(), null);
            conn.setSSLSocketFactory(factory.getSSLContext().getSocketFactory());
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to use enterprise certificate.", e);
        }

        return conn;
    }

    public void checkForUpdate(boolean newThread)
    {
        // retrieve server update.properties
        l.debug("checking for update");

        synchronized (this) {
            if (_ongoing) return;
            _ongoing = true;
        }

        if (!newThread) {
            checkForUpdateImpl();
        } else {
            // user requested a check for update, let them know
            new Thread(this::checkForUpdateImpl, "update").start();
        }
    }

    /**
     * Checks for an update and downloads it if avalable.
     *
     * @return the version string of the downloaded update, or null if no update is available
     */
    @Nullable private String checkAndDownload()
    {
        l.debug("check and download update");

        _percentDownloaded = 0; //reset downloaded statistics
        setUpdateStatus(Status.ONGOING, -1); // signal that the process is ongoing

        try {
            String verServer = getServerVersion();
            CompareResult cr = verServer == null ? null : Versions.compare(Cfg.ver(), verServer);

            if (cr == null || cr == CompareResult.NO_CHANGE) {
                l.debug("no update available");

                setUpdateStatus(Status.LATEST, -1);
                return null;
            }

            if (!doesUpdateFileExist(verServer)) {
                // no update is available locally

                // remove the update directory if it exists
                _factFile.create(Util.join(Cfg.absRTRoot(), ClientParam.UPDATE_DIR))
                         .deleteOrThrowIfExistRecursively();

                // IMPORTANT: _installationFilename is set by checkForDownloadedUpdate()
                String installationFilename = createFilename(_installerFilenameFormat, verServer);
                if (!downloadUpdate(INSTALLER_URL, installationFilename, verServer)) {
                    throw new Exception("cannot download installer");
                }
            }

            l.info("updating to ver:{} file:{}", verServer, _installationFilename);

            return verServer;

        } catch (Exception e) {
            l.warn("update failed:", BaseLogUtil.suppress(e, UnknownHostException.class));
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
        checkNotNull(downloadedVersion);

        setUpdateStatus(Status.APPLY, -1);

        if (!hasPermissions()) {
            // updater doesn't have root permission
            confirmUpdate(downloadedVersion, force, false);
        } else {
            // updater has root permission and can actually execute
            if (!UI.isGUI() || !GUI.get().isOpen()) {
                // not GUI, or GUI window is't open --> can always apply update
                execUpdate(downloadedVersion, true);
            } else {
                confirmUpdate(downloadedVersion, force, true);
            }
        }

        if (UI.isGUI() && GUI.get().st() != null) {
            GUI.get().st().getIcon().showNotification(NotificationReason.UPDATE, true);
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
                    _skipUpdate = !((GUI)UI.get()).askWithDuration(MessageType.INFO,
                            S.IMPORTANT_UPDATE_DOWNLOADED + " Apply it now?\n" +
                                    "Skipping this version may cause " + L.product() +
                                    " to stop syncing with other computers.\n\n" +
                                    L.product() + " is going to update automatically" +
                                    " in %d seconds.", "Apply Update",
                            "Not Now (files may stop syncing)", duration);
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
            UI.get().show(MessageType.WARN, L.product() +
                                            " may shut down to apply an update. A new process will" +
                                            " be started in the background after the update.");
        }
        // NB: try to shutdown the daemon cleanly
        // If the daemon is killed halfway through applying changes the database will
        // most likely be fine however the filesystem could very well be in an unstable
        // intermediate state that will cause unexpected artifacts when the daemon is
        // restarted. For instance I once observed the creation of an duplicate folder
        // because the daemon was killed halfway through joining a shared folder, before
        // the tag file could be written, which resulted in the created folder later being
        // treated as a local change, moved out of the way and submitted to polaris.
        // While this specific case admits a potential solution (creating the folder in the
        // auxroot and writing the tag file there before moving the folder to the root anchor)
        // it is impractical to implement and this approach doesn't scale to more complex
        // operations.
        UIGlobals.dm().stopIgnoreException();
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

        UIGlobals.notifier().notify(Type.UPDATE, new UpdaterNotification(status, progress));
    }

    private boolean hasPermissions()
    {
        // AppRoot.abs() is "current/" subdir of min approot
        InjectableFile f = _factFile.create(AppRoot.abs()).getParentFile();
        f = f.newChild(".tmp" + Math.random());

        return (f.mkdirIgnoreError() && f.deleteIgnoreError());
    }

    private static String createFilename(String filenameFormat, String version)
    {
        return String.format(filenameFormat, version);
    }
}
