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
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;

import com.aerofs.bifrost.oaaas.auth.principal.PrincipalUtils;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import com.aerofs.oauth.AuthenticatedPrincipal;

/**
 * A representation of an <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2#section-4.1.1"
 * >AuthorizationRequest</a>.
 *
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "authorizationrequest")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class AuthorizationRequest extends AbstractEntity {

  @Column
  @NotNull
  private String responseType;

  @Transient
  private String clientId;

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
  @NotNull
  private String authState;

  @Column(unique = true)
  private String authorizationCode;

  public AuthorizationRequest() {
    super();
  }

  public AuthorizationRequest(String responseType, String clientId, String redirectUri, Set<String> requestedScopes, String state,
      String authState) {
    super();
    this.responseType = responseType;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.requestedScopes = requestedScopes;
    this.state = state;
    this.authState = authState;
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
   * @return the responseType
   */
  public String getResponseType() {
    return responseType;
  }

  /**
   * @param responseType
   *          the responseType to set
   */
  public void setResponseType(String responseType) {
    this.responseType = responseType;
  }

  /**
   * @return the clientId
   */
  public String getClientId() {
    return clientId;
  }

  /**
   * @param clientId
   *          the clientId to set
   */
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  /**
   * @return the redirectUri
   */
  public String getRedirectUri() {
    return redirectUri;
  }

  /**
   * @param redirectUri
   *          the redirectUri to set
   */
  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  /**
   * @return the requested scopes
   */
  public Set<String> getRequestedScopes() {
    return requestedScopes;
  }

  /**
   * @param requestedScopes
   *          the requestedScopes to set
   */
  public void setRequestedScopes(Set<String> requestedScopes) {
    this.requestedScopes = requestedScopes;
  }

  /**
   * @return the granted scopes
   */
  public Set<String> getGrantedScopes() {
    return grantedScopes;
  }

  /**
   * @param grantedScopes
   *          the grantedScopes to set
   */
  public void setGrantedScopes(Set<String> grantedScopes) {
    this.grantedScopes = grantedScopes;
  }

  /**
   * @return the state
   */
  public String getState() {
    return state;
  }

  /**
   * @param state
   *          the state to set
   */
  public void setState(String state) {
    this.state = state;
  }

  /**
   * @return the authState
   */
  public String getAuthState() {
    return authState;
  }

  /**
   * @param authState
   *          the authState to set
   */
  public void setAuthState(String authState) {
    this.authState = authState;
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
   * @return the authorizationCode
   */
  public String getAuthorizationCode() {
    return authorizationCode;
  }

  /**
   * @param authorizationCode
   *          the authorizationCode to set
   */
  public void setAuthorizationCode(String authorizationCode) {
    this.authorizationCode = authorizationCode;
  }

  /**
   * @return the principal
   */
  public AuthenticatedPrincipal getPrincipal() {
    return principal;
  }

  /**
   * @param principal
   *          the principal to set
   */
  public void setPrincipal(AuthenticatedPrincipal principal) {
    this.principal = principal;
    this.encodePrincipal();
  }

  /**
   * @return the encodedPrincipal
   */
  public String getEncodedPrincipal() {
    return encodedPrincipal;
  }

  /* (non-Javadoc)
   * @see com.aerofs.bifrost.oaaas.model.AbstractEntity#validate()
   */
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
