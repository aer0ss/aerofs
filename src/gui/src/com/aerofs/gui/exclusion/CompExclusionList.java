package com.aerofs.gui.exclusion;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.common.BackgroundRenderer;
import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompExclusionList extends Composite
{
    private static final Logger l = Loggers.getLogger(GUI.class);
    public static final String PATH_DATA = "PATH_DATA";

    Table _table;
    Model _m;

    public CompExclusionList(Composite parent)
    {
        super(parent, SWT.NONE);
        setLayout(new FillLayout(SWT.HORIZONTAL));
        _table = new Table(this, SWT.BORDER);
        _table.setFocus();
        _table.addListener(SWT.EraseItem, new BackgroundRenderer(_table, PATH_DATA, true));
        _table.addListener(SWT.Resize, new SelectiveSyncTableResizeListener());
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
        // We require this column because of space issues on Windows. Somehow inserting a checkbox
        // in the first (column 0) column makes the column right align. To solve that add a dummy
        // column at the beginning and then checkbox in the second column.
        TableColumn spaceColumn = new TableColumn(_table, SWT.NONE);
        // This column i.e. column 1 will hold the checkbox.
        TableColumn checkboxColumn = new TableColumn(_table, SWT.LEFT);
        // This column will i.e. column 2 will hold the folder icon and name.
        TableColumn folderNameIcnColumn = new TableColumn(_table, SWT.FILL);
        // Again this is because of Windows magic. Need to hardcode the widths.
        spaceColumn.setWidth(10);
        checkboxColumn.setWidth(30);

        spaceColumn.setResizable(true);
        checkboxColumn.setResizable(true);
        folderNameIcnColumn.setResizable(true);
        folderNameIcnColumn.pack();
    }

    private void addSeparatorToTable(TableItem item, String separatorText)
    {
        item.setData(PATH_DATA, null);
        item.setFont(GUIUtil.makeBold(item.getFont()));
        item.setText(2, separatorText);
    }

    private void addFolderToTableItem(final TableItem item, Path currentPath, TableEditor editor)
    {
        // Setting item data to distinguish between separator and folders.
        item.setData(PATH_DATA, currentPath);
        final Button button = GUIUtil.createButton(_table, SWT.CHECK);
        button.setBackground(_table.getBackground());
        button.pack();
        editor.horizontalAlignment = SWT.LEFT;
        editor.minimumWidth = button.getSize().x;
        editor.setEditor(button, item, 1);
        button.setSelection((!_m._alreadyExcluded.contains(currentPath)));
        // Associating data with button so that when we retrieve checked buttons, we can get
        // data about the folder which we are going to include. Since the control is not a CheckBox
        // table viewer or tree viewer it is not very easy to get state of the checkbox from
        // TableItems.
        button.setData(PATH_DATA, currentPath);
        FolderData folderData = _m._external.containsKey(currentPath) ?
                _m._external.get(currentPath) : _m._internal.get(currentPath);
        button.addSelectionListener(new SelectiveSyncSelectionAdapter(button, currentPath,
                folderData, _m._external));
        // Not a big fan of hardcoding column numbers but life is hard.
        item.setImage(2, folderData._isShared ? Images.getSharedFolderIcon() :
                Images.getFolderIcon());
        item.setText(2, folderData._name);
    }

    private List<Object> getTableContent()
    {
        boolean needSeparator = _m._external.size() > 0;

        List<Object> tableContents = Lists.newArrayList();

        for(Path path: _m._internal.keySet()) {
            tableContents.add(path);
        }
        if (needSeparator) {
            tableContents.add("Folders outside your AeroFS folder");
            for(Path path: _m._external.keySet()) {
                tableContents.add(path);
            }
        }
        return tableContents;
    }

    private class SelectiveSyncSelectionAdapter extends SelectionAdapter
    {
        private final Button _button;
        private final Path  _currentPath;
        private final FolderData _folderData;
        private final Map<Path, FolderData> _external;

        public SelectiveSyncSelectionAdapter(Button button, Path currentPath, FolderData folderData,
                Map<Path, FolderData> external)
        {
            _button = button;
            _currentPath = currentPath;
            _folderData = folderData;
            _external = external;
        }

        @Override
        public void widgetSelected(SelectionEvent e)
        {
            // Show dialog box only if:
            // 1. The checkbox is selected
            // 2. The selected folder is an external folder
            // 3. The selected folder belongs to the excluded list. This prevents the dialog box
            // from opening if we uncheck and check an external folder, because without clicking
            // "OK" no unlinking has actually been done.
            if (_button.getSelection() && _currentPath.toPB().getElemCount() == 0 &&
                    _m._alreadyExcluded.contains(_currentPath)) {
                DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.SHEET);
                dlg.setMessage("Select a location to sync this folder into");
                String absPath = dlg.open();

                if( absPath != null) {
                    File f = new File(absPath);
                    // Extracting the last name of the location chosen to sync into.
                    // If user decides to share / or C:/, we need to get the Absolute Path.
                    // I know I am being ridiculous but life is a vampire of sorts.
                    String syncingDirName = f.getName().length() > 0 ? f.getName() :
                            f.getAbsolutePath();
                    if (GUI.get().ask(getShell(), MessageType.QUESTION, "You are choosing to sync "+
                            _folderData._name + " to:\n\n" + absPath + "\n\nThis will start syncing" +
                            " everything within " + syncingDirName + ". " +
                            "Do you still want to continue?")) {
                        _external.put(_currentPath, new FolderData(_folderData._name,
                                _folderData._isShared, _folderData._isInternal, absPath));
                        return;
                    }
                }
                _button.setSelection(false);
            }
        }
    }

    /**
     * This function populates the selective sync dialog. It populates the table by first inserting
     * all internal folders, then a separator and then external folders.
     */
    private void fill()
    {
        List<Object> tableContent = getTableContent();

        for (Object elem: tableContent) {
            TableItem ti = new TableItem(_table, SWT.NONE);
            TableEditor editor = new TableEditor(_table);
            if (elem instanceof Path) {
                final Path currentPath = (Path)elem;
                addFolderToTableItem(ti, currentPath, editor);
            } else if (elem instanceof String) {
                addSeparatorToTable(ti, (String) elem);
            }
        }
    }

    /**
     * A private model class that maintains a set of:
     * 1. Set of already excluded folders.
     * 2. Map of all internal folders
     * 3. Map of all external folders.
     */
    private static class Model
    {
        Set<Path> _alreadyExcluded;
        // These are Tree maps because we need them to be ordered.
        Map<Path, FolderData> _internal;
        Map<Path, FolderData> _external;
    }

    Model getPrevSyncedAndExpelledFolders() throws Exception
    {
        Model m = new Model();
        m._alreadyExcluded = Sets.newHashSet();

        List<PBSharedFolder> sharedFolders = UIGlobals.ritual()
                .listSharedFolders()
                .getSharedFolderList();

        Collection<PBSharedFolder> internalStores =
                UIUtil.filterStoresIntoInternalOrExternal(sharedFolders, true);
        Collection<PBSharedFolder> externalStores =
                UIUtil.filterStoresIntoInternalOrExternal(sharedFolders, false);

        m._internal = SharedFolderToFolderDataConverter.getAllInternalFolders(internalStores);
        m._external = SharedFolderToFolderDataConverter.resolveStoresToPathFolderDataMap(
                externalStores);
        m._alreadyExcluded = SharedFolderToFolderDataConverter.getAllExcludedFolders(sharedFolders);
        return m;
    }

    /**
     * This function returns a operations object that contains which folders are to be selectively
     * synced.
     *
     *  Folders in the newly included list = (Set of all checked folders (Intersect) Set of excluded
     * folders when we populated the list)
     * This will avoid including any folders that have already been included.
     *
     * Folders in the newly excluded set = (Set of all  unchecked folders - Set of excluded folders
     * when we populated the list)
     * This will avoid trying to exclude an already excluded folder.
     */
    SelectivelySyncedFolders computeSelectivelySyncedFolders()
    {
        SelectivelySyncedFolders ssf = new SelectivelySyncedFolders();
        if (_m == null) {
            ssf._newlyExcludedFolders = Maps.newHashMap();
            ssf._newlyIncludedFolders = Maps.newHashMap();
            return ssf;
        }

        Map<Path, FolderData> allFolders = Maps.newHashMap(_m._internal);
        allFolders.putAll(_m._external);

        Map<Path, FolderData> allCheckedFolders = Maps.newHashMap();
        Map<Path, FolderData> allUnCheckedFolders = Maps.newHashMap();

        for(Control control: _table.getChildren()) {
            if (control instanceof Button) {
                Button checkBox = (Button)control;
                Path path = (Path)checkBox.getData(PATH_DATA);
                if (checkBox.getSelection()) {
                    allCheckedFolders.put(path, allFolders.get(path));
                } else {
                    allUnCheckedFolders.put(path, allFolders.get(path));
                }
            }
        }
        ssf._newlyIncludedFolders = Maps.newHashMap(allCheckedFolders);
        ssf._newlyIncludedFolders.keySet().retainAll(_m._alreadyExcluded);

        ssf._newlyExcludedFolders = Maps.newHashMap(allUnCheckedFolders);
        ssf._newlyExcludedFolders.keySet().removeAll(_m._alreadyExcluded);
        return ssf;
    }

    /**
     * This class is needed to properly resize the Selective Sync dialog box. This will help the
     * last column (the column for folder name and icons) to fill the space that remains after
     * adding the dummy space column and checkbox column.
     */
    private class SelectiveSyncTableResizeListener implements Listener
    {
        @Override
        public void handleEvent(Event event) {
            Table table = (Table)event.widget;
            int widthForLastColumn = table.getClientArea().width;
            for(int i = 0; i < table.getColumnCount() - 1; i++) {
                widthForLastColumn -= table.getColumn(i).getWidth();
            }
            table.getColumn(table.getColumnCount() - 1).setWidth(widthForLastColumn);
        }
    }
}