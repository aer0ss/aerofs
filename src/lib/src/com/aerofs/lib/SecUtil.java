package com.aerofs.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
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
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

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

import sun.security.pkcs.PKCS10;
import sun.security.x509.X500Name;
import sun.security.x509.X500Signer;

import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.scrypt.Scrypt;
import com.google.common.io.ByteStreams;

public class SecUtil
{
    private static final int KEY_STRENGTH = 2048;
    private static final String ORGANIZATION_UNIT = "na";
    private static final String ORGANIZATION_NAME = "aerofs.com";
    private static final String LOCALITY_NAME = "SF";
    private static final String STATE_NAME = "CA";
    private static final String COUNTRY_NAME = "US";

    // this object is thread safe
    // http://stackoverflow.com/questions/1461568/is-securerandom-thread-safe
    static final SecureRandom s_rand;

    static {
        try {
            s_rand = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw Util.fatal(e);
        }
    }

    private static int MAX_PLAIN_TEXT_SIZE = 117;
    public static byte[] newChallengeData() throws GeneralSecurityException
    {
        return newRandomBytes(MAX_PLAIN_TEXT_SIZE);
    }

    public static int newRandomInt()
    {
        return s_rand.nextInt();
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

//        X509EncodedKeySpec spec =
//          (X509EncodedKeySpec) factory.getKeySpec(privKey,
//                  X509EncodedKeySpec.class);
//        byte[] bs = spec.getEncoded();
    }


//  private static PublicKey decodePublicKey(byte[] bs)
//      throws GeneralSecurityException
//  {
//      KeyFactory factory = KeyFactory.getInstance("RSA");
//      X509EncodedKeySpec specPublic = new X509EncodedKeySpec(bs);
//      return factory.generatePublic(specPublic);
//  }
//
//
//  X509EncodedKeySpec specPublic =
//          (X509EncodedKeySpec) factory.getKeySpec(kp.getPublic(),
//                  X509EncodedKeySpec.class);
//  byte[] bsPubic = specPublic.getEncoded();

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

    public static String getCertificateCName(String user, DID did)
    {
        return alphabetEncode(hash(Util.string2utf(user), did.getBytes()));
    }

    public static PKCS10 newCSR(PublicKey pubKey, PrivateKey privKey, String user,
            DID did)
        throws GeneralSecurityException, IOException
    {
        PKCS10 request = new PKCS10(pubKey);

        Signature signature = Signature.getInstance("SHA1withRSA");
        signature.initSign(privKey);

        X500Name subject = new X500Name(getCertificateCName(user, did),
                                        ORGANIZATION_UNIT,
                                        ORGANIZATION_NAME,
                                        LOCALITY_NAME,
                                        STATE_NAME,
                                        COUNTRY_NAME);

         // In JDK 1.7 class X500Signer doesn't exists and encodeAndSign() method
         // has changed that takes Signature and X500Name as params instead of
         // X500Signer. So catch the error and use reflection method to invoke
         // the new encodeAndSign() method.
        try {
            X500Signer signer = new X500Signer(signature, subject);

            request.encodeAndSign(signer);

        } catch (NoClassDefFoundError noClassErr) {
            try {
                Class<?> requestClass = request.getClass();

                Class<?> params[] = new Class[2];
                params[0] = X500Name.class;
                params[1] = Signature.class;

                Method encodeAndSignMethod = requestClass.getDeclaredMethod(
                                "encodeAndSign", params);
                encodeAndSignMethod.invoke(request, subject, signature);
            } catch(Exception e) {
                throw new GeneralSecurityException(e);
            }
        }

        return request;
    }

    public static void newRSAKeyPair(OutArg<PublicKey> pubKey,
        OutArg<PrivateKey> privKey)
        throws GeneralSecurityException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_STRENGTH);
        KeyPair kp = generator.generateKeyPair();
        pubKey.set(kp.getPublic());
        privKey.set(kp.getPrivate());
    }

    public static PrivateKey decryptPrivateKey(byte[] encryptedKey, char[] passwd)
        throws ExBadCredential
    {
        try {
            return decodePrivateKey(decryptPBEwithAES(encryptedKey, passwd,
                    false));
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
    public static String exportPrivateKey(PrivateKey privKey)
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
            // (byte)0xc7, (byte)0x73, (byte)0x21, (byte)0x8c,
            // (byte)0x7e, (byte)0xc8, (byte)0xee, (byte)0x99
            (byte) 0x87, (byte) 0x73, (byte) 0x54, (byte) 0x8c, (byte) 0x7e,
            (byte) 0xc8, (byte) 0xa0, (byte) 0x88 };

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
        return Util.concatenate(iv, ciphertext);
    }

    /**
     * N.B. don't use strong for the client side because Windows doesn't support it
     */
    public static byte[] decryptPBEwithAES(byte[] data, char[] passwd,
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
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
        return secret;
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
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            return c;
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
            while(buffered != _mac_length) {
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
     * @param passwd
     * @param initvector null if @encrypt is true, has the initialization vector
     * if decrypting
     * @param encrypt
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
            throw Util.fatal(e);
        }
    }

    public static MessageDigest newMessageDigestMD5()
    {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw Util.fatal(e);
        }
    }

    public static byte[] hash(File f) throws IOException
    {
        MessageDigest md = newMessageDigest();

        FileInputStream is = new FileInputStream(f);
        try {
            byte[] bs = new byte[Param.FILE_BUF_SIZE];
            while (true) {
                int read = is.read(bs);
                if (read < 0) break;
                md.update(bs, 0, read);
            }
        } finally {
            is.close();
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
    private static final int N = 8192;
    private static final int r = 8;
    private static final int p = 1;

    /**
     * Use <b>only</b> this function to convert a char[] password to bytes
     * @param passwd
     * @return
     */
    public static byte[] getPasswordBytes(char[] passwd)
    {
        // even though it's not a good idea to convert the password into string
        // we have to do it here for backward compatibility (due to a complex
        // historical reason). we use getBytes() instead of string2utf() for the
        // same reason
        return new String(passwd).getBytes();
    }

    public static byte[] scrypt(char[] passwd, String user)
    {
        byte[] bsPass = getPasswordBytes(passwd);
        byte[] bsUser = Util.string2utf(user);

        OSUtil.get().loadLibrary("aerofsd");

        byte[] scrypted = new byte[64];
        int rc = Scrypt.crypto_scrypt(bsPass, bsPass.length, bsUser, bsUser.length,
                N, r, p, scrypted, scrypted.length);

        // sanity checks
        if (rc != 0) Util.fatal("scr rc != 0");

        return scrypted;
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
        InputStream in = new FileInputStream(f);
        try {
            byte[] fileContents = new byte[(int) f.length()];
            in.read(fileContents);

            String fileString = new String(fileContents);
            fileString = fileString.replace("-----BEGIN PRIVATE KEY-----", "");
            fileString = fileString.replace("-----END PRIVATE KEY-----"  , "");
            fileString = fileString.trim();

            byte[] decoded = Base64.decode(fileString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } finally {
            in.close();
        }
    }

    /**
     * @param serverCertFilename PEM-encoded file with X509 certificate
     * @return a new {@link X509Certificate}
     * @throws CertificateException
     * @throws IOException
     */
    public static Certificate newCertificateFromFile(String serverCertFilename)
            throws CertificateException, IOException
    {
        InputStream in = new FileInputStream(serverCertFilename);
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return cf.generateCertificate(in);
        } finally {
            in.close();
        }
    }
}
