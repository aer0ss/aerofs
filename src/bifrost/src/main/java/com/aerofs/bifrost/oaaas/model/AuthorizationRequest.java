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

import com.aerofs.ids.UniqueID;
import com.aerofs.bifrost.oaaas.auth.principal.PrincipalUtils;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * A representation of an <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2#section-4.1.1"
 * >AuthorizationRequest</a>.
 *
 */
@Entity
@Table(name = "authorizationrequest")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class AuthorizationRequest extends AbstractEntity
{
  private static final long serialVersionUID = 1;
  @Column
  @NotNull
  private String responseType;

  @Transient
  private AuthenticatedPrincipal principal;

  @Lob
  @Column(length = 16384)
  private String encodedPrincipal;

  @ManyToOne(optional = false)
  @JoinColumn(name = "client_id", nullable = false, updatable = false)
  private Client client;

  @Column
  @NotNull
  private String redirectUri;

  @ElementCollection(fetch= FetchType.EAGER)
  private Set<String> requestedScopes = Sets.newHashSet();

  @ElementCollection(fetch = FetchType.EAGER)
  private Set<String> grantedScopes = Sets.newHashSet();

  @Column
  private String state;

  @Column(unique = true)
  private String authorizationCode;

  @Transient
  public @Nullable Long expiresInSeconds;

  public AuthorizationRequest() {
    super();
  }

  public AuthorizationRequest(String responseType, Client client, String redirectUri,
          Set<String> requestedScopes, String state, AuthenticatedPrincipal principal)
  {
    super();
    this.responseType = responseType;
    this.client = client;
    this.redirectUri = redirectUri;
    this.requestedScopes = requestedScopes;
    this.state = state;
    this.authorizationCode = UniqueID.generate().toStringFormal();
    setPrincipal(principal);  // use the setter because it does encoding and stuff
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

  public String getResponseType() {
    return responseType;
  }

  public void setResponseType(String responseType) {
    this.responseType = responseType;
  }

  public String getRedirectUri() {
    return redirectUri;
  }

  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  public Set<String> getRequestedScopes() {
    return requestedScopes;
  }

  public void setRequestedScopes(Set<String> requestedScopes) {
    this.requestedScopes = ImmutableSet.copyOf(requestedScopes);
  }

  public Set<String> getGrantedScopes() {
    return grantedScopes;
  }

  public void setGrantedScopes(Set<String> grantedScopes) {
    this.grantedScopes = ImmutableSet.copyOf(grantedScopes);
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public Client getClient() {
    return client;
  }

  public void setClient(Client client) {
    this.client = client;
  }

  public String getAuthorizationCode() {
    return authorizationCode;
  }

  public void setAuthorizationCode(String authorizationCode) {
    this.authorizationCode = authorizationCode;
  }

  public AuthenticatedPrincipal getPrincipal() {
    return principal;
  }

  public void setPrincipal(AuthenticatedPrincipal principal) {
    this.principal = principal;
    this.encodePrincipal();
  }

  public String getEncodedPrincipal() {
    return encodedPrincipal;
  }

  @Override
  public boolean validate(ConstraintValidatorContext context) {
    if (StringUtils.isNotBlank(redirectUri)) {
      if (redirectUri.contains("#")) {
        context.buildConstraintViolationWithTemplate(
            "Fragment component is not allowed in redirectUri").addConstraintViolation();
        return false;
      }
    }
    return true;
  }
}
