/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.proto.Diagnostics;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * This is a wrapper class to assist with displaying device name
 * when accessing the network diagnostic. With the device name,
 * user can easily view the email:device_name within the network.
 */

public class TransportDiagnosticsWithDeviceName {
    private Diagnostics.TransportDiagnostics _transportDiagnostics = null;
    private List<TCPDeviceWithName> _tcpDevicesWithName;
    private List<ZephyrDeviceWithName> _zephyrDevicesWithName;
    private Diagnostics.PBInetSocketAddress _listeningAddress;
    private Diagnostics.ServerStatus _zephyrStatus;

    public TransportDiagnosticsWithDeviceName(Diagnostics.TransportDiagnostics transportDiagnostics) {
        _transportDiagnostics = transportDiagnostics;
        setUpTransportDeviceInfo();
    }

    protected Diagnostics.TransportDiagnostics getTransportDiagnostics() {
        return  _transportDiagnostics;
    }

    protected boolean hasTransportDiagnostics() {
        return _transportDiagnostics != null;
    }

    protected void addToTCPDeviceList(TCPDeviceWithName tcpDeviceWithName){
        _tcpDevicesWithName.add(tcpDeviceWithName);
    }


    protected List<TCPDeviceWithName> getTcpDevicesWithName(){
        return _tcpDevicesWithName;
    }

    protected boolean hasTcpDiagnostics(){
        return hasTransportDiagnostics() && _transportDiagnostics.hasTcpDiagnostics();
    }


    protected void addToZephyrDeviceList(ZephyrDeviceWithName zephyrDeviceWithName){
        _zephyrDevicesWithName.add(zephyrDeviceWithName);
    }

    protected List<ZephyrDeviceWithName> getZephyrDevicesWithName(){
        return _zephyrDevicesWithName;
    }

    protected Diagnostics.PBInetSocketAddress getListeningAddress(){
        return _listeningAddress;
    }

    protected boolean hasZephyrDiagnostics(){
        return hasTransportDiagnostics() && _transportDiagnostics.hasZephyrDiagnostics();
    }

    protected void setListeningAddress(Diagnostics.PBInetSocketAddress listeningAddress){
        _listeningAddress = listeningAddress;
    }

    protected Diagnostics.ServerStatus getZephyrServer(){
        return _zephyrStatus;
    }

    protected void setZephyrServer(Diagnostics.ServerStatus serverStatus){
        _zephyrStatus = serverStatus;
    }

    protected int getReachableTCPDevices(){ return _tcpDevicesWithName.size(); }

    protected int getReachableZephyrDevices(){ return _zephyrDevicesWithName.size(); }

    /**
     * With _transportDiagnostics, pre-populate the network devices with their respective device info
     */
    private void setUpTransportDeviceInfo(){
        _tcpDevicesWithName = Lists.newArrayList();
        _zephyrDevicesWithName = Lists.newArrayList();

        setListeningAddress(_transportDiagnostics.getTcpDiagnostics().getListeningAddress());
        setZephyrServer(_transportDiagnostics.getZephyrDiagnostics().getZephyrServer());
    }


}
