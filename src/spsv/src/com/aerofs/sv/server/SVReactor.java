package com.aerofs.sv.server;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.MessagingException;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;
import org.slf4j.Logger;

import com.aerofs.base.C;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.sv.common.EmailCategory;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVDefect;
import com.aerofs.proto.Sv.PBSVGzippedLog;
import com.aerofs.proto.Sv.PBSVHeader;
import com.aerofs.proto.Sv.PBSVReply;
import com.google.common.collect.Maps;
import java.util.concurrent.Future;

import static com.aerofs.sv.server.SVParam.SV_NOTIFICATION_RECEIVER;
import static com.aerofs.sv.server.SVParam.SV_NOTIFICATION_SENDER;
import static java.util.concurrent.TimeUnit.MINUTES;

public class SVReactor
{
    private static final Logger l = Loggers.getLogger(SVReactor.class);

    private static final String DEFECT_LOG_PREFIX = "log.defect-";
    private static final int FILE_BUF_SIZE = C.MB;
    private final SVDatabase _db;
    private final IThreadLocalTransaction<SQLException> _transaction;

    private static final AsyncEmailSender _emailSender = AsyncEmailSender.create();
    private static final AsyncEmailSender _logEmailSender = AsyncEmailSender.createDeprecatedForLogEmails();

    private String _pathDefect;
    private String _pathArchive;
    private final Map<ObfStackTrace, String> _retraceMap = Maps.newHashMap();

    private final Meter _defectMeter = Metrics.newMeter(new MetricName("client", "defect", "all"),
            "defects", MINUTES);

    SVReactor(SVDatabase db, IThreadLocalTransaction<SQLException> transaction)
    {
        _db = db;
        _transaction = transaction;
    }

    void init_()
    {
        _pathDefect = "/var/svlogs_prod/defect";
        _pathArchive = "/var/svlogs_prod/archived";
    }

    // @param client is for tracing only
    PBSVReply react(PBSVCall call, InputStream is, String client)
    {
        l.info(call.getType() + " from " + client);

        PBSVReply.Builder bdReply = PBSVReply.newBuilder();

        try {
            switch (call.getType()) {
            case DEFECT:
                defect(call, is, client);
                break;
            case GZIPPED_LOG:
                gzippedLog(call, is);
                break;
            default:
                throw new Exception("unknown call type: " + call.getType());
            }
        } catch (Exception e) {
            l.error("failed serving request: ", LogUtil.suppress(e, java.io.IOException.class));
            bdReply.setException(Exceptions.toPB(e));
        }

        if (bdReply.hasException()) {
            l.info("reply exception " + bdReply.getException().getType());
        }

        return bdReply.build();
    }

