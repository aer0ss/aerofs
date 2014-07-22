package com.aerofs.lib.injectable;

import com.aerofs.lib.os.OSUtil;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.linux.INotifyListener;
import net.contentobjects.jnotify.linux.JNotify_linux;
import net.contentobjects.jnotify.macosx.FSEventListener;
import net.contentobjects.jnotify.macosx.JNotify_macosx;
import net.contentobjects.jnotify.win32.IWin32NotifyListener;
import net.contentobjects.jnotify.win32.JNotify_win32;

/**
 * The injectable wrapper for the JNotify library
 */
public class InjectableJNotify
{
    public InjectableJNotify()
    {
        // Currently, we do not support 64-bit AeroFS on Windows,
        // so there is no need to check the architecture
        // OSUtil.get().loadLibrary(OSUtil.isWindows() && OSUtil.getOSArch()
        //    == OSArch.X86_64 ? "aerofsjn64" : "aerofsjn");
        OSUtil.get().loadLibrary("aerofsjn");
    }

    public void win32_setLogFile(String path)
    {
        JNotify_win32.initLogger(path);
    }

    public void win32_setNotifyListener(IWin32NotifyListener l)
    {
        JNotify_win32.setNotifyListener(l);
    }

    public int win32_addWatch(String path, int mask, boolean watchSubtree)
            throws JNotifyException
    {
        return JNotify_win32.addWatch(path, mask, watchSubtree);
    }

    public void win32_removeWatch(int watchID)
    {
        JNotify_win32.removeWatch(watchID);
    }

    public void macosx_setNotifyListener(FSEventListener l)
    {
        JNotify_macosx.setNotifyListener(l);
    }

    public int macosx_addWatch(String path) throws JNotifyException
    {
        return JNotify_macosx.addWatch(path);
    }

    public boolean macosx_removeWatch(int watchID)
    {
        return JNotify_macosx.removeWatch(watchID);
    }

    public void linux_setNotifyListener(INotifyListener l)
    {
        JNotify_linux.setNotifyListener(l);
    }

    public int linux_addWatch(String path, int mask) throws JNotifyException
    {
        return JNotify_linux.addWatch(path, mask);
    }

    public void linux_removeWatch(int watchID) throws JNotifyException
    {
        JNotify_linux.removeWatch(watchID);
    }
}
