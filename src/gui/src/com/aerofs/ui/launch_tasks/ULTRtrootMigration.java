/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.launch_tasks;

import com.aerofs.base.Loggers;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.os.OSUtil;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
 * N.B. this class isn't an actual UILaunchTask. It was placed here and named as such because this
 * task is intended as an UILaunchTask.
 *
 * However, because UILaunchTasks are run _after_ the daemon is online whereas this task needs to be
 * run _before_ the daemon is online, that's why this class isn't an UILaunchTask.
 *
 * This class is created specifically to perform one specific data migration.
 *
 * Previously, the default runtime root on Windows could be under the roaming app data folder
 *   %APPDATA%. In a corporate environment, that causes the some problems.
 *
 * Roaming app data is associated with the user in a domain, and the domain server will back
 *   up the files in the folder and restore them when the user logs into a new machine.
 *
 * This causes 2 problems. First, the client database is propagated but the root anchor is not.
 *   Second, the disk usage of rtroot can grow quickly and exceed the user's disk quota.
 *
 * Thus a decision was made to move rtroot from roaming app data to local app data, and this
 *   class is created specifically to handle this migration, and nothing else.
 *
 * In edge cases, the priorities are to leave the file system in a recoverable state first, and
 *   to do as much as we can in hope that it succeeds second.
 *
 * In extreme cases of multiple failures, the task will leave the file system in a state that will
 *   cause AeroFS to prompt the user to re-run setup or simply not work. However, the intention is
 *   that the file system will be in a recoverable state so we'll be able to restore to a sane
 *   state if the user contacts us.
 */
public class ULTRtrootMigration
{
    private static final Logger l = Loggers.getLogger(ULTRtrootMigration.class);

    ////////
    // dependencies
    protected String _productSpaceFreeName;
    protected String _rtRoot;
    ////////

    protected File _oldRtRoot;
    protected File _newRtRoot;

    /**
     * The dependency is passed in so this class can remain detached from the AeroFS codebase.
     *
     * @param productSpaceFreeName - expects L.productSpaceFreeName()
     * @param rtRoot - the path to the rtRoot used by the program
     */
    public ULTRtrootMigration(@Nonnull String productSpaceFreeName, @Nonnull String rtRoot)
    {
        _productSpaceFreeName = productSpaceFreeName;
        _rtRoot = rtRoot;
    }

    public void run() throws ExFailedToMigrate, ExFailedToReloadCfg
    {
        if (needToMigrate()) {
            renameLogsInOldRtroot();
            migrateRtroot();
            reloadCfg();
        }
    }

    /**
     * Determines old rtroot, new rtroot, and whether the migration should be performed.
     *
     * Side effects: sets _oldRtRoot & _newRtRoot if migration should be performed. Otherwise the
     * values of these 2 fields are indeterministic. It could be altered/unaltered, set/unset.
     *
     * @return true iff we need to migrate rtroot.
     */
    protected boolean needToMigrate()
    {
        // It only make sense to migrate rtRoot if we are using the default rtRoot.
        // Otherwise, we are in a testing environment and there's no need to migrate.
        //
        // If you want to specifically test rtRoot migration, back up your actual rtRoot and
        // use the default rtRoot in your tests.
        if (!_rtRoot.equals(OSUtil.get().getDefaultRTRoot())) return false;

        // if the platform isn't Windows, we do not need to migrate
        if (!OSUtil.isWindows()) return false;

        _oldRtRoot = new File(getOldRtRootPath());
        _newRtRoot = new File(OSUtil.get().getDefaultRTRoot());

        // we don't need to migrate if old rtroot doesn't exist
        if (!_oldRtRoot.isDirectory()) return false;

        // it's important to check whether the old and new rtroots are different because both the
        // old default rtroot and new default rtroot fall back to using C:\AeroFS when all else
        // fails. This is a realistic scenario and there are reported cases of this happening.
        if (isSameFile(_oldRtRoot, _newRtRoot)) {
            l.debug("the old rtroot is the same as the new rtroot; do not migrate.");
            return false;
        }

        l.debug("checking new rtroot to see if we need to migrate.");

        // If the new root doesn't exist or is a file, delete the object (ignore errors) and request
        // to migrate.
        if (!_newRtRoot.isDirectory()) {
            l.warn("new rtroot isn't a directory.");
            // delete it in case it's a file. ignore errors.
            _newRtRoot.delete();
            return true;
        }

        // at this point we know the new rtroot exists as a directory. in this case, we could have
        // succeeded or failed copying files over in the past.
        // check for finished file, which marks that we have succeeded copying files over.
        File finished = new File(_newRtRoot, LibParam.RTROOT_MIGRATION_FINISHED);

        // if the finished file exists, then we have succeeded in the past and failed to cleanup
        // otherwise just proceed to migrate and overwrite everything
        if (finished.isFile()) {
            l.warn("finished marker exists, attempt to cleanup.");

            // try to cleanup again.
            FileUtil.deleteRecursivelyOrOnExit(_oldRtRoot);
            return false;
        } else {
            l.debug("finished marker doesn't exist, let's migrate rtroot.");
            return true;
        }
    }

