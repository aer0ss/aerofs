/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.ids.UserID;
import com.aerofs.lib.S;
import com.google.protobuf.ByteString;

/**
 * Simple class to store Device Name on TCP
 * for Network Diagnostic troubleshooting.
 */

public class TCPDeviceWithName implements Comparable<TCPDeviceWithName>{
    private ByteString _did;
    private String _ipAddress;
    private String _deviceName;
    private String _email;

    public TCPDeviceWithName(ByteString did, String ipAddress, String deviceName, String email){
        _did = did;
        _ipAddress = ipAddress;
        _deviceName = deviceName;
        _email = checkIdForTeamServer(email);
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

    private String checkIdForTeamServer(String id){
        if (UserID.fromInternal(id).isTeamServerID()){
            return S.TEAM_SERVER_USER_ID;
        }
        return id;
    }

    /*
        Used for sorting TCP devices under Network Diagnostics. It will sort the devices
        alphabetically by user id then by device name.
     */
    @Override
    public int compareTo(TCPDeviceWithName device) {
        int i = this.getEmail().compareToIgnoreCase(device.getEmail());
        if (i == 0) {
            i = this.getDeviceName().compareToIgnoreCase(device.getDeviceName());
        }

        return i;
    }

}
