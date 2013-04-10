/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.TreeMap;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.labeling.L;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.os.OSUtil.Icon;
import com.aerofs.lib.rocklog.EventType;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import org.slf4j.Logger;

import com.aerofs.lib.Param;
import com.aerofs.lib.Param.PostUpdate;
import com.aerofs.base.BaseParam.SP;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.ControllerProto.PBS3Config;
import com.aerofs.proto.Sv;
import com.aerofs.ui.UI;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;

class Setup
{
    private static final Logger l = Loggers.getLogger(Setup.class);
    private final String _rtRoot;
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    public Setup(String rtRoot)
    {
        assert rtRoot != null;
        _rtRoot = rtRoot;
    }

    /**
     * @return the default anchor root that we suggest to use for AeroFS.
     */
    String getDefaultAnchorRoot()
    {
        String parent = L.isStaging() ? _rtRoot : OSUtil.get().getDefaultRootAnchorParent();
        return new File(parent, L.rootAnchorName()).getAbsolutePath();
    }

    /**
     * @return a default name for this device, that the user can accept or change
     */
    String getDefaultDeviceName()
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
    void setupSingleuser(UserID userId, char[] password, String rootAnchorPath, String deviceName,
            StorageType storageType, PBS3Config s3cfg)
            throws Exception
    {
        try {
            /**
             * Returning user & setup in a non-empty root-anchor is likely to be a reinstall (or a
             * "seeded" install which is pretty much equivalent for us). Log that event to determine
             * how badly we need to restore shared folders unpon reinstall.
             */
            if (_factFile.create(rootAnchorPath).list() != null) {
                SVClient.sendEventAsync(Type.REINSTALL);
                RockLog.newEvent(EventType.REINSTALL_CLIENT).sendAsync();
            }

            PreSetupResult res = preSetup(userId, password, rootAnchorPath, storageType);

            setupSingluserImpl(userId, rootAnchorPath, deviceName, storageType, s3cfg,
                    res._scrypted, res._sp);

            SVClient.sendEventSync(Sv.PBSVEvent.Type.SIGN_RETURNING, "");

            RockLog.newEvent(EventType.INSTALL_CLIENT).sendAsync();

        } catch (Exception e) {
            handleSetupException(userId, e);
        }
    }

    void setupMultiuser(UserID userId, char[] password, String rootAnchorPath, String deviceName,
            StorageType storageType, PBS3Config s3cfg)
            throws Exception
    {
        try {
            PreSetupResult res = preSetup(userId, password, rootAnchorPath, storageType);

            setupMultiuserImpl(userId, rootAnchorPath, deviceName, storageType, s3cfg, res._sp);

            // Send event for S3 Setup
            if (s3cfg != null) {
                SVClient.sendEventAsync(Sv.PBSVEvent.Type.S3_SETUP);
                RockLog.newEvent(EventType.ENABLE_S3).sendAsync();
            }
            RockLog.newEvent(EventType.INSTALL_TEAM_SERVER).sendAsync();

        } catch (Exception e) {
            handleSetupException(userId, e);
        }
    }

    private static class PreSetupResult
    {
        // scrypt'ed password
        byte[] _scrypted;

        // N.B. the returned sp has not signed in
        SPBlockingClient _sp;
    }

    /**
     * Perform pre-setup sanity checks and generate information needed by later setup steps
     */
    private PreSetupResult preSetup(UserID userID, char[] password, String rootAnchorPath,
            StorageType storageType)
            throws Exception
    {
        assert !rootAnchorPath.isEmpty();

        RootAnchorUtil.checkRootAnchor(rootAnchorPath, _rtRoot, storageType, true);

        createSettingUpFlagFile();

        PreSetupResult res = new PreSetupResult();
        res._scrypted = SecUtil.scrypt(password, userID);

        // When setting up Team Servers, the ID of the user who sets up the server is used with this
        // SP client to sign in. Since the default configurator is SSLURLConnectionConfigurator
        // which doesn't work with regular clients, we force a null connection configurator.
        res._sp = SPClientFactory.newBlockingClientWithNullConnectionConfigurator(userID);
        res._sp.signIn(userID.getString(), ByteString.copyFrom(res._scrypted));

        return res;
    }

    /**
     * @param sp must have been signed in
     */
    private void setupSingluserImpl(UserID userID, String rootAnchorPath, String deviceName,
            StorageType storageType, PBS3Config s3config, byte[] scrypted, SPBlockingClient sp)
            throws Exception
    {
        assert deviceName != null; // can be empty, but can't be null

        DID did = CredentialUtil.registerDeviceAndSaveKeys(userID, scrypted, deviceName, sp);

        initializeConfiguration(userID, did, rootAnchorPath, storageType, s3config, scrypted);

        setupCommon(rootAnchorPath);

        addToFavorite(rootAnchorPath);
    }

