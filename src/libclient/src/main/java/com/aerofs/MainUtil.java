package com.aerofs;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesRenderer;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.InitErrors;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgUtils;
import com.aerofs.lib.configuration.ClientConfigurationLoader;
import com.aerofs.lib.configuration.ClientConfigurationLoader.HttpConfigException;
import com.aerofs.lib.configuration.ClientConfigurationLoader.RenderConfigException;
import com.aerofs.lib.configuration.ClientConfigurationLoader.SiteConfigException;
import com.aerofs.lib.ex.*;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.LogLevel;
import com.google.common.collect.ImmutableMap;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import static com.aerofs.defects.Defects.newDefect;
import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.aerofs.defects.Defects.newMetric;


public class MainUtil
{
    private final static Logger l = Loggers.getLogger(MainUtil.class);
    private static boolean s_exceptionsRegistered = false;

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
            // way too much noise at DEBUG level when connection is closed abruptly
            LogUtil.setLevel(SslHandler.class, Level.INFO);
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
            System.err.println("error starting log subsystem");
            e.printStackTrace(System.err);
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

    public static void initializeConfigurationSystem(String appRoot, String rtroot, @Nullable Map<String, String> additionalProps)
    {
        try {
            ClientConfigurationLoader loader =
                    new ClientConfigurationLoader(appRoot, rtroot, new PropertiesRenderer());

            Properties p = loader.loadConfiguration();
            if (additionalProps != null) {
                additionalProps.forEach(p::setProperty);
            }
            ConfigurationProperties.setProperties(p);
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
            l.warn("The property java.library.path could not be set to {}", appRoot, e);
        }
    }

    public static void initDriver(String logFileName, String rtRoot)
    {
        OSUtil.get().loadLibrary("aerofsd");
        Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        if (rootLogger.isTraceEnabled() || rootLogger.isDebugEnabled()) {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LDEBUG);
        } else if (rootLogger.isInfoEnabled()) {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LINFO);
        } else if (rootLogger.isWarnEnabled()) {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LWARN);
        } else {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LERROR);
        }
    }

    /**
     * Register exception types from lib
     * Modules using the lib module should call this method in a static block in order to get
     * exceptions from the wire converted back to the appropriate types
     */
    public static void registerLibExceptions()
    {
        if (s_exceptionsRegistered) return;

        // Register exception types from lib
        Exceptions.registerExceptionTypes(
                new ImmutableMap.Builder<Type, Class<? extends AbstractExWirable>>().put(
                        Type.DEVICE_ID_ALREADY_EXISTS, ExDeviceIDAlreadyExists.class)
                        .put(Type.ALREADY_INVITED, ExAlreadyInvited.class)
                        .put(Type.UPDATING, ExUpdating.class)
                        .put(Type.INDEXING, ExIndexing.class)
                        .put(Type.NOT_SHARED, ExNotShared.class)
                        .put(Type.PARENT_ALREADY_SHARED, ExParentAlreadyShared.class)
                        .put(Type.CHILD_ALREADY_SHARED, ExChildAlreadyShared.class)
                        .put(Type.DEVICE_OFFLINE, ExDeviceOffline.class)
                        .put(Type.NOT_DIR, ExNotDir.class)
                        .put(Type.NOT_FILE, ExNotFile.class)
                        .put(Type.UI_MESSAGE, ExUIMessage.class)
                        .put(Type.NOT_AUTHENTICATED, ExNotAuthenticated.class)

                        // exception used by shared folder rules
                        .put(Type.SHARING_RULES_WARNINGS, ExSharingRulesWarning.class)

                        // The following exceptions are consumed by Python clients only. No need to
                        // list them here for the time being.
                        /*
                        .put(Type.NO_ADMIN_OR_OWNER, ExNoAdminOrOwner.class)
                        .put(Type.INVALID_EMAIL_ADDRESS, ExInvalidEmailAddress.class)
                        */

                        .build());

        s_exceptionsRegistered = true;
    }

    public static void setDefaultUncaughtExceptionHandler()
    {
        SystemUtil._h.set(cause -> {
            if (cause instanceof ExDBCorrupted) {
                ExDBCorrupted corrupted = (ExDBCorrupted) cause;
                newMetric("sqlite.corrupt")
                        .setMessage(corrupted._integrityCheckResult)
                        .sendAsync();
                l.error(corrupted._integrityCheckResult);
                ExitCode.CORRUPTED_DB.exit();
            } else {
                newDefect("system.fatal")
                        .setMessage("FATAL:")
                        .setException(cause)
                        .sendSyncIgnoreErrors();
            }
        });
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            newDefectWithLogs("system.uncaught_exception")
                    .setMessage("uncaught exception from " +
                            t.getName() + ". program exits now.")
                    .setException(e)
                    .sendSyncIgnoreErrors();
            // must abort the process as the abnormal thread can no longer run properly
            SystemUtil.fatal(e);
        });
    }
}
