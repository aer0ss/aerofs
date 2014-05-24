/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.LaunchArgs;
import com.aerofs.base.Base64;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sp.RecertifyDeviceReply;
import com.aerofs.proto.Sp.RegisterDeviceReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UIGlobals;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newOneWayAuthClientFactory;

public class CredentialUtil
{
    // FIXME: tick tock, buddy. You're next. This method gonna go away.
    // updateStoredPassword updated the credentials stored locally.  This must be called
    // when a password is changed so the client can authenticate successfully.  It also
    // is called when a new password is provided (after ExBadCredentials).
    static public void updateStoredPassword(UserID userId, char[] password, LaunchArgs launchArgs)
            throws Exception
    {
        byte[] scrypted = SecUtil.scrypt(password, userId);

        newOneWayAuthClientFactory()
                .create()
                .signInUser(Cfg.user().getString(), ByteString.copyFrom(scrypted));

        CfgDatabase db = Cfg.db();
        writePrivateKey(scrypted, Cfg.privateKey());
        db.set(Key.CRED, SecUtil.scrypted2encryptedBase64(scrypted));

        Cfg.setPrivKeyAndScryptedUsingScrypted(scrypted);

        //restart the daemon to reload the new password.
        UIGlobals.dm().stop();
        UIGlobals.dm().start(launchArgs);
    }

    /**
     * Call this method only to setup a Team Server. After setup, the Team Server can use
     * registerDeviceAndSaveKeys. See sp.proto:CertifyTeamServerDevice for detail.
     *
     * See registerDeviceAndSaveKeys for the parameter list
     */
    static DID registerTeamServerDeviceAndSaveKeys(UserID certUserId, byte[] scrypted,
            final String deviceName, SPBlockingClient sp)
            throws Exception
    {
        return certifyAndSaveDeviceKeysImpl(certUserId, scrypted, sp, new ISPCertifyDeviceCaller()
        {
            @Override
            public RegisterDeviceReply call(SPBlockingClient sp, ByteString did, ByteString csr)
                    throws Exception
            {
                IOSUtil osu = OSUtil.get();
                return sp.registerTeamServerDevice(did, csr, osu.getOSFamily().getString(),
                        osu.getFullOSName(), deviceName, InterfacesUtil.getSystemInterfaces());
            }
        });
    }

    /**
     * @param sp must have signed in
     * @param certUserId used only to generate the certificate's CNAME, but not to sign in
     * @param scrypted used only to encrypt the private key but not to sign in
     */
    static DID registerDeviceAndSaveKeys(UserID certUserId, byte[] scrypted,
            final String deviceName, SPBlockingClient sp)
            throws Exception
    {
        return certifyAndSaveDeviceKeysImpl(certUserId, scrypted, sp, new ISPCertifyDeviceCaller()
        {
            @Override
            public RegisterDeviceReply call(SPBlockingClient sp, ByteString did, ByteString csr)
                    throws Exception
            {
                IOSUtil osu = OSUtil.get();
                return sp.registerDevice(did, csr, osu.getOSFamily().getString(),
                        osu.getFullOSName(), deviceName, InterfacesUtil.getSystemInterfaces());
            }
        });
    }

    public static void recertifyDevice(UserID certUserId, SPBlockingClient sp) throws Exception
    {
        String cert = recertify(certUserId, Cfg.did(), Cfg.cert().getPublicKey(), Cfg.privateKey(),
                sp, new ISPRecertifyDeviceCaller()
                {
                    @Override
                    public RecertifyDeviceReply call(SPBlockingClient sp, ByteString did,
                            ByteString csr)
                            throws Exception
                    {
                        return sp.recertifyDevice(did, csr);
                    }
                });
        writeCertificate(cert);
    }

    public static void recertifyTeamServerDevice(UserID certUserId, SPBlockingClient sp)
            throws Exception
    {
        String cert = recertify(certUserId, Cfg.did(), Cfg.cert().getPublicKey(), Cfg.privateKey(),
                sp, new ISPRecertifyDeviceCaller()
                {
                    @Override
                    public RecertifyDeviceReply call(SPBlockingClient sp, ByteString did,
                            ByteString csr)
                            throws Exception
                    {
                        return sp.recertifyTeamServerDevice(did, csr);
                    }
                });
        writeCertificate(cert);
    }

    private static DID certifyAndSaveDeviceKeysImpl(UserID certUserId, byte[] scrypted,
            SPBlockingClient sp, ISPCertifyDeviceCaller caller)
            throws Exception
    {
        KeyPair kp = SecUtil.newRSAKeyPair();
        DID did = new DID(UniqueID.generate());

        String cert = certify(certUserId, did, kp.getPublic(), kp.getPrivate(), sp, caller);

        // write encrypted private key
        writePrivateKey(scrypted, kp.getPrivate());

        // write certificate
        writeCertificate(cert);
        return did;
    }

    private static interface ISPCertifyDeviceCaller
    {
        RegisterDeviceReply call(SPBlockingClient sp, ByteString did, ByteString csr)
                throws Exception;
    }
    private static interface ISPRecertifyDeviceCaller
    {
        RecertifyDeviceReply call(SPBlockingClient sp, ByteString did, ByteString csr)
                throws Exception;
    }

    private static String certify(UserID certUserId, DID did, PublicKey pubKey, PrivateKey privKey,
            SPBlockingClient sp, ISPCertifyDeviceCaller caller)
            throws Exception
    {
        byte[] csr = SecUtil.newCSR(pubKey, privKey, certUserId, did).getEncoded();
        return caller.call(sp, did.toPB(), ByteString.copyFrom(csr)).getCert();
    }

    private static String recertify(UserID certUserId, DID did, PublicKey pubKey,
            PrivateKey privKey, SPBlockingClient sp, ISPRecertifyDeviceCaller caller)
            throws Exception
    {
        byte[] csr = SecUtil.newCSR(pubKey, privKey, certUserId, did).getEncoded();
        return caller.call(sp, did.toPB(), ByteString.copyFrom(csr)).getCert();
    }

    private static void writeCertificate(String cert)
            throws IOException
    {
        File file = new File(Cfg.absRTRoot(), LibParam.DEVICE_CERT);
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file));
        try {
            writer.write(cert);
        } finally {
            writer.close();
        }
    }

    private static void writePrivateKey(byte[] scrypted, PrivateKey privKey)
            throws GeneralSecurityException, IOException
    {
        char[] pbePass = Base64.encodeBytes(scrypted).toCharArray();
        byte[] bs = SecUtil.encryptPrivateKey(privKey, pbePass);
        Base64.encodeToFile(bs, new File(Cfg.absRTRoot(), LibParam.DEVICE_KEY).getPath());
    }

}
