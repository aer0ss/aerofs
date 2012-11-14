package com.aerofs.s3;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.CfgDatabase;
import com.google.inject.Inject;

public class S3Config
{
    private S3Config() {}

    public static class S3DirConfig
    {
        private final CfgDatabase _db;

        @Inject
        public S3DirConfig(CfgDatabase db)
        {
            _db = db;
        }

        public File getS3Dir()
        {
            return new File(_db.get(CfgDatabase.Key.S3_DIR));
        }
    }

    public static class S3BucketIdConfig
    {
        private final CfgDatabase _db;

        @Inject
        public S3BucketIdConfig(CfgDatabase db)
        {
            _db = db;
        }

        private String getConfigValue()
        {
            return _db.get(CfgDatabase.Key.S3_BUCKET_ID);
        }

        public String getS3BucketId()
        {
            String value = getConfigValue();
            int index = value.indexOf('/');
            if (index != -1) value = value.substring(0, index);
            return value;
        }

        public String getS3DirPath()
        {
            String value = getConfigValue();
            int start = value.indexOf('/');
            if (start == -1) return "";
            ++start;

            int end = value.length();
            while (start < end && value.charAt(start) == '/') ++start;
            while (start < end && value.charAt(end - 1) == '/') --end;
            value = value.substring(start, end);

            assert !value.startsWith("/");
            assert !value.endsWith("/");

            return value;
        }
}

    public static interface S3EncryptionPasswordConfig
    {
        public char[] getPassword();

        public static class S3EncryptionPasswordFromDB implements S3EncryptionPasswordConfig
        {
            private final CfgDatabase _db;

            @Inject
            public S3EncryptionPasswordFromDB(CfgDatabase db)
            {
                _db = db;
            }

            @Override
            public char[] getPassword()
            {
                return _db.get(CfgDatabase.Key.S3_ENCRYPTION_PASSWORD).toCharArray();
            }
        }

        public static class S3EncryptionPasswordFromEnv implements S3EncryptionPasswordConfig
        {
            public static final String S3_ENC_PASS_VAR = "S3ENCPASS";

            @Override
            public char[] getPassword() {
                String pass = System.getenv(S3_ENC_PASS_VAR);
                if (pass == null || pass.isEmpty()) {
                    SystemUtil.fatal("environment variable " + S3_ENC_PASS_VAR + " must be set");
                }
                return pass.toCharArray();
            }
        }
    }

    public static class S3CryptoConfig
    {
        private final S3EncryptionPasswordConfig _passwordConfig;
        private SecretKey _secretKey;

        @Inject
        public S3CryptoConfig(S3EncryptionPasswordConfig passConfig)
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

    public static class S3ChunkDirConfig
    {
        private final String _chunkDir;

        public S3ChunkDirConfig(String chunkDir)
        {
            _chunkDir = chunkDir;
        }

        public String getChunkDir()
        {
            return _chunkDir;
        }
    }

}
