/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.lib.os.OSUtil;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.concurrent.Executor;

import static com.aerofs.defects.DefectUtils.*;
import static com.aerofs.defects.DryadClientUtil.createDefectLogsResource;
import static com.aerofs.lib.Util.test;
import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Maps.newHashMap;

/**
 * Represents a defect where the defect is reported to RockLog and logs are sent to Dryad.
 *
 * Defects are essentially objects with some well-known fields (timestamp, name, message,
 * exception, etc...) and optionally additional custom fields
 *
 * All defects must have a name (set in the constructor). This name enables easy aggregation of
 * defects, so please be sure to pick a name specific to your defect. Feel free to use dots to
 * create namespaces.
 *
 * Example:
 *
 * try {
 *      ...
 * } catch (Exception e) {
 *      Defects.newDefect("mycomponent.foobar")
 *          .setMessage("Something failed")
 *          .setException(e)
 *          .sendAsync();
 * }
 *
 * N.B.: both defects and logs are uploaded on the same executor, hence new defects have to wait
 *   for existing defects to finish uploading the logs before sending the new defect to RockLog.
 * TODO: we can potentially change this ^ behaviour at the cost of increasing code complexity.
 */
public class Defect
{
    private static final Logger l = Loggers.getLogger(Defect.class);

    // bit field flags for what files to upload
    public static final int UPLOAD_NONE         = 0x00;
    public static final int UPLOAD_LOGS         = 0x01;
    public static final int UPLOAD_DB           = 0x02;
    public static final int UPLOAD_HEAP_DUMPS   = 0x04;
    public static final int UPLOAD_FILENAMES    = 0x08;
    public static final int UPLOAD_ALL_FILES    = 0x10;

    protected String    _defectID;
    protected String    _name;
    protected String    _message;
    protected Throwable _exception;
    protected String    _time;
    protected Priority  _priority;

    protected UserID    _userID;
    protected DID       _deviceID;
    protected String    _absRTRoot;

    // FIXME (AT): getting rid of these flags is hard; punting this till another day
    protected boolean   _uploadLogs;
    protected boolean   _uploadDB;
    protected boolean   _uploadHeapDumps;
    protected boolean   _uploadFilenames;
    protected boolean   _uploadAllFiles;

    private final Map<String, Object> _data = newHashMap();

    private final InjectableCfg         _cfg;
    private final RockLog               _rockLog;
    private final DryadClient           _dryad;
    private final Executor              _executor;
    private final RecentExceptions      _recentExceptions;
    private final Map<String, String>   _properties;

    public enum Priority {Auto, User, Command}

    // N.B. turns out passing in properties is necessary because querying System properties
    //   each time will cause defects to fail under heavy load
    public Defect(String name, InjectableCfg cfg, RockLog rockLog, DryadClient dryad,
            Executor executor, RecentExceptions recentExceptions, Map<String, String> properties)
    {
        _cfg = cfg;
        _rockLog = rockLog;
        _dryad = dryad;
        _executor = executor;
        _recentExceptions = recentExceptions;
        _properties = properties;

        // Set the timestamp field as early as possible
        _time = getTimeStamp();
        _defectID = newDefectID();
        _name = name;
        _message = "";
        // need the stacktrace at this point assuming it's not overwritten later
        _exception = new Exception();
        _priority = Priority.Auto;

        _userID = getCfgUser(_cfg);
        _deviceID = getCfgDID(_cfg);
        _absRTRoot = _cfg.absRTRoot();
    }

    protected Defect setDefectID(String defectID)
    {
        _defectID = defectID;
        return this;
    }

    public Defect setMessage(String message)
    {
        _message = firstNonNull(message, "");
        return this;
    }

    public Defect setException(@Nullable Throwable exception)
    {
        // do not update if exception is null
        _exception = firstNonNull(exception, _exception);
        return this;
    }

    protected Defect setUserID(UserID userID)
    {
        _userID = userID;
        return this;
    }

    protected Defect setAbsRTRoot(String absRTRoot)
    {
        _absRTRoot = absRTRoot;
        return this;
    }

    protected Defect setPriority(Priority priority)
    {
        _priority = priority;
        return this;
    }

