/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

import javax.annotation.Nonnull;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public abstract class BaseSecUtil
{
    private static final int KEY_STRENGTH = 2048;
    protected static final String CERTIFICATE_TYPE = "X.509";
    protected static final String SHA1_WITH_RSA = "SHA1withRSA";
    protected static final String ORGANIZATION_UNIT = "na";
    protected static final String ORGANIZATION_NAME = "aerofs.com";
    protected static final String LOCALITY_NAME = "SF";
    protected static final String STATE_NAME = "CA";
    protected static final String COUNTRY_NAME = "US";

    private static final char[] PASSWD_PASSWD = { '*', '$', '%', '^', '@', '#', '$', '*', 'C', 'X',
            '%', ' ', 'H', 'Z', 'S' };
    private static final int PASSWD_RANDOM_BYTES = 16;

    // this object is thread safe
    // http://stackoverflow.com/questions/1461568/is-securerandom-thread-safe
    static final SecureRandom s_rand;

    static {
        try {
            s_rand = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    private final static int MAX_PLAIN_TEXT_SIZE = 117;

    public static byte[] newChallengeData() throws GeneralSecurityException
    {
        return newRandomBytes(MAX_PLAIN_TEXT_SIZE);
    }

    public static int newRandomInt()
    {
        return s_rand.nextInt();
    }

    /**
     * Get a secure random number in the range [0, upperBound).
     * @param upperBound one more than the maximum number this function will return
     * @return a secure, uniformly random integer between 0 (inclusive) and upperBound (exclusive)
     */
    public static int newRandomInt(int upperBound)
    {
        return s_rand.nextInt(upperBound);
    }

    public static byte[] newRandomBytes(int length)
    {
        byte[] bs = new byte[length];
        s_rand.nextBytes(bs);
        return bs;
    }

    public static PrivateKey decodePrivateKey(byte[] bs)
            throws GeneralSecurityException
    {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec specPrivate = new PKCS8EncodedKeySpec(bs);
        return factory.generatePrivate(specPrivate);
    }

    // TODO simply use privKey.getEncoded()
    public static byte[] encodePrivateKey(PrivateKey privKey)
            throws GeneralSecurityException
    {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec specPrivate =
                factory.getKeySpec(privKey,
                        PKCS8EncodedKeySpec.class);
        return specPrivate.getEncoded();
    }

    public static byte[] encryptChallenge(byte[] plainText, PublicKey publicKey)
            throws GeneralSecurityException
    {
        return cryptRSA(plainText, publicKey, true);
    }

    public static byte[] decryptChallenge(byte[] challenge, PrivateKey privateKey)
            throws ExBadCredential
    {
        try {
            return cryptRSA(challenge, privateKey, false);
        } catch (GeneralSecurityException e) {
            throw new ExBadCredential(e);
        }
    }

    public static String getCertificateCName(UserID userId, DID did)
    {
        return alphabetEncode(hash(BaseUtil.string2utf(userId.getString()), did.getBytes()));
    }

    public static KeyPair newRSAKeyPair()
            throws GeneralSecurityException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_STRENGTH);
        return generator.generateKeyPair();
    }

    public static PrivateKey decryptPrivateKey(byte[] encryptedKey, char[] passwd)
            throws ExBadCredential
    {
        try {
            return decodePrivateKey(decryptPBEwithAES(encryptedKey, passwd, false));
        } catch (GeneralSecurityException e) {
            throw new ExBadCredential(e);
        }
    }

    public static byte[] encryptPrivateKey(PrivateKey privKey, char[] passwd)
            throws GeneralSecurityException
    {
        return encryptPBEwithAES(encodePrivateKey(privKey), passwd, false);
    }

    /**
     * export the private key to PEM format
     */
    public static String exportPrivateKey(@Nonnull PrivateKey privKey)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN RSA PRIVATE KEY-----\n");
        String str = Base64.encodeBytes(privKey.getEncoded());
        while (!str.isEmpty()) {
            int len = Math.min(64, str.length());
            String prefix = str.substring(0, len);
            str = str.substring(len);
            sb.append(prefix);
            sb.append('\n');
        }
        sb.append("-----END RSA PRIVATE KEY-----\n");
        return sb.toString();
    }

    private static final byte[] PBE_AES_SALT = {
            (byte) 0x87, (byte) 0x73, (byte) 0x54, (byte) 0x8c,
            (byte) 0x7e, (byte) 0xc8, (byte) 0xa0, (byte) 0x88 };

    private static final int PBE_AES_ITERATION = 1024;
    private static final int PBE_AES_STRENGTH_STRONG = 256;
    private static final int PBE_AES_STRENGTH_WEAK = 128;
    private static final int IV_SIZE = 16;

    /**
     * N.B. don't use strong for the client side because Windows doesn't support it
     */
    public static byte[] encryptPBEwithAES(byte[] data, char[] passwd,
            boolean strong) throws GeneralSecurityException
    {
        Cipher cipher = getAESEncCipher(passwd, strong);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(data);
        return BaseUtil.concatenate(iv, ciphertext);
    }

    /**
     * N.B. don't use strong for the client side because Windows doesn't support it
     */
    private static byte[] decryptPBEwithAES(byte[] data, char[] passwd,
            boolean strong) throws GeneralSecurityException
    {
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(data, 0, iv, 0, IV_SIZE);

        Cipher cipher = getAESDecCipher(passwd, iv, strong);

        byte[] ciphertext = new byte[data.length - IV_SIZE];
        System.arraycopy(data, IV_SIZE, ciphertext, 0, ciphertext.length);

        return cipher.doFinal(ciphertext);
    }

    public static SecretKey getAESSecretKey(char[] passwd, boolean strong)
            throws NoSuchAlgorithmException, InvalidKeySpecException
    {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(passwd, PBE_AES_SALT, PBE_AES_ITERATION,
                strong ? PBE_AES_STRENGTH_STRONG : PBE_AES_STRENGTH_WEAK);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * @param base64 a string with the value of
     * b64(pbe_daemonkey(scrypt(p|u)|random_bytes))
     * @return scrypt(p|u)
     */
    public static byte[] encryptedBase642scrypted(String base64) throws IOException
    {
        try {
            // returns scrypt(p|u)|random_bytes
            byte[] scrypted = decryptPBEwithAES(Base64.decode(base64), PASSWD_PASSWD, false);
            // remove random_bytes
            scrypted = Arrays.copyOfRange(scrypted, 0, scrypted.length - PASSWD_RANDOM_BYTES);
            return scrypted;
        } catch (GeneralSecurityException e) {
            throw new Error(e);
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
            byte[] rand = newRandomBytes(PASSWD_RANDOM_BYTES);
            byte[] bytes = new byte[rand.length + scrypted.length];
            System.arraycopy(scrypted, 0, bytes, 0, scrypted.length);
            System.arraycopy(rand, 0, bytes, scrypted.length, rand.length);

            byte[] encrypt = encryptPBEwithAES(bytes, PASSWD_PASSWD, false);
            return Base64.encodeBytes(encrypt);
        } catch (GeneralSecurityException e) {
            throw new Error(e);
        }
    }

    public static class CipherFactory
    {
        private final SecretKey _secret;

        public CipherFactory(SecretKey secret)
        {
            _secret = secret;
        }

        private Cipher newCipher() throws NoSuchAlgorithmException, NoSuchPaddingException
        {
            return Cipher.getInstance("AES/CTR/NoPadding");
        }

        public Cipher newEncryptingCipher()
                throws InvalidKeyException, InvalidAlgorithmParameterException,
                NoSuchAlgorithmException, NoSuchPaddingException
        {
            Cipher c = newCipher();
            byte[] iv = newRandomBytes(IV_SIZE);
            c.init(Cipher.ENCRYPT_MODE, _secret, new IvParameterSpec(iv));
            return c;
        }

        public Cipher newDecryptingCipher(byte[] iv)
                throws InvalidKeyException, InvalidAlgorithmParameterException,
                NoSuchAlgorithmException, NoSuchPaddingException
        {
            Cipher c = newCipher();
            c.init(Cipher.DECRYPT_MODE, _secret, new IvParameterSpec(iv));
            return c;
        }

        public OutputStream encryptingOutputStream(OutputStream out) throws IOException
        {
            boolean ok = false;
            try {
                Cipher cipher = newEncryptingCipher();
                out.write(cipher.getIV());
                out = new CipherOutputStream(out, cipher);
                ok = true;
                return out;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            } finally {
                if (!ok) out.close();
            }
        }

        @SuppressWarnings("resource")
        public OutputStream encryptingHmacedOutputStream(OutputStream out) throws IOException
        {
            boolean ok = false;
            try {
                out = new HmacOutputStream(_secret, out);
                out = encryptingOutputStream(out);
                ok = true;
                return out;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            } finally {
                if (!ok) out.close();
            }
        }

        public InputStream decryptingInputStream(InputStream in) throws IOException
        {
            boolean ok = false;
            try {
                byte[] iv = new byte[IV_SIZE];
                ByteStreams.readFully(in, iv);
                Cipher cipher = newDecryptingCipher(iv);
                in = new CipherInputStream(in, cipher);
                ok = true;
                return in;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            } finally {
                if (!ok) in.close();
            }
        }

        @SuppressWarnings("resource")
        public InputStream decryptingHmacedInputStream(InputStream in) throws IOException
        {
            boolean ok = false;
            try {
                in = new HmacInputStream(_secret, in);
                in = decryptingInputStream(in);
                ok = true;
                return in;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            } finally {
                if (!ok) in.close();
            }
        }
    }

    public static class HmacOutputStream extends OutputStream
    {
        private final OutputStream _inner_stream;
        private final Mac _mac;
        public HmacOutputStream(Key key, OutputStream out)
                throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException
        {
            _inner_stream = out;
            _mac = Mac.getInstance("HmacSHA256");
            _mac.init(key);
        }
        @Override
        public void close() throws IOException
        {
            // Compute the final MAC on the whole stream contents and append that.
            byte[] hmac = _mac.doFinal();
            _inner_stream.write(hmac);
            _inner_stream.close();
        }
        @Override
        public void write(int b) throws IOException
        {
            // Update the MAC, then write the byte
            _mac.update((byte) b);
            _inner_stream.write(b);
        }
        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
            // Update the Mac, then write the bytes
            _mac.update(b, off, len);
            _inner_stream.write(b, off, len);
        }
    }

    public static class HmacInputStream extends InputStream
    {
        private final InputStream _inner_stream;
        private final Mac _mac;
        private final int _mac_length;
        private final byte[] _buffer;
        private Boolean _mac_ok;
        // We must keep at least _mac_length bytes in _buffer that we do not hand to the
        // client.  When we reach EOF, we should hand off all but the last _mac_length bytes,
        // check the MAC, and throw if it's bad.
        public HmacInputStream(Key key, InputStream in)
                throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException,
                IOException
        {
            _inner_stream = in;
            _mac = Mac.getInstance("HmacSHA256");
            _mac.init(key);
            _mac_length = _mac.getMacLength();
            _buffer = new byte[_mac_length];
            int buffered = 0;
            while (buffered != _mac_length) {
                int bytes_read = _inner_stream.read(_buffer, buffered, _mac_length - buffered);
                if (bytes_read == -1) {
                    throw new IOException("Stream contained too few bytes to hold an HMAC.");
                }
                buffered += bytes_read;
            }
        }

        private void checkMac() throws IOException
        {
            if (_mac_ok == null) {
                _mac_ok = constantTimeIsEqual(_mac.doFinal(), _buffer);
            }
            if (!_mac_ok) {
                throw new IOException("HMAC didn't match expected value");
            }
        }

        @Override
        public void close() throws IOException
        {
            checkMac();
            _inner_stream.close();
        }

        @Override
        public int read() throws IOException
        {
            byte[] array = new byte[1];
            int result = read(array, 0, 1);
            if (result == -1)
                return result;
            return array[0];
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            if (b == null) throw new NullPointerException();
            byte[] second_buffer = new byte[len];
            int actually_read = _inner_stream.read(second_buffer, 0, len);
            if (actually_read == -1) {
                // We've reached the end of the file.  We should verify the HMAC on the last
                // _mac_length bytes (which we have stored in _buffer).  Then, if we had more than
                // that still buffered, we should return those that remain.  Otherwise, we should
                // return -1.
                // Verify the MAC.
                checkMac();
                return -1;
            }

            // The total bytes we have read from the stream that we haven't yielded yet =
            // _buffer.length + actually_read.  Since we need to keep _buffer.length bytes around,
            // we can yield only the first actually_read bytes of [buffer | second_buffer].  The
            // last _mac_length bytes of [buffer | second_buffer] we must keep as the next
            // "could be the MAC" bytes.
            for (int i = 0; i < actually_read; i++) {
                // Copy as many bytes as we can safely pass up to the caller
                if (i < _mac_length) {
                    b[off + i] = _buffer[i];
                } else {
                    b[off + i] = second_buffer[i - _mac_length];
                }
            }
            // We have now placed actually_read bytes in b at the appropriate offset.

            // Update the last _mac_length bytes we keep in _buffer:
            for (int i = 0 ; i < _mac_length; i++) {
                if (i + actually_read < _mac_length) {
                    // copy bytes from the previous _buffer
                    _buffer[i] = _buffer[i + actually_read];
                } else {
                    // copy bytes from the new buffer
                    _buffer[i] = second_buffer[actually_read + i - _mac_length];
                }
            }
            // Before returning to the user, update the MAC.
            _mac.update(b, off, actually_read);
            // Return.
            return actually_read;
        }
    }

    /**
     * Takes a constant amount of time to compare byte arrays a and b.  This defends against
     * timing attacks: http://codahale.com/a-lesson-in-timing-attacks/
     * @param a one of the byte arrays to compare
     * @param b the other byte array to compare
     * @return true if the arrays are byte-for-byte identical, false otherwise
     */
    public static boolean constantTimeIsEqual(byte[] a, byte[] b)
    {
        if (a.length != b.length) {
            return false;
        }
        int equality = 0;
        for (int i = 0 ; i < a.length; i++) {
            equality |= a[i] ^ b[i];
        }
        return equality == 0;
    }

    /**
     * OBSOLETE. USE CipherFactory instead
     *
     * @param initvector null if @encrypt is true, has the initialization vector
     * if decrypting
     * @throws GeneralSecurityException
     */
    private static Cipher getAESCipher(char[] passwd, byte[] initvector,
            boolean encrypt, boolean strong)
            throws GeneralSecurityException
    {
        SecretKey secret = getAESSecretKey(passwd, strong);

        // TODO similar to Cipher factory, use AES/CTR instead
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");

        //we generate our own IV because we need to guarantee that the IV is of a certain size.
        if (encrypt) {
            //generate IV
            byte[] iv = newRandomBytes(IV_SIZE);
            c.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
        } else {
            //use IV
            c.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(initvector));
        }
        return c;
    }

    public static Cipher getAESEncCipher(final char[] passwd, boolean strong)
            throws GeneralSecurityException
    {
        return getAESCipher(passwd, null, true, strong);
    }

    public static Cipher getAESDecCipher(final char[] passwd, final byte[] iv,
            boolean strong)
            throws GeneralSecurityException
    {
        return getAESCipher(passwd, iv, false, strong);
    }

    // may throw exception if decryption/encryption key doesn't match
    //
    private static byte[] cryptRSA(byte[] data, Key key, boolean encrypt)
            throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key);

        return cipher.doFinal(data);
    }

    public static MessageDigest newMessageDigest()
    {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    public static MessageDigest newMessageDigestMD5()
    {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    public static byte[] hash(File f) throws IOException
    {
        MessageDigest md = newMessageDigest();

        try (FileInputStream is = new FileInputStream(f)) {
            byte[] bs = new byte[BaseParam.FILE_BUF_SIZE];
            while (true) {
                int read = is.read(bs);
                if (read < 0) break;
                md.update(bs, 0, read);
            }
        }
        return md.digest();
    }

    public static byte[] hash(byte[] bs, int offset, int len)
    {
        MessageDigest md = newMessageDigest();
        md.update(bs, offset, len);
        return md.digest();
    }

    public static byte[] hash(byte[] bs)
    {
        return hash(bs, 0, bs.length);
    }

    public static byte[] hash(byte[] ...bss)
    {
        MessageDigest md = newMessageDigest();
        for (byte[] bs : bss) md.update(bs);
        return md.digest();
    }

    // 8192/8/1 equals 100ms or so on Allen's MBP with a 2GHz i7 CPU

    /**
     * Constants and utilities for key-derivation function (currently SCrypt).
     * Kept here for compatibility while we still have client-side scrypt users.
     * See: sp.server.authentication.LocalCredential
     */
    public static class KeyDerivation
    {
        public static final int N = 8192;
        public static final int r = 8;
        public static final int p = 1;
        public static final int dkLen = 64;

        /** Convert a userId to salt for an scrypt invocation */
        public static byte[] getSaltForUser(final UserID user)
        {
            return BaseUtil.string2utf(user.getString());
        }

        /**
         * Use <b>only</b> this function to convert a char[] password to bytes
         */
        public static byte[] getPasswordBytes(char[] passwd)
        {
            // even though it's not a good idea to convert the password into string
            // we have to do it here for backward compatibility (due to a complex
            // historical reason). we use getBytes() instead of string2utf() for the
            // same reason
            return new String(passwd).getBytes();
        }
    }

    // throw ExCannotDecrypt if authentication failed`
    public static void selfAuthenticate(X509Certificate cert, PrivateKey privateKey)
            throws ExBadCredential, GeneralSecurityException
    {
        byte[] data = newChallengeData();
        byte[] challenge = encryptChallenge(data, cert.getPublicKey());
        byte[] response = decryptChallenge(challenge, privateKey);
        if (!Arrays.equals(data, response)) throw new ExBadCredential();
    }

    private static char[] ALPHABET = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p' };

    private static String alphabetEncode(byte[] bs)
    {
        StringBuilder sb = new StringBuilder();
        for (byte b : bs) {
            int hi = (b >> 4) & 0xF;
            int lo = b & 0xF;
            sb.append(ALPHABET[hi]);
            sb.append(ALPHABET[lo]);
        }
        return sb.toString();
    }

    /**
     * @param serverKeyFilename PEM-encoded file with PCKS#8 private key
     * @return a new {@link PrivateKey}
     */
    public static PrivateKey newPrivateKeyFromFile(String serverKeyFilename)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException
    {
        File f = new File(serverKeyFilename);
        try (InputStream in = new FileInputStream(f)) {
            byte[] fileContents = new byte[(int)f.length()];
            in.read(fileContents);

            String fileString = new String(fileContents);
            fileString = fileString.replace("-----BEGIN PRIVATE KEY-----", "");
            fileString = fileString.replace("-----END PRIVATE KEY-----", "");
            fileString = fileString.trim();

            byte[] decoded = Base64.decode(fileString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        }
    }

    /**
     * @param certFilename PEM-encoded file with X509 certificate
     * @return a new {@link X509Certificate}
     * @throws java.security.cert.CertificateException
     * @throws IOException
     */
    public static Certificate newCertificateFromFile(String certFilename)
            throws CertificateException, IOException
    {
        InputStream in = new FileInputStream(certFilename);
        return newCertificateFromStream(in);
    }

    /**
     *
     * @param certData PEM-encoded string with the X509 certificate
     * @return a new {@link X509Certificate}
     * @throws CertificateException
     * @throws IOException
     */
    public static Certificate newCertificateFromString(String certData)
            throws CertificateException, IOException
    {
        return newCertificateFromStream(new ByteArrayInputStream(certData.getBytes()));
    }

    /**
     * @param in Input stream which will yield a PEM-encoded file holding an X509 certificate
     * @return a new {@link X509Certificate}
     * @throws java.security.cert.CertificateException
     * @throws IOException
     */
    public static Certificate newCertificateFromStream(InputStream in)
            throws CertificateException, IOException
    {
        try {
            CertificateFactory cf = CertificateFactory.getInstance(CERTIFICATE_TYPE);
            return cf.generateCertificate(in);
        } finally {
            in.close();
        }
    }

    /**
     * Determines if the given endEntityCert is signed by caCert or not.
     * Ignores certificate validity.
     * @return true if caCert signed endEntityCert, false otherwise
     */
    public static boolean signingPathExists(X509Certificate endEntityCert, X509Certificate caCert)
            throws CertificateException, InvalidAlgorithmParameterException,
            NoSuchAlgorithmException
    {
        // Make a certpath for the end-entity cert
        CertificateFactory certificateFactory = CertificateFactory.getInstance(CERTIFICATE_TYPE);
        List<X509Certificate> certList = Lists.newArrayList(endEntityCert);
        CertPath path = certificateFactory.generateCertPath(certList);
        // Make a trust anchor for the CA (and parameters)
        TrustAnchor anchor = new TrustAnchor(caCert, null);
        PKIXParameters params = new PKIXParameters(Collections.singleton(anchor));
        params.setRevocationEnabled(false);
        // Try to validate
        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
        boolean retval;
        try {
            cpv.validate(path, params);
            retval = true;
        } catch (CertPathValidatorException e) {
            // No path from EEC to CA was found, return false
            retval = false;
        }
        return retval;
    }

    /**
     * Return true if the given certificate expiry is at least 'requiredMs' millis in the future.
     * Does not check other elements of cert validity.
     */
    public static boolean validForAtLeast(X509Certificate entityCertificate, long requiredMs)
    {
        long remainingMillis = entityCertificate.getNotAfter().getTime() - new Date().getTime();
        return (remainingMillis >= requiredMs);
    }
}
