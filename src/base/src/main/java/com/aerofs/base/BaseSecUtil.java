/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
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
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

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

    @Deprecated
    private static PrivateKey decodePrivateKey(byte[] bs)
            throws GeneralSecurityException
    {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec specPrivate = new PKCS8EncodedKeySpec(bs);
        return factory.generatePrivate(specPrivate);
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

    @Deprecated
    @SuppressWarnings("deprecation")
    public static PrivateKey decryptPrivateKey(byte[] encryptedKey, char[] passwd)
            throws ExBadCredential
    {
        try {
            return decodePrivateKey(decryptPBEwithAES(encryptedKey, passwd, false));
        } catch (GeneralSecurityException e) {
            throw new ExBadCredential(e);
        }
    }

    /**
     * export the private key to PEM-encoded PCKS#8 format
     */
    public static String exportPrivateKey(@Nonnull PrivateKey privKey)
            throws GeneralSecurityException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PRIVATE KEY-----\n");
        String str = Base64.encodeBytes(encodePrivateKey(privKey));
        while (!str.isEmpty()) {
            int len = Math.min(64, str.length());
            String prefix = str.substring(0, len);
            str = str.substring(len);
            sb.append(prefix);
            sb.append('\n');
        }
        sb.append("-----END PRIVATE KEY-----\n");
        return sb.toString();
    }

    private static byte[] encodePrivateKey(PrivateKey privKey)
            throws GeneralSecurityException
    {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec specPrivate = factory.getKeySpec(privKey, PKCS8EncodedKeySpec.class);
        return specPrivate.getEncoded();
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
    @Deprecated
    @SuppressWarnings("deprecation")
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
    @Deprecated
    @SuppressWarnings("deprecation")
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
     * Write the private key to a new file with the given name.
     *
     * If the file already exists, we attempt to delete it. This can happen if an installation
     * fails after getting certified but before really really finishing setup. Let's be paranoid since
     * this problem is hard to recover from elsewhere. Also clean up if we fail exporting the private key.
     */
    public static void writePrivateKey(PrivateKey privKey, String filename)
            throws IOException, GeneralSecurityException {

        // Dear Java, it sure is annoying how a 'catch' is outside the lexical scope of the 'try'. Love, jP.
        File keyFile = null;
        try {
            keyFile = new File(filename);
            if (keyFile.exists()) keyFile.delete();

            try (OutputStream out = new FileOutputStream(keyFile)) {
                out.write(exportPrivateKey(privKey).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            // clean up if there was an unexpected problem
            if (keyFile != null) keyFile.delete();
            throw t;
        }

        try {
            Files.setPosixFilePermissions(Paths.get(filename),
                    ImmutableSet.of(PosixFilePermission.OWNER_READ));
        } catch (UnsupportedOperationException e) {
            // fallback to old permission API for non-Posix systems
            // TODO: on windows we should try to use DACL to keep other users away
            keyFile.setReadable(false, false);   // chmod a-r
            keyFile.setWritable(false, false);   // chmod a-w
            keyFile.setExecutable(false, false); // chmod a-x
            keyFile.setReadable(true, true);     // chmod u+r
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

        public InputStream encryptingInputStream(InputStream in) throws IOException
        {
            boolean ok = false;
            try {
                Cipher cipher = newEncryptingCipher();
                in = new SequenceInputStream(Collections.enumeration(ImmutableList.of(
                        new ByteArrayInputStream(cipher.getIV()),
                        new CipherInputStream(in, cipher))));
                ok = true;
                return in;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            } finally {
                if (!ok) in.close();
            }
        }

        @SuppressWarnings("resource")
        public OutputStream encryptingHmacedOutputStream(OutputStream out) throws IOException
        {
            boolean ok = false;
            try {
                out = new HmacAppendingOutputStream(_secret, out);
                out = encryptingOutputStream(out);
                ok = true;
                return out;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            } finally {
                if (!ok) out.close();
            }
        }

        @SuppressWarnings("resource")
        public InputStream encryptingHmacedInputStream(InputStream in) throws IOException
        {
            boolean ok = false;
            try {
                in = encryptingInputStream(in);
                in = new HmacAppendingInputStream(_secret, in);
                ok = true;
                return in;
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            } finally {
                if (!ok) in.close();
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
                in = new HmacVerifyingInputStream(_secret, in);
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

    public static class HmacAppendingOutputStream extends OutputStream
    {
        private final OutputStream _inner_stream;
        private final Mac _mac;
        public HmacAppendingOutputStream(Key key, OutputStream out)
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

    /**
     * Append the Hmac to the InputStream
     */
    public static class HmacAppendingInputStream extends InputStream
    {
        private InputStream _inner_stream;
        private boolean _inner_stream_terminated;
        private final Mac _mac;

        /**
         * Append the Hmac of the input stream at the end of it
         *
         * @param key The key
         * @param in The input InputStream
         * @throws NoSuchProviderException
         * @throws NoSuchAlgorithmException
         * @throws InvalidKeyException
         * @throws IOException
         */
        public HmacAppendingInputStream(Key key, InputStream in)
                throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeyException,
                IOException
        {
            _inner_stream = in;
            _inner_stream_terminated = false;
            _mac = Mac.getInstance("HmacSHA256");
            _mac.init(key);
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

            // Read from the input stream
            int actually_read = _inner_stream.read(b, 0, len);

            if (!_inner_stream_terminated) {
                // Handle as usual...

                if (actually_read == -1) {
                    // We've reached the end of the input stream.  We should append the HMAC now.

                    _inner_stream.close();
                    _inner_stream_terminated = true;

                    // We replace the original input stream by the hmac
                    _inner_stream = new ByteArrayInputStream(_mac.doFinal());

                    return read(b, off, len);
                }

                // Before returning to the user, update the MAC.
                _mac.update(b, off, actually_read);
            }

            // Return.
            return actually_read;
        }
    }

    public static class HmacVerifyingInputStream extends InputStream
    {
        private final InputStream _inner_stream;
        private final Mac _mac;
        private final int _mac_length;
        private final byte[] _buffer;
        private Boolean _mac_ok;
        // We must keep at least _mac_length bytes in _buffer that we do not hand to the
        // client.  When we reach EOF, we should hand off all but the last _mac_length bytes,
        // check the MAC, and throw if it's bad.
        public HmacVerifyingInputStream(Key key, InputStream in)
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

    public static boolean constantTimeIsEqual(String a, String b)
    {
        if (a.length() != b.length()) {
            return false;
        }
        int equality = 0;
        for (int i = 0 ; i < a.length(); i++) {
            equality |= a.charAt(i) ^ b.charAt(i);
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
    @Deprecated
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

    @Deprecated
    @SuppressWarnings("deprecation")
    public static Cipher getAESDecCipher(final char[] passwd, final byte[] iv,
            boolean strong)
            throws GeneralSecurityException
    {
        return getAESCipher(passwd, iv, false, strong);
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
            byte[] bs = new byte[BaseUtil.FILE_BUF_SIZE];
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
            ByteStreams.readFully(in, fileContents);

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
    public static X509Certificate newCertificateFromFile(String certFilename)
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
    public static X509Certificate newCertificateFromString(String certData)
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
    public static X509Certificate newCertificateFromStream(InputStream in)
            throws CertificateException, IOException
    {
        try {
            CertificateFactory cf = CertificateFactory.getInstance(CERTIFICATE_TYPE);
            return (X509Certificate)cf.generateCertificate(in);
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
