/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sv;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.aerofs.lib.Util;
import com.aerofs.lib.spsv.sendgrid.Sendgrid.Category;
import com.aerofs.lib.spsv.sendgrid.Sendgrid.Events;
import com.aerofs.servletlib.db.PooledConnectionFactory;
import com.aerofs.servletlib.db.ThreadLocalTransaction;
import com.aerofs.servletlib.sv.SVDatabase;
import com.aerofs.sp.server.AeroServlet;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import static com.aerofs.servletlib.sv.SVParam.SV_DATABASE_REFERENCE_PARAMETER;

public class SendgridServlet extends AeroServlet {

    private static final Logger l = Util.l(SendgridServlet.class);
    private final PooledConnectionFactory _dbFactory = new PooledConnectionFactory();
    private final ThreadLocalTransaction _transaction = new ThreadLocalTransaction(_dbFactory);
    private final SVDatabase _db = new SVDatabase(_transaction);
    private static final long serialVersionUID = 1L;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        try {
            initdb_();
        } catch (Exception e) {
            l.error("init: ", e);
            throw new ServletException(e);
        }
    }

    private void initdb_()
            throws SQLException, ClassNotFoundException
    {
        String context = getServletContext().getInitParameter(SV_DATABASE_REFERENCE_PARAMETER);

        _dbFactory.init_(context);
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
        JSONParser parser = new JSONParser();
        try {
            while ((line = br.readLine()) != null) {
                JSONObject obj = (JSONObject)parser.parse(line);

                handleEvent(obj);
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

    private void handleEvent(JSONObject obj)
            throws SQLException
    {
        _transaction.begin();

        assert obj.containsKey("category");
        String category = (String)obj.get("category");
        String email = (String)obj.get("email");
        String event = (String)obj.get("event");
        Long timestamp = (Long)obj.get("timestamp");

        l.info("EVENT(" + event + ")" +
                "FROM EMAIL(" + email + ") " +
                (category == null ? "" : (" Category(" + category + ")")));

        String reason = null;
        switch (Events.valueOf(event.toUpperCase())) {
        case DROPPED:
            dropDesc(obj);
            break;
        case DEFERRED:
            deferredDesc(obj);
            break;
        case DELIVERED:
            deliveredDesc(obj);
            break;
        case BOUNCE:
            bounceDesc(obj);
            break;
        case CLICK:
            clickDesc(obj);
            break;
        case UNSUBSCRIBE:
            unsubscribe(email, Category.valueOf(category));
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

        _db.addEmailEvent(email, event, reason, category, timestamp);

        _transaction.commit();
    }

    private String deferredDesc(JSONObject obj)
            throws SQLException
    {
        StringBuilder desc = new StringBuilder();
        if (obj.containsKey("attempt")) desc.append("attempt =").append((String)obj.get("attempt"));
        if (obj.containsKey("response")) desc.append(" response = ").append((String)obj.get("response"));
        return desc.toString();
    }

    private String deliveredDesc(JSONObject obj)
            throws SQLException
    {
        return (String)obj.get("response");
    }

    private String clickDesc(JSONObject obj)
    {
        return (String)obj.get("url");
    }

    private String bounceDesc(JSONObject obj)
            throws SQLException
    {
        StringBuilder desc = new StringBuilder();
        if (obj.containsKey("status")) desc.append("status =").append((String)obj.get("status"));
        if (obj.containsKey("reason")) desc.append(" reason =").append((String)obj.get("reason"));
        if (obj.containsKey("type")) desc.append(" type =").append((String)obj.get("type"));

        return desc.toString();
    }

    private String dropDesc(JSONObject obj)
    {
        return (String) obj.get("reason");
    }

    private void unsubscribe(String email, Category category)
            throws SQLException
    {
        _db.modifyEmailSubscription(email, category, false);
    }
}
