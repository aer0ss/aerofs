/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationDatabase;
import com.aerofs.sp.server.lib.user.UserDatabase;
import com.aerofs.verkehr.api.rest.Updates;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.collect.ImmutableList;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.sp.server.lib.SPParam.SP_DATABASE_REFERENCE_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CLIENT_ATTRIBUTE;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class is used by the sanity checker to probe for the state of Team Server installations.
 */
public class TeamServersProbeServlet extends HttpServlet
{
    private static final Logger l = Loggers.getLogger(TeamServersProbeServlet.class);
    private static final long serialVersionUID = 1904508893297745572L;

    // SQL Transaction.
    private final PooledSQLConnectionProvider _sqlConProvider = new PooledSQLConnectionProvider();
    private final SQLThreadLocalTransaction _sqlTrans = new SQLThreadLocalTransaction(_sqlConProvider);

    // Databases.
    private final OrganizationDatabase _odb = new OrganizationDatabase(_sqlTrans);
    private final UserDatabase _udb = new UserDatabase(_sqlTrans);
    private final DeviceDatabase _ddb = new DeviceDatabase(_sqlTrans);

    // Date formatting.
    private final SimpleDateFormat _dateformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // Configuration.
    public static final Integer ALLOWED_OFFLINE_SECONDS =
            getIntegerProperty("sp.probe.ts_allowed_offline_seconds", 300);

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        // SP Database.
        String dbResourceName = getServletContext().getInitParameter(
                SP_DATABASE_REFERENCE_PARAMETER);
        _sqlConProvider.init_(dbResourceName);

        // Vekrehr.
        VerkehrClient verkehrClient = (VerkehrClient) getServletContext().getAttribute(VERKEHR_CLIENT_ATTRIBUTE);
        checkNotNull(verkehrClient);

        // Date formatting.
        _dateformat.setTimeZone(TimeZone.getTimeZone("UTC"));

        l.info("TeamServersProbeServlet initialized.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        try {
            // Only perform the check if this is PC.
            if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) {
                teamServersSanityCheck(resp);
            }
        } catch (Exception e) {
            l.error("TS sanity check exception: ", e);
            resp.setStatus(500);
        }
    }

    private VerkehrClient getVerkehrClient()
    {
        return (VerkehrClient) getServletContext().getAttribute(VERKEHR_CLIENT_ATTRIBUTE);
    }

    private long getDiffSecondsWithCurrentDate(Date d)
    {
        return (System.currentTimeMillis() - d.getTime()) / 1000;
    }

    /**
     * Sanity check linked Team Servers.
     *
     * In PC, if any linked Team Server is reported as offline by charlie, return a 503.
     *
     * HC behavior is undefined.
     */
    private void teamServersSanityCheck(HttpServletResponse resp) throws Exception
    {
        _sqlTrans.begin();

        ImmutableList<OrganizationID> organizationIDs = _odb.getOrganizationIDs();
        UserID tsUserID = organizationIDs.get(0).toTeamServerUserID();
        List<DID> deviceIDs = _udb.getDevices(tsUserID);

        _sqlTrans.commit();

        if (deviceIDs.size() == 0) {
            l.debug("No linked Team Servers. Sanity check passed.");
            return;
        }

        for (DID did : deviceIDs) {
            l.debug("DID {}", did);

            String topic = "online%2F" + did.toStringFormal();
            Updates updates = getVerkehrClient().getUpdates(topic, -1).get();

            if (updates == null || updates.getUpdates() == null || updates.getUpdateCount() != 1) {
                _sqlTrans.begin();
                Date install = new Date(_ddb.getInstallDate(did));
                _sqlTrans.commit();

                long installDiffSeconds = getDiffSecondsWithCurrentDate(install);

                l.warn("No last seen time for {} installed {}", did, install);

                if (installDiffSeconds > ALLOWED_OFFLINE_SECONDS) {
                    l.debug("Device recently installed; ignore.");
                } else {
                    l.debug("Device never checked-in; fire probe.");
                    resp.setStatus(503);
                }

                continue;
            }

            String payload = new String(updates.getUpdates().get(0).getPayload());
            JSONObject json = (JSONObject) JSONValue.parse(payload);
            Date date = _dateformat.parse((String)json.get("time"));

            long checkinDiffSeconds = (System.currentTimeMillis() - date.getTime()) / 1000;
            l.debug("Last check-in: {} seconds", checkinDiffSeconds);

            // If the check-in was within the few minutes, we consider this TS to be online. This
            // value must be larger than the charlie check-in interval.
            if (checkinDiffSeconds > ALLOWED_OFFLINE_SECONDS) {
                l.error("TS sanity check failed for {}", did);
                resp.setStatus(503);
            }
        }
    }
}
