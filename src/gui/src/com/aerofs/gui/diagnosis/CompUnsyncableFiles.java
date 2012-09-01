package com.aerofs.gui.diagnosis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Table;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.fsi.FSIClient;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtilWindows;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.DriverConstants;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.events.SelectionAdapter;

public class CompUnsyncableFiles extends Composite {

    static interface IStatus
    {
        void setStatusText(String text);
        void setStatusImage(Image img);
    }

    private static enum Type {
        SYMLINK("Symbolic links are not supported."),
        SPECIAL("This is a platform-specific file (e.g. a device file)."),
        WINDOWS_INVALID_CHARS("Invalid file name for Windows (okay on other platforms).");

        final String _desc;

        Type(String desc)
        {
            _desc = desc;
        }
    }

    private final static String DEF_STATUS = "";//"Click on a file to show why it's unsyncable.";

    private static class Entry {
        final String _path;
        final Type _type;

        private Entry(String path, Type type)
        {
            _path = path;
            _type = type;
        }
    }

    private class ContentProvider implements IStructuredContentProvider {

        @Override
        public void dispose()
        {
        }

        @Override
        public void inputChanged(Viewer arg0, Object arg1, Object arg2)
        {
        }

        @Override
        public Object[] getElements(Object input)
        {
            return ((ArrayList<?>) input).toArray();
        }
    }

    private class LabelProvider
    extends org.eclipse.jface.viewers.LabelProvider implements ITableLabelProvider
    {
        @Override
        public String getColumnText(Object element, int columnIndex)
        {
            if (element instanceof String) return columnIndex == 0 ?
                    element.toString() : "";

            Entry en = (Entry) element;

            int start = Cfg.absRootAnchor().length() + 1;
            String path = en._path.length() > start ? en._path.substring(start)
                    : en._path;
            return GUIUtil.shortenText(_gc, path, _table.getClientArea().width,
                    true);
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex)
        {
            if (columnIndex != 0) return null;
            if (element instanceof String) return Images.get(Images.ICON_ERROR);
            return getEntryImage((Entry) element);
        }
    }

    private static Image getEntryImage(Entry en)
    {
        String img;
        switch (en._type) {
        case SYMLINK:
            img = Images.ICON_LINK; break;
        case SPECIAL:
            img = Images.ICON_BRICK; break;
        case WINDOWS_INVALID_CHARS:
            img = Images.ICON_DOUBLE_QUESTION; break;
        default:
            assert false;
            img = null;
        }

        return Images.get(img);
    }

    private final static int MAX_ITEM_COUNTS = 1000;

    protected Object result;
    private GC _gc;
    private final ArrayList<Entry> _entries = new ArrayList<Entry>();
    private final Shell _shell;
    private Button _btnDelete;
    private Button _btnBrowse;
    private Table _table;
    private TableViewer _tv;
    private CompSpin _compSpin;
    private final IStatus _status;
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    static {
        // needed for DriverConstants etc
        OSUtil.get().loadLibrary("aerofsd");
    }

