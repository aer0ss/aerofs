package com.aerofs.controller;

import com.aerofs.proto.Sp.RegisterDeviceCall.Interface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class InterfacesUtil
{
    /**
     * Format mac address bytes to visually appealing and standard xx:xx:xx:xx:xx:xx format. Returns
     * empty string when bytes is null.
     */
    private static String formatMacAddress(@Nullable byte[] bytes)
    {
        String result = "";
        if (bytes != null) {
            StringBuilder sb = new StringBuilder();

            for (byte b : bytes) {
                if (sb.length() > 0) {
                    sb.append(':');
                }

                sb.append(String.format("%02x", b));
            }
            result = sb.toString();
        }

        return result;
    }

    /**
     * Get protobuf formatted system interface information.
     */
    public static ImmutableList<Interface> getSystemInterfaces()
            throws SocketException
    {
        ImmutableList.Builder<Interface> interfaces = new ImmutableList.Builder<Interface>();

        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface ni : Collections.list(nets)) {
            Interface.Builder builder = Interface.newBuilder()
                    .setName(ni.getName())
                    .setMac(formatMacAddress(ni.getHardwareAddress()));

            List<String> ips = Lists.newLinkedList();
            for (InetAddress inetAddress : Collections.list(ni.getInetAddresses())) {
                ips.add(inetAddress.getHostAddress());
            }

            builder.addAllIps(ips);
            interfaces.add(builder.build());
        }

        return interfaces.build();
    }
}