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
package com.aerofs.bifrost.oaaas.model;

import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.aerofs.bifrost.oaaas.auth.principal.PrincipalUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import com.aerofs.oauth.AuthenticatedPrincipal;

/**
 * Representation of an <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2-30#section-1.4"
 * >AccessToken</a>
 *
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "accesstoken")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class AccessToken extends AbstractEntity {

  @Column(unique = true)
  @NotNull
  private String token;

  @Column(unique = true, nullable = true)
  private String refreshToken;

  @Transient
  private AuthenticatedPrincipal principal;

  @Lob
  @Column(length = 16384)
  @NotNull
  private String encodedPrincipal;

  @ManyToOne(optional = false)
  @JoinColumn(name = "client_id", nullable = false, updatable = false)
  private Client client;

  @Column
  private long expires;

  @ElementCollection(fetch= FetchType.EAGER)
  private Set<String> scopes = Sets.newHashSet();

  @Column
  @NotNull
  private String resourceOwnerId;

  public AccessToken(String token, AuthenticatedPrincipal principal, Client client, long expires, Set<String> scopes,
      String refreshToken) {
    super();
    this.token = token;
    this.principal = principal;
    this.encodePrincipal();
    this.resourceOwnerId = principal.getName();
    this.client = client;
    this.expires = expires;
    this.scopes = scopes;
    this.refreshToken = refreshToken;
    invariant();
  }

  private void invariant() {
    Preconditions.checkNotNull(token, "Token may not be null");
    Preconditions.checkNotNull(client, "Client may not be null");
    Preconditions.checkNotNull(principal, "AuthenticatedPrincipal may not be null");
    Preconditions.checkState(StringUtils.isNotBlank(principal.getName()), "AuthenticatedPrincipal#name may not be null");
  }

  @PreUpdate
  @PrePersist
  public void encodePrincipal() {
    if (principal != null) {
      this.encodedPrincipal = PrincipalUtils.serialize(principal);
    }
  }

  @PostLoad
  @PostPersist
  @PostUpdate
  public void decodePrincipal() {
    if (StringUtils.isNotBlank(encodedPrincipal)) {
      this.principal = PrincipalUtils.deserialize(encodedPrincipal);
    }
  }

  /**
   * @return the token
   */
  public String getToken() {
    return token;
  }

  /**
   * @param token
   *          the token to set
   */
  public void setToken(String token) {
    this.token = token;
  }

  /**
   * @return the client
   */
  public Client getClient() {
    return client;
  }

  /**
   * @param client
   *          the client to set
   */
  public void setClient(Client client) {
    this.client = client;
  }

  /**
   * @return the expires
   */
  public long getExpires() {
    return expires;
  }

  /**
   * @param expires
   *          the expires to set
   */
  public void setExpires(long expires) {
    this.expires = expires;
  }

  /**
   * @return the scopes
   */
  public Set<String> getScopes() {
    return scopes;
  }

  /**
   * @param scopes
   *          the scopes to set
   */
  public void setScopes(Set<String> scopes) {
    this.scopes = scopes;
  }

  /**
   * @return the principal
   */
  public AuthenticatedPrincipal getPrincipal() {
    return principal;
  }

  /**
   * @return the encodedPrincipal
   */
  public String getEncodedPrincipal() {
    return encodedPrincipal;
  }

  /**
   * @return the refreshToken
   */
  public String getRefreshToken() {
    return refreshToken;
  }

  /**
   * @param refreshToken
   *          the refreshToken to set
   */
  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  /**
   * @return the resourceOwnerId
   */
  public String getResourceOwnerId() {
    return resourceOwnerId;
  }

  public String getClientId() {
    return client.getClientId();
  }

}
