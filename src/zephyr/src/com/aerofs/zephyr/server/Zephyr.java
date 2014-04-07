package com.aerofs.zephyr.server;

import com.aerofs.base.DefaultUncaughtExceptionHandler;
import com.aerofs.base.Loggers;
import com.aerofs.zephyr.server.core.Dispatcher;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Zephyr
{
    private static final Logger l = Loggers.getLogger(Zephyr.class);

    public static void main(String args[])
            throws IOException
    {
        logBanner("banner.txt");

        // get the command-line arguments

        if (args.length < 2) {
            System.err.println("usage: <prog_name> [listen_host] [listen_port]");
            System.exit(1);
        }

        // setup the catch-all exception handler

        Thread.setDefaultUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());

        // setup the accept socket

        String host = args[0];
        short port = Short.parseShort(args[1]);

        // run zephyr

        try {
            l.info("zephyr: {}:{}", host, port);
            ZephyrServer z = new ZephyrServer(host, port, new Dispatcher());
            z.init();
            z.start(); // blocking run
        } catch (IOException e) {
            l.error("zephyr fail run on {}:{} err:{}", host, port, e);
        }
    }

    private static void logBanner(String bannerFilename)
    {
        BufferedReader bannerReader = null;
        try {
            File bannerFile = new File(bannerFilename);
            if (!bannerFile.exists() || !bannerFile.canRead()) {
                l.warn("zephyr cannot load banner:{}", bannerFilename);
                return;
            }

            bannerReader = new BufferedReader(new FileReader(bannerFile));
            String bannerLine;
            l.info("=====================================");
            l.info("I                                   I");
            while ((bannerLine = bannerReader.readLine()) != null) {
                l.info("I " + bannerLine + " I");
            }
            l.info("I                                   I");
            l.info("=====================================");
        } catch (IOException e) {
            l.warn("zephyr fail banner load err:{}", e);
        } finally {
            try {
                if (bannerReader != null) {
                    bannerReader.close();
                }
            } catch (IOException e) {
                // ignore...
            }
        }
    }
}
