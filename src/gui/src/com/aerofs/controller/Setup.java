/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.base.Base64;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.controller.SetupModel.BackendConfig;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgKey;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.Icon;
import com.aerofs.proto.Sp.GetUserPreferencesReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UIGlobals;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.HashMap;

import static com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents.INSTALL_CLIENT;
import static com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents.REINSTALL_CLIENT;
import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.aerofs.defects.Defects.newDefectWithLogsNoCfg;
import static com.aerofs.lib.cfg.CfgDatabase.*;
import static com.google.common.base.Preconditions.*;
import static org.apache.commons.lang.StringUtils.isEmpty;

public class Setup
{
    private static final Logger l = Loggers.getLogger(Setup.class);
    private final String _rtRoot;
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    public Setup(String rtRoot)
    {
        _rtRoot = checkNotNull(rtRoot);
    }

    /**
     * @return the default anchor root that we suggest to use for AeroFS.
     */
    public static String getDefaultAnchorRoot()
    {
        String parent = OSUtil.get().getDefaultRootAnchorParent();
        return new File(parent, L.rootAnchorName()).getAbsolutePath();
    }

    /**
     * @return a default name for this device, that the user can accept or change
     */
    public static String getDefaultDeviceName()
    {
        try {
            String host = java.net.InetAddress.getLocalHost().getHostName();
            int dot = host.indexOf('.');
            return dot > 0 ? host.substring(0, dot) : host;
        } catch (UnknownHostException e) {
            return System.getProperty("user.name") + "'s Beloved Computer";
        }
    }

    /**
     * Runs setup for existing users.
     *
     * See setupNewUser's comments for more.
     */
    void setupSingleuser(
            SPBlockingClient client, UserID userId,
            String rootAnchorPath, String deviceName,
            StorageType storageType, BackendConfig backendConfig, boolean apiAccess)
            throws Exception
    {
        try {
            /**
             * Returning user & setup in a non-empty root-anchor is likely to be a reinstall (or a
             * "seeded" install which is pretty much equivalent for us). Log that event to determine
             * how badly we need to restore shared folders unpon reinstall.
             */
            boolean isReinstall = (_factFile.create(rootAnchorPath).list() != null);

            preSetup(rootAnchorPath, storageType);

            setupSingleUserImpl(userId, rootAnchorPath, deviceName, storageType, backendConfig,
                    client, apiAccess);

            UIGlobals.analytics().track(isReinstall ? REINSTALL_CLIENT : INSTALL_CLIENT);

        } catch (Exception e) {
            handleSetupException(userId, e);
        }
    }

    void setupMultiuser(
            SPBlockingClient client, UserID userId,
            String rootAnchorPath, String deviceName,
            StorageType storageType, BackendConfig backendConfig, boolean apiAccess) throws Exception
    {
        try {
            preSetup(rootAnchorPath, storageType);

            setupMultiuserImpl(userId, rootAnchorPath, deviceName, storageType, backendConfig, client,
                    apiAccess);

            // Send event for S3 Setup
            if (backendConfig != null && backendConfig._storageType == StorageType.S3) {
                UIGlobals.analytics().track(SimpleEvents.ENABLE_S3);
            }
            // Send event for Swift Setup
            if (backendConfig != null && backendConfig._storageType == StorageType.SWIFT) {
                UIGlobals.analytics().track(SimpleEvents.ENABLE_SWIFT);
            }
            UIGlobals.analytics().track(SimpleEvents.INSTALL_TEAM_SERVER);

        } catch (Exception e) {
            handleSetupException(userId, e);
        }
    }

    /**
     * Perform pre-setup sanity checks and generate information needed by later setup steps.
     */
    private void preSetup(
            String rootAnchorPath,
            StorageType storageType)
            throws Exception
    {
        checkArgument(!isEmpty(rootAnchorPath));

        FileUtil.ensureDirExists(new File(rootAnchorPath));

        RootAnchorUtil.checkRootAnchor(rootAnchorPath, _rtRoot, storageType, true);

        createSettingUpFlagFile();
    }

    /**
     * @param sp must have been signed in
     */
    private void setupSingleUserImpl(UserID userID,
                                     String rootAnchorPath,
                                     String deviceName,
                                     StorageType storageType,
                                     BackendConfig backendConfig,
                                     SPBlockingClient sp,
                                     boolean apiAccess) throws Exception
    {
        assert deviceName != null; // can be empty, but can't be null

        DID did = CredentialUtil.registerDeviceAndSaveKeys(userID, deviceName, sp);

        initializeConfiguration(userID, userID.getString(), did, rootAnchorPath, storageType,
                backendConfig, sp, apiAccess);

        setupCommon(rootAnchorPath);

        addToFavorite(rootAnchorPath);
    }

