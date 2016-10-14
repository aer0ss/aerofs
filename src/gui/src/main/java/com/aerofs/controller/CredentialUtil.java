/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.ClientSecUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sp.RegisterDeviceReply;
import com.aerofs.sp.client.SPBlockingClient;
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
    /**
     * Call this method only to setup a Team Server. After setup, the Team Server can use
     * registerDeviceAndSaveKeys. See sp.proto:CertifyTeamServerDevice for detail.
     *
     * See registerDeviceAndSaveKeys for the parameter list
     */
    static DID registerTeamServerDeviceAndSaveKeys(UserID certUserId,
            final String deviceName, SPBlockingClient sp)
            throws Exception
    {
        return certifyAndSaveDeviceKeysImpl(certUserId, (did, csr) -> {
            IOSUtil osu = OSUtil.get();
            return sp.registerTeamServerDevice(did, csr, osu.getOSFamily().getString(),
                    osu.getFullOSName(), deviceName, InterfacesUtil.getSystemInterfaces(), null);
        });
    }

    /**
     * @param sp must have signed in
     * @param certUserId used only to generate the certificate's CNAME, but not to sign in
     */
    static DID registerDeviceAndSaveKeys(UserID certUserId,
            final String deviceName, SPBlockingClient sp)
            throws Exception
    {
        return certifyAndSaveDeviceKeysImpl(certUserId, (did, csr)  -> {
            IOSUtil osu = OSUtil.get();
            return sp.registerDevice(did, csr, osu.getOSFamily().getString(),
                    osu.getFullOSName(), deviceName, InterfacesUtil.getSystemInterfaces(), null);
        });
    }

    public static void recertifyDevice(UserID certUserId, SPBlockingClient sp) throws Exception
    {
        String cert = certify(certUserId, Cfg.did(), Cfg.cert().getPublicKey(), Cfg.privateKey(),
                sp::recertifyDevice).getCert();
        writeCertificate(cert);
    }

    public static void recertifyTeamServerDevice(UserID certUserId, SPBlockingClient sp)
            throws Exception
    {
        String cert = certify(certUserId, Cfg.did(), Cfg.cert().getPublicKey(), Cfg.privateKey(),
                sp::recertifyTeamServerDevice).getCert();
        writeCertificate(cert);
    }

    private static DID certifyAndSaveDeviceKeysImpl(UserID certUserId,
            ISPCertifyDeviceCaller<RegisterDeviceReply> caller)
            throws Exception
    {
        KeyPair kp = BaseSecUtil.newRSAKeyPair();
        DID did = new DID(UniqueID.generate());

        String cert = certify(certUserId, did, kp.getPublic(), kp.getPrivate(), caller).getCert();

        // write private key
        writePrivateKey(kp.getPrivate());

        // write certificate
        writeCertificate(cert);
        return did;
    }

    private interface ISPCertifyDeviceCaller<T>
    {
        T call(ByteString did, ByteString csr) throws Exception;
    }

    private static <T> T certify(UserID certUserId, DID did, PublicKey pubKey, PrivateKey privKey,
            ISPCertifyDeviceCaller<T> caller)
            throws Exception
    {
        byte[] csr = ClientSecUtil.newCSR(pubKey, privKey, certUserId, did).getEncoded();
        return caller.call(BaseUtil.toPB(did), ByteString.copyFrom(csr));
    }

    private static void writeCertificate(String cert)
            throws IOException
    {
        File file = new File(Cfg.absRTRoot(), ClientParam.DEVICE_CERT);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file))) {
            writer.write(cert);
        }
    }

    public static void writePrivateKey(PrivateKey privKey)
            throws IOException, GeneralSecurityException {
        BaseSecUtil.writePrivateKey(privKey, Cfg.absRTRoot() + File.separator + ClientParam.DEVICE_KEY);
    }
}
