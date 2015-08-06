/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.google.protobuf.ByteString;

/**
 * Simple class to store Device Name on Zephyr
 * for Network Diagnostic troubleshooting.
 */
public class ZephyrDeviceWithName {
    private ByteString _did;
    private String _deviceName;
    private String _email;

    public ZephyrDeviceWithName(ByteString did, String deviceName, String email){
        _did = did;
        _deviceName = deviceName;
        _email = email;
    }

    public ByteString getDid(){
        return _did;
    }

    public String getDeviceName() {
        return _deviceName;
    }

    public String getEmail() {
        return _email;
    }
}
