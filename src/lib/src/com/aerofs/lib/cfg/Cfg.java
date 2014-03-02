package com.aerofs.lib.cfg;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.PostUpdate;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.Versions;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDatabaseParams;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.google.protobuf.LeanByteString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Map;
import java.util.Scanner;

/**
 * This class is unfriendly to dependency injection and should be eventually removed.
 * User Dynamic*Properties from ArrowConfiguration instead.
 */
public class Cfg
{
    public static enum PortType {
        FSI,
        RITUAL_NOTIFICATION,
        UI,
        UI_SINGLETON,
        RITUAL
    }

    private static String _absRTRoot;
    private static DID _did;
    private static UserID _user;
    private static SID _rootSID;
    private static boolean _useDM;
    private static boolean _useTCP;
    private static boolean _useJingle;
    private static boolean _useZephyr;
    private static boolean _useAutoUpdate;
    private static boolean _isAggressiveCheckingEnabled;
    private static boolean _useXFF;
    private static String _absDefaultRootAnchor;
    private static String _absDefaultAuxRoot;
    private static String _ver;
    private static X509Certificate _cert;
    private static X509Certificate _cacert;
    private static PrivateKey _privKey;
    private static LeanByteString _scrypted;
    private static boolean _inited;
    private static int _portbase;
    private static @Nullable StorageType _storageType;

    private static final long _profilerStartingThreshold;

    private static IDBCW _dbcw;
    private static CfgDatabase _db;
    private static RootDatabase _rdb;

    // default value might be needed before init_
    private static long _timeout = Long.parseLong(Key.TIMEOUT.defaultValue());

