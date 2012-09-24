package com.aerofs.zephyr.server;

import com.aerofs.lib.Util;
import com.aerofs.zephyr.core.Dispatcher;
import org.apache.log4j.xml.DOMConfigurator;

import java.io.IOException;

public class Zephyr
{
    public static void main(String args[])
            throws IOException
    {
        // get the command-line arguments

        if (args.length < 2) {
            System.err.println("usage: <prog_name> [listen_host] [listen_port]");
            System.exit(1);
        }

        // setup the accept socket

        String host = args[0];
        short port = Short.parseShort(args[1]);

        // setup the logger

        DOMConfigurator.configure("log4j.xml");

        // run zephyr

        try {
            Dispatcher d = new Dispatcher();
            d.init_();

            Util.l().info("zephyr: " + host + ":" + port);
            ZephyrServer z = new ZephyrServer(host, port, d);
            z.init();

            d.run(); // blocking run
        } catch (IOException e) {
            e.printStackTrace();
            Util.l().error("zephyr run fail on" + host + ":" + port, e);
        }
    }
}
