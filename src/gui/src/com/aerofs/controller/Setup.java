/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.os.OSUtil.Icon;
import com.aerofs.proto.Sv.PBSVEvent.Type;
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
import com.aerofs.lib.cfg.CfgBuildType;
import com.aerofs.lib.cfg.CfgCoreDatabaseParams;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.db.CoreSchema;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.S3Schema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.spsv.InvitationCode;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SPClientFactory;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.ControllerProto.PBS3Config;
import com.aerofs.proto.Sv;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIParam;
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
        return new File(parent, S.ROOT_ANCHOR_NAME).getAbsolutePath();
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
     * Returns the name of the user invited in the signup code, or an empty string if it's a
     * batch invitation.
     *
     * @throws ExNotFound if the signup code was not found
     */
    String getInvitedUser(final String code)
            throws ExNotFound, Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        switch (InvitationCode.getType(code)) {
        case TARGETED_SIGNUP:
            return sp.resolveTargetedSignUpCode(code).getEmailAddress();
        case BATCH_SIGNUP:
            sp.verifyBatchSignUpCode(code);
            return "";
        default:
            throw new ExNotFound(S.INVITATION_CODE_NOT_FOUND);
        }
    }

    void setupExistingUser(String userId, char[] password, String rootAnchorPath,
            String deviceName, PBS3Config s3config)
            throws Exception
    {
        run(userId, password, rootAnchorPath, deviceName, true, null, null, null, s3config);
    }

    void setupNewUser(String userId, char[] password, String rootAnchorPath,
            String deviceName, String signUpCode, String firstName, String lastName,
            PBS3Config s3config)
            throws Exception
    {
        run(userId, password, rootAnchorPath, deviceName, false, signUpCode, firstName, lastName,
                s3config);
    }

    /**
     * Runs the setup
     *
     * @param returning whether we are signing-up an existing user or a new user
     * Note: all params after returning are ignored and should be null if returning is true
     *
     * @throws ExAlreadyExist if the desired anchor root already exists.
     * @throws ExNoPerm if we couldn't read/write to the anchor root
     */
    // TODO: needs organization
    // TODO: gui needs to handle case where a no-invite user has signed up already
    private void run(String userId, char[] password, String rootAnchorPath, String deviceName,
            boolean returning, @Nullable String signUpCode, @Nullable String firstName,
            @Nullable String lastName, @Nullable PBS3Config s3config)
            throws Exception
    {
        try {
            // basic preconditions - all of these should be enforced at the UI level
            assert !userId.isEmpty();
            // new sign ups must have a decent password length
            assert returning || password.length >= Param.MIN_PASSWD_LENGTH;
            assert !rootAnchorPath.isEmpty();
            assert deviceName != null; // can be empty, but can't be null
            if (!returning) {
                assert signUpCode != null; // can be empty, but can't be null
                assert !firstName.isEmpty();
                assert !lastName.isEmpty();
            }

            l.info("userId:" + userId + " returning:" + returning);
            RootAnchorUtil.checkRootAnchor(rootAnchorPath, _rtRoot, true);

            /**
             * Returning user & setup in a non-empty root-anchor is likely to be a reinstall (or a
             * "seeded" install which is pretty much equivalent for us). Log that event to determine
             * how badly we need to restore shared folders unpon reinstall.
             */
            if (returning && _factFile.create(rootAnchorPath).list() != null) {
                SVClient.sendEventAsync(Type.REINSTALL);
            }

            // Create the setup flag file. Ignore errors if the file already exists.
            // This file is used to mark the completion of the set up so that we
            // never run into a partially set up system.
            InjectableFile fSettingUp = _factFile.create(_rtRoot, C.SETTING_UP);
            try {
                fSettingUp.createNewFile();
            } catch (IOException e) {
                if (!fSettingUp.exists()) throw e; // ignore errors if file already exists
            }

            byte[] scrypted = SecUtil.scrypt(password, userId);

            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            if (!returning) {
                SPSignupHelper signupHelper = new SPSignupHelper(sp);
                signupHelper.signUp(userId, scrypted, signUpCode, firstName, lastName);
            }
            // always sign in, regardless of whether we had to sign up first or not
            sp.signIn(userId, ByteString.copyFrom(scrypted));

            DID did = CredentialUtil.generateDeviceKeys(userId, scrypted, sp);

            // initialize config database.
            CfgDatabase db = Cfg.db();
            db.recreateSchema_();
            TreeMap<CfgDatabase.Key, String> map = Maps.newTreeMap();
            map.put(CfgDatabase.Key.USER_ID, userId);
            map.put(CfgDatabase.Key.DEVICE_ID, did.toStringFormal());
            map.put(CfgDatabase.Key.CRED, Cfg.scrypted2encryptedBase64(scrypted));
            map.put(CfgDatabase.Key.ROOT, rootAnchorPath);
            map.put(CfgDatabase.Key.LAST_VER, Cfg.ver());
            map.put(CfgDatabase.Key.DAEMON_POST_UPDATES,
                    Integer.toString(PostUpdate.DAEMON_POST_UPDATE_TASKS));
            map.put(CfgDatabase.Key.UI_POST_UPDATES,
                    Integer.toString(PostUpdate.UI_POST_UPDATE_TASKS));
            if (s3config != null) {
                map.put(CfgDatabase.Key.S3_BUCKET_ID, s3config.getBucketId());
                map.put(CfgDatabase.Key.S3_ACCESS_KEY, s3config.getAccessKey());
                map.put(CfgDatabase.Key.S3_SECRET_KEY, s3config.getSecretKey());
                map.put(CfgDatabase.Key.S3_ENCRYPTION_PASSWORD, s3config.getEncryptionKey());
                map.put(CfgDatabase.Key.S3_DIR, s3config.getLocalDir());
            }
            db.set(map);

            Cfg.writePortbase(_rtRoot, findPortBase());

            Cfg.init_(_rtRoot, true);

            // clean up the running daemon if any. it is needed as the daemon
            // process may lock files in cache and aerofs.db
            try {
                UI.dm().stop();
            } catch (IOException e) {
                l.warn("cleaning up the old daemon failed: " + Util.e(e));
            }

            // clear auxiliary folders under the default auxroot so files from the previous install
            // aren't carried to the new install. we don't need to clear files under non-default
            // auxroot since they are scoped by device ids.
            for (C.AuxFolder af : C.AuxFolder.values()) {
                InjectableFile f = _factFile.create(Util.join(Cfg.absDefaultAuxRoot(), af._name));
                f.deleteOrThrowIfExistRecursively();
            }

            initializeCoreDatabase(s3config != null);

            // setup root anchor
            InjectableFile fRootAnchor = _factFile.create(Cfg.absRootAnchor());
            if (!fRootAnchor.exists()) fRootAnchor.mkdirs();

            UI.dm().start();

            // indicates that the user is fully setup
            fSettingUp.deleteOrOnExit();

            // Proceed with AeroFS launch
            new Launcher(_rtRoot).launch(true);

            runNonEssential(userId, did, deviceName, returning, sp);
        } catch (Exception e) {
            UI.dm().stopIgnoreException();
            if (!(e instanceof ExBadCredential)) { // Don't send SV defect for bad credentials
                if (Cfg.inited()) {
                    SVClient.logSendDefectSyncIgnoreError(true, "setup", e);
                } else {
                    SVClient.logSendDefectSyncNoCfgIgnoreError(true, "setup", e, userId, _rtRoot);
                }
            }

            throw e;
        }
    }

    /**
     * Perform the tasks whose errors can be ignored by the setup process. Users doesn't need to
     * wait for them before start using AeroFS, and therefore we put these tasks into a separate
     * thread.
     */
    private void runNonEssential(final String userId, final DID did, final String deviceName,
            final boolean returning, final SPBlockingClient sp)
    {
        Util.startDaemonThread("setup-non-essential", new Runnable()
        {
            @Override
            public void run()
            {

                try {
                    sp.setPreferences(null, null, did.toPB(), deviceName);
                } catch (Exception e) {
                    l.warn("set prefs: " + Util.e(e));
                }

                try {
                    OSUtil.get().addToFavorite(Cfg.absRootAnchor());
                } catch (Exception e) {
                    l.warn("add anchor root to os fav: " + Util.e(e));
                }

                setRootAnchorIcon();

                if (returning) {
                    SVClient.sendEventSync(Sv.PBSVEvent.Type.SIGN_RETURNING, "");
                } else {
                    SVClient.sendEventSync(Sv.PBSVEvent.Type.SIGN_UP, "id: " + userId);
                }
            }
        });
    }

    private void setRootAnchorIcon()
    {
        if (OSUtil.isLinux()) return;

        // TODO use real dependency injection
        InjectableDriver dr = new InjectableDriver();
        dr.setFolderIcon(Cfg.absRootAnchor(), OSUtil.getIconPath(Icon.RootAnchor));
    }

    private void initializeCoreDatabase(boolean s3setup)
            throws IOException, SQLException
    {
        // remove database file
        InjectableFile fDB = _factFile.create(_rtRoot, C.CORE_DATABASE);
        fDB.deleteOrThrowIfExist();

        // initialize database. TODO use dependency injection
        CfgBuildType cfgBuildType = new CfgBuildType();
        CfgCoreDatabaseParams cfgCoreDBParams = new CfgCoreDatabaseParams(Cfg.db(), cfgBuildType);
        IDBCW dbcw = DBUtil.newDBCW(cfgCoreDBParams);
        dbcw.init_();
        InjectableDriver dr = new InjectableDriver();
        try {
            Connection c = dbcw.getConnection();
            new CoreSchema(dbcw, dr).create_();
            c.commit();
            if (s3setup) new S3Schema(dbcw).create_();
        } finally {
            dbcw.fini_();
        }
    }



    /**
     * @throws IOException if unable to find a port due to any reason
     */
    private int findPortBase()
            throws IOException
    {
        int base = UIParam.DEFAULT_PORT_BASE;
        boolean error = false;

        // try 100 times only
        for (int i = 0; i < 100; i++) {
            for (int port = Cfg.minPort(base); port < Cfg.nextUnreservedPort(base); port++) {
                try {
                    ServerSocket ss = new ServerSocket(port, 0, C.LOCALHOST_ADDR);
                    ss.close();
                } catch (BindException e) {
                    base = Cfg.nextUnreservedPort(base);
                    error = true;
                    break;
                }
            }
            if (!error) return base;
            else error = false;
        }

        throw new IOException("couldn't find available local ports");
    }
}