    static {
        long pst;
        try {
            Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), LibParam.PROFILER)));
            try {
                pst = Integer.parseInt(s.nextLine());
            } finally {
                s.close();
            }
        } catch (Exception e) {
            pst = 0;
        }
        _profilerStartingThreshold = pst;
    }

    /**
     * @param readPasswd enable passwd initialization only if needed as it's expensive
     * @throws ExNotSetup if the device.conf is not found
     */
    public static synchronized void init_(String rtRoot, boolean readPasswd)
            throws ExFormatError, IOException, ExBadCredential, SQLException, ExNotSetup,
            CertificateException
    {
        // initialize rtroot first so it's available even if the method failed later
        _absRTRoot = new File(rtRoot).getAbsolutePath();

        initDB_();
        _db.reload();

        _user = UserID.fromInternal(_db.get(Key.USER_ID));
        _did = new DID(_db.get(Key.DEVICE_ID));
        _rootSID = SID.rootSID(_user);

        _timeout = _db.getInt(Key.TIMEOUT);

        // We want to keep the user-specified path in the DB, but we need the canonical path to
        // watch for filesystem changes on OSX.
        File rootAnchor = new File(_db.get(Key.ROOT));
        assert rootAnchor.isAbsolute();
        _absDefaultRootAnchor = rootAnchor.getCanonicalPath();
        _absDefaultAuxRoot = absAuxRootForPath(_absDefaultRootAnchor, _rootSID);
        _storageType = StorageType.fromString(_db.getNullable(Key.STORAGE_TYPE));

        if (storageType() == StorageType.LINKED && !L.isMultiuser()) {
            // upgrade schema if needed
            // NB: ideally this would have been done in a DPUT unfortunately Cfg is loaded before
            // DPUT are run so this is not a viable option...
            if (_rdb.getRootNullable(_rootSID) == null) {
                _rdb.addRoot(_rootSID, _absDefaultRootAnchor);
            }
        }

        try {
            if (readPasswd) readCreds();
            readCert();
        } catch (FileNotFoundException e) {
            throw new ExNotSetup();
        }

        _portbase = readPortbase();
        _useDM = disabledByFile(rtRoot, LibParam.NODM);
        _useTCP = disabledByFile(rtRoot, LibParam.NOTCP);
        _useJingle = disabledByFile(rtRoot, LibParam.NOSTUN);
        _useZephyr = disabledByFile(rtRoot, LibParam.NOZEPHYR);
        _useAutoUpdate = disabledByFile(rtRoot, LibParam.NOAUTOUPDATE);
        _isAggressiveCheckingEnabled = enabledByFile(rtRoot, LibParam.AGGRESSIVE_CHECKS);
        _useXFF = disabledByFile(rtRoot, LibParam.NOXFF);

        _inited = true;
    }

    private static void readCert()
            throws CertificateException, IOException
    {
        String certFileName = absRTRoot() + File.separator + LibParam.DEVICE_CERT;
        _cert = (X509Certificate)BaseSecUtil.newCertificateFromFile(certFileName);
    }

    /**
     * Nop if this method has been called and succeeded before
     */
    private static void initDB_() throws SQLException
    {
        if (_dbcw == null) {
            _dbcw = DBUtil.newDBCW(new DatabaseParams());
            _dbcw.init_();

            _db = new CfgDatabase(_dbcw);
            _rdb = new RootDatabase(_dbcw);

            // the conf database cannot simply be updated in a DPUT or UPUT
            // this table is created during setup but clients that were installed also need it so
            // we create it during init if it's absent
            _rdb.createRootTableIfAbsent_(null);
        }
    }

    public static void recreateSchema_() throws SQLException
    {
        _db.recreateSchema_();
        _rdb.recreateSchema_();
    }

    private static class DatabaseParams implements IDatabaseParams
    {
        @Override
        public boolean isMySQL()
        {
            return false;
        }

        @Override
        public String url()
        {
            return "jdbc:sqlite:" + (Cfg.absRTRoot() + File.separator + LibParam.CFG_DATABASE);
        }

        @Override
        public boolean sqliteExclusiveLocking()
        {
            return false;
        }

        @Override
        public boolean sqliteWALMode()
        {
            return false;
        }

        @Override
        public boolean autoCommit()
        {
            return true;
        }
    }

    private static boolean disabledByFile(String rtRoot, String filename)
    {
        return !new File(rtRoot, filename).exists();
    }

    private static boolean enabledByFile(String rtRoot, String filename)
    {
        return new File(rtRoot, filename).exists();
    }

    /**
     * When possible, use injection provided by CfgModule instead of directly calling this method.
     */
    public static CfgDatabase db()
    {
        return _db;
    }

    public static boolean inited()
    {
        return _inited;
    }

    public static void writePortbase(String rtRoot, int portbase) throws IOException
    {
        File file = new File(rtRoot, LibParam.PORTBASE);
        PrintStream ps = new PrintStream(new FileOutputStream(file));
        try {
            ps.println(portbase);
        } finally {
            ps.close();
        }
    }

    private static int readPortbase() throws IOException
    {
        Scanner s = new Scanner(new File(_absRTRoot, LibParam.PORTBASE));
        try {
            return Integer.parseInt(s.nextLine());
        } finally {
            s.close();
        }
    }

    public static String ver()
    {
        if (_ver == null) {
            try {
                Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), LibParam.VERSION)));
                try {
                    _ver = s.nextLine();
                } finally {
                    s.close();
                }
            } catch (FileNotFoundException e) {
                _ver = Versions.ZERO;
            }
        }

        return _ver;
    }

    public static String absRTRoot()
    {
        return _absRTRoot;
    }

    /**
     * @return the absolute path to the aux root
     */
    public static String absDefaultAuxRoot()
    {
        return _absDefaultAuxRoot;
    }

    /**
     * Ideally the aux root should reside under the root it is associated with to simplify relocate
     * algorithm and allow using the root of a disk/partition as an external root
     *
     * In the meantime we place the aux root of each store at the same level as the physical root to
     * reduce the likelihood of problems arising form lack of permissions or non-atomicity of file
     * renaming
     */
    public static String absAuxRootForPath(String path, SID sid)
    {
        return new File(new File(path).getParentFile(),
                LibParam.AUXROOT_NAME + "." + sid.toStringFormal().substring(0, 6)).getAbsolutePath();
    }

    /**
     * deprecated. use CfgLocalDID with dependency injection instead
     */
    public static DID did()
    {
        return _did;
    }

    public static UserID user()
    {
        return _user;
    }

    /**
     * @return the user's root store id
     *
     * TODO move this method into multiplicity.singleuser package
     */
    public static SID rootSID()
    {
        return _rootSID;
    }

    public static StorageType defaultStorageType()
    {
        return L.isMultiuser()
                ? (_db.getNullable(Key.S3_BUCKET_ID) != null
                           ? StorageType.S3
                           : StorageType.LOCAL)
                : StorageType.LINKED;
    }

    public static StorageType storageType()
    {
        return _storageType != null ? _storageType : defaultStorageType();
    }

    public static boolean useDM()
    {
        return _useDM;
    }

    public static boolean useTCP()
    {
        return _useTCP;
    }

    public static boolean useJingle()
    {
        return _useJingle;
    }

    public static boolean useZephyr()
    {
        return _useZephyr;
    }

    public static boolean useAutoUpdate()
    {
        return _useAutoUpdate;
    }

    public static boolean isAggressiveCheckingEnabled()
    {
        return _isAggressiveCheckingEnabled;
    }

    public static boolean useTransferFilter()
    {
        return _useXFF;
    }

    public static boolean useFSTypeCheck(String rtRoot)
    {
        return !new File(rtRoot, LibParam.NO_FS_TYPE_CHECK).exists();
    }

    public static int port(PortType type)
    {
        Preconditions.checkState(_portbase != 0);
        return _portbase + type.ordinal();
    }

    public static boolean lotsOfLog(String rtRoot)
    {
        return new File(Util.join(rtRoot, LibParam.LOL)).exists();
    }

    public static boolean lotsOfLotsOfLog(String rtRoot)
    {
        return new File(Util.join(rtRoot, LibParam.LOLOL)).exists();
    }

    public synchronized static boolean recertify(String rtRoot)
    {
        File marker = new File(Util.join(rtRoot, LibParam.RECERT));
        boolean doRecertify = marker.exists();
        FileUtil.deleteOrOnExit(marker);
        return doRecertify;
    }

    public static boolean useProfiler()
    {
        return _profilerStartingThreshold != 0;
    }

    /**
     * @return 0 if profiling is disabled
     */
    public static long profilerStartingThreshold()
    {
        return _profilerStartingThreshold;
    }

    public static long timeout()
    {
        return _timeout;
    }

    /**
     * @return absolute path to the anchor of the root store
     */
    public static String absDefaultRootAnchor()
    {
        return _absDefaultRootAnchor;
    }

    public static Map<SID, String> getRoots() throws SQLException
    {
        return _rdb.getRoots();
    }

    public static @Nullable String getRootPathNullable(SID sid) throws SQLException
    {
        return _rdb.getRootNullable(sid);
    }

    static void addRoot(SID sid, String absPath) throws SQLException
    {
        _rdb.addRoot(sid, absPath);
    }

    static void removeRoot(SID sid) throws SQLException
    {
        _rdb.removeRoot(sid);
    }

    public static synchronized void moveRoot(SID sid, String newAbsPath) throws SQLException
    {
        try {
            _rdb.moveRoot(sid, newAbsPath);
        } catch (SQLException e) {
            // we do not tolerate exception when reading the config db
            throw new AssertionError(e);
        }
    }

    public static boolean hasPendingDPUT()
    {
        return _db.getInt(Key.DAEMON_POST_UPDATES) < PostUpdate.DAEMON_POST_UPDATE_TASKS;
    }

    //-------------------------------------------------------------------------
    //
    // IMPORTANT: SECURITY-RELATED FIELDS AND FUNCTIONS FOLLOW
    //
    //-------------------------------------------------------------------------

    // return null if neither password is set or setPrivateKey_() is called
    public static PrivateKey privateKey()
    {
        return _privKey;
    }

    /**
     * @return null if scrypt(p|u) is neither set, or if the bytes required
     * to derive it is not found in device.conf; return scrypt(p|u) otherwise
     */
    public static byte[] scrypted()
    {
        return _scrypted.getInternalByteArray();
    }

    public static ByteString scryptedPB()
    {
        return _scrypted;
    }

    private static void resetSecurityTokens()
    {
        _privKey = null;
        _scrypted = null;
    }

    /**
     * Sets the security tokens (i.e the bytes representing scrypt(p|u) and
     * private key)
     *
     * @param scrypted bytes representing scrypt(p|u)
     * @throws IOException if the key file doesn't exist
     * @throws com.aerofs.base.ex.ExBadCredential if we can't decrypt the private key
     */
    public static void setPrivKeyAndScryptedUsingScrypted(byte[] scrypted)
        throws IOException, ExBadCredential
    {
        // decrypt device_private_key using b64(scrypt(p|u))
        char[] pbePasswd = Base64.encodeBytes(scrypted).toCharArray();
        byte[] encryptedKey = Base64.decodeFromFile(absRTRoot() + File.separator + LibParam.DEVICE_KEY);
        _privKey = SecUtil.decryptPrivateKey(encryptedKey, pbePasswd);

        // do it after decryption has succeeded
        _scrypted = new LeanByteString(scrypted);
    }

    /*
     * scrypted = scrypt( password | username )
     * confdb[cred] = base64( AES_E[PBKDF2(PASSWD_PASSWD)]( scrypted | random ) )
     */
    private static void readCreds() throws ExBadCredential, IOException
    {
        String cred = _db.getNullable(Key.CRED);
        if (cred != null) {
            byte[] scrypted = SecUtil.encryptedBase642scrypted(cred);
            setPrivKeyAndScryptedUsingScrypted(scrypted);
        } else {
            resetSecurityTokens();
        }
    }

    /**
     * Get the device certificate.
     */
    public static X509Certificate cert()
    {
        return _cert;
    }

    public static X509Certificate cacert() throws IOException, CertificateException
    {
        if (_cacert == null) {
            InputStream in = PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT
                    ? new ByteArrayInputStream(
                    PrivateDeploymentConfig.BASE_CA_CERTIFICATE.getBytes())
                    : new FileInputStream(new File(AppRoot.abs(), LibParam.CA_CERT).getAbsolutePath());

            _cacert = (X509Certificate) BaseSecUtil.newCertificateFromStream(in);
        }
        return _cacert;
    }

    @Nonnull public static Map<Key, String> dumpDb()
    {
        assert inited();

        Map<Key, String> contents = Maps.newTreeMap();
        for (Key key : Key.values()) {
            // skip sensitive fields
            if (key == Key.CRED ||
                    key.keyString().startsWith("s3_") ||
                    key.keyString().startsWith("mysql_")) {
                continue;
            }

            String value = db().getNullable(key);
            if (value != null && !value.equals(key.defaultValue())) contents.put(key, value);
        }

        return contents;
    }
}
