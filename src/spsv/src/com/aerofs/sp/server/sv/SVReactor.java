package com.aerofs.sp.server.sv;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.MessagingException;

import com.aerofs.lib.Param.SV;
import com.aerofs.proto.Sv.PBSVEmail;
import com.aerofs.servletlib.db.IThreadLocalTransaction;
import org.apache.log4j.Logger;

import com.aerofs.lib.C;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.raven.RavenClient;
import com.aerofs.lib.raven.RavenTrace;
import com.aerofs.lib.raven.RavenTraceElement;
import com.aerofs.lib.raven.RavenUtils;
import com.aerofs.lib.spsv.sendgrid.EmailCategory;
import com.aerofs.proto.Sv.PBSVAnalytics;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVDefect;
import com.aerofs.proto.Sv.PBSVEvent;
import com.aerofs.proto.Sv.PBSVGzippedLog;
import com.aerofs.proto.Sv.PBSVHeader;
import com.aerofs.proto.Sv.PBSVReply;
import com.google.common.collect.Maps;
import java.util.concurrent.Future;

import static com.aerofs.sp.server.SPSVParam.SV_NOTIFICATION_RECEIVER;
import static com.aerofs.sp.server.SPSVParam.SV_NOTIFICATION_SENDER;


public class SVReactor
{
    private static final Logger l = Util.l(SVReactor.class);

    private static final String DEFECT_LOG_PREFIX = "log.defect-";
    private static final int FILE_BUF_SIZE = 1 * C.MB;
    private final SVDatabase _db;
    private final IThreadLocalTransaction<SQLException> _transaction;

    private String _pathDefect;
    private String _pathArchive;
    private String _pathAnalytics;
    private final Map<ObfStackTrace, String> _retraceMap = Maps.newHashMap();

    private static final RavenClient ravenClient =
            new RavenClient("https://79e419090a9049ba98e30674544cfc13:7de75d799f774a6ea8e8f75e9f7eb7ad@sentry.aerofs.com/3");

    SVReactor(SVDatabase db, IThreadLocalTransaction<SQLException> transaction)
    {
        _db = db;
        _transaction = transaction;
    }

