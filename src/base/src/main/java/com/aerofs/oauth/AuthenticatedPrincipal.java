/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.aerofs.oauth;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.UserID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;

/**
 * {@link Principal} that can contain roles and additional attributes.
 */
public class AuthenticatedPrincipal implements Serializable, Principal
{
    public static final String USERID_ATTRIB = "userid";
    public static final String ORGID_ATTRIB = "orgid";
    private static final long serialVersionUID = -667145001740406817L;
    private String name;
    // FIXME: can these be safely removed without breaking serialized data?
    // FIXME 2: update the schema and get rid of all this serialized nonsense
    private Collection<String> roles;
    private Collection<String> groups;
    /*
     * Extra attributes, depending on the authentication implementation. Note that we only support
     * String - String attributes as we need to be able to persist the Principal generically
     */
    private Map<String, String> attributes;

    public AuthenticatedPrincipal(String username, UserID userID, OrganizationID orgID) {
        this(username);
        setOrganizationID(orgID);
        setEffectiveUserID(userID);
    }

    // FIXME: make this go away
    public AuthenticatedPrincipal(String username) {
        this.name = username;
        this.attributes = Maps.newHashMap();
        // useless junk:
        this.roles = Lists.newArrayList();
        this.groups = Lists.newArrayList();
    }

    /**
     * Get the given attribute.
     * @param key the attribute key to get.
     * @return String value if attribute found. Null if attribute not found or no attributes at all.
     */
    public String getAttribute(String key) {
        if (attributes == null) {
            return null;
        }
        return attributes.get(key);
    }

    public void addAttribute(String key, String value) {
        if (attributes == null) attributes = Maps.newHashMap();
        attributes.put(key, value);
    }

    /**
     * This is the _issuer_, i.e. the userID who performed the authentication work.
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getClass().getName() + " [name=" + name + ", roles=" + roles + ", groups=" + groups + ",attributes=" + attributes + "]";
    }

    /**
     * The UserID that authenticated - this cannot be a team server ID.
     */
    public UserID getIssuingUserID()
    {
        return UserID.fromInternal(name);
    }

    /**
     * Get the effective UserID.
     *
     * The effective userID is the userID whose authority is vested in a given access token.
     *
     *  - A regular user can issue an access token for him or herself; in that case the effective
     * and owner are the same.
     *
     *  - An admin user can issue an administrative access token, which uses the Team Server ID. In
     * that case the owner is foo@example.com, and the effective user is :2 (for instance).
     */
    public UserID getEffectiveUserID()
    {
        return UserID.fromInternal(getAttribute(USERID_ATTRIB));
    }

    /**
     * Set the effective user ID.
     * @see com.aerofs.oauth.AuthenticatedPrincipal#getEffectiveUserID
     */
    public void setEffectiveUserID(UserID userid)
    {
        addAttribute(USERID_ATTRIB, userid.getString());
    }

    public OrganizationID getOrganizationID()
    {
        return OrganizationID.fromHexString(getAttribute(ORGID_ATTRIB));
    }

    public void setOrganizationID(OrganizationID orgid)
    {
        addAttribute(ORGID_ATTRIB, orgid.toHexString());
    }
}
