package com.aerofs.gui.sharing.manage;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.SimpleContentProvider;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Ritual.GetACLReply;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Lists;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.slf4j.Logger;

import java.util.ArrayList;

public class CompUserList extends Composite
{
    private static final Logger l = Loggers.getLogger(CompUserList.class);

    public static interface ILoadListener
    {
        // this method is called within the GUI thread
        void loaded();
    }

    private TableViewer _tv;

    private TableColumn _tcIcon;
    private TableColumn _tcSubject;
    private TableColumn _tcRole;
    private TableColumn _tcArrow;

    private Role _rSelf;
    private Path _path;
    private final ILoadListener _ll;

    public CompUserList(Composite parent, Path path, ILoadListener ll)
    {
        super(parent, SWT.NONE);

        _path = path;
        _ll = ll;

        setLayout(new FillLayout(SWT.HORIZONTAL));
        _tv = new TableViewer(this, SWT.BORDER | SWT.FULL_SELECTION);
        final Table t = _tv.getTable();
        t.setBackground(getShell().getDisplay().getSystemColor(SWT.COLOR_WHITE));
        t.setHeaderVisible(false);

        ////////
        // add columns

        TableViewerColumn tvcIcon = new TableViewerColumn(_tv, SWT.CENTER);
        tvcIcon.setLabelProvider(new IconLabelProvider());
        _tcIcon = tvcIcon.getColumn();

        TableViewerColumn tvcSubject = new TableViewerColumn(_tv, SWT.NONE);
        tvcSubject.setLabelProvider(new SubjectLabelProvider());
        _tcSubject = tvcSubject.getColumn();

        TableViewerColumn tvcRole = new TableViewerColumn(_tv, SWT.NONE);
        tvcRole.setLabelProvider(new RoleLabelProvider());
        _tcRole = tvcRole.getColumn();

        TableViewerColumn tvcArrow = new TableViewerColumn(_tv, SWT.NONE);
        tvcArrow.setLabelProvider(new ArrowLabelProvider(this));
        _tcArrow = tvcArrow.getColumn();

        ////////
        // table-wise operations

        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent ev)
            {
                ViewerCell vc = _tv.getCell(new Point(ev.x, ev.y));
                if (vc == null) return;

                Object elem = vc.getElement();
                if (!(elem instanceof SubjectRolePair)) return;

                _tv.setSelection(new StructuredSelection(elem), true);

                SubjectRolePair srp = (SubjectRolePair) elem;
                if (!canChangeACL(srp)) return;

                new RoleMenu(CompUserList.this, srp, _tv.getTable(), _path).open();
            }
        });

        _tv.setContentProvider(new SimpleContentProvider());
        _tv.setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer v, Object e1, Object e2)
            {
                return UIUtil.compareUser(((SubjectRolePair) e1)._subject,
                        ((SubjectRolePair) e2)._subject);
            }
        });

        t.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                recalcUserColumnWidth();
            }
        });

        _tv.setInput(new Object[] { S.GUI_LOADING });

        loadAsync();
    }

    private void loadAsync()
    {
        ThreadUtil.startDaemonThread("userlist-async-load", new Runnable()
        {
            @Override
            public void run()
            {
                thdLoadAsync();
            }
        });
    }

    public boolean canChangeACL(SubjectRolePair srp)
    {
        return !srp._subject.equals(Cfg.user()) && _rSelf == Role.OWNER;
    }

    private boolean _recalcing;
    private int _users;

    private void recalcUserColumnWidth()
    {
        // this is to prevent an infinite recursion bug found in production
        if (_recalcing) return;

        _recalcing = true;
        try {
            _tcIcon.pack();
            _tcArrow.pack();

            Table t = _tv.getTable();
            ScrollBar sb = t.getVerticalBar();
            int scroll = OSUtil.isOSX() ? 1 : 0;
            scroll += sb != null && sb.isVisible() ? sb.getSize().x : 0;
            int width = t.getBounds().width - 2 * t.getBorderWidth() - _tcIcon.getWidth() -
                    _tcArrow.getWidth() - scroll;
            _tcSubject.setWidth(width * 618 / 1000);
            _tcRole.setWidth(width * 382 / 1000);
        } finally {
            _recalcing = false;
        }
    }

    private void thdLoadAsync()
    {
        load();
    }

    /**
     * This method can be called in either UI or non-UI threads
     */
    void load()
    {
        _rSelf = null;
        _users = 0;

        Object[] elems;
        try {
            ArrayList<SubjectRolePair> srps = Lists.newArrayList();
            GetACLReply reply = UI.ritual().getACL(Cfg.user().getString(), _path.toPB());
            _users = reply.getSubjectRoleCount();
            for (int i = 0; i < _users; i++) {
                PBSubjectRolePair srp = reply.getSubjectRole(i);
                UserID subject = UserID.fromExternal(srp.getSubject());
                Role role = Role.fromPB(srp.getRole());

                srps.add(new SubjectRolePair(subject, role));

                if (subject.equals(Cfg.user())) _rSelf = role;
            }
            elems = srps.toArray();
        } catch (ExNoPerm e) {
            l.warn("no perm to list acl");
            elems = new Object[] {
                new ExUIMessage("You are no longer a member of this shared folder.")
            };
        } catch (Exception e) {
            l.warn("list acl: " + Util.e(e));
            elems = new Object[] { e };
        }

        final Object[] elemsFinal = elems;
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run()
            {
                _tv.setInput(elemsFinal);
                recalcUserColumnWidth();
                _ll.loaded();
            }
        });
    }

    /**
     * @return 0 before the user list is fully loaded
     */
    int getUsersCount()
    {
        return _users;
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }
}
