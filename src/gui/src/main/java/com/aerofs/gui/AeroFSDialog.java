package com.aerofs.gui;

import com.google.common.base.Strings;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.SWT;
import com.aerofs.lib.S;

/**
 * Sometimes we need this class instead of AeroFSJFaceDialog because JFace
 * dialogs mandates the ugly bottom bar.
 *
 * The dialog is always on top for all platforms except Linux.
 */
public abstract class AeroFSDialog extends Dialog {

    private Object _result;
    private Shell _shell;
    private final String _title;

    /**
     * @param title the title of the dialog. will be suffixed with S.TITLE_SUFFIX;
     * null to use S.DEFAULT_DIALOG_TITLE
     */
    public AeroFSDialog(Shell parent, String title, boolean sheet, boolean resizable)
    {
        this(parent, title, sheet, resizable, true);
    }

    /**
     * @param title the title of the dialog. will be suffixed with S.TITLE_SUFFIX;
     * null to use S.DEFAULT_DIALOG_TITLE
     */
    public AeroFSDialog(Shell parent, String title,
            boolean sheet, boolean resizable, boolean closeable)
    {
        super(parent, GUIUtil.createShellStyle(sheet, resizable, closeable));
        _title = title;
    }

    /**
     * Subclasses need to implement this method. The following code must be
     * present at the beginning of the method so WindowsBuilder Pro can parse the
     * file:
     *
     *      if (isWindowBuilderPro()) // $hide$
     *          shell = new Shell(getParent(), getStyle());
     *
     * The reason of this method name is that the name has to be in the
     * implementing class before WindowsBuilder agrees to parse the file.
     *
     * @param shell a newly created shell object without layout.
     */
    abstract protected void open(Shell shell);

    /**
     * Override in the subclass if you do not want the shell to have a certain size
     *   or simply not pack
     */
    protected void setShellSize()
    {
        _shell.pack();
    }

    /**
     * Open the dialog in blocking mode.
     *
     * Note: using this method name instead of open() is due to WindowBuilder's
     * limitation. See open().
     *
     * @return the result set by setResult(), or null if the method hasn't called
     */
    public Object openDialog()
    {
        _shell = new Shell(getParent(), getStyle());
        GUI.get().registerShell(_shell, getClass());

        _shell.setText(Strings.isNullOrEmpty(_title) ?
                S.DEFAULT_DIALOG_TITLE : _title + S.DIALOG_TITLE_SUFFIX);
        GUIUtil.setShellIcon(_shell);

        open(_shell);

        _shell.layout();
        setShellSize();

        if ((_shell.getStyle() & SWT.SHEET) == 0) {
            GUIUtil.centerShell(_shell);
            _shell.open();
            _shell.forceActive();
        } else {
            _shell.open();
        }

        Display display = getParent().getDisplay();
        while (!_shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        return _result;
    }

    /**
     * close the dialog and cause openDialog() to return null
     */
    public void closeDialog()
    {
        closeDialog(null);
    }

    /**
     * close the dialog and cause openDialog() to return result
     */
    public void closeDialog(Object result)
    {
        _result = result;
        _shell.close();
        _shell.dispose();
    }

    public boolean isDisposed()
    {
        return _shell == null || _shell.isDisposed();
    }

    public void forceActive()
    {
        assert _shell != null;
        _shell.forceActive();
    }

    protected Shell getShell()
    {
        return _shell;
    }
}
