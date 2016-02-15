/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.Loggers;
import com.aerofs.ids.UserID;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.os.OSUtil;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.aerofs.defects.DefectUtils.*;
import static com.aerofs.defects.DryadClientUtil.createDefectLogsResource;
import static com.aerofs.lib.Util.test;

/**
 * Represents an defect with debug info, possibly logs, and throttled by rex.
 *
 * When the defect includes logs, any logs and associated files are uploaded to Dryad.
 *
 * N.B.: both defects and logs are uploaded on the same executor, hence new defects have to wait
 *   for existing defects to finish uploading the logs before sending the new defect to RockLog.
 * TODO: we can potentially change this ^ behaviour at the cost of increasing code complexity.
 */
public class AutoDefect extends Defect
{
    private static final Logger l = Loggers.getLogger(AutoDefect.class);

    // bit field flags for what files to upload
    public static final int UPLOAD_NONE         = 0x00;
    public static final int UPLOAD_LOGS         = 0x01;
    public static final int UPLOAD_DB           = 0x02;
    public static final int UPLOAD_HEAP_DUMPS   = 0x04;
    public static final int UPLOAD_FILENAMES    = 0x08;
    public static final int UPLOAD_ALL_FILES    = 0x10;

    protected String    _absRTRoot;

    // FIXME (AT): getting rid of these flags is hard; punting this till another day
    protected boolean   _uploadLogs;
    protected boolean   _uploadDB;
    protected boolean   _uploadHeapDumps;
    protected boolean   _uploadFilenames;
    protected boolean   _uploadAllFiles;

    private final DryadClient           _dryad;
    private final RecentExceptions      _recentExceptions;
    private final Map<String, String>   _properties;

    // N.B. turns out passing in properties is necessary because querying System properties
    //   each time will cause defects to fail under heavy load. This is observed when running
    //   TestRockLog#shouldNotDieWhenSubmittingManyRequests()
    public AutoDefect(String name, RockLog rockLog, DryadClient dryad,
                      Executor executor, RecentExceptions recentExceptions, Map<String, String> properties,
                      CfgLocalUser cfgLocalUser, CfgLocalDID cfgLocalDID, String rtroot,
                      CfgVer cfgVer)
    {
        super(name, rockLog, executor, cfgLocalUser, cfgLocalDID, cfgVer);

        _dryad = dryad;
        _recentExceptions = recentExceptions;
        _properties = properties;
        _absRTRoot = rtroot;
    }

    protected AutoDefect setDefectID(String defectID)
    {
        _defectID = defectID;
        return this;
    }

    protected AutoDefect setUserID(UserID userID)
    {
        _userID = userID;
        return this;
    }

    protected AutoDefect setAbsRTRoot(String absRTRoot)
    {
        _absRTRoot = absRTRoot;
        return this;
    }

    protected AutoDefect setPriority(Priority priority)
    {
        _priority = priority;
        return this;
    }

    /**
     * @param filesToSend bit field of UPLOAD_*
     */
    protected AutoDefect setFilesToUpload(int filesToSend)
    {
        _uploadLogs = test(filesToSend, UPLOAD_LOGS);
        _uploadDB = test(filesToSend, UPLOAD_DB);
        _uploadHeapDumps = test(filesToSend, UPLOAD_HEAP_DUMPS);
        _uploadFilenames = test(filesToSend, UPLOAD_FILENAMES);
        _uploadAllFiles = test(filesToSend, UPLOAD_ALL_FILES);
        return this;
    }

    @Override
    public void sendSync()
            throws Exception
    {
        super.sendSync();

        if (_priority == Priority.Auto && _recentExceptions.isRecent(_exception)) {
            l.info("Repeating last defect: {}: {}", _message, _exception.toString());
            return;
        } else {
            l.info("Sending defect: {}: {}: {}", _subject, _message, _exception.toString());
        }

        if (BaseCfg.getInstance().inited()) {
            File[] files = getFiles(_absRTRoot, _uploadLogs, _uploadDB, _uploadHeapDumps,
                    _uploadFilenames, _uploadAllFiles);
            if (ArrayUtils.isNotEmpty(files)) {
                String resourceURL = createDefectLogsResource(_defectID, _userID, _deviceID);
                _dryad.uploadFiles(resourceURL, files);
            }
        }

        _recentExceptions.add(_exception);

        if (BaseCfg.getInstance().inited() && (_uploadAllFiles || _uploadHeapDumps)) {
            deleteOldHeapDumps();
        }

        if (_exception instanceof OutOfMemoryError) {
            ExitCode.OUT_OF_MEMORY.exit();
        }
    }

    @Override
    protected Map<String, Object> getDefectData()
    {
        Map<String, Object> data = super.getDefectData();

        if (OSUtil.get() != null && !OSUtil.isWindows()) {
            data.put("uname",           getCommandOutput("uname", "-a"));
            data.put("df",              getCommandOutput("df"));
        }

        data.put("java_properties",     _properties);

        if (!StringUtils.isEmpty(_absRTRoot)) {
            data.put("rtroot",          getDirectoryInfo(_absRTRoot));
            data.put("rtroot_files",    listTopLevelContents(_absRTRoot));
        }


        if (Cfg.inited() && Cfg.storageType() == StorageType.LINKED) {
            data.put("linked_roots",    listLinkedRoots());
        }

        if (BaseCfg.getInstance().inited()) {
            data.put("cfg_db",          listCfgDBContent());
        }

        return data;
    }
}
