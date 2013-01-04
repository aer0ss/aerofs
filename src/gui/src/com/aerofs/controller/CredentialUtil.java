/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.base.Base64;
import com.aerofs.base.id.DID;
import com.aerofs.lib.C;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.CertifyDeviceReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.ui.UI;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class CredentialUtil
{
    static void sendPasswordResetEmail(String userid)
            throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.sendPasswordResetEmail(userid);
    }

    static void resetPassword(UserID userId, String token, char[] password)
            throws Exception
    {
        byte[] scrypted = SecUtil.scrypt(password, userId);
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.resetPassword(token, ByteString.copyFrom(scrypted));
    }

    static void changePassword(UserID userID, char[] oldPassword, char[] newPassword)
        throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.signInRemote();
        byte[] oldScrypted = SecUtil.scrypt(oldPassword, userID);
        byte[] newScrypted = SecUtil.scrypt(newPassword, userID);
        sp.changePassword(ByteString.copyFrom(oldScrypted), ByteString.copyFrom(newScrypted));
        updateStoredPassword(userID, newPassword);
    }

    // updateStoredPassword updated the credentials stored locally.  This must be called
    // when a password is changed so the client can authenticate successfully.  It also
    // is called when a new password is provided (after ExBadCredentials).
    static void updateStoredPassword(UserID userId, char[] password)
            throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        byte[] scrypted = SecUtil.scrypt(password, userId);
        // use signIn instead of sign_in remote ( we haven't updated Cfg yet )
        sp.signIn(Cfg.user().toString(), ByteString.copyFrom(scrypted));

        CfgDatabase db = Cfg.db();
        writePrivateKey(scrypted, Cfg.privateKey());
        db.set(Key.CRED, SecUtil.scrypted2encryptedBase64(scrypted));

        Cfg.setPrivKeyAndScryptedUsingScrypted(scrypted);

        //restart the daemon to reload the new password.
        UI.dm().stop();
        UI.dm().start();
    }

    /**
     * Call this method only to setup a team server. After setup, the team server can use
     * certifyAndSaveDeviceKeys. See sp.proto:CertifyTeamServerDevice for detail.
     *
     * See certifyAndSaveDeviceKeys for the parameter list
     */
    static DID certifyAndSaveTeamServerDeviceKeys(UserID certUserId, byte[] scrypted,
            SPBlockingClient sp)
            throws Exception
    {
        return certifyAndSaveDeviceKeysImpl(certUserId, scrypted, sp, new ISPCertifyDeviceCaller()
        {
            @Override
            public CertifyDeviceReply call(SPBlockingClient sp, ByteString did, ByteString csr)
                    throws Exception
            {
                return sp.certifyTeamServerDevice(did, csr);
            }
        });
    }

    /**
     * @param sp must have signed in
     * @param certUserId used only to generate the certificate's CNAME, but not to sign in
     * @param scrypted used only to encrypt the private key but not to sign in
     */
    static DID certifyAndSaveDeviceKeys(UserID certUserId, byte[] scrypted, SPBlockingClient sp)
            throws Exception
    {
        return certifyAndSaveDeviceKeysImpl(certUserId, scrypted, sp, new ISPCertifyDeviceCaller()
        {
            @Override
            public CertifyDeviceReply call(SPBlockingClient sp, ByteString did, ByteString csr)
                    throws Exception
            {
                return sp.certifyDevice(did, csr, false);
            }
        });
    }

    private static DID certifyAndSaveDeviceKeysImpl(UserID certUserId, byte[] scrypted,
            SPBlockingClient sp, ISPCertifyDeviceCaller caller)
            throws Exception
    {
        KeyPair kp = SecUtil.newRSAKeyPair();
        DID did = new DID(UniqueID.generate());

        certifyAndSaveDeviceKeys(certUserId, did, kp.getPublic(), kp.getPrivate(), scrypted, sp,
                caller);
        return did;
    }

    private static interface ISPCertifyDeviceCaller
    {
        CertifyDeviceReply call(SPBlockingClient sp, ByteString did, ByteString csr)
                throws Exception;
    }

    private static void certifyAndSaveDeviceKeys(UserID certUserId, DID did, PublicKey pubKey,
            PrivateKey privKey, byte[] scrypted, SPBlockingClient sp, ISPCertifyDeviceCaller caller)
            throws Exception
    {
        byte[] csr = SecUtil.newCSR(pubKey, privKey, certUserId, did).getEncoded();
        String cert = caller.call(sp, did.toPB(), ByteString.copyFrom(csr)).getCert();

        // write encrypted private key
        writePrivateKey(scrypted, privKey);

        // write certificate
        File file = new File(Cfg.absRTRoot(), C.DEVICE_CERT);
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
        Base64.encodeToFile(bs, new File(Cfg.absRTRoot(), C.DEVICE_KEY).getPath());
    }
}