    /**
     * Only works on Windows, hence it's a private method local to this class
     */
    private boolean isSameFile(@Nonnull File f1, @Nonnull File f2)
    {
        try {
            return f1.getCanonicalPath().equalsIgnoreCase(f2.getCanonicalPath());
        } catch (IOException e) {
            return f1.getAbsolutePath().equalsIgnoreCase(f2.getAbsolutePath());
        }
    }

    /**
     * make best effort to migrate rtroot and leaves a finished file in the new rtroot to indicate
     * we have succeeded in copying files over.
     *
     * dropping a file to indicate we have finished is necessary because of the following scenario:
     * * we succeeded in copying from old rtroot to new rtroot but failed to delete files in the
     *   old rtroot.
     * * since rtroot migration finished and new rtroot is proper, the program continues to run,
     *   possibly adding new data in the new rtroot.
     * * when the program restarts again, it notices the old rtroot and tries to migrate again,
     *   which will override the new data with the stale data from old rtroot.
     *
     * @throws ExFailedToMigrate - if we failed to migrate rtroot
     */
    protected void migrateRtroot() throws ExFailedToMigrate
    {
        l.debug("migrating rtroot.");

        try {
            // since both oldRtRoot/conf and newRtRoot/conf exist, there's no way a rename
            // could have succeeded, so we may as well copy recursively right away.
            FileUtil.copyRecursively(_oldRtRoot, _newRtRoot, false, true);

            l.debug("creating finished marker.");
            // This file marks that we have succeeded in copying all the files over from old rtroot
            // to new rtroot.
            // Failure to create this file is considered as a failure to copy files over.
            // This _must_ be the last thing we do in migration
            FileUtil.createNewFile(new File(_newRtRoot, LibParam.RTROOT_MIGRATION_FINISHED));
        } catch (IOException e) {
            l.warn("failed to copy from old rtroot to new rtroot recursively.");

            // if recursive copy failed, delete the new rtroot so we'll try again when we restart
            FileUtil.deleteRecursivelyOrOnExit(_newRtRoot);
            throw new ExFailedToMigrate("Failed to copy files from old rtroot to new rtroot.",
                    e, _oldRtRoot, _newRtRoot);
        }

        try {
            // if we succeeded in recursive copy, delete the old rtroot
            // the try-catch is intended so that we can log the occurrence
            FileUtil.deleteRecursively(_oldRtRoot, null);
        } catch (IOException e) {
            l.warn("failed to delete old rtroot recursively.");

            // try harder
            FileUtil.deleteRecursivelyOrOnExit(_oldRtRoot);
        }
    }

