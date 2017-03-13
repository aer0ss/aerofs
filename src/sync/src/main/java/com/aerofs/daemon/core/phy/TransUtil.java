package com.aerofs.daemon.core.phy;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.injectable.InjectableFile;

import java.io.IOException;

public class TransUtil
{
    /**
     * Alternative to Callable<Void> that only throws IOException
     */
    public static interface IPhysicalOperation
    {
        void run_() throws IOException;
    }

    private static int makeFileSyncable(InjectableFile f) {
        if (!f.exists() || f.canWrite()) return 0;
        try {
            f.fixFilePermissions();
            return 1;
        } catch (IOException e) {
            return 0;
        }
    }

    private static int makeDirSyncable(InjectableFile f) {
        if (!f.exists() || f.canWrite()) return 0;
        try {
            f.fixDirectoryPermissions();
            return 1;
        } catch (IOException e) {
            return 0;
        }
    }

    public static void moveWithRollback_(
            final InjectableFile from, final InjectableFile to, Trans t)
            throws IOException
    {
        try {
            from.moveInSameFileSystem(to);
        } catch (IOException e) {
            // attempt to correct permissions
            // NB: OR bits instead of using || with boolean operands to avoid short-circuit
            int retry = makeFileSyncable(from)
                    | makeDirSyncable(from.getParentFile()) << 1
                    | makeDirSyncable(to.getParentFile()) << 2;
            if (retry != 0) {
                Loggers.getLogger(TransUtil.class).info("retry move w/ fixed perm {} {} {}", retry, from, to);
                from.moveInSameFileSystem(to);
            } else {
                throw e;
            }
        }

        onRollback_(from, t, () -> to.moveInSameFileSystem(from));
    }

    /**
     * N.B. this must be called *after* the actual file operation is done
     */
    public static void onRollback_(final InjectableFile f, Trans t, final IPhysicalOperation rh)
    {
        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                try {
                    rh.run_();
                } catch (IOException e) {
                    String str = "db/fs inconsistent on " + (f.isDirectory() ? "dir " : "file ") +
                            f.getAbsolutePath();
                    throw new Error(str, e);
                }
            }
        });
    }
}
