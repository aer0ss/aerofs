/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.dryad;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.aerofs.labeling.L;
import com.aerofs.servlets.lib.SyncEmailSender;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sv.common.EmailCategory;
import com.google.common.collect.Lists;

import java.util.List;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class DryadService
{
    private final SQLThreadLocalTransaction     _sqlTrans;
    private final OrganizationDatabase          _dbOrg;
    private final SyncEmailSender               _email;

    public DryadService(SQLThreadLocalTransaction sqlTrans, OrganizationDatabase dbOrg,
            SyncEmailSender email)
    {
        _sqlTrans = sqlTrans;
        _dbOrg = dbOrg;
        _email = email;
    }

    public List<String> listUserIDs(int offset, int maxResult)
            throws Exception
    {
        _sqlTrans.begin();
        List<UserID> userIDs = _dbOrg.listUsers(OrganizationID.PRIVATE_ORGANIZATION,
                offset, maxResult);
        _sqlTrans.commit();

        List<String> results = Lists.newArrayListWithCapacity(userIDs.size() + 1);
        results.add(OrganizationID.PRIVATE_ORGANIZATION.toTeamServerUserID().getString());

        for (UserID userID : userIDs) {
            results.add(userID.getString());
        }

        return results;
    }

    public void postToZenDesk(String dryadID, String customerID, String customerName,
            String email, String desc)
            throws Exception
    {
        String from = getStringProperty("base.www.support_email_address", "");
        String fromName = SPParam.EMAIL_FROM_NAME;
        // TODO (AT): replace with support@aerofs.com before release
//        String to = "support@aerofs.com";
        String to = "alex+dryad@aerofs.com";
        String replyTo = email;
        String subject = L.brand() + " Problem #" + dryadID;
        String textBody =
                "Customer ID: " + customerID + "\n" +
                "Customer Name: " + customerName + "\n" +
                "Contact Email: " + email + "\n\n" +
                desc;
        String htmlBody = null;
        EmailCategory category = EmailCategory.SUPPORT;

        _email.sendPublicEmail(from, fromName, to, replyTo, subject, textBody, htmlBody, category);
    }
}
