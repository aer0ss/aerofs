/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.dryad;

import com.aerofs.base.Loggers;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.SPServlet;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

import static com.aerofs.sp.server.lib.SPParam.SP_DATABASE_REFERENCE_PARAMETER;

public class DryadServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;

    private Logger l = Loggers.getLogger(SPServlet.class);

    // before we start, create the universe #sigh
    PooledSQLConnectionProvider     _sqlConnProvider = new PooledSQLConnectionProvider();
    SQLThreadLocalTransaction       _sqlTrans = new SQLThreadLocalTransaction(_sqlConnProvider);

    OrganizationDatabase            _odb = new OrganizationDatabase(_sqlTrans);
    OrganizationInvitationDatabase  _oidb = new OrganizationInvitationDatabase(_sqlTrans);
    UserDatabase                    _udb = new UserDatabase(_sqlTrans);
    SharedFolderDatabase            _sfdb = new SharedFolderDatabase(_sqlTrans);
    DeviceDatabase                  _ddb = new DeviceDatabase(_sqlTrans);
    CertificateDatabase             _certdb = new CertificateDatabase(_sqlTrans);

    License                         _license = new License();
    Organization.Factory            _factOrg = new Organization.Factory();
    OrganizationInvitation.Factory  _factOrgInvite = new OrganizationInvitation.Factory();
    User.Factory                    _factUser = new User.Factory();
    SharedFolder.Factory            _factSharedFolder = new SharedFolder.Factory();
    Device.Factory                  _factDevice = new Device.Factory();
    Certificate.Factory             _factCert = new Certificate.Factory(_certdb);

    CertificateGenerator            _certgen = new CertificateGenerator();

    Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private DryadService _service;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        String dbResourceName = getServletContext().getInitParameter(SP_DATABASE_REFERENCE_PARAMETER);
        _sqlConnProvider.init_(dbResourceName);

        _factOrg.inject(_odb, _oidb, _factUser, _factSharedFolder, _factOrgInvite);
        _factOrgInvite.inject(_oidb, _factUser, _factOrg);
        _factUser.inject(_udb, _oidb, _factDevice, _factOrg, _factOrgInvite, _factSharedFolder,
                _license);
        _factSharedFolder.inject(_sfdb, _factUser);
        _factDevice.inject(_ddb, _certdb, _certgen, _factUser, _factCert);

        _service = new DryadService(_sqlTrans, _factOrg);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    {
        resp.setStatus(200);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    {
        try {
            try {
                List<String> userIDs = _service.listUserIDs(0, 50);
                String content = _gson.toJson(userIDs);

                resp.setContentType("application/json");
                resp.setContentLength(content.length());
                resp.getOutputStream().print(content);
            } finally {
                _sqlTrans.cleanUp();
            }
        } catch (Exception e) {
            l.warn("Failed GET request", e);
        }

        resp.setStatus(200);
    }
}
