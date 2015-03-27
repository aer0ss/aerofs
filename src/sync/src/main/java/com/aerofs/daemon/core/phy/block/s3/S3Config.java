package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.daemon.core.phy.block.encrypted.BackendConfig;
import com.aerofs.lib.cfg.ICfgStore;
import com.google.inject.Inject;

import static com.aerofs.lib.cfg.ICfgStore.*;

public class S3Config extends BackendConfig
{
    private S3Config() {}

    public static class S3BucketIdConfig
    {
        private final ICfgStore _cfgStore;

        @Inject
        public S3BucketIdConfig(ICfgStore cfgStore)
        {
            _cfgStore = cfgStore;
        }

        private String getConfigValue()
        {
            return _cfgStore.get(S3_BUCKET_ID);
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
}
