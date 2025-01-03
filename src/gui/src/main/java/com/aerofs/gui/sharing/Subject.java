/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.acl.SubjectPermissions;
import com.aerofs.base.id.GroupID;
import com.aerofs.gui.Images;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.Cfg;
import org.apache.commons.lang.StringEscapeUtils;
import org.eclipse.swt.graphics.Image;

import static java.util.Objects.hash;
import static org.apache.commons.lang.StringUtils.isEmpty;

public interface Subject
{
    public String toPB();

    // whether the subject has a name, used for sorting
    public boolean hasName();

    // a short text to identify the subject.
    public String getLabel();

    // a long text to describe the subject
    public String getDescription();

    public boolean isLocalUser();

    public Image getImage();

    public static class User implements Subject
    {
        public final UserID         _userID;
        public final String         _firstname;
        public final String         _lastname;

        public User(UserID userID, String firstname, String lastname)
        {
            _userID     = userID;
            _firstname  = firstname;
            _lastname   = lastname;
        }

        @Override
        public String toPB()
        {
            return SubjectPermissions.getStringFromSubject(_userID);
        }

        @Override
        public boolean hasName()
        {
            return !isEmpty(getName());
        }

        private String getName()
        {
            return (StringEscapeUtils.unescapeHtml(_firstname.trim()) +
                    " " + StringEscapeUtils.unescapeHtml(_lastname.trim()).trim());
        }

        @Override
        public String getLabel()
        {
            return isLocalUser() ? "me"
                    : hasName() ? getName()
                    : _userID.getString();
        }

        @Override
        public String getDescription()
        {
            return _userID.getString();
        }

        @Override
        public boolean isLocalUser()
        {
            return Cfg.user().equals(_userID);
        }

        @Override
        public Image getImage()
        {
            return Images.get(Images.ICON_USER);
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o instanceof User && _userID.equals(((User)o)._userID));
        }

        @Override
        public int hashCode()
        {
            return hash(_userID);
        }
    }

    public static class Group implements Subject
    {
        public final GroupID    _groupID;
        public final String     _name;

        public Group(GroupID groupID, String name)
        {
            _groupID    = groupID;
            _name       = name;
        }

        @Override
        public String toPB()
        {
            return SubjectPermissions.getStringFromSubject(_groupID);
        }

        @Override
        public boolean hasName()
        {
            return true;
        }

        @Override
        public String getLabel()
        {
            return _name;
        }

        @Override
        public String getDescription()
        {
            return _name;
        }

        @Override
        public boolean isLocalUser()
        {
            return false;
        }

        @Override
        public Image getImage()
        {
            return Images.get(Images.ICON_GROUP);
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o instanceof Group && _groupID.equals(((Group)o)._groupID));
        }

        @Override
        public int hashCode()
        {
            return _groupID.hashCode();
        }
    }

    public static class InvalidSubject implements Subject
    {
        public final String _label;

        public InvalidSubject(String label)
        {
            _label = label;
        }

        @Override
        public String toPB()
        {
            throw new UnsupportedOperationException("toPB() is not supported.");
        }

        @Override
        public boolean hasName()
        {
            return true;
        }

        @Override
        public String getLabel()
        {
            return _label;
        }

        @Override
        public String getDescription()
        {
            return _label;
        }

        @Override
        public boolean isLocalUser()
        {
            return false;
        }

        @Override
        public Image getImage()
        {
            return Images.get(Images.ICON_ERROR);
        }
    }
}
