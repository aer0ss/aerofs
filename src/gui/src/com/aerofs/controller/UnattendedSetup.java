/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgDatabase.Key;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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
     *  first_name = John
     *  last_name = Smith
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
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(_setupFile));
        try {
            props.load(in);
        } finally {
            in.close();
        }

        model.setUserID(props.getProperty(PROP_USERID));
        model.setPassword(props.getProperty(PROP_PASSWORD));
        model.setDeviceName(props.getProperty(PROP_DEVICE, model.getDeviceName()));

        model._localOptions._rootAnchorPath = props.getProperty(PROP_ROOT, model._localOptions._rootAnchorPath);

        model._storageType = StorageType.fromString(props.getProperty(PROP_STORAGE_TYPE));

        String s3BucketId = props.getProperty(Key.S3_BUCKET_ID.keyString());
        if (s3BucketId != null) {
            model._s3Config._bucketID = s3BucketId;
            model._s3Config._accessKey = props.getProperty(Key.S3_ACCESS_KEY.keyString());
            model._s3Config._secretKey = props.getProperty(Key.S3_SECRET_KEY.keyString());
            model._s3Config._passphrase = props.getProperty(Key.S3_ENCRYPTION_PASSWORD.keyString());

            if (model._storageType == null) model._storageType = StorageType.S3;
        }
    }

    public boolean setupFileExists()
    {
        return _setupFile.exists();
    }
}
