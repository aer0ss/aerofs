/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.diagnosis;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.AeroFSMessageBox;
import com.aerofs.gui.AeroFSMessageBox.IconType;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBBranch.PBPeer;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.ListConflictsReply.ConflictedPath;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Maps;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class DlgConflicts extends AeroFSDialog
{
    private final @Nullable Path _singlePath;

    private FileList _fileList;
    private BranchList _conflictList;

    private final Map<Program, Image> _iconCache = Maps.newHashMap();

    public DlgConflicts(Shell parent)
    {
        this(parent, null);
    }

    public DlgConflicts(Shell parent, @Nullable Path path)
    {
        super(parent, "Conflicts", false, true);
        _singlePath = path;
    }

    @Override
    protected void open(Shell sh)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            sh = new Shell(getParent(), getStyle());

        final Shell shell = sh;
        // setup dialog controls
        GridLayout grid = new GridLayout(1, false);
        grid.marginHeight = GUIParam.MARGIN;
        grid.marginWidth = GUIParam.MARGIN;
        grid.horizontalSpacing = 4;
        grid.verticalSpacing = 4;
        shell.setLayout(grid);

        Label lblHelp = new Label(shell, SWT.WRAP);
        lblHelp.setText("Please deal with these conflicts");
        lblHelp.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        /**
         * This dialog has two display modes:
         *    one-pane: only show conflicts for one file, when invoked from shellext context menu
         *    two-pane: show a list of all files with conflicts and conflicts for selected file
         */
        if (_singlePath == null) {
            SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL | SWT.SMOOTH);
            GridData sashData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
            sashData.widthHint = 600;
            sashData.heightHint = 350;
            sashForm.setLayoutData(sashData);
            sashForm.setSashWidth(7);

            _fileList = new FileList(sashForm, SWT.NONE);
            _conflictList = new BranchList(sashForm);

            // set default proportion between version tree and version table
            // NOTE: must be done AFTER children have been added to the SashForm
            sashForm.setWeights(new int[] {1, 2});

            refreshConflictedFileList();
        } else {
            _conflictList = new BranchList(shell);
            _conflictList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        }

        // Create a composite that will hold the buttons row
        Composite buttons = newButtonContainer(shell);
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 1, 1));

        Button doneBtn = new Button(buttons, SWT.NONE);
        doneBtn.setText("Close");
        doneBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                shell.close();
            }
        });

        shell.addListener(SWT.Close, new Listener() {
            @Override
            public void handleEvent(Event event)
            {
                // Clear the icon
                for (Image img : _iconCache.values()) img.dispose();
                _iconCache.clear();
            }
        });

        // the version table will only be shown when a file is selected
        refreshConflictingVersionList(_singlePath);

        if (_singlePath == null) {
            // auto-refresh when number of conflicting file changes
            // TODO: auto-refresh when conflict branches of selected file change?
            UIGlobals.rnc().addListener(new IRitualNotificationListener() {
                @Override
                public void onNotificationReceived(PBNotification pb)
                {
                    if (isDisposed()) {
                        UIGlobals.rnc().removeListener(this);
                    } else if (pb.getType() == Type.CONFLICT_COUNT) {
                        GUI.get().safeAsyncExec(shell, new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                refreshConflictedFileList();
                            }
                        });
                    }
                }

                @Override
                public void onNotificationChannelBroken()
                {

                }
            });
        }
    }

    private void refreshConflictedFileList()
    {
        // TODO: looks like selection() may return null if the file list does not have focus...
        Path p = _fileList.selection();

        RitualBlockingClient ritual = UIGlobals.ritual();

        _fileList.clear();
        try {
            _fileList.fill(ritual.listConflicts().getConflictList());
        } catch (Exception e) {
            new AeroFSMessageBox(getShell(), true, e.toString(), IconType.ERROR)
                    .open();
            closeDialog();
        } finally {
            ritual.close();
        }

        if (p == null || !_fileList.select(p)) {
            // previously selected file disappeared from list of conflicted files
            refreshConflictingVersionList(null);
            _fileList.setFocus();
        }
    }

    private void refreshConflictingVersionList(@Nullable Path path)
    {
        _conflictList.clear();

        if (path == null) return;

        RitualBlockingClient ritual = UIGlobals.ritual();

        try {
            _conflictList.fill(path, ritual.getObjectAttributes(path.toPB())
                    .getObjectAttributes().getBranchList());
        } catch (Exception e) {
            new AeroFSMessageBox(getShell(), true, e.toString(), IconType.ERROR)
                    .open();
            closeDialog();
        } finally {
            ritual.close();
        }
    }

    private class FileList extends Composite
    {
        private final Table _d;

        public FileList(Composite composite, int i)
        {
            super(composite, i);

            FillLayout l = new FillLayout();
            l.spacing = 0;
            l.marginHeight = 0;
            l.marginWidth = 0;
            setLayout(l);

            _d = new Table(this, SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.H_SCROLL);
            _d.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    refreshConflictingVersionList(selection());
                }
            });
        }

        void clear()
        {
            _d.removeAll();
        }

        void fill(List<ConflictedPath> l)
        {
            _d.setItemCount(l.size());
            int i = 0;
            for (ConflictedPath conflict : l) {
                Path path = Path.fromPB(conflict.getPath());
                TableItem item = _d.getItem(i++);
                item.setText(0, path.toStringRelative());
                item.setData("PATH", path);
                item.setImage(0, Images.getFileIcon(path.last(), _iconCache));
            }
        }

        private Path getPath(TableItem item)
        {
            return (Path)item.getData("PATH");
        }

        @Nullable Path selection()
        {
            TableItem[] items = _d.getSelection();
            return items.length != 1 ? null : getPath(items[0]);
        }

        boolean select(@Nonnull Path p)
        {
            for (int i = 0; i < _d.getItemCount(); ++i) {
                if (getPath(_d.getItem(i)).equals(p)) {
                    _d.select(i);
                    return true;
                }
            }
            return false;
        }
    }

    private class BranchList extends ScrolledComposite
    {
        private final Composite _content;

        public BranchList(Composite composite)
        {
            super(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
            setExpandVertical(true);
            setExpandHorizontal(true);

            _content = new Composite(this, SWT.NONE);
            GridLayout r = new GridLayout(3, false);
            _content.setLayout(r);
            setContent(_content);

            // needed to prevent sash-resizing from messing up visibility somehow...
            addControlListener(new ControlAdapter() {
                @Override
                public void controlResized(ControlEvent controlEvent)
                {
                    layout();
                    _content.layout();
                }
            });
        }

        void clear()
        {
            for (Control c : _content.getChildren()) c.dispose();
        }

        void fill(Path p, java.util.List<PBBranch> l)
        {
            if (l.size() < 2) {
                new Label(_content, SWT.NONE).setText("No conflicting versions");
                refreshConflictedFileList();
                return;
            }

            PBBranch master = null;

            for (PBBranch b : l) {
                if (b.getKidx() == KIndex.MASTER.getInt()) {
                    master = b;
                    break;
                }
            }

            if (master == null) {
                new Label(_content, SWT.NONE).setText("No master branch");
                refreshConflictedFileList();
                return;
            }

            // always put master branch at the top
            new Branch(p, master, _content);
            for (PBBranch b : l) {
                if (b.getKidx() == KIndex.MASTER.getInt()) continue;
                new Label(_content, SWT.SEPARATOR | SWT.HORIZONTAL)
                        .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
                new Branch(p, b, _content);
            }

            Point sz = _content.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            setMinSize(sz);

            layout();
            _content.layout();
        }
    }

    /**
     * Display information about one conflict branch
     */
    private class Branch
    {
        private final Path _path;
        private final PBBranch _branch;
        private @Nullable String _export;

        public Branch(Path path, PBBranch branch, Composite parent)
        {
            _path = path;
            _branch = branch;

            boolean isMaster = _branch.getKidx() == KIndex.MASTER.getInt();

            Label lTitle = new Label(parent, SWT.NONE);
            lTitle.setText(isMaster ?
                    "Current version on this computer" :
                    "Branch " + _branch.getKidx());  // TODO: editors...
            scaleFont(lTitle, 1.3f);
            lTitle.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

            Composite cAction = new Composite(parent, SWT.NONE);
            cAction.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 1, 3));

            Label lAncestorToBranch = new Label(parent, SWT.WRAP);
            lAncestorToBranch.setText("Contributors: " +
                    joinEditors(_branch.getAncestorToBranchList()));
            lAncestorToBranch.setLayoutData(
                    new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

            Label lSize = new Label(parent, SWT.NONE);
            lSize.setText(Util.formatSize(_branch.getLength()));
            lSize.setAlignment(SWT.LEFT);
            lSize.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false, 1, 1));

            Label lModified = new Label(parent, SWT.NONE);
            lModified.setText("Last modified " + Util.formatAbsoluteTime(_branch.getMtime()));
            lModified.setAlignment(SWT.RIGHT);
            lModified.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false, 1, 1));

            RowLayout buttonLayout = new RowLayout(SWT.VERTICAL);
            buttonLayout.fill = true;
            buttonLayout.center = false;
            buttonLayout.spacing = 0;
            buttonLayout.wrap = false;
            buttonLayout.pack = false;
            buttonLayout.marginWidth = 0;
            buttonLayout.marginHeight = 0;
            buttonLayout.marginRight = 0;
            buttonLayout.marginTop = 0;
            buttonLayout.marginBottom = 0;
            cAction.setLayout(buttonLayout);

            Button btnOpen = new Button(cAction, SWT.NONE);
            btnOpen.setText("Open");
            btnOpen.setToolTipText(isMaster ?
                    "Open the current version of this file" :
                    "Open a read-only copy of the conflicting version");
            btnOpen.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    open();
                }
            });

            Button btnSave = new Button(cAction, SWT.NONE);
            btnSave.setText("Save...");
            btnSave.setToolTipText("Save a copy of this version in a different location");
            btnSave.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    save();
                }
            });

            Button btnResolve = new Button(cAction, SWT.NONE);
            btnResolve.setText("Resolve");
            btnResolve.setToolTipText("Resolve conflict by keeping only this version");
            btnResolve.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    resolve();
                }
            });
        }

        private boolean isMaster()
        {
            return _branch.getKidx() == KIndex.MASTER.getInt();
        }

        private void open()
        {
            if (!ensureExported()) return;

            if (!GUIUtil.launch(_export)) {
                GUI.get().show(getShell(), MessageType.ERROR, "Unable to open file");
            }
        }

        private void save()
        {
            if (!ensureExported()) return;

            String dest = saveAs(_path);
            if (dest == null) return;

            try {
                FileUtil.copy(new File(_export), new File(dest), false, true);
            } catch (IOException e) {
                GUI.get().show(getShell(), MessageType.ERROR, e.toString());
            }
        }

        private void resolve()
        {
            // TODO: better warning message
            // TODO: have a message box with a "don't ask me again" checkbox
            if (!GUI.get().ask(getShell(), MessageType.WARN,
                    "The conflict will be resolved by picking this version and deleting all the" +
                    " others. The change will propagate to other peers. Are you sure you want to " +
                    " proceed?")) {
                return;
            }

            RitualBlockingClient ritual = UIGlobals.ritual();

            PBPath path = _path.toPB();
            try {
                if (!isMaster()) {
                    // import exported branch content into MASTER before deleting
                    if (_export == null) {
                        _export = ritual.exportConflict(path, _branch.getKidx()).getDest();
                    }
                    ritual.importFile(path, _export);
                }

                // delete all non-master branches
                PBObjectAttributes attr = ritual.getObjectAttributes(path)
                        .getObjectAttributes();
                for (PBBranch b : attr.getBranchList()) {
                    if (b.getKidx() == KIndex.MASTER.getInt()) continue;
                    ritual.deleteConflict(path, b.getKidx());
                }
            } catch (Exception e) {
                GUI.get().show(getShell(), MessageType.ERROR, e.toString());

            } finally {
                ritual.close();

                if (_singlePath != null) closeDialog();
                refreshConflictedFileList();
            }
        }

        private boolean ensureExported()
        {
            if (_export != null) return true;

            // master branch is opened in place
            if (_branch.getKidx() == KIndex.MASTER.getInt()) {
                _export = UIUtil.absPathNullable(_path);
                return _export != null;
            }

            try {
                _export = exportBranch(_path, _branch.getKidx());

                // prevent users from making changes to temp files
                new File(_export).setReadOnly();
            } catch (Exception e) {
                GUI.get().show(getShell(), MessageType.ERROR, e.toString());

                refreshConflictedFileList();
                return false;
            }
            return true;
        }
    }

    private static String joinEditors(List<PBPeer> peers)
    {
        boolean first = true;
        StringBuilder bd = new StringBuilder();
        for (PBPeer p : peers) {
            if (first) {
                first = false;
            } else {
                bd.append(", ");
            }

            String user = p.getUserName();
            if (user.equals(Cfg.user().getString())) user = "You";
            bd.append(user);

            if (p.hasDeviceName()) bd.append(" on ").append(p.getDeviceName());
        }
        return bd.toString();
    }

    private static String exportBranch(Path p, int kidx) throws Exception
    {
        RitualBlockingClient ritual = UIGlobals.ritual();
        try {
            return ritual.exportConflict(p.toPB(), kidx).getDest();
        } finally {
            ritual.close();
        }
    }

    private String saveAs(Path path)
    {
        FileDialog fDlg = new FileDialog(this.getShell(), SWT.SHEET | SWT.SAVE);
        fDlg.setFilterNames(new String[]{"All files"});
        fDlg.setFilterExtensions(new String[]{"*.*"});
        String absPath = UIUtil.absPathNullable(path.removeLast());
        if (absPath != null) fDlg.setFilterPath(absPath);
        fDlg.setFileName(path.last());
        fDlg.setOverwrite(true); // The OS will show a warning if the user chooses an existing name

        return fDlg.open();
    }

    private static void scaleFont(Control c, float scale) {
        FontData[] d = c.getFont().getFontData();
        for (FontData fd : d) fd.setHeight((int)(scale * fd.getHeight()));
        final Font f = new Font(c.getDisplay(), d);
        c.setFont(f);
        c.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent disposeEvent)
            {
                f.dispose();
            }
        });
    }

    private static Composite newButtonContainer(Composite parent)
    {
        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.END, SWT.BOTTOM, true, false, 2, 1));
        RowLayout buttonLayout = new RowLayout();
        buttonLayout.pack = false;
        buttonLayout.wrap = false;
        buttonLayout.fill = true;
        buttonLayout.center = true;
        buttonLayout.marginBottom = 0;
        buttonLayout.marginTop = 0;
        buttonLayout.marginHeight = 0;
        buttonLayout.marginLeft = 0;
        buttonLayout.marginRight = 0;
        buttonLayout.marginWidth = 0;
        buttonLayout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        if (OSUtil.isOSX()) {
            // workaround broken margins on OSX
            buttonLayout.marginLeft = -4;
            buttonLayout.marginRight = -4;
            buttonLayout.marginTop = 4;
            buttonLayout.marginBottom = -6;
        }
        buttons.setLayout(buttonLayout);
        return buttons;
    }
}
