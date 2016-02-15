/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link CoreProgressWatcher} periodically checks whether the daemon is making progress. The main
 * check revolves around {@link CoreEventDispatcher}. However some events operate on large object
 * hierarchies in ways that do not allow them to release the core lock and may thus take longer than
 * the interval between two successive progress checks.
 *
 * To remedy to that situation we need alternate ways for long running events to indicate that they
 * are making progress, however slowly.
 *
 * The first approach is to use a monotonic progress counter, incremented after each item of a
 * potentially large collection is processed. This is mostly used in {@link DirectoryService#walk_}
 * and recursive file I/O operations.
 *
 * Unfortunately, some long running tasks do not necessarily provide feedback on their progress. For
 * instance copying a large file across file systems, or shelling out to an external program may
 * span more than on progress check interval. To work around this we use a second counter, which we
 * dub the "in-progress syscall" counter. It is incremented/decremented around potentially long
 * running tasks and CoreProgressWatcher will not kill a daemon when this counter is non-zero.
 *
 * NB: This is one of the situations where the lack of scoping semantics in Java is annoying because
 * it makes it harder to transparently enforce decrements so y'all better be damn careful when using
 * this counter
 *
 * NB: This class is part of the lib module to make sure it can be used directly in {@link FileUtil}
 * and similar locations but ideally it should belong in the daemon module...
 */
public class ProgressIndicators
{
    /**
     * sigh, this shouldn't be a singleton but we cannot currently inject it properly because OSUtil
     * is statically initialized.
     *
     * TODO: injection-ize or otherwise cleanup OSUtil to allow this class to be injected
     */
    private static final ProgressIndicators _inst = new ProgressIndicators();
    private ProgressIndicators() {}

    public static ProgressIndicators get()
    {
        return _inst;
    }

    private final AtomicLong _monotonicProgress = new AtomicLong();
    public void incrementMonotonicProgress()
    {
        _monotonicProgress.incrementAndGet();
    }

    public long getMonotonicProgress()
    {
        return _monotonicProgress.get();
    }

    private final AtomicInteger _inProgressSyscall = new AtomicInteger(0);
    public void startSyscall()
    {
        _inProgressSyscall.incrementAndGet();
    }

    public void endSyscall()
    {
        _inProgressSyscall.decrementAndGet();
    }

    public boolean hasInProgressSyscall()
    {
        return _inProgressSyscall.get() > 0;
    }
}
