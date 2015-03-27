/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.lib.LibParam;
import com.aerofs.lib.StorageType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static com.aerofs.lib.cfg.ICfgStore.*;

public class UnattendedSetup
{
    /**
     * File containing settings for unattended setup.
     *
     * If a file with this name exists in the runtime root, the CLI will
     * use the values therein for running the setup procedure instead of asking the
     * user interactively.
     */
    private static final String UNATTENDED_SETUP_FILE = "unattended-setup.properties";

    /**
     *  Example file contents:
     *
     *  userid = test@aerofs.com
     *  password = password
     *  device = Ye Olde MacBook Pro
     */
    private File _setupFile = null;

    private static final String
        PROP_USERID = "userid",
        PROP_PASSWORD = "password",
        PROP_DEVICE = "device",
        PROP_ROOT = "root",
        PROP_STORAGE_TYPE = "storage_type";

    public UnattendedSetup(String rtRoot)
    {
        _setupFile = new File(rtRoot, UNATTENDED_SETUP_FILE);
    }

    public void populateModelFromSetupFile(SetupModel model)
            throws IOException
    {
        Properties props = new Properties();
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(_setupFile))) {
            props.load(in);
        }

        model.setUserID(props.getProperty(PROP_USERID));
        model.setPassword(props.getProperty(PROP_PASSWORD));
        model.setDeviceName(props.getProperty(PROP_DEVICE, model.getDeviceName()));

        model._localOptions._rootAnchorPath = props.getProperty(PROP_ROOT, model._localOptions._rootAnchorPath);

        model._storageType = StorageType.fromString(props.getProperty(PROP_STORAGE_TYPE));

        // External backend
        model._backendConfig._storageType = model._storageType;

        // Amazon S3
        if (model._storageType == StorageType.S3) {
            model._backendConfig._s3Config = new SetupModel.S3Config();
            model._backendConfig._s3Config._endpoint = props.getProperty(S3_ENDPOINT.keyString(), LibParam.DEFAULT_S3_ENDPOINT);
            model._backendConfig._s3Config._bucketID = props.getProperty(S3_BUCKET_ID.keyString());
            model._backendConfig._s3Config._accessKey = props.getProperty(S3_ACCESS_KEY.keyString());
            model._backendConfig._s3Config._secretKey = props.getProperty(S3_SECRET_KEY.keyString());
            model._backendConfig._passphrase = props.getProperty(STORAGE_ENCRYPTION_PASSWORD.keyString());
        }

        // OpenStack Swift
        if (model._storageType == StorageType.SWIFT) {
            model._backendConfig._swiftConfig = new SetupModel.SwiftConfig();
            model._backendConfig._swiftConfig._authMode = props.getProperty(SWIFT_AUTHMODE.keyString());
            model._backendConfig._swiftConfig._username = props.getProperty(SWIFT_USERNAME.keyString());
            model._backendConfig._swiftConfig._password = props.getProperty(SWIFT_PASSWORD.keyString());
            model._backendConfig._swiftConfig._url = props.getProperty(SWIFT_URL.keyString());
            model._backendConfig._swiftConfig._container = props.getProperty(SWIFT_CONTAINER.keyString());
            model._backendConfig._passphrase = props.getProperty(STORAGE_ENCRYPTION_PASSWORD.keyString());
            model._backendConfig._swiftConfig._tenantId = props.getProperty(SWIFT_TENANT_ID.keyString());
            model._backendConfig._swiftConfig._tenantName = props.getProperty(SWIFT_TENANT_NAME.keyString());
        }
    }

    public boolean setupFileExists()
    {
        return _setupFile.exists();
    }
}