    public CompUnsyncableFiles(Composite parent, int style, IStatus status)
    {
        super(parent, style);
        _shell = getShell();
        _status = status;

        GridLayout glShell = new GridLayout(3, false);
        glShell.marginHeight = 0;
        glShell.marginWidth = 0;
        glShell.horizontalSpacing = GUIUtil.getInterButtonHorizontalSpace(glShell);
        setLayout(glShell);

        _tv = new TableViewer(this, SWT.BORDER | SWT.MULTI);
        _tv.setContentProvider(new ContentProvider());
        _tv.setLabelProvider(new LabelProvider());
        _tv.setInput(_entries);

        _table = _tv.getTable();
        _table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 3, 1));

        _table.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetDefaultSelected(SelectionEvent arg0)
            {
                browse();
            }

            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                selectionChanged();
            }
        });

        _table.addListener(SWT.Resize, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                // update text shortening
                _tv.refresh();
            }
        });

        _gc = new GC(_table);
        _table.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent arg0)
            {
                _gc.dispose();
            }
        });

        _table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent ev)
            {
                // 0x400000 is the Cmd key
                boolean ctrl = (ev.stateMask & (OSUtil.isOSX() ? 0x400000 : SWT.CONTROL)) != 0;
                if (!ctrl || ev.keyCode != 99 /* C */) return;

                StringBuilder sb = new StringBuilder();
                int count = 0;
                for (Entry en : getSelectedEntries()) {
                    if (count++ != 0) {
                        sb.append(OSUtil.isWindows() ? "\r\n" : "\n");
                    }
                    sb.append(en._path);
                }

                if (count == 0) return;

                Clipboard cb = new Clipboard(getDisplay());
                try {
                    cb.setContents(new Object[] { sb.toString() },
                            new Transfer[] { TextTransfer.getInstance() });
                } catch (SWTError e) {
                    Util.l(CompUnsyncableFiles.class).warn("cp 2 clipboard: " + Util.e(e));
                    return;
                } finally {
                    cb.dispose();
                }
            }
        });

        _btnBrowse = new Button(this, SWT.NONE);
        _btnBrowse.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                browse();
            }
        });
        _btnBrowse.setEnabled(false);
        _btnBrowse.setText("Browse");

        _btnDelete = new Button(this, SWT.NONE);
        _btnDelete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                boolean plural = getSelectedEntries().size() > 1;
                if (GUI.get().ask(getShell(), MessageType.QUESTION,
                        "Are you sure you want to delete the selected file" +
                        (plural ? "s" : "") + "?")) {
                    try {
                        delete();
                    } catch (Exception e) {
                        GUI.get().show(getShell(), MessageType.ERROR,
                                "The file" + (plural ? "s" : "") + " couldn't" +
                                        "be deleted " + UIUtil.e2msg(e) + ".");
                    }
                }
            }
        });
        _btnDelete.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        _btnDelete.setEnabled(false);
        _btnDelete.setText("Delete");

        int max = 0;
        for (Type type : Type.values()) {
            max = Math.max(max, _gc.textExtent(type._desc).x);
        }

        _compSpin = new CompSpin(this, SWT.NONE);
    }

    private void selectionChanged()
    {
        ArrayList<Entry> sels = getSelectedEntries();

        _btnDelete.setEnabled(sels.size() > 0);
        _btnBrowse.setEnabled(sels.size() > 0);

        if (sels.size() == 1) {
            Entry en = sels.get(0);
            _status.setStatusText(en._type._desc);
            _status.setStatusImage(getEntryImage(en));
        } else {
            _status.setStatusText(DEF_STATUS);
            _status.setStatusImage(null);
        }
    }

    int getStatusWidthHint(Font font)
    {
        Font old = _gc.getFont();
        _gc.setFont(font);
        try {
            int max = 0;
            for (Type type : Type.values()) {
                max = Math.max(max, _gc.textExtent(type._desc).x);
            }
            return max;
        } finally {
            _gc.setFont(old);
        }
    }

    private void browse()
    {
        for (Entry en : getSelectedEntries()) {
            Program.launch(_factFile.create(en._path).getParent());
        }
    }

    private void delete() throws IOException
    {
        for (Entry en : getSelectedEntries()) {
            _factFile.create(en._path).deleteOrThrowIfExistRecursively();
            _tv.remove(en);
        }
        selectionChanged();
    }

    ArrayList<Entry> getSelectedEntries()
    {
        IStructuredSelection sel = ((IStructuredSelection) _tv.getSelection());

        ArrayList<Entry> ens = new ArrayList<Entry>();
        Iterator<?> iter = sel.iterator();
        while (iter.hasNext()) {
            Object o = iter.next();
            if (o instanceof Entry) ens.add((Entry) o);
        }
        return ens;
    }

    // run in UI thread
    void search()
    {
        _compSpin.start();

        Thread thd = new Thread() {
            @Override
            public void run()
            {
                final InOutArg<Integer> count = new InOutArg<Integer>(0);
                Exception ex = null;
                FSIClient fsi = FSIClient.newConnection();
                try {
                    thdSearch(fsi, count);
                } catch (Exception e) {
                    Util.l(CompUnsyncableFiles.class).warn("search 4 unsyncables: " +
                            Util.e(e));
                    ex = e;
                } finally {
                    fsi.close_();
                }

                final Exception exFinal = ex;
                GUI.get().safeAsyncExec(_shell, new Runnable() {
                    @Override
                    public void run()
                    {
                        if (exFinal != null) {
                            _tv.add(UIUtil.e2msg(exFinal));
                        }

                        _compSpin.stop();
                    }
                });
            }
        };
        thd.setDaemon(true);
        thd.start();
    }

    private void thdSearch(FSIClient fsi, final InOutArg<Integer> count) throws Exception
    {
        if (!OSUtil.isWindows()) {
            listSpecialFilesRecursive(Cfg.absRootAnchor(),
                    new IListSpecialFileCallback() {
                @Override
                public boolean add(String path, Type type)
                {
                    count.set(count.get() + 1);
                    if (count.get() > MAX_ITEM_COUNTS) return false;
                    addFile(path, type);
                    return true;
                }
            });
        }
    }

    /**
     * this method may be called in non-UI threads
     */
    private void addFile(final String path, final Type type)
    {
        GUI.get().safeExec(_shell, new Runnable() {
            @Override
            public void run()
            {
                Entry en = new Entry(path, type);
                _entries.add(en);
                _tv.add(en);
            }
        });
    }

    private static interface IListSpecialFileCallback {
        /**
         * @return false to stop listing
         */
        boolean add(String path, Type type);
    }

    private static boolean listSpecialFilesRecursive(String path,
            IListSpecialFileCallback cb)
    {
        int ret = Driver.getFid(null, path, null);
        if (ret == DriverConstants.GETFID_SYMLINK) {
            return cb.add(path, Type.SYMLINK);
        } else if (ret == DriverConstants.GETFID_SPECIAL) {
            return cb.add(path, Type.SPECIAL);
        } else if (!OSUtil.isWindows() &&
                !OSUtilWindows.isValidFileName(new File(path).getName())) {
            return cb.add(path, Type.WINDOWS_INVALID_CHARS);
        } else if (ret == DriverConstants.GETFID_DIR) {
            // the file might be changed or deleted after the ret == ... test
            // above and the list() call below
            String[] children = new File(path).list();
            if (children != null) {
                for (String child : children) {
                    if (!listSpecialFilesRecursive(Util.join(path, child), cb)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
