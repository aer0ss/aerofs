package com.aerofs.lib.cfg;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.StorageType;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Map;

import static com.aerofs.lib.cfg.ICfgStore.*;

public class BaseCfg {

    private ICfgStore _store;
    private String _absRTRoot;
    private DID _did;
    private UserID _user;
    private String _absDefaultRootAnchor;
    private String _ver;
    private X509Certificate _cert;
    private X509Certificate _cacert;
    private boolean _inited;
    private @Nullable StorageType _storageType;

    private PrivateKey _privKey;

    // default value might be needed before init_
    private long _timeout = Long.parseLong(TIMEOUT.defaultValue());

    private static BaseCfg _instance = null;

    protected static BaseCfg initialize(String rtRoot)
            throws CertificateException, ExInvalidID, IOException, SQLException,
            ExBadCredential, ExNotSetup
    {
        if (_instance == null) {
            _instance = new BaseCfg(rtRoot);
        }
        return _instance;
    }

    public static BaseCfg getInstance()
    {
        assert _instance != null;
        return _instance;
    }

    private BaseCfg(String rtRoot) throws CertificateException, IOException
    {
        // initialize rtroot first so it's available even if the method failed later.
        _absRTRoot = new File(rtRoot).getAbsolutePath();
        _ver = CfgUtils.getVersion();
        _cacert = BaseSecUtil.newCertificateFromStream(CfgUtils.cacertReaderInputStream());
    }

    void initializeValuesFromConfigStore(ICfgStore store)
            throws ExInvalidID, IOException, CertificateException, ExNotSetup
    {
        _store = store;
        _user = UserID.fromInternal(store.get(USER_ID));
        _did = new DID(store.get(DEVICE_ID));
        _timeout = store.getLong(TIMEOUT);

        // We want to keep the user-specified path in the DB, but we need the canonical path to
        // watch for filesystem changes on OSX.
        File rootAnchor = new File(store.get(ROOT));
        assert rootAnchor.isAbsolute();
        _absDefaultRootAnchor = rootAnchor.getCanonicalPath();

        try {
            _cert = readCert();
        } catch (FileNotFoundException e) {
            throw new ExNotSetup();
        }

        _storageType = StorageType.fromString(store.getNullable(STORAGE_TYPE));
        _inited = true;
    }

    public void readPrivateKey(boolean readPasswd) throws IOException, GeneralSecurityException
    {
        if (readPasswd) {
            String key = absRTRoot() + File.separator + LibParam.DEVICE_KEY;
            _privKey = BaseSecUtil.newPrivateKeyFromFile(key);
        }
    }

    private X509Certificate readCert() throws CertificateException, IOException
    {
        String certFileName =  _absRTRoot + File.separator + LibParam.DEVICE_CERT;
        return  BaseSecUtil.newCertificateFromFile(certFileName);
    }

    public String absRTRoot() {
        return _absRTRoot;
    }

    public String absDefaultRootAnchor() {
        return _absDefaultRootAnchor;
    }

    public UserID user() {
        return _user;
    }

    public DID did() {
        return _did;
    }

    public X509Certificate cert() {
        return _cert;
    }

    public String ver() {
        return _ver;
    }

    public X509Certificate cacert() {
        return _cacert;
    }

    public boolean inited() {
        return _inited;
    }

    @SuppressWarnings("deprecation")
    @Nonnull
    public Map<CfgKey, String> dumpDB()
    {
        assert inited();
        Map<CfgKey, String> contents = Maps.newHashMap();

        for (CfgKey key: CfgKey.getAllConfigKeys()) {
            // skip sensitive fields
            if (key.keyString().equals(CRED.keyString()) ||
                    key.keyString().startsWith("s3_") ||
                    key.keyString().startsWith("mysql_")) {
                continue;
            }

            String value = _store.getNullable(key);
            if (value != null && !value.equals(key.defaultValue())) contents.put(key, value);
        }

        return contents;
    }

    @Nullable
    public StorageType storageType() {
        return _storageType;
    }

    public long timeout() {
        return _timeout;
    }

    public PrivateKey privateKey()
    {
        return _privKey;
    }

    public void setPrivateKey(PrivateKey privateKey)
    {
        _privKey = privateKey;
    }
}