    void init_()
    {
        _pathDefect = "/var/svlogs_prod/defect";
        _pathArchive = "/var/svlogs_prod/archived";
        _pathAnalytics = "/var/svlogs_prod/analytics";
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
                gzippedLog(call, is, client);
                break;
            case ANALYTICS:
                analytics(call, is, client);
                break;
            case EVENT:
                event(call, client);
                break;
            case EMAIL:
                email(call);
                break;
            default:
                throw new Exception("unknown call type: " + call.getType());
            }
        } catch (Exception e) {
            l.error("failed serving request: ", e);
            bdReply.setException(Exceptions.toPB(e));
        }

        if (bdReply.hasException()) {
            l.info("reply exception " + bdReply.getException().getType());
        }

        return bdReply.build();
    }

    private void event(PBSVCall call, String client)
        throws ExProtocolError, SQLException
    {
        Util.checkPB(call.hasEvent(), PBSVEvent.class);

        PBSVEvent ev = call.getEvent();
        PBSVHeader header = call.getHeader();

        _transaction.begin();
        _db.addEvent(header, ev.getType(), ev.hasDesc() ? ev.getDesc() : null, client);
        _transaction.commit();
    }

    private void email(PBSVCall call)
            throws ExProtocolError, MessagingException, UnsupportedEncodingException
    {
        Util.checkPB(call.hasEmail(), PBSVEmail.class);

        PBSVEmail emailContents = call.getEmail();
        Future<Void> f = EmailSender.sendEmail(emailContents.getFrom(), emailContents.getFromName(),
                emailContents.getTo(),
                emailContents.hasReplyTo() ? emailContents.getReplyTo() : null,
                emailContents.getSubject(), emailContents.getTextBody(),
                emailContents.hasHtmlBody() ? emailContents.getHtmlBody() : null,
                emailContents.getUsingSendgrid(),
                emailContents.hasCategory() ? EmailCategory.valueOf(emailContents.getCategory()) :
                                              null);

        try {
            f.get(); //block under email either sends or fails
        } catch (Exception e) {
            // the only type of exception that EmailSender.sendEmail() can throw throw the
            // executor service is a MessagingException
            throw new MessagingException(e.getCause().getMessage());
        }
    }


    private void gzippedLog(PBSVCall call, InputStream is, String client)
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
        String did = Util.hexEncode(header.getDeviceId().toByteArray());
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

    private void analytics(PBSVCall call, InputStream is, String client)
        throws ExProtocolError, IOException
    {
        Util.checkPB(call.hasAnalytics(), PBSVAnalytics.class);
        //PBSVAnalytics analytics = call.getAnalytics();
        PBSVHeader header = call.getHeader();

        // create the directory
        String pathDir = _pathAnalytics + File.separator + header.getUser();
        File parent = new File(pathDir);
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("cannot create " + parent.getAbsolutePath());
            }
        }

        // save the dump
        String did = Util.hexEncode(header.getDeviceId().toByteArray());
        String path = pathDir + File.separator + did + ".db." +
                new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date()) + ".gz";

        FileOutputStream os = new FileOutputStream(path);
        try {
                Util.copy(is, os);
        } finally {
            os.close();
        }
    }

    public static void emailSVNotification(final String subject, final String body)
    {
        try {
            EmailSender.sendEmail(SV_NOTIFICATION_SENDER, SV_NOTIFICATION_SENDER,
                    SV_NOTIFICATION_RECEIVER, null, subject, body, null, false, null);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }
    private void defect(PBSVCall call, InputStream is, String client)
        throws ExProtocolError, IOException, MessagingException, SQLException
    {
        Util.checkPB(call.hasDefect(), PBSVDefect.class);
        PBSVDefect defect = call.getDefect();
        PBSVHeader header = call.getHeader();

        StringBuilder javaEnv = new StringBuilder();
        for (int i = 0; i < defect.getJavaEnvNameCount(); i++) {
            javaEnv.append(defect.getJavaEnvName(i));
            javaEnv.append('=');
            javaEnv.append(defect.getJavaEnvValue(i));
            javaEnv.append('\n');
        }

        String desc = defect.getDescription();
        if (defect.hasStacktrace()) {
            desc = desc + "\n" + defect.getStacktrace();

            //log the defect into sentry
            sentry(defect.getStacktrace(),
                    header.getUser(),
                    Util.hexEncode(header.getDeviceId().toByteArray()),
                    header.getVersion());
        }

        // save to db
        String dc = defect.hasCfgDb() ? defect.getCfgDb() : "(unknown)";
        _transaction.begin();
        int id = _db.addDefect(header, client, defect.getAutomatic(), desc, dc, javaEnv.toString());
        _transaction.commit();

        // send email to the customer support system
        int eom = desc.indexOf(C.END_OF_DEFECT_MESSAGE);
        if (eom >= 0) {
            String msg = desc.substring(0, eom);
            Future<Void> f = EmailSender.sendEmail(header.getUser(),
                                  header.getUser(),
                                  SV.SUPPORT_EMAIL_ADDRESS,
                                  null,
                                  S.PRODUCT + " Problem # " + id,
                                  msg,
                                  null,
                                  true,
                                  EmailCategory.SUPPORT);
            try {
                f.get(); // block to make sure email reaches support system
            } catch (Exception e) {
                // the only type of exception that EmailSender.sendEmail() can throw throw the
                // executor service is a MessagingException
                throw new MessagingException(e.getCause().getMessage());
            }
        }

        // create defect file directory
        File parent = new File(_pathDefect);
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("cannot create " + parent.getAbsolutePath());
            }
        }

        // save defect logs
        byte[] bs = new byte[FILE_BUF_SIZE];
        int len;
        FileOutputStream zlogos = new FileOutputStream(
                _pathDefect + File.separator + DEFECT_LOG_PREFIX + id + ".zip");
        try {
            while ((len = is.read(bs)) > 0) { zlogos.write(bs, 0, len); }
        } finally {
            zlogos.close();
        }

        // send notification email
        String body = desc
                + "\n\n" + header.getVersion()
                + "\n" + "DID " + (header.hasDeviceId() ?
                     Util.hexEncode(header.getDeviceId().toByteArray()) : "(unknown)")
                + "\n" + dc
                + "\n\n" + javaEnv.toString();

        String subject = (defect.getAutomatic() ? "" : "Priority ") + " Defect " +
            id + ": " + header.getUser();

        emailSVNotification(subject, body);
    }

    // in short, we want to take something like:
    // at com.aerofs.gui.misc.DlgDefect$2.run(SourceFile:105) and split it into
    // group[1]: com.aerofs.gui.misc.DlgDefect$2 (class)
    // group[2]: run (method)
    // group[3]: 105 (line number)
    // java regex strings requires extra escape characters, so things like \w become \\w
    // group[1] is matched by ([\w\.\$]) which matches all word characters, periods and $'s up to the last period
    // group[2] is matched by ([\w]) which matches all word characters after the last period and before the bracket
    //          for the function
    // group[3] matches any number that is followed by a bracket and some word characters and ends with a bracket
    //
    private final static Pattern PATTERN = Pattern.compile(
            "([\\w\\.\\$]*)\\.([\\w]*)\\([\\w\\.]*:([\\d]*)\\)");

    /**
     * we expect a stack trace generated by Util.stackTrace2String()
     */
    private void sentry(String exString, String user, String deviceId, String version)
            throws ExProtocolError, IOException
    {
        OutArg<String> retracedEx = new OutArg<String>();
        String message;
        Matcher matcher;

        synchronized (_retraceMap) {
            ObfStackTrace obfStack = new ObfStackTrace(exString, version);

            if (_retraceMap.containsKey(obfStack)) {
                retracedEx.set(_retraceMap.get(obfStack));
            } else {
                File f = FileUtil.createTempFile("afs", "retrace", null, false);
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                try {
                    bw.write(exString);
                } finally {
                    bw.close();
                }

                //yuris note: please make sure that the "proguard" package is installed
                Util.execForeground(retracedEx,
                        "java",
                        "-jar",
                        "/usr/share/java/retrace.jar",
                        "/maps/aerofs-"+version+"-prod.map",
                        f.getAbsolutePath()
                        );

                FileUtil.deleteOrOnExit(f);

                _retraceMap.put(obfStack, retracedEx.get());
            }
        } //synchronized


        message = retracedEx.get().substring(0, retracedEx.get().indexOf("\n"));
        matcher = PATTERN.matcher(retracedEx.get());


        ArrayList<RavenTraceElement> rteList = new ArrayList<RavenTraceElement>();

        while (matcher.find()){
            String className = (matcher.group(1) == null ? "null" : matcher.group(1));
            String methodName = (matcher.group(2) == null ? "null" : matcher.group(2));
            String lineNumber = (matcher.group(3) == null ? "0" : matcher.group(3));
            RavenTraceElement rte = new RavenTraceElement(className, methodName, lineNumber);
            rteList.add(rte);
        }

        RavenTrace rt = new RavenTrace(rteList.toArray(new RavenTraceElement[rteList.size()]),
                                        message,
                                        user,
                                        deviceId,
                                        version,
                                        RavenUtils.getTimestampLong());

        ravenClient.captureException(rt);
    }

}