    private void setupMultiuserImpl(UserID userID, String rootAnchorPath, String deviceName,
            StorageType storageType, BackendConfig backendConfig, SPBlockingClient sp, boolean apiAccess)
            throws Exception
    {
        assert deviceName != null; // can be empty, but can't be null

        // Retrieve the Team Server user ID
        UserID tsUserId = UserID.fromInternal(sp.getTeamServerUserID().getId());

        DID tsDID = CredentialUtil.registerTeamServerDeviceAndSaveKeys(tsUserId,
                deviceName, sp);

        initializeConfiguration(tsUserId, userID.getString(), tsDID, rootAnchorPath, storageType,
                backendConfig, sp, apiAccess);

        setupCommon(rootAnchorPath);
    }

    private void setupCommon(String rootAnchorPath)
            throws Exception
    {
        initializeAndLaunchDaemon();

        // indicates that the user is fully setup
        _factFile.create(_rtRoot, ClientParam.SETTING_UP).deleteOrOnExit();

        // Proceed with AeroFS launch
        Launcher.launch(true);

        setRootAnchorIcon(rootAnchorPath);
    }

    private void handleSetupException(UserID userId, Exception e)
            throws Exception
    {
        UIGlobals.dm().stopIgnoreException();

        // Don't send SV defect for bad credentials
        if (!(e instanceof ExBadCredential)) {
            if (Cfg.inited()) {
                newDefectWithLogs("setup")
                        .setMessage("setup")
                        .setException(e)
                        .sendSyncIgnoreErrors();
            } else {
                newDefectWithLogsNoCfg("setup", userId, _rtRoot)
                        .setMessage("setup")
                        .setException(e)
                        .sendSyncIgnoreErrors();
            }
        }

        throw e;
    }

    /**
     * Create the setup flag file. Ignore errors if the file already exists.
     * This file is used to mark the completion of the set up so that we never run into a partially
     * set up system.
     */
    private void createSettingUpFlagFile()
            throws IOException
    {
        InjectableFile fSettingUp = _factFile.create(_rtRoot, ClientParam.SETTING_UP);
        try {
            fSettingUp.createNewFile();
        } catch (IOException e) {
            if (!fSettingUp.exists()) throw e; // ignore errors if file already exists
        }
    }

    private void initializeAndLaunchDaemon()
            throws Exception
    {
        // Clean up the running daemon if any. It is needed as the daemon process may lock files in
        // cache and aerofs.db.
        try {
            UIGlobals.dm().stop();
        } catch (IOException e) {
            l.warn("cleaning up the old daemon failed: ", e);
        }

        // Create default root anchor, if missing
        _factFile.create(Cfg.absDefaultRootAnchor()).ensureDirExists();

        // TODO: use a dot-prefixed default root for S3 storage to hide it on *nix
        // TODO: hide default root for S3 storage (it only contains the aux root)
        // NB: markHiddenSystemFile asserts on *nix if the fname does not start with a dot...

        // Cleanup aux root, if present, create if missing
        // NB: Linked TeamServer does not need a default aux root as each store is treated as an
        // external root
        if (!(L.isMultiuser() && Cfg.storageType() == StorageType.LINKED)) {
            File aux = RootAnchorUtil.cleanAuxRootForPath(Cfg.absDefaultRootAnchor(), Cfg.rootSID());
            FileUtil.ensureDirExists(aux);
            OSUtil.get().markHiddenSystemFile(aux.getAbsolutePath());
        }

        // Remove database file (the daemon will setup the schema if it detects a missing DB)
        InjectableFile fDB = _factFile.create(_rtRoot, ClientParam.CORE_DATABASE);
        fDB.deleteOrThrowIfExist();

        UIGlobals.dm().start();
    }

