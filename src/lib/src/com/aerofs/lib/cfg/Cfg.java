package com.aerofs.lib.cfg;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.PostUpdate;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDatabaseParams;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.os.OSUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Map;
import java.util.Scanner;

import static com.aerofs.lib.cfg.CfgDatabase.DAEMON_POST_UPDATES;
import static com.aerofs.lib.cfg.CfgDatabase.PHOENIX_CONVERSION;
import static com.aerofs.lib.cfg.ICfgStore.CRED;
import static com.aerofs.lib.cfg.ICfgStore.ROOT;
import static com.aerofs.lib.cfg.ICfgStore.S3_BUCKET_ID;

/**
 * This class is unfriendly to dependency injection and should be eventually removed.
 * User Dynamic*Properties from ArrowConfiguration instead.
 *
 * This class is initialized before the configuration system is initialized. Therefore, this class
 * may not have static final configuration properties.
 */
public class Cfg
{
    public static enum PortType {
        @Deprecated FSI,
        @Deprecated RITUAL_NOTIFICATION,
        @Deprecated UI,
        UI_SINGLETON,
        @Deprecated RITUAL
    }

    public static enum NativeSocketType {
        RITUAL ("ritual"),
        RITUAL_NOTIFICATION ("rns"),
        SHELLEXT ("shellext");

        private String fileName;

        NativeSocketType(String fileName)
        {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }

    private static BaseCfg _baseCfg;

    private static boolean _useDM;
    private static boolean _useAutoUpdate;
    private static boolean _useXFF;
    private static String _absDefaultAuxRoot;
    private static boolean _inited;
    private static int _portbase;

    private static final long _profilerStartingThreshold;

    private static IDBCW _dbcw;
    private static CfgDatabase _db;
    private static RootDatabase _rdb;

