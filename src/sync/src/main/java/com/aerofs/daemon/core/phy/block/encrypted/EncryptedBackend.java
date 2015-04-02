package com.aerofs.daemon.core.phy.block.encrypted;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.core.phy.block.encrypted.BackendConfig.CryptoConfig;
import com.aerofs.lib.SystemUtil;
import org.slf4j.Logger;

import javax.crypto.SecretKey;
import java.io.*;

import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * Encrypted storage backend
 *
 * The blocks are transparently encrypted locally before on the way to remote storage and
 * transparently decrypted on the way back.
 *
 * Warning: the method wrapForEncoding being highly specific to the backend
 * (S3 needs MD5 / Content-length), it has to be defined in the child class.
 */
public abstract class EncryptedBackend implements IBlockStorageBackend
{
    private static final Logger l = Loggers.getLogger(EncryptedBackend.class);

    protected SecretKey _secretKey;
    protected CryptoConfig _cryptoConfig;

    protected static final String BLOCK_SUFFIX = ".chunk.gz.aes";
    protected static final String BLOCK_DIR = "chunks/";

    protected EncryptedBackend(CryptoConfig cryptoConfig)
    {
        _cryptoConfig = cryptoConfig;
    }

    @Override
    public void init_() throws IOException
    {
        try {
            _secretKey = _cryptoConfig.getSecretKey();
        } catch (NoSuchAlgorithmException |InvalidKeySpecException e) {
            SystemUtil.ExitCode.REMOTE_STORAGE_INVALID_CONFIG.exit();
        }
    }

    /**
     * Wraps the blocks to decrypt them
     *
     * @param in the input stream
     * @return the decoded input stream
     * @throws IOException
     */
    protected InputStream wrapForDecoding(InputStream in) throws IOException
    {
        boolean ok = false;
        try {
            in = new BaseSecUtil.CipherFactory(_secretKey).decryptingHmacedInputStream(in);
            ok = true;
            return in;
        } finally {
            if (!ok) in.close();
        }
    }
}
