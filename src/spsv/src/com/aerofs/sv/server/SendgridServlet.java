/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.server;

import com.aerofs.lib.Util;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sv.common.Event;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.log4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;

public class SendgridServlet extends AeroServlet {

    private static final Logger l = Util.l(SendgridServlet.class);
    private final PooledSQLConnectionProvider _conProvider = new PooledSQLConnectionProvider();
    private final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_conProvider);
    private final SVDatabase _db = new SVDatabase(_transaction);
    private static final long serialVersionUID = 1L;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        try {
            initDB_();
        } catch (Exception e) {
            l.error("init: ", e);
            throw new ServletException(e);
        }
    }

    private void initDB_()
            throws SQLException, ClassNotFoundException
    {
        String context = getServletContext().getInitParameter(
                SVParam.SV_DATABASE_REFERENCE_PARAMETER);

        _conProvider.init_(context);
    }

    /**
     * SendGrid will do a Post request to our servlet with a batch of events.
     * Although the mime type will be set to application/json, the body of the document is
     * _not_ a valid json body. Instead, each individual line is a valid json body.
     *
     * For example, one possible body is:
     *
     *  {"email":"foo@bar.com","timestamp":1322000095,"unique_arg":"my unique arg","event":"delivered"}
     *  {"email":"foo@bar.com","timestamp":1322000096,"unique_arg":"my unique arg","event":"open"}
     *
     * If a non-200 response is received, SendGrid will attempt to retry POSTing until a 200
     * response is received, or a maximum time has passed.
     *
     * @param req The HttpServletRequest
     * @param resp 200 if everything is ok, any other error code otherwise
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        BufferedReader br = req.getReader();
        String line;
        JsonParser parser = new JsonParser();
        try {
            while ((line = br.readLine()) != null) {
                JsonObject obj = (JsonObject)parser.parse(line);

                handleEvent(obj);

                _transaction.cleanUp();
            }

            resp.setStatus(200);
        } catch (Exception e) {
            _transaction.handleException();
            l.error("doPost: ", e);
            throw new IOException(e);
        } finally {
            br.close();
        }
    }

    private void handleEvent(JsonObject obj)
            throws SQLException
    {
        _transaction.begin();

        assert obj.has("category");
        String category = obj.get("category").getAsString();
        String email = obj.get("email").getAsString();
        String eventStr = obj.get("event").getAsString();
        Long timestamp = obj.get("timestamp").getAsLong();

        l.info("EVENT(" + eventStr + ")" +
                "FROM EMAIL(" + email + ") " +
                (category == null ? "" : (" Category(" + category + ")")));

        String reason = null;
        Event event = Event.valueOf(eventStr.toUpperCase());

        switch (event) {
        case DROPPED:
            reason = dropDesc(obj);
            break;
        case DEFERRED:
            reason = deferredDesc(obj);
            break;
        case DELIVERED:
            reason = deliveredDesc(obj);
            break;
        case BOUNCE:
            reason = bounceDesc(obj);
            break;
        case CLICK:
            reason = clickDesc(obj);
            break;
        case UNSUBSCRIBE:
            break;
        case PROCESSED:
            break;
        case OPEN:
            break;
        case SPAMREPORT:
            break;
        default:
            l.warn("undefined event: " + event);
            break;

        }

        _db.insertEmailEvent(new EmailEvent(email, event, reason, category, timestamp));

        _transaction.commit();
    }

    private String deferredDesc(JsonObject obj)
            throws SQLException
    {
        StringBuilder desc = new StringBuilder();
        if (obj.has("attempt")) desc.append("attempt =").append(obj.get("attempt").getAsString());
        if (obj.has("response")) desc.append(" response = ").append(obj.get("response").getAsString());
        return desc.toString();
    }

    private String deliveredDesc(JsonObject obj)
            throws SQLException
    {
        return obj.get("response").getAsString();
    }

    private String clickDesc(JsonObject obj)
    {
        return obj.get("url").getAsString();
    }

    private String bounceDesc(JsonObject obj)
            throws SQLException
    {
        StringBuilder desc = new StringBuilder();
        if (obj.has("status")) desc.append("status =").append(obj.get("status").getAsString());
        if (obj.has("reason")) desc.append(" reason =").append(obj.get("reason").getAsString());
        if (obj.has("type")) desc.append(" type =").append(obj.get("type").getAsString());

        return desc.toString();
    }

    private String dropDesc(JsonObject obj)
    {
        return obj.get("reason").getAsString();
    }

}
