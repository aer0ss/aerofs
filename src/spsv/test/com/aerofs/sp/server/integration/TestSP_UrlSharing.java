/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.proto.Sp.ListUrlsForStoreReply;
import com.aerofs.proto.Sp.PBRestObjectUrl;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestSP_UrlSharing extends AbstractSPFolderTest
{
    private User owner;
    private User editor;
    private SID sid;

    @Before
    public void setUp() throws Exception
    {
        sqlTrans.begin();
        owner = saveUser();
        editor = saveUser();
        sqlTrans.commit();

        sid = SID.generate();
        shareAndJoinFolder(owner, sid, editor, Permissions.allOf(Permission.WRITE));
        setSession(owner);
    }

    @Test
    public void createUrl_shouldThrowIfUserIsNotManager() throws Exception
    {
        setSession(editor);
        String token = UniqueID.generate().toStringFormal();

        try {
            service.createUrl(new RestObject(sid).toStringFormal(), token);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void createUrl_shouldRespondWithObjectUrl() throws Exception
    {
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl objectUrl = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();

        assertNotNull(objectUrl.getKey());
        assertEquals(object.toStringFormal(), objectUrl.getSoid());
        assertEquals(token, objectUrl.getToken());
        assertEquals(owner.id().getString(), objectUrl.getCreatedBy());
        assertFalse(objectUrl.hasExpires());
    }

    @Test
    public void createUrl_shouldThrowExBadArgsIfSoidIsInvalid() throws Exception
    {
        try {
            service.createUrl("abc123", UniqueID.generate().toStringFormal());
            fail();
        } catch (ExBadArgs ignored) {
            // success
        }
    }

    @Test
    public void createUrl_shouldThrowExBadArgsIfSoidIsAlias() throws Exception
    {
        try {
            service.createUrl("root", UniqueID.generate().toStringFormal());
            fail();
        } catch (ExBadArgs ignored) {
            // success
        }

        try {
            service.createUrl("appdata", UniqueID.generate().toStringFormal());
            fail();
        } catch (ExBadArgs ignored) {
            // success
        }
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfo() throws Exception
    {
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(key, getReply.getKey());
        assertEquals(token, getReply.getToken());
        assertEquals(object.toStringFormal(), getReply.getSoid());
        assertEquals(owner.id().getString(), getReply.getCreatedBy());
        assertFalse(getReply.hasExpires());
    }

    @Test
    public void getUrlInfo_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        try {
            service.getUrlInfo(key).get().getUrlInfo();
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void getUrlInfo_shouldThrowIfNonManagerProvidesNoPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // check that GetUrlInfo fails for an editor
        setSession(editor);
        try {
            service.getUrlInfo(key).get();
            fail();
        } catch (ExBadCredential ignored) {
            // success
        }
    }

    @Test
    public void getUrlInfo_shouldThrowIfNonManagerProvidesInvalidPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // check that GetUrlInfo fails for an editor
        setSession(editor);
        try {
            service.getUrlInfo(key, ByteString.copyFromUtf8("*******")).get();
            fail();
        } catch (ExBadCredential ignored) {
            // success
        }
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfoIfNonManagerProvidesCorrectPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // check that GetUrlInfo succeeds for editor with password
        setSession(editor);
        service.getUrlInfo(key, ByteString.copyFromUtf8("hunter2")).get();
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfoIfAnonProvidesCorrectPassword() throws Exception
    {

        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // check that GetUrlInfo succeeds with password and no session user
        service.signOut();
        service.getUrlInfo(key, ByteString.copyFromUtf8("hunter2")).get();
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfoIfManagerProvidesNoPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // check that GetUrlInfo succeeds for an owner
        service.getUrlInfo(key).get().getUrlInfo();
    }

    @Test
    public void setUrlExpires_shouldSetUrlExpires() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the expiry
        long expires = 1234567L;
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlExpires(key, expires, newToken);

        // check that the expiry was saved
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(key, getReply.getKey());
        assertEquals(newToken, getReply.getToken());
        assertEquals(object.toStringFormal(), getReply.getSoid());
        assertEquals(expires, getReply.getExpires());
    }

    @Test
    public void setUrlExpires_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        long expires = 42;
        String token = UniqueID.generate().toStringFormal();
        try {
            service.setUrlExpires(key, expires, token).get();
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void setUrlExpires_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        setSession(editor);
        try {
            service.setUrlExpires(key, 0L, token);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void removeUrlExpires_shouldRemoveUrlExpires() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the expiry
        long expires = 1234567L;
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlExpires(key, expires, newToken);

        // check that the expiry was saved
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(expires, getReply.getExpires());

        // remove the expiry
        String newerToken = UniqueID.generate().toStringFormal();
        service.removeUrlExpires(key, newerToken);

        // check that the expiry was removed
        getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertFalse(getReply.hasExpires());
    }

    @Test
    public void removeUrlExpires_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        String token = UniqueID.generate().toStringFormal();
        try {
            service.removeUrlExpires(key, token).get();
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void removeUrlExpires_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        setSession(editor);
        try {
            service.removeUrlExpires(key, token);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void removeUrl_shouldRemoveUrl() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // remove the link
        service.removeUrl(key);

        // try to get the link
        try {
            service.getUrlInfo(key);
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void removeUrl_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        try {
            service.removeUrl(UniqueID.generate().toStringFormal());
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void removeUrl_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // try to delete the link
        setSession(editor);
        try {
            service.removeUrl(key);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void setUrlPassword_shouldSetUrlPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // check that the password was set
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertTrue(getReply.getHasPassword());
        assertEquals(newToken, getReply.getToken());
    }

    @Test
    public void setUrlPassword_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        ByteString password = ByteString.copyFromUtf8("hunter2");
        String token = UniqueID.generate().toStringFormal();
        try {
            service.setUrlPassword(key, password, token);
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void setUrlPassword_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // try to set the password
        setSession(editor);
        ByteString password = ByteString.copyFromUtf8("hunter2");
        String newToken = UniqueID.generate().toStringFormal();
        try {
            service.setUrlPassword(key, password, newToken);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void validateUrlPassword_shouldValidateUrlPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // validate the password
        service.validateUrlPassword(key, ByteString.copyFromUtf8("hunter2"));
    }

    @Test
    public void validateUrlPassword_shouldValidateUrlPasswordForEditor() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // validate the password
        setSession(editor);
        service.validateUrlPassword(key, ByteString.copyFromUtf8("hunter2"));
    }

    @Test
    public void validateUrlPassword_shouldFailToValidateIncorrectPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // validate the password
        try {
            service.validateUrlPassword(key, ByteString.copyFromUtf8("all I see is *******"));
            fail();
        } catch (ExBadCredential ignored) {
            // success
        }
    }

    @Test
    public void validateUrlPassword_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        ByteString password = ByteString.copyFromUtf8("hunter2");
        try {
            service.validateUrlPassword(key, password);
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void validateUrlPassword_shouldThrowIfNoPasswordSet() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // validate the password
        try {
            service.validateUrlPassword(key, ByteString.copyFromUtf8("hunter2"));
            fail();
        } catch (ExBadCredential ignored) {
            // success
        }
    }

    @Test
    public void removeUrlPassword_shouldRemoveUrlPassword() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"), newToken);

        // remove the password
        service.removeUrlPassword(key);

        // check that the password was removed
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertFalse(getReply.hasHasPassword() && getReply.getHasPassword());
    }

    @Test
    public void removeUrlPassword_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        try {
            service.removeUrlPassword(key);
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void removeUrlPassword_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        String token = UniqueID.generate().toStringFormal();
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal(), token)
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        ByteString password = ByteString.copyFromUtf8("hunter2");
        String newToken = UniqueID.generate().toStringFormal();
        service.setUrlPassword(key, password, newToken);

        // try to remove the password
        setSession(editor);
        try {
            service.removeUrlPassword(key);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void listUrlsForStore_shouldThrowIfUserIsNotManager() throws Exception
    {
        setSession(editor);
        try {
            service.listUrlsForStore(sid);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void listUrlsForStore_shouldThrowIfStoreDoesNotExist() throws Exception
    {
        try {
            service.listUrlsForStore(SID.generate());
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void listUrlsForStore_shouldListUrlsForStore() throws Exception
    {
        // create three links in the store
        RestObject ro1 = new RestObject(sid, OID.generate());
        RestObject ro2 = new RestObject(sid, OID.generate());
        String token1 = UniqueID.generate().toStringFormal();
        String token2 = UniqueID.generate().toStringFormal();
        String token3 = UniqueID.generate().toStringFormal();
        service.createUrl(ro1.toStringFormal(), token1);
        service.createUrl(ro2.toStringFormal(), token2);
        service.createUrl(ro2.toStringFormal(), token3);

        // create one link in a different store
        SID otherSid = SID.generate();
        shareAndJoinFolder(owner, otherSid, editor, Permissions.allOf(Permission.WRITE));
        RestObject ro3 = new RestObject(otherSid);
        String token4 = UniqueID.generate().toStringFormal();
        service.createUrl(ro3.toStringFormal(), token4);

        // list Urls
        ListUrlsForStoreReply reply = service.listUrlsForStore(sid.toPB()).get();
        assertEquals(3, reply.getUrlCount());
        for (PBRestObjectUrl url : reply.getUrlList()) {
            if (token1.equals(url.getToken())) {
                assertEquals(ro1.toStringFormal(), url.getSoid());
            } else if (token2.equals(url.getToken())) {
                assertEquals(ro2.toStringFormal(), url.getSoid());
            } else if (token3.equals(url.getToken())) {
                assertEquals(ro2.toStringFormal(), url.getSoid());
            }
            if (token4.equals(url.getToken()) || ro3.toStringFormal().equals(url.getSoid())) {
                fail();
            }
        }
    }

    @Test
    public void listUrlsForStore_shouldListUrlsForRoot() throws Exception
    {
        SID root = SID.rootSID(owner.id());
        OID oid = OID.generate();
        String soid = root.toStringFormal() + oid.toStringFormal();
        String token = UniqueID.generate().toStringFormal();
        service.createUrl(soid, token);

        ListUrlsForStoreReply reply = service.listUrlsForStore(ByteString.copyFromUtf8("root"))
                .get();

        assertEquals(1, reply.getUrlCount());
        assertEquals(soid, reply.getUrl(0).getSoid());
    }
}