    /**
     * initialize the configuration database and the in-memory Cfg object
     */
    private void initializeConfiguration(UserID userId, String contactEmail, DID did,
            String rootAnchorPath, StorageType storageType, BackendConfig backendConfig,
            SPBlockingClient userSp, boolean apiAccess)
            throws Exception
    {
        HashMap<CfgKey, String> map = Maps.newHashMap();

        map.put(USER_ID, userId.getString());
        map.put(DEVICE_ID, did.toStringFormal());
        map.put(ROOT, rootAnchorPath);
        map.put(STORAGE_TYPE, storageType.name());
        map.put(CONTACT_EMAIL, contactEmail);
        map.put(LAST_VER, Cfg.ver());
        map.put(DAEMON_POST_UPDATES, Integer.toString(ClientParam.PostUpdate.DAEMON_POST_UPDATE_TASKS));
        if (new CfgUsePolaris().get()) {
            map.put(PHOENIX_CONVERSION, Integer.toString(ClientParam.PostUpdate.PHOENIX_CONVERSION_TASKS));
        }
        map.put(UI_POST_UPDATES, Integer.toString(ClientParam.PostUpdate.UI_POST_UPDATE_TASKS));
        map.put(SIGNUP_DATE, Long.toString(getUserSignUpDate(userSp)));
        map.put(REST_SERVICE, Boolean.toString(apiAccess));

        // Register the storage type
        if (backendConfig != null) {
            checkState(backendConfig._storageType == storageType);
            checkState(backendConfig._passphrase != null);
            map.put(STORAGE_ENCRYPTION_PASSWORD, Base64.encodeBytes(
                    ClientSecUtil.scrypt(backendConfig._passphrase.toCharArray(), userId)));
        }

        // Configure specific backends
        if (storageType == StorageType.S3) {
            map.put(S3_ENDPOINT, backendConfig._s3Config._endpoint);
            map.put(S3_BUCKET_ID, backendConfig._s3Config._bucketID);
            map.put(S3_ACCESS_KEY, backendConfig._s3Config._accessKey);
            map.put(S3_SECRET_KEY, backendConfig._s3Config._secretKey);
        }
        if (storageType == StorageType.SWIFT) {
            map.put(SWIFT_AUTHMODE, backendConfig._swiftConfig._authMode);
            map.put(SWIFT_URL, backendConfig._swiftConfig._url);
            map.put(SWIFT_USERNAME, backendConfig._swiftConfig._username);
            map.put(SWIFT_PASSWORD, backendConfig._swiftConfig._password);
            map.put(SWIFT_CONTAINER, backendConfig._swiftConfig._container);
            map.put(SWIFT_TENANT_ID, backendConfig._swiftConfig._tenantId);
            map.put(SWIFT_TENANT_NAME, backendConfig._swiftConfig._tenantName);
        }

        Cfg.recreateSchema_();
        Cfg.db().set(map);

        Cfg.writePortbase(_rtRoot, findPortBase());

        Cfg.init_(_rtRoot, true);
    }

    /**
     * Retrieve the user sign-up date from SP
     */
    private long getUserSignUpDate(SPBlockingClient sp)
            throws Exception
    {
        GetUserPreferencesReply reply = sp.getUserPreferences(null);
        return reply.getSignupDate();
    }

    /**
     * Since the operations in this method is not critical, and the users doesn't need to wait for
     * them before start using AeroFS, we put them into a separate thread.
     */
    private static void addToFavorite(final String rootAnchorPath)
    {
        ThreadUtil.startDaemonThread("gui-fav", () -> {
            try {
                OSUtil.get().addToFavorite(rootAnchorPath);
            } catch (Exception e) {
                l.warn("add to fav: ", e);
            }
        });
    }

    private static void setRootAnchorIcon(String rootAnchorPath)
    {
        if (OSUtil.isLinux()) return;

        // TODO use real dependency injection
        InjectableDriver dr = new InjectableDriver(OSUtil.get());
        dr.setFolderIcon(rootAnchorPath, OSUtil.get().getIconPath(Icon.RootAnchor));
    }

    /**
     * @throws IOException if unable to find a port due to any reason
     */
    private static int findPortBase()
            throws IOException
    {
        int portbase = L.defaultPortbase();

        // try 100 times only
        for (int i = 0; i < 100; i++) {
            if (isPortRangeAvailable(portbase)) return portbase;
            portbase += requiredPortCount();
        }

        throw new IOException("couldn't find available local ports");
    }

    private static boolean isPortRangeAvailable(int portbase)
            throws IOException
    {
        boolean available = true;
        for (int offset = 0; available && offset < requiredPortCount(); offset++) {
            try {
                ServerSocket ss = new ServerSocket(portbase + offset, 0, ClientParam.LOCALHOST_ADDR);
                ss.close();
            } catch (BindException e) {
                available = false;
            }
        }
        return available;
    }

    /**
     * Return the number of ports AeroFS requires. Add some room for future addition of new port
     * types.
     */
    private static int requiredPortCount()
    {
        return PortType.values().length + 5;
    }
}
