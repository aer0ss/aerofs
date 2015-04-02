package com.aerofs.daemon.core.phy.linked.linker.notifier.windows;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification.RescanSubtree;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import net.contentobjects.jnotify.win32.IWin32NotifyListener;
import net.contentobjects.jnotify.win32.JNotify_win32;
import org.slf4j.Logger;

import com.aerofs.daemon.core.phy.linked.linker.Linker;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.phy.linked.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.phy.linked.linker.notifier.INotifier;
import com.aerofs.lib.injectable.InjectableJNotify;

import net.contentobjects.jnotify.JNotifyException;

import java.util.concurrent.ConcurrentMap;

public class WindowsNotifier implements INotifier, IWin32NotifyListener
{
    private static Logger l = Loggers.getLogger(WindowsNotifier.class);

    private final CoreQueue _cq;
    private final InjectableJNotify _jn;

    private final ConcurrentMap<Integer, LinkerRoot> _id2root = Maps.newConcurrentMap();

    public WindowsNotifier(CoreQueue cq, InjectableJNotify jn)
    {
        _cq = cq;
        _jn = jn;
        _jn.win32_setLogFile(Util.join(Cfg.absRTRoot(), "jn.log"));
    }

    @Override
    public void start_() throws JNotifyException
    {
        _jn.win32_setNotifyListener(this);
    }

    @Override
    public int addRootWatch_(LinkerRoot root) throws JNotifyException
    {
        int id = _jn.win32_addWatch(root.absRootAnchor(),
                JNotify_win32.FILE_NOTIFY_CHANGE_FILE_NAME |
                        JNotify_win32.FILE_NOTIFY_CHANGE_DIR_NAME |
                        JNotify_win32.FILE_NOTIFY_CHANGE_ATTRIBUTES |
                        JNotify_win32.FILE_NOTIFY_CHANGE_SIZE |
                        JNotify_win32.FILE_NOTIFY_CHANGE_LAST_WRITE |
                        // We don't care about last access time.
                        //JNotify_win32.FILE_NOTIFY_CHANGE_LAST_ACCESS |
                        JNotify_win32.FILE_NOTIFY_CHANGE_CREATION |
                        JNotify_win32.FILE_NOTIFY_CHANGE_SECURITY, true);
        l.info("addroot {} {}", root, id);
        _id2root.put(id, root);
        return id;
    }

    @Override
    public void removeRootWatch_(LinkerRoot root) throws JNotifyException
    {
        int id = root.watchId();
        l.info("remroot {} {}", root, id);
        _jn.win32_removeWatch(id);
        _id2root.remove(id);
    }

    private IEvent mightCreate(LinkerRoot lr, String root, String name)
    {
        return new EIMightCreateNotification(lr, Util.join(root, name), RescanSubtree.DEFAULT);
    }

    private IEvent mightDelete(LinkerRoot lr, String root, String name)
    {
        return new EIMightDeleteNotification(lr, Util.join(root, name));
    }

    @Override
    public void notifyChange(int watchID, int action, String name)
    {
        LinkerRoot lr = _id2root.get(watchID);
        // avoid race condition between FS notification and root removal
        if (lr == null) {
            l.debug("ignore notif for removed root {}", watchID);
            return;
        }
        String root = lr.absRootAnchor();

        logEvent(watchID, action, root, name);

        if (!Linker.isInternalPath(name)) {
            IEvent event = buildMightCreateOrMightDelete(lr, action, root, name);
            // We need to do the enqueueing without holding the object lock as some core threads
            // may add new roots as part of a transaction and that would cause a deadlock...
            if (event != null) {
                _cq.enqueueBlocking(event, Linker.PRIO);
            }
        }
    }

    private void logEvent(int watchID, int action, String root, String name)
    {
        switch (action) {
        case JNotify_win32.FILE_ACTION_ADDED:
            l.debug("winnotify: ADDED {}: {} {}", watchID, root, name);
            break;
        case JNotify_win32.FILE_ACTION_REMOVED:
            l.debug("winnotify: REMOVED {}: {} {}", watchID, root, name);
            break;
        case JNotify_win32.FILE_ACTION_MODIFIED:
            l.debug("winnotify: MODIFIED {}: {} {}", watchID, root, name);
            break;
        case JNotify_win32.FILE_ACTION_RENAMED_OLD_NAME:
            l.debug("winnotify: RENAMED_OLD_NAME {}: {} {}", watchID, root, name);
            break;
        case JNotify_win32.FILE_ACTION_RENAMED_NEW_NAME:
            l.debug("winnotify: RENAMED_NEW_NAME {}: {} {}", watchID, root, name);
            break;
        case JNotify_win32.FILE_ACTION_QUEUE_OVERFLOW:
            l.debug("winnotify: QUEUE_OVERFLOW {}: {}", watchID, root);
            break;
        }
    }

    private IEvent buildMightCreateOrMightDelete(LinkerRoot lr, int action, String root,
            String name)
    {
        switch (action) {
        case JNotify_win32.FILE_ACTION_ADDED:
            return mightCreate(lr, root, name);
        case JNotify_win32.FILE_ACTION_REMOVED:
            return mightDelete(lr, root, name);
        case JNotify_win32.FILE_ACTION_MODIFIED:
            return mightCreate(lr, root, name);
        case JNotify_win32.FILE_ACTION_RENAMED_OLD_NAME:
            return mightDelete(lr, root, name);
        case JNotify_win32.FILE_ACTION_RENAMED_NEW_NAME:
            return mightCreate(lr, root, name);
        case JNotify_win32.FILE_ACTION_QUEUE_OVERFLOW:
            return new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    lr.scanImmediately_(ImmutableSet.of(root), true);
                }
            };
        default:
            Preconditions.checkState(false, "unknown notification type from jnotify");
            return null;
        }
    }
}
