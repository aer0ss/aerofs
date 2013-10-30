/*
 * Copyright 2012 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.bifrost.oaaas.auth;

import com.aerofs.oauth.AuthenticatedPrincipal;
import com.sun.jersey.spi.container.ContainerRequest;

/**
 * To be implemented by various authentication methods.
 */
public abstract class AbstractAuthenticator extends AbstractFilter {

  /**
   * The constant that contains the principal, set by concrete authenticators
   * and consumed by the authorization endpoint.
   */
  public static final String PRINCIPAL = "PRINCIPAL";

    @Override
  public final ContainerRequest filter(ContainerRequest request)  {
      authenticate(request, getAuthStateValue(request), getReturnUri(request));
      return request;
  }

  /**
   * Implement this method to state whether the given request is a continuation that can be handled.
   * This method will be called for every consecutive request after the initial one.<br />
   * Returning true means that the request is part of an ongoing authentication.<br />
   * Returning false indicates to the framework that the request is not known.<br />
   * Typically this can be determined by the http method or one or more request parameters/attributes being present.
   *
   * @param request the HttpServletRequest
   */
  public abstract boolean canCommence(ContainerRequest request);


  /**
   * Implement this method to perform the actual authentication.
   * 
   * In general, the contract is:
   * <p>
   * assert that the user is authenticated. You can use the request and response
   * for this. When not yet authenticated:
   * </p>
   * <ul>
   * <li>use {@link #getAuthStateValue(ContainerRequest)} to
   * pass-around for user agent communication</li>
   * <li>use {@link #getReturnUri(ContainerRequest)} if you need to
   * step out and return to the current location
   * </ul>
   * <p>
   * When authenticated:
   * </p>
   * <ul>
   * <li>set the authState attribute, by calling
   * {@link #setAuthStateValue(ContainerRequest, String)}</li>
   * <li>set the principal attribute, by calling
   * {@link #setPrincipal(ContainerRequest, AuthenticatedPrincipal)}</li>
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
   */
  public abstract void authenticate(ContainerRequest request, String authStateValue, String returnUri);

  /**
   * Set the given principal on the request.
   * 
   * @param request
   *          the original ServletRequest
   * @param principal
   *          the Principal to set.
   */
  protected final void setPrincipal(ContainerRequest request, AuthenticatedPrincipal principal) {
    request.getProperties().put(PRINCIPAL, principal);
  }
}
