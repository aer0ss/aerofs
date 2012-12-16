package com.aerofs.lib.cfg;

import com.aerofs.lib.AppRoot;
import com.aerofs.base.Base64;
import com.aerofs.lib.C;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.Versions;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

/**
 * This class is unfriendly to dependency injection and should be eventually removed. Use Cfg*
 * classes instead.
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
    private static String _absRootAnchor;
    private static String _absAuxRoot;
    private static String _ver;
    private static X509Certificate _cert;
    private static PrivateKey _privKey;
    private static byte[] _scrypted;
    private static boolean _inited;
    private static int _portbase;

    private static final boolean _staging;
    private static final long _profilerStartingThreshold;
    private static final CfgDatabase _db = new CfgDatabase();

    // we have to set it before init_() because the setup process may need the value.
    private static long _timeout = _db.getLong(Key.TIMEOUT);

    static {
        _staging = new File(Util.join(AppRoot.abs(), C.STAGING)) .exists();

        long pst;
        try {
            Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), C.PROFILER)));
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

        _timeout = _db.getInt(Key.TIMEOUT);

        // We want to keep the user-specified path in the DB, but we need the canonical path to
        // watch for filesystem changes on OSX.
        File rootAnchor = new File(_db.get(Key.ROOT));
        assert rootAnchor.isAbsolute();
        _absRootAnchor = rootAnchor.getCanonicalPath();
        _absAuxRoot = absAuxRootForPath(_absRootAnchor, _did);

        _portbase = readPortbase();
        _rootSID = SID.rootSID(_user);
        _useDM = !new File(rtRoot, C.NODM).exists();
        _useTCP = !new File(rtRoot, C.NOTCP).exists();
        _useXMPP = !new File(rtRoot, C.NOXMPP).exists();
        _useAutoUpdate = !new File(rtRoot, C.NOAUTOUPDATE).exists();

        _inited = true;
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
        File file = new File(rtRoot, C.PORTBASE);
        PrintStream ps = new PrintStream(new FileOutputStream(file));
        try {
            ps.println(portbase);
        } finally {
            ps.close();
        }
    }

    private static int readPortbase() throws IOException
    {
        Scanner s = new Scanner(new File(_absRTRoot, C.PORTBASE));
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
                Scanner s = new Scanner(new File(Util.join(AppRoot.abs(), C.VERSION)));
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
    public static String absAuxRoot()
    {
        return _absAuxRoot;
    }

    /**
     * @return the location of the aux root for a given path
     * @param did to use to generate the path
     * This is needed because during setup we want to use this method to check if we have the
     * permission to create the aux root folder, but don't have the real did yet.
     */
    public static String absAuxRootForPath(String path, DID did)
    {
        String shortDid = did.toStringFormal().substring(0, 6);
        File parent = new File(path).getParentFile();
        File auxRoot = new File(parent, C.AUXROOT_PREFIX + shortDid);
        return auxRoot.getAbsolutePath();
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

    public static boolean useFSTypeCheck(String rtRoot)
    {
        return !new File(rtRoot, C.NO_FS_TYPE_CHECK).exists();
    }

    public static int port(PortType type)
    {
        return _portbase + type.ordinal();
    }

    public static boolean lotsOfLog(String rtRoot)
    {
        return new File(Util.join(rtRoot, C.LOL)).exists();
    }

    public static boolean staging()
    {
        return _staging;
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
        return !isSP() && !staging();
    }

    /**
     * @return absolute path to the anchor of the root store
     */
    public static String absRootAnchor()
    {
        return _absRootAnchor;
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

    public static void resetSecurityTokens()
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
     * @throws com.aerofs.lib.ex.ExBadCredential if we can't decrypt the private key
     */
    public static void setPrivKeyAndScryptedUsingScrypted(byte[] scrypted)
        throws IOException, ExBadCredential
    {
        // decrypt device_private_key using b64(scrypt(p|u))
        char[] pbePasswd = Base64.encodeBytes(scrypted).toCharArray();
        byte[] encryptedKey = Base64.decodeFromFile(absRTRoot() + File.separator +
                C.DEVICE_KEY);
        _privKey = SecUtil.decryptPrivateKey(encryptedKey, pbePasswd);

        // do it after decryption has succeeded
        _scrypted = scrypted;
    }

    private static final char[] PASSWD_PASSWD = { '*', '$', '%', '^', '@', '#',
        '$', '*', 'C', 'X', '%', ' ', 'H', 'Z', 'S' };

    private static final int PASSWD_RANDOM_BYTES = 16;

    /**
     * @param base64 a string with the value of
     * b64(pbe_daemonkey(scrypt(p|u)|random_bytes))
     * @return scrypt(p|u)
     */
    public static byte[] encryptedBase642scrypted(String base64)
        throws IOException
    {
        try {
            // returns scrypt(p|u)|random_bytes
            byte[] scrypted = SecUtil.decryptPBEwithAES(Base64.decode(base64),
                PASSWD_PASSWD, false);
            // remove random_bytes
            scrypted = Arrays.copyOfRange(scrypted, 0, scrypted.length -
                    PASSWD_RANDOM_BYTES);
            return scrypted;
        } catch (GeneralSecurityException e) {
            SystemUtil.fatal(e);
            // keep compiler happy
            return null;
        }
    }

    /**
     * Turns a byte[] with the scrypted credential-bytes into a String suitable
     * for writing to device.conf
     *
     * @param scrypted requires a byte[] with scrypt(p|u)
     * @return b64(pbe_daemonkey(scrypt(p|u)|random_byte)))
     */
    public static String scrypted2encryptedBase64(byte[] scrypted)
    {
        try {
            byte[] rand = SecUtil.newRandomBytes(PASSWD_RANDOM_BYTES);

            byte[] bytes = new byte[rand.length + scrypted.length];
            System.arraycopy(scrypted, 0, bytes, 0, scrypted.length);
            System.arraycopy(rand, 0, bytes, scrypted.length, rand.length);

            byte[] encrypt = SecUtil.encryptPBEwithAES(bytes, PASSWD_PASSWD,
                    false);
            return Base64.encodeBytes(encrypt);
        } catch (GeneralSecurityException e) {
            SystemUtil.fatal(e);
            return null;
        }
    }

    /*
     * jnorris:
     *
     * scrypted = scrypt( password | username )
     * confdb[cred] = base64( AES_E[PBKDF2(PASSWD_PASSWD)]( scrypted | random ) )
     *
     */

    public static void readCreds() throws ExBadCredential, IOException
    {
        String cred = _db.getNullable(Key.CRED);
        if (cred != null) {
            byte[] scrypted = encryptedBase642scrypted(cred);
            setPrivKeyAndScryptedUsingScrypted(scrypted);
        } else {
            resetSecurityTokens();
        }
    }

    public static X509Certificate cert() throws IOException, CertificateException
    {
        if (_cert == null) {
            InputStream in = new FileInputStream(absRTRoot() + File.separator +
                    C.DEVICE_CERT);
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
}