    private void gzippedLog(PBSVCall call, InputStream is)
        throws ExProtocolError, IOException
    {
        Util.checkPB(call.hasGzippedLog(), PBSVGzippedLog.class);
        PBSVGzippedLog log = call.getGzippedLog();
        PBSVHeader header = call.getHeader();

        // create log file directory
        String pathDir = _pathArchive + File.separator + header.getUser();
        File parent = new File(pathDir);
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("cannot create " + parent.getAbsolutePath());
            }
        }

        // save the log
        String did = BaseUtil.hexEncode(header.getDeviceId().toByteArray());
        String path = pathDir + File.separator + did + '.' + log.getName();
        byte[] bs = new byte[FILE_BUF_SIZE];
        int len;
        FileOutputStream zlogos = new FileOutputStream(path);
        try {
            while ((len = is.read(bs)) > 0) { zlogos.write(bs, 0, len); }
        } finally {
            zlogos.close();
        }
    }

    public static void emailSVNotification(final String subject, final String body)
    {
        try {
            _logEmailSender.sendDeprecatedNotificationEmail(SV_NOTIFICATION_SENDER,
                    SV_NOTIFICATION_SENDER, SV_NOTIFICATION_RECEIVER, null, subject, body, null);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }
    private void defect(PBSVCall call, InputStream is, String client)
        throws ExProtocolError, IOException, MessagingException, SQLException
    {
        Util.checkPB(call.hasDefect(), PBSVDefect.class);

        _defectMeter.mark();

        PBSVDefect defect = call.getDefect();
        PBSVHeader header = call.getHeader();

        // Check blacklist.  We have a blacklist to allow us to mitigate resource consumption by
        // overenthusiastic clients.  However, we should never reject a priority defect.
        if (defect.getAutomatic() && isBlackListed(header.getUser())) {
            l.warn("rejecting defect from blacklisted client {} {}", header.getUser(),
                    header.getDeviceId());
            throw new IOException("Bug report collection disabled for " + header.getUser() +
                    " due to excessive resource usage");
        }

        StringBuilder javaEnv = new StringBuilder();
        for (int i = 0; i < defect.getJavaEnvNameCount(); i++) {
            javaEnv.append(defect.getJavaEnvName(i));
            javaEnv.append('=');
            javaEnv.append(defect.getJavaEnvValue(i));
            javaEnv.append('\n');
        }

        String desc = defect.getDescription();
        if (defect.hasStacktrace()) {
            String unobfuscatedStackTrace = retrace(defect.getStacktrace(), header.getVersion());
            desc = desc + "\n" + unobfuscatedStackTrace;
        }

        // save to db
        _transaction.begin();
        int id = _db.insertDefect(header, client, defect.getAutomatic(), desc, defect.getCfgDb(),
                javaEnv.toString());
        _transaction.commit();

        String did = header.hasDeviceId() ? BaseUtil.hexEncode(header.getDeviceId().toByteArray()) :
                "unknown";

        if (!defect.getAutomatic()) {
            // old clients may not populate the contact email field
            String contactEmail = defect.hasContactEmail() ? defect.getContactEmail() :
                    header.getUser();
            emailCustomerSupport(desc, id, contactEmail, did);
        }

        // create defect file directory
        File defectRoot = new File(_pathDefect);
        File userDefectFolder = new File(defectRoot, header.getUser());
        File didDefectFolder = new File(userDefectFolder, did);
        File thisDefectFile = new File(didDefectFolder, DEFECT_LOG_PREFIX + id + ".zip");

        if (!didDefectFolder.exists()) {
            if (!didDefectFolder.mkdirs()) {
                throw new IOException("cannot create " + didDefectFolder.getAbsolutePath());
            }
        }

        // save defect logs
        byte[] bs = new byte[FILE_BUF_SIZE];
        int len;
        FileOutputStream zlogos = new FileOutputStream(thisDefectFile);
        try {
            while ((len = is.read(bs)) > 0) { zlogos.write(bs, 0, len); }
        } finally {
            zlogos.close();
        }
        // Add symbolic link to the file from the root folder.  This way, old scripts that refer
        // to defects by defect ID can still find the defect at the expected location
        // But Java's File sucks and can't create symlinks, so we shell out to ln -s :(
        // syntax: ln -s <existing file> <folder to create symlink in>
        SystemUtil.execBackground("ln", "-s", thisDefectFile.getAbsolutePath(),
                defectRoot.getAbsolutePath());

        // send notification email
        String body = desc
                + "\n\n" + header.getVersion() + " on dev " + did
                + "\n" + defect.getCfgDb()
                + "\n\n" + javaEnv.toString();

        String subject = (defect.getAutomatic() ? "" : "Priority ") + " Defect " +
            id + ": " + header.getUser();

        emailSVNotification(subject, body);
    }

    private boolean isBlackListed(String user)
    {
        String blacklistPath = "/etc/sv/blacklist.conf";
        BufferedReader br = null;
        // Blacklist should avoid blocking all defects on unexpected errors.
        boolean toReturn = false;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(blacklistPath),
                    Charset.forName("UTF-8")));
            String line;
            while ((line = br.readLine()) != null) {
                if (user.equals(line)) {
                    // This user was found in the blacklist.
                    toReturn = true;
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            // If no blacklist file found, no users should be marked as blacklisted
            l.warn("blacklist: Couldn't open {}", blacklistPath);
            return false;
        } catch (IOException e) {
            l.warn("blacklist: IOException reading {}", blacklistPath);
            return false;
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // ignored, file is being closed already
                }
            }
        }
        return toReturn;
    }

    private void emailCustomerSupport(String desc, int id, String contactEmail, String did)
            throws MessagingException, UnsupportedEncodingException
    {
        // truncate the description to leave only the message the user provides
        int eom = desc.indexOf(LibParam.END_OF_DEFECT_MESSAGE);
        String msg =  eom >= 0 ? desc.substring(0, eom) : desc;

        String body = msg + "\n\n" +
                "contact email: " + contactEmail + "\n" +
                "device: " + did;

        Future<Void> f = _emailSender.sendPublicEmail(contactEmail, contactEmail,
                WWW.SUPPORT_EMAIL_ADDRESS, null, L.brand() + " Problem # " + id, body, null,
                EmailCategory.SUPPORT);
        try {
            f.get(); // block to make sure email reaches support system
        } catch (Exception e) {
            // the only type of exception that AsyncEmailSender.sendEmail() can throw throw the
            // executor service is a MessagingException
            throw new MessagingException(e.getCause().getMessage());
        }
    }

    private String retrace(String exString, String version)
            throws IOException
    {
        synchronized (_retraceMap) {
            ObfStackTrace obfStack = new ObfStackTrace(exString, version);

            if (_retraceMap.containsKey(obfStack)) {
                return _retraceMap.get(obfStack);
            } else {
                OutArg<String> retracedEx = new OutArg<String>();
                File f = FileUtil.createTempFile("afs", "retrace", null);
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                try {
                    bw.write(exString);
                } finally {
                    bw.close();
                }

                //yuris note: please make sure that the "proguard" package is installed
                SystemUtil.execForeground(retracedEx, "java", "-jar", "/usr/share/java/retrace.jar",
                        "/maps/aerofs-" + version + "-public.map", f.getAbsolutePath());

                FileUtil.deleteOrOnExit(f);

                _retraceMap.put(obfStack, retracedEx.get());
                return retracedEx.get();
            }
        }
    }

}
