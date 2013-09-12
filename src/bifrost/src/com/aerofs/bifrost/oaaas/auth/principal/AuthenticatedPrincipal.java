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
package com.aerofs.bifrost.oaaas.auth.principal;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
* {@link Principal} that can contain roles and additional attributes. This is
* the return Object for AbstractAuthenticator implementations.
*/
public class AuthenticatedPrincipal implements Serializable, Principal {

  private static final long serialVersionUID = 1L;

  private String name;

  private Collection<String> roles;

  private Collection<String> groups;

  private boolean adminPrincipal;

  /*
   * Extra attributes, depending on the authentication implementation. Note that we only support String - String attributes as we
   * need to be able to persist the Principal generically
   */
  private Map<String, String> attributes;

  public AuthenticatedPrincipal() {
    super();
  }

  public AuthenticatedPrincipal(String username) {
    this(username, new ArrayList<String>());
  }

  public AuthenticatedPrincipal(String username, Collection<String> roles) {
    this(username, roles, new HashMap<String, String>());
  }

  public AuthenticatedPrincipal(String username, Collection<String> roles, Map<String, String> attributes) {
    this(username, roles, attributes, new ArrayList<String>());
  }

  public AuthenticatedPrincipal(String username, Collection<String> roles, Map<String, String> attributes, Collection<String> groups) {
    this(username, roles, attributes, groups, false);
  }

  public AuthenticatedPrincipal(String username, Collection<String> roles, Map<String, String> attributes, Collection<String> groups, boolean adminPrincipal) {
    this.name = username;
    this.roles = roles;
    this.attributes = attributes;
    this.groups = groups;
    this.adminPrincipal = adminPrincipal;
  }

  /**
   * @return the roles
   */
  public Collection<String> getRoles() {
    return roles;
  }

  /**
   * @return the attributes
   */
  public Map<String, String> getAttributes() {
    return attributes;
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
    if (attributes == null) {
      attributes = new HashMap<String, String>();
    }
    attributes.put(key, value);
  }

  public void addGroup(String name) {
    if (groups == null) {
      groups = new ArrayList<String>();
    }
    groups.add(name);
  }

  /*
   * (non-Javadoc)
   *
   * @see java.security.Principal#getName()
   */
  @Override
  public String getName() {
    return name;
  }

  public String getDisplayName() {
    return name;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return getClass().getName() + " [name=" + name + ", roles=" + roles + ", attributes=" + attributes + "]";
  }

  /**
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @param roles the roles to set
   */
  public void setRoles(Collection<String> roles) {
    this.roles = roles;
  }

  /**
   * @param attributes the attributes to set
   */
  public void setAttributes(Map<String, String> attributes) {
    this.attributes = attributes;
  }

  public Collection<String> getGroups() {
    return groups;
  }

  public void setGroups(Collection<String> groups) {
    this.groups = groups;
  }

  public boolean isGroupAware() {
    return groups != null && !groups.isEmpty();
  }

  public boolean isAdminPrincipal() {
    return adminPrincipal;
  }

  public void setAdminPrincipal(boolean adminPrincipal) {
    this.adminPrincipal = adminPrincipal;
  }

   private static final Gson _gson = new GsonBuilder()
           .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
           .create();

    public String serialize() {
        return _gson.toJson(this);
    }

    public static AuthenticatedPrincipal deserialize(String encodedPrincipal) {
        return _gson.fromJson(encodedPrincipal, AuthenticatedPrincipal.class);
    }
}
