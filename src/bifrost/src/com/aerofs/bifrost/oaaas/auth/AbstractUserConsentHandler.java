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
package com.aerofs.bifrost.oaaas.auth;


import com.google.common.collect.Sets;
import com.sun.jersey.spi.container.ContainerRequest;
import com.aerofs.bifrost.oaaas.model.Client;

/**
 * Responsible for handling user consent.
 *
 */
public abstract class AbstractUserConsentHandler extends AbstractFilter {

  /**
   * The constant that contains the scopes, set by concrete userConsentHandlers
   * and consumed by the authorization endpoint.
   */
  public static final String GRANTED_SCOPES = "GRANTED_SCOPES";

  /**
   * Constant to get the Client when the control should be returned to the
   * implementor
   */
  public static final String CLIENT = "CLIENT";

  /**
   *
   * Get the Client from the request context to use in handling user consent
   *
   * @param request
   *          the {@link ContainerRequest}
   * @return the Client which is asking for consent
   */
  public final Client getClient(ContainerRequest request) {
    return (Client) request.getProperties().get(CLIENT);
  }

  @Override
  public final ContainerRequest filter(ContainerRequest request)
  {
        handleUserConsent(request, getAuthStateValue(request),
        getReturnUri(request), getClient(request));
      return request;
  }

  /**
   * Implement this method to perform the actual authentication.
   *
   * In general, the contract is:
   * <p>
   * assert that the user has granted consent. You can use the request and
   * response for this. When not yet granted consent:
   * </p>
   * <ul>
   * <li>use {@link #getAuthStateValue(ContainerRequest)} to
   * pass-around for user agent communication</li>
   * <li>use {@link #getReturnUri(ContainerRequest)} if you need to
   * step out and return to the current location</li>
   * <li>use {@link #getClient(ContainerRequest)} for accessing the
   * {@link Client} data</li>
   * </ul>
   * <p>
   * When consent granted:
   * </p>
   * <ul>
   * <li>set the authState attribute, by calling
   * {@link #setAuthStateValue(ContainerRequest, String)}</li>
   * <li>set the scopes (optional) the user has given consent for, by calling
   * {@link #setGrantedScopes}</li>
   * <li>call chain.doFilter(request, response) to let the flow continue..
   * </ul>
   *
   * @param request
   *          the ContainerRequest
   * @param authStateValue
   *          the authState nonce to set back on the {@link ContainerRequest} when
   *          done
   * @param returnUri
   *          the startpoint of the chain if you want to return from a form or
   *          other (external) component
   * @param client
   *          the Client wished to obtain an access token
   */
  public abstract void handleUserConsent(ContainerRequest request, String authStateValue, String returnUri, Client client);

  /**
   * Set the granted scopes of the consent on the request. Note: this optional.
   *
   * @param request
   *          the original ServletRequest
   * @param scopes
   *          the {@link String[]} scopes.
   */
  protected final void setGrantedScopes(ContainerRequest request, String[] scopes) {
    request.getProperties().put(GRANTED_SCOPES, Sets.newHashSet(scopes));
  }
}
