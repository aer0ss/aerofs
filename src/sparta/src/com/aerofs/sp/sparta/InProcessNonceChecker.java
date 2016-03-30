package com.aerofs.sp.sparta;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.bifrost.oaaas.auth.NonceChecker;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.IdentitySessionManager;
import com.aerofs.sp.server.lib.user.User;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InProcessNonceChecker implements NonceChecker {
    private final static Logger l = LoggerFactory.getLogger(InProcessNonceChecker.class);

    @Inject private AuditClient _auditClient;
    @Inject private SQLThreadLocalTransaction _sqlTrans;
    @Inject private IdentitySessionManager _sessionManager;
    @Inject private User.Factory _factUser;

    @Override
    public AuthorizedClient authorizeAPIClient(String nonce, String devName) throws Exception {
        User user = _factUser.create(_sessionManager.getAuthorizedDevice(nonce));

        // avoid craziness if the user existed when the nonce was generated, but since deleted
        _sqlTrans.begin();
        if (!user.exists())
        {
            // TODO: can't easily unit-test this case until we can delete users
            l.warn("Authorized device nonce {} has invalid user {}", nonce, user.id().getString());

            AuditClient.AuditableEvent auditEvent = _auditClient.event(AuditTopic.USER, "device.mobile.error")
                    .add("user", user.id());
            _sqlTrans.rollback();
            auditEvent.publish();
            throw new ExBadCredential("Authorized user does not exist.");
        }

        AuthorizedClient r = new AuthorizedClient(user.id(), user.getOrganization().id(), user.isAdmin());
        _sqlTrans.commit();

        l.info("SI: authorized device for {}", user.id().getString());

        _auditClient.event(AuditTopic.DEVICE, "device.certify")
                .add("user", user.id())
                .add("device_type", "API Client")
                .publish();
        return r;
    }
}
