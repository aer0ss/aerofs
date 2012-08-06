package com.aerofs.downloader;

class CLIDownloader implements IProgressIndicator {

    private int _total;
    private int _current;    // current number of dots

    @Override
    public void setTotal(int len)
    {
        System.out.println("---------------------------------------|");
        _total = len;
    }

    @Override
    public void setProgress(int count)
    {
        int cur = count * 40 / _total;
        for (int i = 0; i < cur - _current; i++) {
            System.out.print('>');
            System.out.flush();
        }
        _current = cur;
    }

    @Override
    public void complete()
    {
        setProgress(_total);
        System.out.println();
        System.exit(0);
    }

    @Override
    public void run(Runnable runnable)
    {
        System.out.println(Main.TITLE);

        runnable.run();
    }

    @Override
    public void error(Throwable e)
    {
        System.out.println(Main.getErrorMessage(e));
        System.exit(1);
    }
}
