package com.aerofs.gui.diagnosis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util.FileName;
import com.aerofs.lib.cfg.Cfg;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
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
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.fsi.FSIClient;
import com.aerofs.lib.fsi.FSIUtil;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Fsi.PBDeleteBranchCall;
import com.aerofs.proto.Fsi.PBFSICall;
import com.aerofs.proto.Fsi.PBFSICall.Type;
import com.aerofs.proto.Fsi.PBListConflictsReply;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.events.SelectionAdapter;

public class CompConflictFiles extends Composite {

    private static class Entry {
        final PBPath _path;
        final int _kidx;
        final String _fspath;
        //final List<String> _editors;

        private Entry(PBPath path, String fspath, List<String> editors,
                int kidx)
        {
            _path = path;
            _fspath = fspath;
            //_editors = editors;
            _kidx = kidx;
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
        private final Map<Program, Image> _iconCache =
                new HashMap<Program, Image>();

        @Override
        public String getColumnText(Object element, int columnIndex)
        {
            if (element instanceof String) return columnIndex == 0 ?
                    element.toString() : "";

            Entry en = (Entry) element;

            switch (columnIndex) {
            case 0:
                return GUIUtil.shortenText(_gc, FSIUtil.toString(en._path),
                        _table.getClientArea().width, false);
            default:
                return "";
            }
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex)
        {
            if (columnIndex != 0) return null;
            if (element instanceof String) return Images.get(Images.ICON_ERROR);

            Entry en = (Entry) element;
            Path path = new Path(en._path);
            return Images.getFileIcon(path.last(), _iconCache);
        }

        @Override
        public void dispose()
        {
            for (Image img : _iconCache.values()) img.dispose();
            _iconCache.clear();
        }
    }

    protected Object result;
    private GC _gc;
    private final ArrayList<Entry> _entries = new ArrayList<Entry>();
    private final Shell _shell;
    private Button _btnSaveAs;
    private Button _btnOpenLocal;
    private Table _table;
    private TableViewer _tv;
    private CompSpin _compSpin;
    private Button _btnDelete;
    private final boolean _showSystemFiles;
    private Button _btnOpenConflict;
    final private InjectableFile.Factory _factFile = new InjectableFile.Factory();

    static {
        // needed for DriverConstants etc
        OSUtil.get().loadLibrary("aerofsd");
    }

