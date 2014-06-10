package com.aerofs.xray.server;

import com.aerofs.base.Loggers;
import com.aerofs.xray.server.core.Dispatcher;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class XRay
{
    private static final Logger l = Loggers.getLogger(XRay.class);

    public static void main(String args[])
            throws IOException
    {
        logBanner("banner.txt");

        // get the command-line arguments

        if (args.length < 2) {
            System.err.println("usage: <prog_name> [listen_host] [listen_port]");
            System.exit(1);
        }

        // setup the accept socket

        String host = args[0];
        short port = Short.parseShort(args[1]);

        // run xray

        try {
            l.info("xray: {}:{}", host, port);
            XRayServer x = new XRayServer(host, port, new Dispatcher());
            x.init();
            x.start(); // blocking run
        } catch (IOException e) {
            l.error("xray fail run on {}:{} err:{}", host, port, e);
        }
    }

    private static void logBanner(String bannerFilename)
    {
        BufferedReader bannerReader = null;
        try {
            File bannerFile = new File(bannerFilename);

            if (!bannerFile.exists() || !bannerFile.canRead()) {
                l.warn("xray cannot load banner:{}", bannerFilename);
                return;
            }

            bannerReader = new BufferedReader(new FileReader(bannerFile));
            String bannerLine;
            while ((bannerLine = bannerReader.readLine()) != null) {
                l.info("{}", bannerLine);
            }
        } catch (IOException e) {
            l.warn("xray fail banner load err:{}", e);
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
