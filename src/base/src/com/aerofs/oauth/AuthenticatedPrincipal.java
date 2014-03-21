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

import com.aerofs.base.NoObfuscation;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.Map;

/**
 * {@link Principal} that can contain roles and additional attributes.
 */
@NoObfuscation
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

    AuthenticatedPrincipal(String username, UserID userID, OrganizationID orgID) {
        this(username);
        setOrganizationID(orgID);
        setUserID(userID);
    }

    // FIXME: make this go away
    public AuthenticatedPrincipal(String username) {
        this.name = username;
        this.attributes = Maps.<String, String>newHashMap();
        // useless junk:
        this.roles = Lists.<String>newArrayList();
        this.groups = Lists.<String>newArrayList();
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

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getClass().getName() + " [name=" + name + ", roles=" + roles + ", attributes=" + attributes + "]";
    }

    public UserID getUserID()
    {
        return UserID.fromInternal(getAttribute(USERID_ATTRIB));
    }

    public void setUserID(UserID userid)
    {
        addAttribute(USERID_ATTRIB, userid.getString());
    }

    public OrganizationID getOrganizationID()
    {
        return new OrganizationID(Integer.parseInt(getAttribute(ORGID_ATTRIB), 16));
    }

    public void setOrganizationID(OrganizationID orgid)
    {
        addAttribute(ORGID_ATTRIB, orgid.toHexString());
    }
}
