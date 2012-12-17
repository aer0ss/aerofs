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

import com.aerofs.labeling.L;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.id.UserID;
import com.aerofs.lib.os.OSUtil.Icon;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import com.aerofs.sp.common.InvitationCode.CodeType;
import org.apache.log4j.Logger;

import com.aerofs.lib.C;
import com.aerofs.lib.Param;
import com.aerofs.lib.Param.PostUpdate;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sp.common.InvitationCode;
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
    private static final Logger l = Util.l(Setup.class);
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
        String parent = Cfg.staging() ? _rtRoot : OSUtil.get().getDefaultRootAnchorParent();
        return new File(parent, L.get().rootAnchorName()).getAbsolutePath();
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
     * Return the name of the user invited in the signup code.
     *
     * @throws ExNotFound if the signup code was not found
     */
    String getInvitedUser(final String code) throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        if (InvitationCode.getType(code) == CodeType.TARGETED_SIGNUP) {
            return sp.resolveTargetedSignUpCode(code).getEmailAddress();
        } else {
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }
    }

    /**
     * Run setup for new users
     *
     * @throws ExAlreadyExist if the desired anchor root already exists.
     * @throws ExNoPerm if we couldn't read/write to the anchor root
     *
     * TODO: needs organization
     * TODO: gui needs to handle case where a no-invite user has signed up already
     */
    void setupNewUser(UserID userId, char[] password, String rootAnchorPath,
            String deviceName, String signUpCode, String firstName, String lastName,
            PBS3Config s3cfg)
            throws Exception
    {
        try {
            // basic preconditions - all of these should be enforced at the UI level
            // new sign ups must have a decent password length
            assert password.length >= Param.MIN_PASSWD_LENGTH;
            assert signUpCode != null; // can be empty, but can't be null
            assert !firstName.isEmpty();
            assert !lastName.isEmpty();

            PreSetupResult res = preSetup(userId, password, rootAnchorPath);

            // sign up the user
            FullName fullName = new FullName(firstName, lastName);
            new SignupHelper(res._sp).signUp(userId, res._scrypted, signUpCode, fullName);

            setupSingluser(userId, rootAnchorPath, deviceName, s3cfg, res._scrypted, res._sp);

            SVClient.sendEventSync(Sv.PBSVEvent.Type.SIGN_UP, "id: " + userId);
        } catch (Exception e) {
            handleSetupException(userId, e);
        }
    }

    /**
     * Runs setup for existing users.
     *
     * See setupNewUser's comments for more.
     */
    void setupExistingUser(UserID userId, char[] password, String rootAnchorPath, String deviceName,
            PBS3Config s3cfg)
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
            }

            PreSetupResult res = preSetup(userId, password, rootAnchorPath);

            setupSingluser(userId, rootAnchorPath, deviceName, s3cfg, res._scrypted, res._sp);

            SVClient.sendEventSync(Sv.PBSVEvent.Type.SIGN_RETURNING, "");

        } catch (Exception e) {
            handleSetupException(userId, e);
        }
    }

    void setupTeamServer(UserID userId, char[] password, String rootAnchorPath, String deviceName,
            PBS3Config s3cfg)
            throws Exception
    {
        try {
            PreSetupResult res = preSetup(userId, password, rootAnchorPath);

            setupMultiuser(userId, rootAnchorPath, deviceName, s3cfg, res._scrypted, res._sp);

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
    private PreSetupResult preSetup(UserID userID, char[] password, String rootAnchorPath)
            throws IOException, ExNoPerm, ExNotDir, ExAlreadyExist, ExUIMessage
    {
        assert !rootAnchorPath.isEmpty();

        RootAnchorUtil.checkRootAnchor(rootAnchorPath, _rtRoot, true);

        createSettingUpFlagFile();

        PreSetupResult res = new PreSetupResult();
        res._scrypted = SecUtil.scrypt(password, userID);
        res._sp = SPClientFactory.newBlockingClient(SP.URL, userID);

        return res;
    }

    /**
     * @param sp must have been signed in
     */
    private void setupSingluser(UserID userID, String rootAnchorPath, String deviceName,
            PBS3Config s3config, byte[] scrypted, SPBlockingClient sp)
            throws Exception
    {
        assert deviceName != null; // can be empty, but can't be null

        signIn(userID, scrypted, sp);

        DID did = CredentialUtil.certifyAndSaveDeviceKeys(userID, scrypted, sp);

        initializeConfiguration(userID, did, rootAnchorPath, s3config, scrypted);

        setupCommon(did, deviceName, rootAnchorPath, sp);

        addToFavorite(rootAnchorPath);
    }

    private void setupMultiuser(UserID userID, String rootAnchorPath, String deviceName,
            PBS3Config s3config, byte[] scrypted, SPBlockingClient sp)
            throws Exception
    {
        assert deviceName != null; // can be empty, but can't be null

        signIn(userID, scrypted, sp);

        // Retrieve the team server user ID
        UserID tsUserId = UserID.fromInternal(sp.getTeamServerUserID().getId());
        byte[] tsScrypted = SecUtil.scrypt(C.TEAM_SERVER_LOCAL_PASSWORD, tsUserId);

        DID tsDID = CredentialUtil.certifyAndSaveTeamServerDeviceKeys(tsUserId, tsScrypted, sp);

        initializeConfiguration(tsUserId, tsDID, rootAnchorPath, s3config, tsScrypted);

        // sign in with the team server's user ID
        SPBlockingClient tsSP = SPClientFactory.newBlockingClient(SP.URL, tsUserId);
        signIn(tsUserId, tsScrypted, tsSP);

        setupCommon(tsDID, deviceName, rootAnchorPath, tsSP);
    }

    private void setupCommon(DID did, String deviceName, String rootAnchorPath, SPBlockingClient sp)
            throws Exception
    {
        initializeAndLaunchDaemon();

        // indicates that the user is fully setup
        _factFile.create(_rtRoot, C.SETTING_UP).deleteOrOnExit();

        // Proceed with AeroFS launch
        new Launcher(_rtRoot).launch(true);

        setDeviceNameAndRootAnchorIcon(did, deviceName, rootAnchorPath, sp);
    }

    private static void signIn(UserID userId, byte[] scrypted, SPBlockingClient sp)
            throws Exception
    {
        sp.signIn(userId.toString(), ByteString.copyFrom(scrypted));
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
        InjectableFile fSettingUp = _factFile.create(_rtRoot, C.SETTING_UP);
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

        // Create Aux Root
        InjectableFile auxRoot = _factFile.create(Cfg.absAuxRoot());
        if (auxRoot.exists()) auxRoot.deleteOrThrowIfExistRecursively();
        auxRoot.mkdirs();
        OSUtil.get().markHiddenSystemFile(auxRoot.getAbsolutePath());

        // Remove database file (the daemon will setup the schema if it detects a missing DB)
        InjectableFile fDB = _factFile.create(_rtRoot, C.CORE_DATABASE);
        fDB.deleteOrThrowIfExist();

        // Create Root Anchor
        InjectableFile fRootAnchor = _factFile.create(Cfg.absRootAnchor());
        if (!fRootAnchor.exists()) fRootAnchor.mkdirs();

        UI.dm().start();
    }

    /**
     * initialize the configuration database and the in-memory Cfg object
     */
    private void initializeConfiguration(UserID userId, DID did, String rootAnchorPath,
            PBS3Config s3config, byte[] scrypted)
            throws SQLException, IOException, ExFormatError, ExBadCredential, ExNotSetup
    {
        TreeMap<Key, String> map = Maps.newTreeMap();
        map.put(Key.USER_ID, userId.toString());
        map.put(Key.DEVICE_ID, did.toStringFormal());
        map.put(Key.CRED, Cfg.scrypted2encryptedBase64(scrypted));
        map.put(Key.ROOT, rootAnchorPath);
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

        Cfg.writePortbase(_rtRoot, findPortBase());

        Cfg.init_(_rtRoot, true);
    }

    /**
     * Since the operations in this method is not critical, and the users doesn't need to wait for
     * them before start using AeroFS, we put them into a separate thread.
     */
    private static void setDeviceNameAndRootAnchorIcon(final DID did, final String deviceName,
            final String rootAnchorPath, final SPBlockingClient sp)
    {
        ThreadUtil.startDaemonThread("setup-non-essential", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    sp.setPreferences(null, null, did.toPB(), deviceName);
                } catch (Exception e) {
                    l.warn("set prefs: " + Util.e(e));
                }

                setRootAnchorIcon(rootAnchorPath);
            }
        });
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
        int portbase = L.get().defaultPortbase();

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
                ServerSocket ss = new ServerSocket(portbase + offset, 0, C.LOCALHOST_ADDR);
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