    private void setupMultiuserImpl(UserID userID, String rootAnchorPath, String deviceName,
            StorageType storageType, PBS3Config s3config, SPBlockingClient sp)
            throws Exception
    {
        assert deviceName != null; // can be empty, but can't be null

        // Retrieve the team server user ID
        UserID tsUserId = UserID.fromInternal(sp.getTeamServerUserID().getId());
        byte[] tsScrypted = SecUtil.scrypt(Param.MULTIUSER_LOCAL_PASSWORD, tsUserId);

        DID tsDID = CredentialUtil.registerTeamServerDeviceAndSaveKeys(tsUserId, tsScrypted,
                deviceName, sp);

        initializeConfiguration(tsUserId, tsDID, rootAnchorPath, storageType, s3config, tsScrypted);
        Cfg.db().set(Key.AUTO_EXPORT_FOLDER, rootAnchorPath);

        // sign in with the team server's user ID
        SPBlockingClient tsSP = SPClientFactory.newBlockingClient(tsUserId);
        tsSP.signInRemote();

        setupCommon(rootAnchorPath);

        Cfg.db().set(Key.MULTIUSER_CONTACT_EMAIL, userID.getString());
    }

    private void setupCommon(String rootAnchorPath)
            throws Exception
    {
        initializeAndLaunchDaemon();

        // indicates that the user is fully setup
        _factFile.create(_rtRoot, Param.SETTING_UP).deleteOrOnExit();

        // Proceed with AeroFS launch
        new Launcher(_rtRoot).launch(true);

        setRootAnchorIcon(rootAnchorPath);
    }

    private void handleSetupException(UserID userId, Exception e)
            throws Exception
    {
        UI.dm().stopIgnoreException();

        // Don't send SV defect for bad credentials
        if (!(e instanceof ExBadCredential)) {
            if (Cfg.inited()) {
                SVClient.logSendDefectSyncIgnoreErrors(true, "setup", e);
            } else {
                SVClient.logSendDefectSyncNoCfgIgnoreErrors(true, "setup", e, userId, _rtRoot);
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
        InjectableFile fSettingUp = _factFile.create(_rtRoot, Param.SETTING_UP);
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
            UI.dm().stop();
        } catch (IOException e) {
            l.warn("cleaning up the old daemon failed: " + Util.e(e));
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
        InjectableFile fDB = _factFile.create(_rtRoot, Param.CORE_DATABASE);
        fDB.deleteOrThrowIfExist();

        UI.dm().start();
    }

    /**
     * initialize the configuration database and the in-memory Cfg object
     */
    private void initializeConfiguration(UserID userId, DID did, String rootAnchorPath,
            StorageType storageType, PBS3Config s3config, byte[] scrypted)
            throws SQLException, IOException, ExFormatError, ExBadCredential, ExNotSetup
    {
        TreeMap<Key, String> map = Maps.newTreeMap();
        map.put(Key.USER_ID, userId.getString());
        map.put(Key.DEVICE_ID, did.toStringFormal());
        map.put(Key.CRED, SecUtil.scrypted2encryptedBase64(scrypted));
        map.put(Key.ROOT, rootAnchorPath);
        map.put(Key.STORAGE_TYPE, storageType.name());
        map.put(Key.LAST_VER, Cfg.ver());
        map.put(Key.DAEMON_POST_UPDATES, Integer.toString(PostUpdate.DAEMON_POST_UPDATE_TASKS));
        map.put(Key.UI_POST_UPDATES, Integer.toString(PostUpdate.UI_POST_UPDATE_TASKS));
        if (s3config != null) {
            map.put(Key.S3_BUCKET_ID, s3config.getBucketId());
            map.put(Key.S3_ACCESS_KEY, s3config.getAccessKey());
            map.put(Key.S3_SECRET_KEY, s3config.getSecretKey());
            map.put(Key.S3_ENCRYPTION_PASSWORD, s3config.getEncryptionKey());
        }

        CfgDatabase db = Cfg.db();
        db.recreateSchema_();
        db.set(map);

        if (storageType == StorageType.LINKED && !L.isMultiuser()) {
            // make sure the default root anchor is correctly set in the db
            // NB: this does not remove any existing external roots from the db
            SID sid = SID.rootSID(userId);
            db.removeRoot(sid);
            db.addRoot(sid, rootAnchorPath);
        }

        Cfg.writePortbase(_rtRoot, findPortBase());

        Cfg.init_(_rtRoot, true);
    }

    /**
     * Since the operations in this method is not critical, and the users doesn't need to wait for
     * them before start using AeroFS, we put them into a separate thread.
     */
    private static void addToFavorite(final String rootAnchorPath)
    {
        ThreadUtil.startDaemonThread("add-to-fav", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    OSUtil.get().addToFavorite(rootAnchorPath);
                } catch (Exception e) {
                    l.warn("add to fav: " + Util.e(e));
                }
            }
        });
    }

    private static void setRootAnchorIcon(String rootAnchorPath)
    {
        if (OSUtil.isLinux()) return;

        // TODO use real dependency injection
        InjectableDriver dr = new InjectableDriver();
        dr.setFolderIcon(rootAnchorPath, OSUtil.getIconPath(Icon.RootAnchor));
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
                ServerSocket ss = new ServerSocket(portbase + offset, 0, Param.LOCALHOST_ADDR);
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
