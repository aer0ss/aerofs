package com.aerofs.polaris.external_api;

import com.aerofs.polaris.external_api.rest.util.Version;

public class Constants
{
    public static final String EXTERNAL_API_VERSION = "/v{version: [0-9]+\\.[0-9]+}";
    public static final Version HIGHEST_SUPPORTED_VERSION = new Version(1, 3);
    public final static String REQUEST_VERSION = "api-request-version";
    public final static String EXTERNAL_API_LOCATION = "http://polaris.service:8086/api";

    public static final String APPDATA_FOLDER_NAME = ".appdata";
}