    /**
     * @param filesToSend bit field of SEND_*
     */
    protected Defect setFilesToUpload(int filesToSend)
    {
        _uploadLogs = test(filesToSend, UPLOAD_LOGS);
        _uploadDB = test(filesToSend, UPLOAD_DB);
        _uploadHeapDumps = test(filesToSend, UPLOAD_HEAP_DUMPS);
        _uploadFilenames = test(filesToSend, UPLOAD_FILENAMES);
        _uploadAllFiles = test(filesToSend, UPLOAD_ALL_FILES);
        return this;
    }

    public Defect addData(String key, Object value)
    {
        _data.put(key, value);
        return this;
    }

    public void sendSync()
            throws Exception
    {
        l.debug("build defect");

        // If we have any LinkageError (NoClassDefFoundError or UnsatisfiedLinkError) or
        // MissingResourceException, this probably indicates that our stripped-down version of
        // OpenJDK is missing something. Send a different RockLog defect to make sure we catch it.
        if (_exception instanceof LinkageError || _exception instanceof MissingResourceException) {
            _name = "system.classnotfound";
        }

        _rockLog.send("/defects", getDefectData());

        if (_priority == Priority.Auto && _recentExceptions.isRecent(_exception)) {
            l.info("repeating last defect: {}: {}", _message, _exception.toString());
            return;
        } else {
            l.error("sending defect: {}: {}", _message, _exception.toString());
        }

        if (_cfg.inited()) {
            File[] files = getFiles(_absRTRoot, _uploadLogs, _uploadDB, _uploadHeapDumps,
                    _uploadFilenames, _uploadAllFiles);
            if (ArrayUtils.isNotEmpty(files)) {
                String resourceURL = createDefectLogsResource(_defectID, _userID, _deviceID);
                _dryad.uploadFiles(resourceURL, files);
            }
        }

        _recentExceptions.add(_exception);

        if (_cfg.inited() && (_uploadAllFiles || _uploadHeapDumps)) {
            deleteOldHeapDumps();
        }

        // FIXME (AG): really? I'm pretty sure we won't be able to do any of this no?
        if (_exception instanceof OutOfMemoryError) ExitCode.OUT_OF_MEMORY.exit();
    }

    public void sendSyncIgnoreErrors()
    {
        try {
            sendSync();
        } catch (Throwable t) {
            // we don't want stack trace
            l.warn("Ignored. Failed to send defect: {}", t.toString());
        }
    }

    public void sendAsync()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                sendSyncIgnoreErrors();
            }
        });
    }

    /**
     * @return fully composed map of supplementary defect data + caller-supplied defect data
     */
    protected Map<String, Object> getDefectData()
    {
        Map<String, Object> data = getSupplementaryData();
        data.putAll(_data);
        return data;
    }


    /**
     * @return the auto-generated defect data, which may be overwritten by any caller-supplied data
     */
    private Map<String, Object> getSupplementaryData()
    {
        Map<String, Object> data = newHashMap();

        data.put("defect_id",           _defectID);
        data.put("name",                _name);
        data.put("@message",            _message);
        data.put("exception",           encodeException(_exception));
        // Note: some of our json fields start with a '@' to follow the logstash format
        // see: https://github.com/logstash/logstash/wiki/logstash%27s-internal-message-format
        // Kibana expects to find those fields (especially @timestamp)
        data.put("@timestamp",          _time);
        data.put("priority",            _priority.toString());

        data.put("version",             _cfg.ver());
        data.put("user_id",             _userID.getString());
        data.put("did",                 _deviceID.toStringFormal());

        if (OSUtil.get() != null) {
            data.put("os_name",         OSUtil.get().getFullOSName());
            data.put("os_family",       OSUtil.get().getOSFamily().getString());
            data.put("aerofs_arch",     OSUtil.getOSArch().toString());

            if (!OSUtil.isWindows()) {
                data.put("uname",       getCommandOutput("uname", "-a"));
                data.put("df",          getCommandOutput("df"));
            }
        }

        data.put("java_properties",     _properties);

        if (!StringUtils.isEmpty(_absRTRoot)) {
            data.put("rtroot",          getDirectoryInfo(_absRTRoot));
            data.put("rtroot_files",    listTopLevelContents(_absRTRoot));
        }

        if (_cfg.inited() && _cfg.storageType() == StorageType.LINKED) {
            data.put("linked_roots",    listLinkedRoots(_cfg));
        }

        if (_cfg.inited()) {
            data.put("cfg_db",          listCfgDBContent(_cfg));
        }

        return data;
    }
}
