package com.aerofs.gui.exclusion;

import com.aerofs.lib.Util;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import org.slf4j.Logger;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.jface.dialogs.IDialogConstants;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.Path;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Label;

public class CompExclusion extends Composite
{
    private final static Logger l = Util.l(CompExclusion.class);
    private final Button _btnCancel;
    private final Button _btnAdvancedView;
    private final Composite _composite;
    private final CompExclusionList _compList;
    private final Composite composite;

    public CompExclusion(Composite parent)
    {
        super(parent, SWT.NONE);

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        setLayout(glShell);

        Label lblNewLabel = new Label(this, SWT.NONE);
        GridData gd_lblNewLabel = new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1);
        gd_lblNewLabel.heightHint = 26;
        lblNewLabel.setLayoutData(gd_lblNewLabel);
        lblNewLabel.setText("Only checked folders will sync to this computer.");

        _compList = new CompExclusionList(this);
        GridData gd__compList = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd__compList.widthHint = 390;
        gd__compList.heightHint = 194;
        _compList.setLayoutData(gd__compList);

        _composite = new Composite(this, SWT.NONE);
        GridLayout glComposite = new GridLayout(2, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        _composite.setLayout(glComposite);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _btnAdvancedView = new Button(_composite, SWT.NONE);
        _btnAdvancedView.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        _btnAdvancedView.setText("Advanced View");
        _btnAdvancedView.setVisible(false);

        composite = new Composite(_composite, SWT.NONE);
        FillLayout fl = new FillLayout(SWT.HORIZONTAL);
        fl.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(fl);

        Button btnOk = new Button(composite, SWT.NONE);
        btnOk.setText(IDialogConstants.OK_LABEL);
        btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent ev)
            {
                Operations ops = _compList.getOperations();

                if (!ops._exclude.isEmpty()) {
                    if (!GUI.get().ask(getShell(), MessageType.WARN, "Unchecked folders will be" +
                            " deleted from this computer. They will not be deleted from other" +
                            " computers. Do you want to continue?")) return;
                }

                RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
                try {
                    for (Path path : ops._exclude) {
                        ritual.excludeFolder(path.toPB());
                    }
                    for (Path path : ops._include) {
                        ritual.includeFolder(path.toPB());
                    }

                    getShell().close();

                } catch (Exception e) {
                    l.warn("exclude folders: " + Util.e(e));
                    GUI.get().show(getShell(), MessageType.ERROR, "Couldn't complete the request " +
                            UIUtil.e2msg(e));
                } finally {
                    ritual.close();
                }
            }
        });

        _btnCancel = new Button(composite, SWT.NONE);
        _btnCancel.setText(IDialogConstants.CANCEL_LABEL);
        _btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });
    }
}