    public CompConflictFiles(Composite parent, int style, boolean showSystemFiles)
    {
        super(parent, style);
        _shell = getShell();
        _showSystemFiles = showSystemFiles;

        GridLayout glShell = new GridLayout(5, false);
        glShell.marginHeight = 0;
        glShell.marginWidth = 0;
        glShell.horizontalSpacing = GUIUtil.getInterButtonHorizontalSpace(glShell);;
        setLayout(glShell);

        _tv = new TableViewer(this, SWT.BORDER | SWT.MULTI);
        _tv.setContentProvider(new ContentProvider());
        _tv.setLabelProvider(new LabelProvider());
        _tv.setInput(_entries);

        _table = _tv.getTable();
        _table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 5, 1));

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
                    sb.append(FSIUtil.toString(en._path));
                }

                if (count == 0) return;

                Clipboard cb = new Clipboard(_shell.getDisplay());
                try {
                    cb.setContents(new Object[] { sb.toString() },
                            new Transfer[] { TextTransfer.getInstance() });
                } catch (SWTError e) {
                    Util.l(CompConflictFiles.class).warn("cp 2 clipboard: " +
                            Util.e(e));
                    return;
                } finally {
                    cb.dispose();
                }
            }
        });

        _table.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetDefaultSelected(SelectionEvent arg0)
            {
                try {
                    openConflict();
                } catch (Exception e) {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            S.CONFLICT_OPEN_FAIL);
                }
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
                /*
                 * need async exec to work around a weird bug:
       org.eclipse.core.runtime.AssertionFailedException: null argument:
       at org.eclipse.core.runtime.Assert.isNotNull(Assert.java:85)
       at org.eclipse.core.runtime.Assert.isNotNull(Assert.java:73)
       at org.eclipse.jface.viewers.StructuredViewer.disassociate(StructuredViewer.java:640)
       at org.eclipse.jface.viewers.AbstractTableViewer.internalRefreshAll(AbstractTableViewer.java:727)
       at org.eclipse.jface.viewers.AbstractTableViewer.internalRefresh(AbstractTableViewer.java:649)
       at org.eclipse.jface.viewers.AbstractTableViewer.internalRefresh(AbstractTableViewer.java:636)
       at org.eclipse.jface.viewers.StructuredViewer.run(StructuredViewer.java:1457)
       at org.eclipse.jface.viewers.StructuredViewer.preservingSelection(StructuredViewer.java:1392)
       at org.eclipse.jface.viewers.StructuredViewer.preservingSelection(StructuredViewer.java:1353)
       at org.eclipse.jface.viewers.StructuredViewer.refresh(StructuredViewer.java:1455)
       at org.eclipse.jface.viewers.ColumnViewer.refresh(ColumnViewer.java:537)
       at org.eclipse.jface.viewers.StructuredViewer.refresh(StructuredViewer.java:1414)
       at com.aerofs.gui.dialogs.diagnosis.CompConflictFiles$3.handleEvent(SourceFile:229)
       at org.eclipse.swt.widgets.EventTable.sendEvent(EventTable.java:84)
       at org.eclipse.swt.widgets.Widget.sendEvent(Widget.java:1053)
       at org.eclipse.swt.widgets.Widget.sendEvent(Widget.java:1077)
       at org.eclipse.swt.widgets.Widget.sendEvent(Widget.java:1058)
       at org.eclipse.swt.widgets.Table.setDeferResize(Table.java:4325)
       at org.eclipse.swt.widgets.Table.createItem(Table.java:1836)
       at org.eclipse.swt.widgets.TableItem.<init>(TableItem.java:119)
       at org.eclipse.swt.widgets.TableItem.<init>(TableItem.java:113)
       at org.eclipse.jface.viewers.TableViewer.internalCreateNewRowPart(TableViewer.java:183)
       at org.eclipse.jface.viewers.AbstractTableViewer.createItem(AbstractTableViewer.java:277)
       at org.eclipse.jface.viewers.AbstractTableViewer.add(AbstractTableViewer.java:263)
       at org.eclipse.jface.viewers.AbstractTableViewer.add(AbstractTableViewer.java:315)
       at com.aerofs.gui.dialogs.diagnosis.CompConflictFiles$10.run(SourceFile:465)
       at com.aerofs.gui.GUI$4.run(SourceFile:174)
       at org.eclipse.swt.widgets.RunnableLock.run(RunnableLock.java:35)
                 */

                GUI.get().safeAsyncExec(getShell(), new Runnable() {
                    @Override
                    public void run()
                    {
                        // update text shortening
                        _tv.refresh();
                    }
                });
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

        _btnOpenLocal = new Button(this, SWT.NONE);
        _btnOpenLocal.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                try {
                    openLocal();
                } catch (Exception e) {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            S.FILE_OPEN_FAIL);
                }
            }
        });
        _btnOpenLocal.setEnabled(false);
        _btnOpenLocal.setText("Open Local");

        _btnOpenConflict = new Button(this, SWT.NONE);
        _btnOpenConflict.setEnabled(false);
        _btnOpenConflict.setText("Open Conflict");
        _btnOpenConflict.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                try {
                    openConflict();
                } catch (Exception e) {
                    GUI.get().show(getShell(), MessageType.ERROR,
                            S.CONFLICT_OPEN_FAIL);
                }
            }
        });

        _btnSaveAs = new Button(this, SWT.NONE);
        _btnSaveAs.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                try {
                    saveAs();
                } catch (Exception e) {
                    GUI.get().show(_shell, MessageType.WARN, "The file couldn't" +
                            " be saved " + UIUtil.e2msg(e) + ".");
                }
            }
        });
        _btnSaveAs.setEnabled(false);
        _btnSaveAs.setText("Save As...");

        _btnDelete = new Button(this, SWT.NONE);
        _btnDelete.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                List<Entry> ens = getSelectedEntries();

                if (!GUI.get().ask(_shell, MessageType.QUESTION,
                        "Delete " + (ens.size() > 1 ? "these conflicts" : "this conflict") +
                        " copy from ALL the users and devices?" +
                        " Please make sure that its content" +
                        " have been merged into the local copy.")) {
                    return;
                }

                try {
                    deleteConflict();
                } catch (Exception e) {
                    GUI.get().show(_shell, MessageType.ERROR,
                            "The conflict copy couldn't be deleted " +
                            UIUtil.e2msg(e) + ".");
                }
            }
        });
        _btnDelete.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        _btnDelete.setText("Delete Conflict");
        _btnDelete.setEnabled(false);

        _compSpin = new CompSpin(this, SWT.NONE);
    }

    private void selectionChanged()
    {
        ArrayList<Entry> sels = getSelectedEntries();

        _btnSaveAs.setEnabled(sels.size() > 0);
        _btnOpenLocal.setEnabled(sels.size() > 0);
        _btnOpenConflict.setEnabled(sels.size() > 0);
        _btnDelete.setEnabled(sels.size() > 0);
    }

    private void openLocal() throws Exception
    {
        for (Entry en : getSelectedEntries()) {
            String path = (new Path(en._path)).toAbsoluteString(Cfg.absRootAnchor());
            if (!Program.launch(path)) {
                throw new Exception(S.FILE_OPEN_FAIL);
            }
        }
    }

    private void openConflict() throws Exception
    {
        for (Entry en : getSelectedEntries()) {
            FileName file = Util.splitFileName(FSIUtil.getLast(en._path));
            InjectableFile fTmp = _factFile.createTempFile(
                    file.name + " (CONFLICT COPY. READ-ONLY) ", file.extension);
            _factFile.create(en._fspath).copy(fTmp, false, false);
            if (!Program.launch(fTmp.getPath())) {
                throw new Exception(S.FILE_OPEN_FAIL);
            }
        }
    }

    private void saveAs() throws IOException
    {
        for (Entry en : getSelectedEntries()) {
            FileDialog dlg = new FileDialog(_shell, SWT.SHEET | SWT.SAVE);
            dlg.setFileName(FSIUtil.getLast(en._path));
            dlg.setOverwrite(true);
            String path = dlg.open();
            if (path == null) break;

            InjectableFile fSrc = _factFile.create(en._fspath);
            InjectableFile fDest = _factFile.create(path);
            fSrc.copy(fDest, false, false);
        }
    }

    private void deleteConflict() throws Exception
    {
        FSIClient fsi = FSIClient.newConnection();
        try {
            for (Entry en : getSelectedEntries()) {
                fsi.rpc_(PBFSICall.newBuilder()
                        .setType(Type.DELETE_BRANCH)
                        .setDeleteBranch(PBDeleteBranchCall.newBuilder()
                                .setPath(en._path)
                                .setKidx(en._kidx))
                        .build());
                removeEntry(en);
            }
        } finally {
            fsi.close_();
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
                    Util.l(CompConflictFiles.class).warn("search 4 conflict: " + Util.e(e));
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
            PBListConflictsReply reply = fsi.rpc_(PBFSICall.newBuilder()
                    .setType(Type.LIST_CONFLICTS).build()).getListConflicts();
            int cnt = reply.getPathCount();
            for (int i = 0; i < cnt; i++) {
                PBPath path = reply.getPath(i);
                if (!_showSystemFiles && UIUtil.isSystemFile(path)) {
                    continue;
                }
                addEntry(new Entry(path, reply.getFsPath(i),
                        reply.getEditors(i).getEditorList(), reply.getKidx(i)));
                count.set(count.get() + 1);
            }
        }

    /**
     * this method may be called in non-UI threads
     */
    private void addEntry(final Entry en)
    {
        GUI.get().safeExec(_shell, new Runnable() {
            @Override
            public void run()
            {
                _entries.add(en);
                _tv.add(en);
            }
        });
    }

    /**
     * this method may NOT be called in non-UI threads
     */
    private void removeEntry(final Entry en)
    {
        _entries.remove(en);
        _tv.remove(en);
    }
}
