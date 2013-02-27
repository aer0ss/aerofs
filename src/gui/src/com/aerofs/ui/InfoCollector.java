/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.Loggers;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExDaemonFailedToStart;
import org.slf4j.Logger;

public class InfoCollector
{
    private static final Logger l = Loggers.getLogger(InfoCollector.class);
    private final String APP_NAME = "umdc";

    private void startImpl(String cmd) throws ExDaemonFailedToStart
    {
        l.info("starting info collector");

        String jarPath = Util.join(AppRoot.abs(), "aerofs.jar");

        try {
            SystemUtil.execBackground("java", "-Xmx64m", "-jar",
                    jarPath, Cfg.absRTRoot(), APP_NAME, cmd);
        } catch (Exception e) {
            throw new ExDaemonFailedToStart(e);
        }
    }

    public void startUploadDatabase()
            throws ExDaemonFailedToStart
    {
        startImpl("UPLOAD_DATABASE");
    }
}
