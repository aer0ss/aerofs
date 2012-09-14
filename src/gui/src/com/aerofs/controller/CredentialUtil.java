/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.lib.Base64;
import com.aerofs.lib.C;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SPClientFactory;
import com.aerofs.ui.UI;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class CredentialUtil
{
    private static final Logger l = Util.l(CredentialUtil.class);

    static void sendPasswordResetEmail(String userid)
            throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.sendPasswordResetEmail(userid);
    }

    static void resetPassword(String userId, String token, char[] password)
            throws Exception
    {
        byte[] scrypted = SecUtil.scrypt(password, userId);
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.resetPassword(token, ByteString.copyFrom(scrypted));
    }

    static void changePassword(String userID, char[] oldPassword, char[] newPassword)
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
    static void updateStoredPassword(String userID, char[] password)
            throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        byte[] scrypted = SecUtil.scrypt(password, userID);
        // use signIn instead of sign_in remote ( we haven't updated Cfg yet )
        sp.signIn(Cfg.user(), ByteString.copyFrom(scrypted));

        CfgDatabase db = Cfg.db();
        writePrivateKey(scrypted, Cfg.privateKey());
        db.set(Key.CRED, Cfg.scrypted2encryptedBase64(scrypted));

        Cfg.setPrivKeyAndScryptedUsingScrypted(scrypted);

        //restart the daemon to reload the new password.
        UI.dm().stop();
        UI.dm().start();
    }

    static DID generateDeviceKeys(String user, byte[] scrypted, SPBlockingClient sp)
            throws Exception
    {
        OutArg<PublicKey> pubKey = new OutArg<PublicKey>();
        OutArg<PrivateKey> privKey = new OutArg<PrivateKey>();
        SecUtil.newRSAKeyPair(pubKey, privKey);

        DID did = new DID(UniqueID.generate());
        while (true) {
            try {
                certifyAndWriteDeviceKeys(user, did, pubKey.get(), privKey.get(), scrypted, sp);
                break;
            } catch (ExAlreadyExist e) {
                l.info("device id " + did.toStringFormal() + " exists. generate a new one");
                did = new DID(UniqueID.generate());
            }
        }
        return did;
    }

    private static void certifyAndWriteDeviceKeys(String user, DID did, PublicKey pubKey,
            PrivateKey privKey, byte[] scrypted, SPBlockingClient sp)
            throws Exception
    {
        byte[] csr = SecUtil.newCSR(pubKey, privKey, user, did).getEncoded();

        String cert = sp.certifyDevice(did.toPB(), ByteString.copyFrom(csr), false).getCert();

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
        File rtRoot = new File(Cfg.absRTRoot());
        // Create a new key file with a random name
        File privateKeyTempFile = File.createTempFile("keyfile", null, rtRoot);
        // Set permissions to 600
        privateKeyTempFile.setReadable(false, false);   // chmod a-r
        privateKeyTempFile.setWritable(false, false);   // chmod a-w
        privateKeyTempFile.setExecutable(false, false); // chmod a-x
        privateKeyTempFile.setReadable(true, true);     // chmod +r
        privateKeyTempFile.setWritable(true, true);     // chmod +w
        // Write the key out
        Base64.encodeToFile(bs, privateKeyTempFile.getPath());
        // Set permissions to 400
        privateKeyTempFile.setWritable(false, false);     // chmod a-w
        // Move keyfile into final resting place
        File privateKeyFile = new File(rtRoot, C.DEVICE_KEY);
        privateKeyTempFile.renameTo(privateKeyFile);
    }
}
