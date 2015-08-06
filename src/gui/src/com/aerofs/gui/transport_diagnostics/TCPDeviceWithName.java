/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.google.protobuf.ByteString;

/**
 * Simple class to store Device Name on TCP
 * for Network Diagnostic troubleshooting.
 */

public class TCPDeviceWithName {
    private ByteString _did;
    private String _ipAddress;
    private String _deviceName;
    private String _email;

    public TCPDeviceWithName(ByteString did, String ipAddress, String deviceName, String email){
        _did = did;
        _ipAddress = ipAddress;
        _deviceName = deviceName;
        _email = email;
    }

    public ByteString getDid(){
        return _did;
    }

    public String getIpAddress(){
        return _ipAddress;
    }

    public String getDeviceName() {
        return _deviceName;
    }

    public String getEmail() {
        return _email;
    }

}
