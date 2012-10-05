/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.downloader;

class SilentDownloader implements IProgressIndicator
{

    @Override
    public void setProgress(int count)
    {
        // noop
    }

    @Override
    public void setTotal(int len)
    {
        // noop
    }

    @Override
    public void complete()
    {
        System.exit(0);
    }

    @Override
    public void run(Runnable runnable)
    {
        runnable.run();
    }

    @Override
    public void error(Throwable e)
    {
        System.err.println(Main.getErrorMessage(e));
        System.exit(1);
    }
}