    static {
        long pst;
        try {
            try (Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), LibParam.PROFILER)))) {
                pst = Integer.parseInt(s.nextLine());
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
            throws ExInvalidID, IOException, ExBadCredential, SQLException, ExNotSetup,
            GeneralSecurityException
    {
        BaseCfg.initialize(rtRoot);
        _baseCfg = BaseCfg.getInstance();

        initDB_();
        _db.reload();
        _baseCfg.initializeValuesFromConfigStore(_db);
        // always enable verbose logging for internal users to ensure bug reports are useful
        if (_baseCfg.user().isAeroFSUser() && !LogUtil.isVerbose()) {
            LogUtil.setLevel(Level.DEBUG);
        }
        // We want to keep the user-specified path in the DB, but we need the canonical path to
        // watch for filesystem changes on OSX.
        File rootAnchor = new File(_db.get(ROOT));
        assert rootAnchor.isAbsolute();

        if (_baseCfg.storageType() == StorageType.LINKED && !L.isMultiuser()) {
            // upgrade schema if needed
            // NB: ideally this would have been done in a DPUT unfortunately Cfg is loaded before
            // DPUT are run so this is not a viable option...
            if (_rdb.getRootNullable(_baseCfg.rootSID()) == null) {
                _rdb.addRoot(_baseCfg.rootSID(), _baseCfg.absDefaultRootAnchor());
            }
        }

        try {
            try {
                _baseCfg.readPrivateKey(readPasswd);
            } catch (IOException e) {
                Loggers.getLogger(Cfg.class).info("convert private key");
                // NB: We cannot use a DPUT or UPUT to perform key decryption because configuration
                // is loaded before either of them are run  so we need to do an ad-hoc upgrade path
                // when configuration is loaded.
                // The updater kills the daemon and the UI must load the config before it can restart
                // it so there shouldn't be room for any race conditions
                // TODO: remove conversion code and deprecated deps once support deems it acceptable,
                // presumably in about 1 year to give old customers ample time to upgrade so sometimes
                // in July 2016
                if (!convertPrivateKey()) throw e;
            }
        } catch (FileNotFoundException|GeneralSecurityException e) {
            throw new ExNotSetup();
        }

        _portbase = readPortbase();
        _useDM = CfgUtils.disabledByFile(rtRoot, LibParam.NODM);
        _useAutoUpdate = CfgUtils.disabledByFile(rtRoot, LibParam.NOAUTOUPDATE);
        _useXFF = CfgUtils.disabledByFile(rtRoot, LibParam.NOXFF);

        _inited = true;
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
        try (PrintStream ps = new PrintStream(new FileOutputStream(file))) {
            ps.println(portbase);
        }
    }

    private static int readPortbase() throws IOException
    {
        try (Scanner s = new Scanner(new File(_baseCfg.absRTRoot(), LibParam.PORTBASE))) {
            return Integer.parseInt(s.nextLine());
        }
    }

    public static String ver()
    {
        return _baseCfg.ver();
    }


    public static String absRTRoot()
    {
        return _baseCfg.absRTRoot();
    }

    /**
     * @return the absolute path to the aux root
     */
    public static String absDefaultAuxRoot()
    {
        return _baseCfg.absDefaultAuxRoot();
    }

    /**
     * deprecated. use CfgLocalDID with dependency injection instead
     */
    public static DID did()
    {
        return _baseCfg.did();
    }

    public static UserID user()
    {
        return _baseCfg.user();
    }

    /**
     * @return the user's root store id
     *
     * TODO move this method into multiplicity.singleuser package
     */
    public static SID rootSID()
    {
        return _baseCfg.rootSID();
    }

    public static StorageType defaultStorageType()
    {
        return L.isMultiuser()
                ? (_db.getNullable(S3_BUCKET_ID) != null
                    ? StorageType.S3
                        : StorageType.LOCAL)
                : StorageType.LINKED;
    }

    public static StorageType storageType()
    {
        return _baseCfg.storageType() != null ? _baseCfg.storageType() : defaultStorageType();
    }

    public static boolean useDM()
    {
        return _useDM;
    }

    public static boolean useAutoUpdate()
    {
        return _useAutoUpdate;
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

    public static String nativeSocketFilePath(NativeSocketType type)
    {
        Preconditions.checkState(_baseCfg.absRTRoot() != null);

        // In Windows all named pipes are created under a special path i.e. \\.\pipe\.
        // Hence to differentiate between different users we need to add the user name to the pipe
        // name. We further add the type i.e. ritual/rns/shellext to the pipe name and
        // "ts" or "single" to differentiate between the different types of AeroFS clients a
        // user might have. The combination of these gives us a unique windows named pipe file path
        // per user per client type.

        // In OSX/Linux the unix domain socket files are created under the user's rtroot.
        // Hence, as they are created under seperate folders we don't need to add the user name
        // to socket file's name. The socket file only consists of the socket type with the
        // extension .sock.

        if (OSUtil.isWindows()) {
            String parentDir = "\\\\.\\pipe";
            String userName =  System.getProperty("user.name");
            String clientType = L.isMultiuser() ? "ts" : "single";
            return Util.join(parentDir, Joiner.on("_").join(userName, type.getFileName(), clientType));
        } else if (NativeSocketType.SHELLEXT.equals(type)
                && OSUtil.isOSXYosemiteOrNewer()) {
            return Util.join(System.getProperty("user.home"),
                    "/Library/Containers/com.aerofs.finder.sync/Data/",
                    type.getFileName() + ".sock");
        } else {
            return Util.join(_baseCfg.absRTRoot(), type.getFileName() + ".sock");
        }
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

    public static long timeout() {
        return _baseCfg.timeout();
    }

    /**
     * @return absolute path to the anchor of the root store
     */
    public static String absDefaultRootAnchor()
    {
        return _baseCfg.absDefaultRootAnchor();
    }

    public static RootDatabase rootDB()
    {
        return _rdb;
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
        return _db.getInt(DAEMON_POST_UPDATES) < PostUpdate.DAEMON_POST_UPDATE_TASKS
                || (new CfgUsePolaris().get()
                        && _db.getInt(PHOENIX_CONVERSION) < PostUpdate.PHOENIX_CONVERSION_TASKS);
    }

    //-------------------------------------------------------------------------
    //
    // IMPORTANT: SECURITY-RELATED FIELDS AND FUNCTIONS FOLLOW
    //
    //-------------------------------------------------------------------------

    // return null if neither password is set or setPrivateKey_() is called
    public static PrivateKey privateKey()
    {
        return _baseCfg.privateKey();
    }

    /**
     * Sets the security tokens (i.e the bytes representing scrypt(p|u) and
     * private key)
     *
     * @param scrypted bytes representing scrypt(p|u)
     * @throws IOException if the key file doesn't exist
     * @throws com.aerofs.base.ex.ExBadCredential if we can't decrypt the private key
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static void setPrivKeyAndScryptedUsingScrypted(byte[] scrypted)
        throws IOException, ExBadCredential
    {
        // decrypt device_private_key using b64(scrypt(p|u))
        char[] pbePasswd = Base64.encodeBytes(scrypted).toCharArray();
        byte[] encryptedKey = Base64.decodeFromFile(absRTRoot() + File.separator + LibParam.DEVICE_KEY_ENCRYPTED);
        _baseCfg.setPrivateKey(SecUtil.decryptPrivateKey(encryptedKey, pbePasswd));
    }

    @SuppressWarnings("deprecation")
    private static boolean convertPrivateKey()
            throws ExBadCredential, IOException, GeneralSecurityException {
        String newKey = absRTRoot() + File.separator + LibParam.DEVICE_KEY;
        String oldKey = absRTRoot() + File.separator + LibParam.DEVICE_KEY_ENCRYPTED;

        String cred = _db.getNullable(CRED);
        if (cred == null || !(new File(oldKey).exists())) return false;

        byte[] scrypted = BaseSecUtil.encryptedBase642scrypted(cred);
        setPrivKeyAndScryptedUsingScrypted(scrypted);

        BaseSecUtil.writePrivateKey(privateKey(), newKey);

        new File(oldKey).delete();

        try {
            _db.set(CRED, null);
        } catch (SQLException e) {
            Loggers.getLogger(Cfg.class).warn("failed to reset scrypted cred");
        }
        return true;
    }

    /**
     * Get the device certificate.
     */
    public static X509Certificate cert()
    {
        return _baseCfg.cert();
    }

    public static X509Certificate cacert() throws IOException, CertificateException
    {
        return _baseCfg.cacert();
    }

}
