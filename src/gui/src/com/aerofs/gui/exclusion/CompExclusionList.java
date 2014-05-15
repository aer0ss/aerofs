package com.aerofs.gui.exclusion;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class CompExclusionList extends Composite
{
    private static final Logger l = Loggers.getLogger(GUI.class);
    public static final String FOLDER_DATA = "FOLDER_DATA";

    TableViewer _v;
    Model _m;

    public CompExclusionList(Composite parent)
    {
        super(parent, SWT.NONE);
        setLayout(new FillLayout(SWT.HORIZONTAL));

        _v = new TableViewer(this,SWT.BORDER);
        createTableColumns();
        try {
            _m = getPrevSyncedAndExpelledFolders();
            fill();
        } catch (Exception e) {
            l.info("Unable to get synced and expelled folders. Exception: {}", e);
        }
    }

    private void createTableColumns()
    {
        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        // Column for checkbox.
        TableColumn checkboxColumn = new TableColumn(_v.getTable(), SWT.NONE);
        checkboxColumn.pack();
        // Set weight to be 0 but minimum width to be the packed width of the column so that it
        // occupies only the space it needs.
        tableColumnLayout.setColumnData(checkboxColumn, new ColumnWeightData(0,
                checkboxColumn.getWidth(), true));
        // Column for folder icon.
        TableColumn folderIcnColumn = new TableColumn(_v.getTable(), SWT.NONE);
        folderIcnColumn.pack();
        tableColumnLayout.setColumnData(folderIcnColumn, new ColumnWeightData(0,
                folderIcnColumn.getWidth(), true));
        // Column for folder name.
        TableColumn folderNameColumn = new TableColumn(_v.getTable(), SWT.FILL);
        folderNameColumn.pack();
        // Set weight to be 100 so it fills up the rest of the space.
        tableColumnLayout.setColumnData(folderNameColumn, new ColumnWeightData(100,
                folderNameColumn.getWidth(), true));
        setLayout(tableColumnLayout);
    }

    private void customizeEditorRow(TableEditor editor, Control w, boolean grabHorizontal)
    {
        editor.grabHorizontal = grabHorizontal;
        editor.minimumWidth = w.getSize().x;
        editor.horizontalAlignment = SWT.LEFT;
    }

    private void addSeparatorToTable(TableItem item, String separatorText)
    {
        FontData separatorFontData = new FontData();
        separatorFontData.setStyle(SWT.BOLD);
        item.setFont(new Font(getDisplay(), separatorFontData));
        item.setBackground(getDisplay().getSystemColor(SWT.COLOR_GRAY));
        item.setText(2, separatorText);
    }

    private void addFolderToTableItem(TableItem item, FolderData currentFolder, TableEditor editor)
    {
        final Button button = GUIUtil.createButton(_v.getTable(), SWT.CHECK);
        button.pack();

        customizeEditorRow(editor, button, true);
        editor.setEditor(button, item, 0);

        button.setSelection((!_m._alreadyExcluded.contains(currentFolder)));
        // Associating data with button so that when we retrieve checked buttons, we can get
        // data about the folder which we are going to include. Since the control is not a CheckBox
        // table viewer or tree viewer it is not very easy to get state of the checkbox from
        // TableItems.
        button.setData(FOLDER_DATA, currentFolder);
        button.addSelectionListener(new SelectiveSyncSelectionAdapter(button, currentFolder));
        // Not a big fan of hardcoding column numbers but life is hard.
        item.setImage(1, Images.get(Images.ICON_SHARED_FOLDER));
        item.setText(2, currentFolder._name);
    }

    private List<Object> getTableContent()
    {
        boolean needSeparator = _m._external.size() > 0;

        List<Object> tableContents = Lists.newArrayList();

        for(FolderData folder: _m._internal) {
            tableContents.add(folder);
        }
        if (needSeparator) {
            tableContents.add("Folders outside your AeroFS folder");
            for(FolderData folder: _m._external) {
                tableContents.add(folder);
            }
        }
        return tableContents;
    }

    private class SelectiveSyncSelectionAdapter extends SelectionAdapter
    {
        private final Button _button;
        private final FolderData _currentFolder;

        public SelectiveSyncSelectionAdapter(Button button, FolderData currentFolder)
        {
            _button = button;
            _currentFolder = currentFolder;
        }

        @Override
        public void widgetSelected(SelectionEvent e)
        {
            // Show dialog box only if: 1. The checkbox is selected, 2. The selected folder is
            // an external folder, 3. The selected folder belongs to the excluded list (the last one
            // prevents the dialog box from opening if we uncheck and check an external folder, because
            // without clicking "OK" no unlinking has actually been done.
            if (_button.getSelection() && _currentFolder._path.toPB().getElemCount() == 0 &&
                    _m._alreadyExcluded.contains(_currentFolder)) {
                DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.SHEET);
                dlg.setMessage("Select a location to sync this folder into");
                String path = dlg.open();

                if (path == null || !GUI.get().ask(getShell(), MessageType.QUESTION,
                        "Are you sure you want to sync " + _currentFolder._name +
                                " to:\n\n" + path + "?")) {
                    _button.setSelection(false);
                    return;
                }
                _currentFolder._absPath = path;
            }
        }
    }

    /**
     * This function populates the selective sync dialog. It:
     * 1. Creates 3 columns, one each for a checkbox, folder icon and folder name.
     * 2. Creates the total number of table items which is total # of folders + a separator.
     * 3. Populates the table by first inserting all internal folders, then a separator and then
     * external folders.
     */
    private void fill()
    {
        List<Object> tableContent = getTableContent();

        for (Object elem: tableContent) {
            TableItem ti = new TableItem(_v.getTable(), SWT.NONE);
            TableEditor editor = new TableEditor(_v.getTable());

            if (elem instanceof FolderData) {
                final FolderData currentFolder = (FolderData)elem;
                addFolderToTableItem(ti, currentFolder, editor);
            } else if (elem instanceof String) {
                addSeparatorToTable(ti, (String) elem);
            }
        }
    }

    class FolderData implements Comparable<FolderData> {
       public Path _path;
       public String _name;
       public String _absPath;

       FolderData(Path path, String name, String absPath) {
           _path = path;
           _name = name;
           _absPath = absPath;
       }

        @Override
        public boolean equals(Object ob) {
            return (this == ob) || (ob instanceof FolderData
                                            && Objects.equal(_path, ((FolderData)ob)._path));
        }

        @Override
        public int hashCode() {
            return _path.hashCode();
        }


        @Override
        public int compareTo(FolderData folderData)
        {
            return this._name.compareTo(folderData._name);
        }
    }

    static boolean isInternalFolder(FolderData folder) {
        return folder._path.toPB().getElemCount() > 0;
    }

    /**
     * A private model class that maintains a set of:
     * 1. Set of already excluded folders.
     * 2. Set of all internal folders
     * 3. Set of all external folders.
     */
    private static class Model
    {
        HashSet<FolderData> _alreadyExcluded;
        // These are Tree sets because we need them to be ordered.
        TreeSet<FolderData> _internal;
        TreeSet<FolderData> _external;
    }

    Model getPrevSyncedAndExpelledFolders() throws Exception
    {
        Model m = new Model();
        m._alreadyExcluded = Sets.newHashSet();
        m._internal = Sets.newTreeSet();
        m._external = Sets.newTreeSet();

        for (PBSharedFolder sharedFolder: UIGlobals.ritual().listSharedFolders().getSharedFolderList()){
            Path path = Path.fromPB(sharedFolder.getPath());
            FolderData folderData = new FolderData(path, sharedFolder.getName(),
                    UIUtil.absPathNullable(path));
            // If the folder is not admitted(or linked) then add to the excluded list.
            if (!sharedFolder.getAdmittedOrLinked()) {
                m._alreadyExcluded.add(folderData);
            }
            if (sharedFolder.getPath().getElemCount() > 0) {
                m._internal.add(folderData);
            } else {
                m._external.add(folderData);
            }
        }
        return m;
    }

    /**
     * This function returns a operations object that contains which folders are to be selectively
     * synced.
     *
     * Folders in the excluded set = (Set of all folders - Set of excluded
     * folders when we populated the list) - Set of all checked folders
     * This will avoid trying to exclude an already excluded folder.
     *
     * Folders in included list = (Set of all checked folders - Set of excluded
     * folders when we populated the list)
     * This will avoid including any folders that have already been included
     */
    Operations getOperations()
    {
        Operations ops = new Operations();
        if (_m == null) {
            ops._newlyExcludedFolders = Collections.emptySet();
            ops._newlyIncludedFolders = Collections.emptySet();
            return ops;
        }

        HashSet<FolderData> allCheckedFolders = Sets.newHashSet();

        for(Control control: _v.getTable().getChildren()) {
            if (control instanceof Button) {
                Button checkBox = (Button)control;
                if (checkBox.getSelection()) {
                    FolderData folderData = (FolderData)checkBox.getData(FOLDER_DATA);
                    allCheckedFolders.add(folderData);
                }
            }
        }
        HashSet<FolderData> newlyCheckedFolders =
                Sets.newHashSet(allCheckedFolders);
        newlyCheckedFolders.retainAll(_m._alreadyExcluded);

        ops._newlyIncludedFolders = Sets.newHashSet(newlyCheckedFolders);

        ops._newlyExcludedFolders = Sets.newHashSet();
        // Add all folders.
        ops._newlyExcludedFolders.addAll(_m._internal);
        ops._newlyExcludedFolders.addAll(_m._external);

        ops._newlyExcludedFolders.removeAll(_m._alreadyExcluded);
        ops._newlyExcludedFolders.removeAll(allCheckedFolders);

        return ops;
    }
}