    /**
     * N.B. By the time this method is invoked, the logging subsystem will have already been
     * initialized. The log file appenders will have already opened the log files in the new
     * rtroot and will be logging using these file handlers, and this breaks the system in
     * different ways depending on the platform.
     *
     * The observed behaviour on OS X is as the following: the rtroot migration will replace the
     * logs in the new rtroot with the logs in the old rtroot. In the meanwhile, the log file
     *  appenders continue to use the file handler pointing at a defunct file. The end result is
     *  that we lose all log output until the program restarts.
     *
     * The hypothesized behaviour on Linux is identical to OS X, and the hypothesized behaviour
     * on Windows is the log appenders will have held file locks and prevented us from moving
     * the log files over.
     *
     * @pre _oldRtroot is not null, the file exists, and the file is a directory.
     */
    protected void renameLogsInOldRtroot() throws ExFailedToMigrate
    {
        l.debug("renaming logs in old rtroot.");

        Preconditions.checkArgument(_oldRtRoot != null && _oldRtRoot.isDirectory());

        try {
            File[] files = _oldRtRoot.listFiles();
            if (files != null) {
                for (File file : files) {
                    String path = file.getAbsolutePath();
                    if (path.endsWith(".log")) {
                        // N.B. renameLogsInOldRtroot isn't atomic. However, we'll be able to
                        // recover and resume in the event of failure.
                        //
                        // When failure occurs, we'll have some log files renamed, some not.
                        // If the program is run again, it'll pick up where it left off.
                        // If the user notifies us, we can clearly tell these are log files and
                        // manually recover.
                        //
                        // N.B. the suffix can be any pattern as long as it matches the pattern
                        // used by LogArchiver, which is "*.log.*".
                        //
                        // A number pattern is arbitrarily chosen, and as we all know, it's
                        // important to be over 9000.
                        FileUtil.rename(file, new File(path + ".00009001"));
                    }
                }
            }
        } catch (IOException e) {
            l.warn("failed to rename logs in old rtroot.");

            throw new ExFailedToMigrate("Failed to rename log files in the old rtroot",
                    e, _oldRtRoot, _newRtRoot);
        }
    }

    /**
     * Because Cfg is already initialized with an empty conf, we'll have to reload Cfg
     * after migrating the database file over from the old rtRoot.
     * @throws ExFailedToReloadCfg when we failed to reload Cfg
     */
    protected void reloadCfg() throws ExFailedToReloadCfg
    {
        l.debug("reloading cfg database.");

        try {
            // Because this task is only run in UI programs, we always read password.
            Cfg.init_(_rtRoot, true);
        } catch (ExNotSetup e) {
            l.debug("device not setup.");
            // ignored because GUI and CLI will run setup itself
        } catch (Exception e) {
            l.warn("failed to reload cfg database.");

            throw new ExFailedToReloadCfg(e);
        }
    }

    /**
     * This is the implementation of OSUtilWindows.getDefaultRtroot() before the change of
     * default rtRoot.
     */
    protected @Nonnull String getOldRtRootPath()
    {
        // N.B. We have a number of devices in the wild with C:\ as their rtroot.
        return Objects.firstNonNull(System.getenv("APPDATA"), "C:") + "\\" + _productSpaceFreeName;
    }

    public static class ExFailedToReloadCfg extends Exception
    {
        private static final long serialVersionUID = 0L;

        public ExFailedToReloadCfg(Exception cause)
        {
            super(cause);
        }
    }

    public static class ExFailedToMigrate extends Exception
    {
        private static final long serialVersionUID = 0L;

        public final String _oldRtrootPath;
        public final String _newRtrootPath;

        public ExFailedToMigrate(String message, IOException cause, File oldRtroot, File newRtroot)
        {
            super(message, cause);

            _oldRtrootPath = oldRtroot.getAbsolutePath();
            _newRtrootPath = newRtroot.getAbsolutePath();
        }
    }
}
