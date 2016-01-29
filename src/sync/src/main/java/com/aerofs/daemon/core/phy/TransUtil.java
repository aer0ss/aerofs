package com.aerofs.daemon.core.phy;

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

    public static void moveWithRollback_(
            final InjectableFile from, final InjectableFile to, Trans t)
            throws IOException
    {
        from.moveInSameFileSystem(to);

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
