package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesRenderer;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.InitErrors;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgUtils;
import com.aerofs.lib.configuration.ClientConfigurationLoader;
import com.aerofs.lib.configuration.ClientConfigurationLoader.HttpConfigException;
import com.aerofs.lib.configuration.ClientConfigurationLoader.RenderConfigException;
import com.aerofs.lib.configuration.ClientConfigurationLoader.SiteConfigException;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class MainUtil
{
    private final static Logger l = Loggers.getLogger(MainUtil.class);

    private static String getProgramBanner(String rtRoot, String app)
    {
        String strDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        return app + " ========================================================\n" + CfgUtils.getVersion()
                + " " + strDate + " " + AppRoot.abs() + " " + new File(rtRoot).getAbsolutePath();
    }

    public static void initializeLogging(String rtRoot, String prog)
    {
        try {
            final Level logLevel =
                    CfgUtils.lotsOfLotsOfLog(rtRoot) ? Level.TRACE
                            : CfgUtils.lotsOfLog(rtRoot) ? Level.DEBUG
                            : Level.INFO;

            LogUtil.setLevel(logLevel);
            LogUtil.enableFileLogging(rtRoot + "/" + prog + ".log");

            final Date start = new Date();

            // Uncomment this for easier debugging
//            LogUtil.enableConsoleLogging();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                l.debug("TERMINATED [{} / {}]", f.format(start), f.format(new Date()));
            }));

        } catch (Exception e) {
            // FIXME(jP): Can we remove this? Does it ever work?
            String msg = "Error starting log subsystem: " + Util.e(e);
            // I don't know how to output to system.logging on mac/linux. so use
            // the command line as a quick/dirty approach
            try {
                SystemUtil.execForeground("logger", msg);
            } catch (Exception e2) {
                // ignored
            }

            System.err.println(msg);
            ExitCode.FAIL_TO_INITIALIZE_LOGGING.exit();
        }

        l.info("{}", getProgramBanner(rtRoot, prog));

        if (Cfg.useProfiler()) {
            l.debug("profiler: {}", Cfg.profilerStartingThreshold());
        }
    }



    static void createRtRootIfNotExists(String rtRoot)
    {
        File rtRootFile = new File(rtRoot);
        if (rtRootFile.mkdirs()) {
            // Set permissions on the rtroot to 700, to prevent other users from trying to read
            // our logs or steal our cert/key.  The java permissions API is terrible.
            // Note: this doesn't actually exclude other users via ACLs on Windows, but it does
            // improve the situation on Unixes.
            rtRootFile.setReadable(false, false);   // chmod a-r
            rtRootFile.setWritable(false, false);   // chmod a-w
            rtRootFile.setExecutable(false, false); // chmod a-x
            rtRootFile.setReadable(true, true);     // chmod o+r
            rtRootFile.setWritable(true, true);     // chmod o+w
            rtRootFile.setExecutable(true, true);   // chmod o+x
        }
    }

    public static void initializeConfigurationSystem(String appRoot, String rtroot)
    {
        try {
            ClientConfigurationLoader loader =
                    new ClientConfigurationLoader(appRoot, rtroot, new PropertiesRenderer());

            ConfigurationProperties.setProperties(loader.loadConfiguration());
            l.debug("Client configuration initialized");
        } catch (SiteConfigException e) {
            // site config should be properly provisioned by the installation, so any error in
            // loading site config is likely caused by bad installation.
            InitErrors.setErrorMessage(
                    L.product() + " encountered an error while loading the configuration. The " +
                            L.product() + " installation is likely damaged.",
                    "Please download a new installer, reinstall " + L.product() + ", and try " +
                            "again.");

        } catch (HttpConfigException e) {
            // failing to load http config means that we've failed to load from both the remote
            // _and_ local cache.
            InitErrors.setErrorMessage(
                    L.product() + " encountered an error while loading the configuration from " +
                            "the " + L.brand() + " Appliance.",
                    "Please make sure your computer is connected to the network and " +
                            L.product() + " can make network connections to the " +
                            L.brand() + " Appliance on the " +
                            "<a href=\"http://ae.ro/1SJWimL\">required ports</a>.\n\n" +
                            "Please contact your system administrator if the problem persists.");
        } catch (RenderConfigException e) {
            // failing to render the configuration means that the config server and clients are
            // working with bad values. In other words, the whole system is misconfigured.
            //
            // This situation is dire and the user should just reach out to AeroFS Support.
            InitErrors.setErrorMessage(
                    L.product() + " encountered an error while loading the configuration. The " +
                            L.brand() + " Appliance is likely misconfigured.",
                    "Please contact your system administrator and reach out to " +
                            L.brand() + " Support.");
        }
    }

    public static void setLibraryPathToApproot(String appRoot)
    {
        // Set the library path to be APPROOT to avoid library not found exceptions
        // {@see http://blog.cedarsoft.com/2010/11/setting-java-library-path-programmatically/}
        try {
            System.setProperty("java.library.path", appRoot);

            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(null, null); // force sys_paths to re-evaluate java.library.path
        } catch (Exception e) {
            // ignored
            l.warn("The property java.library.path could not be set to {} - {}",appRoot, Util.e(e));
        }
    }
}
