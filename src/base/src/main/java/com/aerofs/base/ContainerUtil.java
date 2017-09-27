package com.aerofs.base;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.*;
import java.util.Map.Entry;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class ContainerUtil {
    /**
     * Defer waiting for container deps until the JVM is started, for maximum latency hiding
     */
    public static void barrier() {
        System.out.println(".----------");
        for (Entry<String, String> e : System.getenv().entrySet()) {
            String k = e.getKey();
            if (!k.endsWith("_TCP_PORT")) continue;
            int i = k.indexOf('.');
            if (i == -1 || !k.substring(i + 1).startsWith("SERVICE_PORT_")) continue;
            waitPort(k.substring(0, i).toLowerCase() + ".service", e.getValue());
        }
        System.out.println("`----------");
        System.out.flush();
    }

    public static void waitPort(String svc, String port) {
        System.out.println("| waiting for " + svc + ":" + port + " ...");
        System.out.flush();
        while (!testPort(svc, port)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    private static boolean testPort(String svc, String port) {
        try (Socket s = new Socket()){
            s.connect(new InetSocketAddress(svc, Integer.valueOf(port)), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void mkdb(String host, String db, String user, String password) throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver").asSubclass(Driver.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        Connection c = DriverManager.getConnection("jdbc:mysql://" + host, user, password);
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("create database if not exists " + db);
        }
        c.close();
    }
}
