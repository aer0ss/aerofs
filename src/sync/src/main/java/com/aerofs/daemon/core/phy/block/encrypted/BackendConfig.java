package com.aerofs.daemon.core.phy.block.encrypted;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.cfg.ICfgStore;
import com.google.inject.Inject;

import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import static com.aerofs.lib.cfg.ICfgStore.STORAGE_ENCRYPTION_PASSWORD;

/**
 * Manage the encryption password used for both S3 and Swift backends
 */
public class BackendConfig {

    public static interface EncryptionPasswordConfig
    {
        public char[] getPassword();

        public static class EncryptionPasswordFromDB implements EncryptionPasswordConfig
        {
            private final ICfgStore _store;

            @Inject
            public EncryptionPasswordFromDB(ICfgStore store)
            {
                _store = store;
            }

            @Override
            public char[] getPassword()
            {
                return _store.get(STORAGE_ENCRYPTION_PASSWORD).toCharArray();
            }
        }
    }

    public static class CryptoConfig
    {
        private final EncryptionPasswordConfig _passwordConfig;
        private SecretKey _secretKey;

        @Inject
        public CryptoConfig(EncryptionPasswordConfig passConfig)
        {
            _passwordConfig = passConfig;
        }

        public synchronized SecretKey getSecretKey()
                throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            if (_secretKey == null) {
                // Drew: we're doing some sorta interesting things to go from password ->
                // scrypt(password) -> base64(scrypt(password)) -> PBKDF2(base64(scrypt(password))) to
                // get the actual AES key. I'd say we can safely drop the PBKDF2 bit and just
                // base64-decode the output of scrypt.
                _secretKey = SecUtil.getAESSecretKey(_passwordConfig.getPassword(), true);
            }
            return _secretKey;
        }
    }
}
