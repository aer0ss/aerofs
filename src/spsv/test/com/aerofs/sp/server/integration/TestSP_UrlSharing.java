/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.proto.Sp.PBRestObjectUrl;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        setSessionUser(owner);
    }

    @Test
    public void createUrl_shouldThrowIfUserIsNotManager() throws Exception
    {
        setSessionUser(editor);
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

        setSessionUser(editor);
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

        setSessionUser(editor);
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
        setSessionUser(editor);
        try {
            service.removeUrl(key);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }
}
