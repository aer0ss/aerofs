package com.aerofs.lib.cfg;

import com.aerofs.base.Base64;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Param;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.Versions;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
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
    private static boolean _useXMPP;
    private static boolean _useJingle;
    private static boolean _useZephyr;
    private static boolean _useAutoUpdate;
    private static boolean _isAggressiveCheckingEnabled;
    private static boolean _useHistory;
    private static boolean _useXFF;
    private static String _absDefaultRootAnchor;
    private static Map<SID, String> _absRoots;
    private static String _absDefaultAuxRoot;
    private static String _ver;
    private static X509Certificate _cert;
    private static PrivateKey _privKey;
    private static byte[] _scrypted;
    private static boolean _inited;
    private static int _portbase;
    private static @Nullable StorageType _storageType;

    private static final long _profilerStartingThreshold;
    private static final CfgDatabase _db = new CfgDatabase();

    // we have to set it before init_() because the setup process may need the value.
    private static long _timeout = _db.getLong(Key.TIMEOUT);

    static {
        long pst;
        try {
            Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), Param.PROFILER)));
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
    public static void init_(String rtRoot, boolean readPasswd)
            throws ExFormatError, IOException, ExBadCredential, SQLException, ExNotSetup
    {
        // initialize rtroot first so it's available even if the method failed later
        _absRTRoot = new File(rtRoot).getAbsolutePath();

        _db.init_();
        _db.reload();

        _user = UserID.fromInternal(_db.get(Key.USER_ID));
        _did = new DID(_db.get(Key.DEVICE_ID));
        if (readPasswd) readCreds();
        _rootSID = SID.rootSID(_user);

        _timeout = _db.getInt(Key.TIMEOUT);

        // We want to keep the user-specified path in the DB, but we need the canonical path to
        // watch for filesystem changes on OSX.
        File rootAnchor = new File(_db.get(Key.ROOT));
        assert rootAnchor.isAbsolute();
        _absDefaultRootAnchor = rootAnchor.getCanonicalPath();
        _absDefaultAuxRoot = absAuxRootForPath(_absDefaultRootAnchor, _rootSID);
        _storageType = StorageType.fromString(_db.getNullable(Key.STORAGE_TYPE));
        _absRoots = Maps.newHashMap();

        if (storageType() == StorageType.LINKED) {
            if (!L.isMultiuser()) {
                // upgrade schema if needed
                // NB: ideally this would have been done in a DPUT unfortunately Cfg is loaded before
                // DPUT are run so this is not a viable option...
                _db.createRootTableIfAbsent_(null);
                if (_db.getRoot(_rootSID) == null) {
                    _db.addRoot(_rootSID, _absDefaultRootAnchor);
                }
            }

            for (Entry<SID, String> e : _db.getRoots().entrySet()) {
                _absRoots.put(e.getKey(), new File(e.getValue()).getCanonicalPath());
            }
        }


        _portbase = readPortbase();
        _useDM = disabledByFile(rtRoot, Param.NODM);
        _useTCP = disabledByFile(rtRoot, Param.NOTCP);
        _useXMPP = disabledByFile(rtRoot, Param.NOXMPP);
        _useJingle = disabledByFile(rtRoot, Param.NOSTUN);
        _useZephyr = disabledByFile(rtRoot, Param.NOZEPHYR);
        _useAutoUpdate = disabledByFile(rtRoot, Param.NOAUTOUPDATE);
        _isAggressiveCheckingEnabled = enabledByFile(rtRoot, Param.AGGRESSIVE_CHECKS);
        _useHistory = disabledByFile(rtRoot,  Param.NOHISTORY);
        _useXFF = disabledByFile(rtRoot, Param.NOXFF);

        _inited = true;
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
        File file = new File(rtRoot, Param.PORTBASE);
        PrintStream ps = new PrintStream(new FileOutputStream(file));
        try {
            ps.println(portbase);
        } finally {
            ps.close();
        }
    }

    private static int readPortbase() throws IOException
    {
        Scanner s = new Scanner(new File(_absRTRoot, Param.PORTBASE));
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
                Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), Param.VERSION)));
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
                Param.AUXROOT_NAME + "." + sid.toStringFormal().substring(0, 6)).getAbsolutePath();
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

    // SP Daemon support is temporarily disabled. Search the code base for "SP_DID" and references
    // to Cfg.isSP() when restoring the function.
    public static boolean isSP()
    {
        return false;
    }

    public static boolean useDM()
    {
        return _useDM;
    }

    public static boolean useTCP()
    {
        return _useTCP;
    }

    public static boolean useXMPP()
    {
        return _useXMPP;
    }

    public static boolean useJingle()
    {
        return _useJingle;
    }

    public static boolean useZephyr()
    {
        return _useZephyr;
    }

    public static boolean isFullReplica()
    {
        return !isSP();
    }

    public static boolean useAutoUpdate()
    {
        return _useAutoUpdate;
    }

    public static boolean useHistory()
    {
        return _useHistory;
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
        return !new File(rtRoot, Param.NO_FS_TYPE_CHECK).exists();
    }

    public static int port(PortType type)
    {
        return _portbase + type.ordinal();
    }

    public static boolean lotsOfLog(String rtRoot)
    {
        return new File(Util.join(rtRoot, Param.LOL)).exists();
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

    public static boolean useArchive()
    {
        return !isSP() && !L.isStaging();
    }

    /**
     * @return absolute path to the anchor of the root store
     */
    public static String absDefaultRootAnchor()
    {
        return _absDefaultRootAnchor;
    }

    public static Map<SID, String> getRoots()
    {
        // need to create a copy to allow caller to make changes during iteration
        return Maps.newHashMap(_absRoots);
    }

    public static @Nullable String getRoot(SID sid)
    {
        return _absRoots.get(sid);
    }

    public static void addRoot(SID sid, String absPath) throws SQLException
    {
        assert !_absRoots.containsKey(sid) : sid + " " + absPath;
        _db.addRoot(sid, absPath);
        _absRoots.put(sid, absPath);
    }

    public static void removeRoot(SID sid) throws SQLException
    {
        assert _absRoots.containsKey(sid) : sid;
        _db.removeRoot(sid);
        _absRoots.remove(sid);
    }

    public static void moveRoot(SID sid, String newAbsPath) throws SQLException
    {
        assert _absRoots.containsKey(sid) : sid + " " + newAbsPath;
        _db.moveRoot(sid, newAbsPath);
        _absRoots.put(sid, newAbsPath);
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
        byte[] encryptedKey = Base64.decodeFromFile(absRTRoot() + File.separator + Param.DEVICE_KEY);
        _privKey = SecUtil.decryptPrivateKey(encryptedKey, pbePasswd);

        // do it after decryption has succeeded
        _scrypted = scrypted;
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

    public static X509Certificate cert() throws IOException, CertificateException
    {
        if (_cert == null) {
            InputStream in = new FileInputStream(absRTRoot() + File.separator + Param.DEVICE_CERT);
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                _cert = (X509Certificate) cf.generateCertificate(in);
            } finally {
                in.close();
            }
        }
        return _cert;
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

    @Nullable public static String absAutoExportFolder()
    {
        // It doesn't make sense to try to get the autoexport folder if there's no config loaded
        // to read.
        Preconditions.checkState(inited());
        String path = db().getNullable(Key.AUTO_EXPORT_FOLDER);
        if (path == null) return null;
        return new File(path).getAbsolutePath();
    }
}
