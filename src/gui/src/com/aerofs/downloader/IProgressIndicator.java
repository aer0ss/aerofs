package com.aerofs.downloader;

interface IProgressIndicator {

    void setProgress(int count);

    // must exit the process with zero
    void complete();

    // this is the last method the main thread runs before exiting
    void run(Runnable runnable);

    void setTotal(int len);

    // must exit the process with non-zero
    void error(Throwable e);
}
