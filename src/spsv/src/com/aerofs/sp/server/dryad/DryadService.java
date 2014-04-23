/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.dryad;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.aerofs.labeling.L;
import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.CommandDispatcher;
import com.aerofs.sp.server.CommandUtil;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sv.common.EmailCategory;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.Callable;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

public class DryadService
{
    private static final Logger l = Loggers.getLogger(DryadService.class);

    private final SQLThreadLocalTransaction     _sqlTrans;
    private final OrganizationDatabase          _odb;
    private final UserDatabase                  _udb;
    private final AbstractEmailSender           _email;
    private final CommandDispatcher             _cmd;

    public DryadService(SQLThreadLocalTransaction sqlTrans, OrganizationDatabase odb,
            UserDatabase udb, AbstractEmailSender email, CommandDispatcher cmd)
    {
        _sqlTrans = sqlTrans;
        _odb = odb;
        _udb = udb;
        _email = email;
        _cmd = cmd;
    }

    public List<String> listUserIDs(final int offset, final int maxResult)
            throws Exception
    {
        List<UserID> userIDs = callWithSqlTrans(new Callable<List<UserID>>()
        {
            @Override
            public List<UserID> call()
                    throws Exception
            {
                return _odb.listUsers(OrganizationID.PRIVATE_ORGANIZATION, offset, maxResult);
            }
        });

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
        // String to = "support@aerofs.com";
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

    public void enqueueCommandsForUsers(String dryadID, String customerID, String[] users)
    {
        String commandMessage = CommandUtil.createUploadLogsCommandMessage(dryadID, customerID);

        for (String user : users) {
            try {
                enqueueCommandForUser(commandMessage, user);
            } catch (Exception e) {
                l.error("Unable to enqueue commands for user: {}", user, e);
                // exception ignored, moving onto the next user
            }
        }
    }

    private void enqueueCommandForUser(String commandMessage, String user)
            throws Exception
    {
        final UserID userID = UserID.fromExternal(user);

        List<DID> dids = callWithSqlTrans(new Callable<List<DID>>()
        {
            @Override
            public List<DID> call()
                    throws Exception
            {
                return _udb.getDevices(userID);
            }
        });

        for (DID did: dids) {
            try {
                _cmd.enqueueCommand(did, commandMessage);
            } catch (Exception e) {
                l.error("Unable to enqueue commands for device: {}", did.toStringFormal(), e);
                // exception ignored, moving onto the next device
            }
        }
    }

    // I'm not a fan of functors in Java, but I like duplicating try-catch-finally blocks even less.
    private<TResult> TResult callWithSqlTrans(Callable<TResult> callable)
            throws Exception
    {
        _sqlTrans.begin();

        try {
            TResult result = callable.call();
            _sqlTrans.commit();
            return result;
        } catch (Exception e) {
            if (_sqlTrans.isInTransaction()) {
                _sqlTrans.rollback();
            }
            throw e;
        } finally {
            _sqlTrans.cleanUp();
        }
    }
}
