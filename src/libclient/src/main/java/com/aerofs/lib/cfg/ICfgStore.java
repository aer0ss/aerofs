package com.aerofs.lib.cfg;

import com.aerofs.base.C;
import com.google.common.collect.Maps;

import java.util.Map;

/*
 * Config values are stored in a db on the regular desktop client and in a conf file in Storage
 * Agent. This interface exists to hide the storage mechanism of the config values. Classes that
 * need the db/conf file should only need access to this interface with DI handling the rest.
 */
public abstract class ICfgStore {

    public static CfgKey USER_ID = new CfgKey("user_id", null);
    public static CfgKey DEVICE_ID = new CfgKey("device_id", null);
    public static CfgKey CRED = new CfgKey("cred", null);
    public static CfgKey SIGNUP_DATE = new CfgKey("signup_date", 0);

    // Absolute path to Root Anchor. N.B. must not use canonical paths so users can use symbolic
    // links as root anchor and repoint to different paths later.
    public static CfgKey ROOT = new CfgKey("root", null);
    public static CfgKey TIMEOUT = new CfgKey("timeout", 60 * C.SEC);

    // Config for Categories (see Cat.java)
    public static CfgKey MAX_CLIENT_STACKS = new CfgKey("max_client_stacks", 5);
    public static CfgKey MAX_SERVER_STACKS = new CfgKey("max_server_stacks", 20);
    public static CfgKey MAX_API_UPLOADS = new CfgKey("max_api_uploads", 10);
    public static CfgKey MAX_HOUSEKEEPING_STACKS = new CfgKey("max_housekeeping_stacks", 10);
    public static CfgKey MAX_D2U_STACKS = new CfgKey("max_d2u_stacks", 25); // 25 = client + server stacks

    // Config for S3
    public static CfgKey S3_DIR  = new CfgKey("s3_dir", null);
    public static CfgKey S3_ENDPOINT  = new CfgKey("s3_endpoint", null);
    public static CfgKey S3_ACCESS_KEY  = new CfgKey("s3_access_key", null);
    public static CfgKey S3_SECRET_KEY  = new CfgKey("s3_secret_key", null);
    public static CfgKey S3_BUCKET_ID  = new CfgKey("s3_bucket_id", null);


    // Config for Swift
    public static CfgKey SWIFT_AUTHMODE = new CfgKey("swift_auth_mode", null);
    public static CfgKey SWIFT_USERNAME = new CfgKey("swift_username", null);
    public static CfgKey SWIFT_PASSWORD = new CfgKey("swift_password", null);
    public static CfgKey SWIFT_TENANT_ID = new CfgKey("swift_tenant_id", null);
    public static CfgKey SWIFT_TENANT_NAME = new CfgKey("swift_tenant_name", null);
    public static CfgKey SWIFT_URL  = new CfgKey("swift_url", null);
    public static CfgKey SWIFT_CONTAINER  = new CfgKey("swift_container", "aerofs");
    public static CfgKey CONTACT_EMAIL  = new CfgKey("contact_email", "");

    // enable sync history
    public static CfgKey SYNC_HISTORY = new CfgKey("sync_history", true);

    // storage type
    public static CfgKey STORAGE_TYPE = new CfgKey("storage_type", null);

    // connecting to the REST API gateway to enable API access
    // DO NOT use this directly, use CfgRestService instead
    public static final CfgKey REST_SERVICE = new CfgKey("rest_service", true);

    // This field stores scrypt of the password provided by the user, salted by user id.
    // i.e. value = base64(scrypt(password|user))
    public static final CfgKey STORAGE_ENCRYPTION_PASSWORD =
            new CfgKey("remote_storage_encryption_password", null);


    Map<CfgKey, String> _map = Maps.newHashMap();

    /**
     * @pre the value is set, or the default value is not null
     */
    public String get(CfgKey key)
    {
        String v = getNullable(key);
        assert v != null;
        return v;
    }

    /**
     * @pre the value must be set, or the default value is not null
     *
     * N.B frequently calling this method is inefficient. Consider caching the result
     */
    public boolean getBoolean(CfgKey key)
    {
        return Boolean.parseBoolean(get(key));
    }

    /**
     * @return the default value if the value is not set or the method is called before loadAll_().
     * The default value may be null.
     */
    public String getNullable(CfgKey key)
    {
        assert _map != null;
        String v = _map.get(key);
        return v == null ? key.defaultValue(): v;
    }

    /**
     * @pre the value must be set, or the default value is not null
     *
     * N.B frequently calling this method is inefficient. Consider caching the result
     */
    public int getInt(CfgKey key)
    {
        return Integer.parseInt(get(key));
    }

    /**
     * @pre the value must be set, or the default value is not null
     *
     * N.B frequently calling this method is inefficient. Consider caching the result
     */
    public long getLong(CfgKey key)
    {
        return Long.parseLong(get(key));
    }
}
