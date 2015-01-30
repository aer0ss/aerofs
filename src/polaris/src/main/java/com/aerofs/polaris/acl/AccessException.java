package com.aerofs.polaris.acl;

import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.PolarisError;
import com.google.common.base.Preconditions;

import java.util.Map;

/**
 * Instances of this class <strong>MUST</strong>
 * be thrown if the user does not have permissions
 * to access a shared folder.
 */
public final class AccessException extends PolarisException {

    private static final long serialVersionUID = -3547417330189536355L;

    private final String user;
    private final String sharedFolderOid;
    private final Access[] requested;

    /**
     * Constructor.
     *
     * @param user user id of the user who cannot access the shared folder
     * @param sharedFolderOid oid of the shared folder that cannot be accessed
     * @param requested one or more permissions the user requested but does not have
     */
    public AccessException(String user, String sharedFolderOid, Access... requested) {
        super(PolarisError.INSUFFICIENT_PERMISSIONS);

        Preconditions.checkArgument(requested.length > 0, "at least one Access type required");

        this.user = user;
        this.sharedFolderOid = sharedFolderOid;
        this.requested = requested;
    }

    @Override
    protected String getSimpleMessage() {
        return String.format("%s does not have %s access for %s", user, getAccessNames(requested), sharedFolderOid);
    }

    private Object getAccessNames(Access[] requested) {
        StringBuilder builder = new StringBuilder();

        builder.append('[');
        for (Access access : requested) {
            builder.append(access.name());
            builder.append(',');
        }
        builder.insert(builder.length() - 1, ']');

        return builder.toString();
    }

    @Override
    protected void addErrorFields(Map<String, Object> errorFields) {
        errorFields.put("user", user);
        errorFields.put("sharedFolderOid", sharedFolderOid);
        errorFields.put("access", requested);
    }
}
