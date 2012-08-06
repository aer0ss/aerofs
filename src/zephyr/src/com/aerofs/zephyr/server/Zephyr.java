package com.aerofs.zephyr.server;

import com.aerofs.lib.Util;
import com.aerofs.zephyr.core.Dispatcher;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;

import static com.aerofs.lib.Util.join;
import static com.aerofs.lib.Util.setupLog4JLayoutAndAppenders;

public class Zephyr
{
    public static void main(String args[])
            throws IOException
    {
        // get the command-line arguments

        if (args.length < 4) {
            System.err.println("usage: <prog_name> " +
                    "[listen_host] [listen_port] " +
                    "[zephyr_log_file_path] [zephyr_log_file_name]");
            System.exit(1);
        }

        String host = args[0];
        short port = Short.parseShort(args[1]);
        String zephyrLogFilePath = args[2];
        String zephyrLogFilename = args[3];

        // setup the logger to log to the console

        setupLog4JLayoutAndAppenders(join(zephyrLogFilePath, zephyrLogFilename), true, false);
        Logger.getRootLogger().setLevel(Level.INFO);

        // run zephyr

        try {
            Dispatcher d = new Dispatcher();
            d.init_();

            Util.l().info("Zephyr: " + host + ":" + port);
            ZephyrServer z = new ZephyrServer(host, port, d);
            z.init();

            d.run(); // blocking run
        } catch (IOException e) {
            e.printStackTrace();
            Util.l().error("zephyr run fail on" + host + ":" + port, e);
        }
    }
}
