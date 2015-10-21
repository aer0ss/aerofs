/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.*;
import com.aerofs.ids.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.proto.Sp.ListUrlsForStoreReply;
import com.aerofs.proto.Sp.PBRestObjectUrl;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class TestSP_UrlSharing extends AbstractSPFolderTest
{
    private User owner;
    private User editor;
    private SID sid;

    // Storing mock token so that it can be used by the tests for comparing tokens.
    protected String mockToken;

    private void mockBifrostTokenRequester()
            throws InterruptedException, ExecutionException, IOException
    {
        // Using thenAnswer as the value of mockToken changes and we want latest value of mockToken
        // to be returned in the tests.
        when(bifrostClient.getBifrostToken(anyString(),
                any(String.class), any(Long.class)))
                .thenAnswer(invocation -> mockToken);

        doNothing().when(bifrostClient).deleteToken(any(String.class));
    }

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

        mockToken = UniqueID.generate().toStringFormal();
        mockBifrostTokenRequester();
    }

    @Test
    public void createUrl_shouldThrowIfUserIsNotManager() throws Exception
    {
        setSession(editor);
        try {
            service.createUrl(new RestObject(sid).toStringFormal());
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void createUrl_shouldRespondWithObjectUrl() throws Exception
    {
        RestObject object = new RestObject(sid);
        PBRestObjectUrl objectUrl = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();

        assertNotNull(objectUrl.getKey());
        assertEquals(object.toStringFormal(), objectUrl.getSoid());
        assertEquals(mockToken, objectUrl.getToken());
        assertEquals(owner.id().getString(), objectUrl.getCreatedBy());
        assertFalse(objectUrl.hasExpires());
        assertTrue(objectUrl.hasRequireLogin());
        assertFalse(objectUrl.getRequireLogin());
    }

    @Test
    public void createUrl_shouldThrowExBadArgsIfSoidIsInvalid() throws Exception
    {
        try {
            service.createUrl("abc123");
            fail();
        } catch (ExBadArgs ignored) {
            // success
        }
    }

    @Test
    public void createUrl_shouldThrowExBadArgsIfSoidIsAlias() throws Exception
    {
        try {
            service.createUrl("root");
            fail();
        } catch (ExBadArgs ignored) {
            // success
        }

        try {
            service.createUrl("appdata");
            fail();
        } catch (ExBadArgs ignored) {
            // success
        }
    }

    @Test
    public void createUrl_shouldThrowWithNoPermToAnchor() throws Exception
    {
        try {
            setSession(editor);
            SID rootSid = SID.rootSID(editor.id());
            OID anchorOid = SID.storeSID2anchorOID(sid);
            RestObject restObject = new RestObject(rootSid, anchorOid);
            service.createUrl(restObject.toStringFormal());
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void createUrl_shouldSucceedWithPermToAnchor() throws Exception
    {
        SID rootSid = SID.rootSID(owner.id());
        OID anchorOid = SID.storeSID2anchorOID(sid);
        RestObject restObject = new RestObject(rootSid, anchorOid);
        service.createUrl(restObject.toStringFormal());
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfo() throws Exception
    {

        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(key, getReply.getKey());
        assertEquals(mockToken, getReply.getToken());
        assertEquals(object.toStringFormal(), getReply.getSoid());
        assertEquals(owner.id().getString(), getReply.getCreatedBy());
        assertFalse(getReply.hasExpires());
        assertTrue(getReply.hasRequireLogin());
        assertFalse(getReply.getRequireLogin());
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
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

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
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

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

        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

        // check that GetUrlInfo succeeds for editor with password
        setSession(editor);
        service.getUrlInfo(key, ByteString.copyFromUtf8("hunter2")).get();
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfoIfAnonProvidesCorrectPassword() throws Exception
    {

        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

        // check that GetUrlInfo succeeds with password and no session user
        service.signOut();
        service.getUrlInfo(key, ByteString.copyFromUtf8("hunter2")).get();
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfoIfManagerProvidesNoPassword() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

        // check that GetUrlInfo succeeds for an owner
        service.getUrlInfo(key).get().getUrlInfo();
    }

    @Test
    public void getUrlInfo_shouldThrowForAnonIfRequireLogin() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the require login parameter
        service.setUrlRequireLogin(key, true);

        // no session user
        session.deauthorize();

        // check that GetUrlInfo fails
        try {
            service.getUrlInfo(key).get();
            fail();
        } catch (ExNotAuthenticated ignored) {
            // success
        }
    }

    @Test
    public void getUrlInfo_shouldGetUrlInfoForOrgMemberIfRequireLogin() throws Exception
    {
        // create the link

        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the require login parameter
        service.setUrlRequireLogin(key, true);

        // check that GetUrlInfo succeeds for owner
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(key, getReply.getKey());

        // and for editor
        setSession(editor);
        PBRestObjectUrl getReplyAgain = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(key, getReplyAgain.getKey());
    }

    @Test
    public void setUrlRequireLogin_shouldSetRequireLoginToTrue() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the require login parameter
        service.setUrlRequireLogin(key, true);

        // check that GetUrlInfo succeeds and that the new require login value is true
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(key, getReply.getKey());
        assertEquals(mockToken, getReply.getToken());
        assertEquals(object.toStringFormal(), getReply.getSoid());
        assertEquals(owner.id().getString(), getReply.getCreatedBy());
        assertFalse(getReply.hasExpires());
        assertTrue(getReply.getRequireLogin());
    }

    @Test
    public void setUrlRequireLogin_shouldSetRequireLoginBackToFalse() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the require login parameter
        service.setUrlRequireLogin(key, true);

        // check that GetUrlInfo succeeds and that the new require login value is true
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertTrue(getReply.getRequireLogin());

        // set the require login parameter
        service.setUrlRequireLogin(key, false);
        getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertFalse(getReply.getRequireLogin());
    }

    @Test
    public void setUrlRequireLogin_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();

        try {
            service.setUrlRequireLogin(key, true);
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void setUrlRequireLogin_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        setSession(editor);
        try {
            service.setUrlRequireLogin(key, true);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void setUrlExpires_shouldSetUrlExpires() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the expiry
        long expires = 1234567L;
        service.setUrlExpires(key, expires);

        // check that the expiry was saved
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(key, getReply.getKey());
        assertEquals(mockToken, getReply.getToken());
        assertEquals(object.toStringFormal(), getReply.getSoid());
        assertEquals(expires, getReply.getExpires());
        assertFalse(getReply.getRequireLogin());
    }

    @Test
    public void setUrlExpires_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        long expires = 42;
        try {
            service.setUrlExpires(key, expires).get();
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void setUrlExpires_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        setSession(editor);
        try {
            service.setUrlExpires(key, 0L);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void removeUrlExpires_shouldRemoveUrlExpires() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the expiry
        long expires = 1234567L;
        service.setUrlExpires(key, expires);

        // check that the expiry was saved
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertEquals(expires, getReply.getExpires());

        // remove the expiry
        service.removeUrlExpires(key);

        // check that the expiry was removed
        getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertFalse(getReply.hasExpires());
    }

    @Test
    public void removeUrlExpires_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        try {
            service.removeUrlExpires(key).get();
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void removeUrlExpires_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        setSession(editor);
        try {
            service.removeUrlExpires(key);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void removeUrl_shouldRemoveUrl() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
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
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
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
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

        // check that the password was set
        PBRestObjectUrl getReply = service.getUrlInfo(key).get().getUrlInfo();
        assertTrue(getReply.getHasPassword());
        assertEquals(mockToken, getReply.getToken());
    }

    @Test
    public void setUrlPassword_shouldThrowIfKeyDoesNotExist() throws Exception
    {
        String key = UniqueID.generate().toStringFormal();
        ByteString password = ByteString.copyFromUtf8("hunter2");
        try {
            service.setUrlPassword(key, password);
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void setUrlPassword_shouldThrowIfUserIsNotManager() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // try to set the password
        setSession(editor);
        ByteString password = ByteString.copyFromUtf8("hunter2");
        try {
            service.setUrlPassword(key, password);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void validateUrlPassword_shouldValidateUrlPassword() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

        // validate the password
        service.validateUrlPassword(key, ByteString.copyFromUtf8("hunter2"));
    }

    @Test
    public void validateUrlPassword_shouldValidateUrlPasswordForEditor() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

        // validate the password
        setSession(editor);
        service.validateUrlPassword(key, ByteString.copyFromUtf8("hunter2"));
    }

    @Test
    public void validateUrlPassword_shouldFailToValidateIncorrectPassword() throws Exception
    {
        // create the link
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

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
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
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
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

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
        RestObject object = new RestObject(sid);
        PBRestObjectUrl createReply = service.createUrl(object.toStringFormal())
                .get()
                .getUrlInfo();
        String key = createReply.getKey();

        // set the password
        service.setUrlPassword(key, ByteString.copyFromUtf8("hunter2"));

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
            service.listUrlsForStore(BaseUtil.toPB(sid));
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void listUrlsForStore_shouldThrowIfStoreDoesNotExist() throws Exception
    {
        try {
            service.listUrlsForStore(BaseUtil.toPB(SID.generate()));
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

        String token1 = mockToken;
        service.createUrl(ro1.toStringFormal());

        // We need to generate a new token and assign it to mockToken and also locally store the
        // value of the new token. This is because:
        // 1. We want to generate a different token for everytime we create a url in this test.
        // 2. For the purpose of these tests, mockToken is the token used to create the url by the
        // bifrostTokenRequestor. Hence, mockToken needs to be updated everytime we create a link.
        // 3. We want to make sure the urls were created with the right token value. Hence, we
        // need to store them locally for comparison.
        mockToken = UniqueID.generate().toStringFormal();
        String token2 = mockToken;
        service.createUrl(ro2.toStringFormal());

        mockToken = UniqueID.generate().toStringFormal();
        String token3 = mockToken;
        service.createUrl(ro2.toStringFormal());

        // create one link in a different store
        SID otherSid = SID.generate();
        shareAndJoinFolder(owner, otherSid, editor, Permissions.allOf(Permission.WRITE));
        RestObject ro3 = new RestObject(otherSid);

        mockToken = UniqueID.generate().toStringFormal();
        String token4 = mockToken;
        service.createUrl(ro3.toStringFormal());

        // list Urls
        ListUrlsForStoreReply reply = service.listUrlsForStore(BaseUtil.toPB(sid)).get();
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
        service.createUrl(soid);

        ListUrlsForStoreReply reply = service.listUrlsForStore(ByteString.copyFromUtf8("root"))
                .get();

        assertEquals(1, reply.getUrlCount());
        assertEquals(soid, reply.getUrl(0).getSoid());
    }
}
