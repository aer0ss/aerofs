package com.aerofs.lib.injectable;

import com.aerofs.lib.os.OSUtil;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.JNotifyListener;
import net.contentobjects.jnotify.macosx.FSEventListener;
import net.contentobjects.jnotify.macosx.JNotify_macosx;

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

    public void macosx_setNotifyListener(FSEventListener l)
    {
        JNotify_macosx.setNotifyListener(l);
    }

    public int macosx_addWatch(String path) throws JNotifyException
    {
        return JNotify_macosx.addWatch(path);
    }

    public int addWatch(String path, int mask, boolean watchSubtree, JNotifyListener listener)
            throws JNotifyException
    {
        return JNotify.addWatch(path, mask, watchSubtree, listener);
    }

    public boolean removeWatch(int watchID) throws JNotifyException
    {
        return JNotify.removeWatch(watchID);
    }
}
