/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.ids.UserID;
import com.aerofs.lib.S;
import com.google.protobuf.ByteString;

/**
 * Simple class to store Device Name on Zephyr
 * for Network Diagnostic troubleshooting.
 */
public class ZephyrDeviceWithName implements Comparable<ZephyrDeviceWithName>{
    private ByteString _did;
    private String _deviceName;
    private String _email;

    public ZephyrDeviceWithName(ByteString did, String deviceName, String email){
        _did = did;
        _deviceName = deviceName;
        _email = checkIdForTeamServer(email);
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

    private String checkIdForTeamServer(String id){
        if (UserID.fromInternal(id).isTeamServerID()) {
            return S.TEAM_SERVER_USER_ID;
        }
        return id;
    }

    /*
        Used for sorting Zephyr devices under Network Diagnostics. It will sort the devices
        alphabetically by user id then by device name.
     */
    @Override
    public int compareTo(ZephyrDeviceWithName device) {
        int i = this.getEmail().compareToIgnoreCase(device.getEmail());
        if (i == 0) {
            i = this.getDeviceName().compareToIgnoreCase(device.getDeviceName());
        }

        return i;
    }

}
