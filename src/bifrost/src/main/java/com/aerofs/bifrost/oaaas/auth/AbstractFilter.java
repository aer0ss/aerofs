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

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import org.apache.commons.lang.StringUtils;


/**
 * Shared functionality of the different authorization and userconsent Filters
 * 
 */
public abstract class AbstractFilter implements ContainerRequestFilter
{

  /**
   * Constant to get the return uri when the control should be returned to the
   * implementor
   */
  public static final String RETURN_URI = "RETURN_URI";

  /**
   * The constant used to keep 'session' state when we give flow control to the
   * authenticator filter. Part of the contract with the authenticator Filter is
   * that we expect to get the value back when authentication is done.
   */
  public static final String AUTH_STATE = "auth_state";

    /**
   * Get the attribute value that serves as session state.
   * @param request the HttpServletRequest
   */
  public final String getAuthStateValue(ContainerRequest request) {
    String authStateValue = (String) request.getProperties().get(AUTH_STATE);
    if (StringUtils.isEmpty(authStateValue)) {
      authStateValue = request.getQueryParameters().getFirst(AUTH_STATE);
    }
    return authStateValue;
  }

  public final String getReturnUri(ContainerRequest request) {
    String returnUri = (String) request.getProperties().get(RETURN_URI);
    if (StringUtils.isEmpty(returnUri)) {
      returnUri = request.getQueryParameters().getFirst(RETURN_URI);
    }

    return returnUri;
  }

  protected final void setAuthStateValue(ContainerRequest request, String authState) {
    request.getProperties().put(AUTH_STATE, authState);
  }

}
