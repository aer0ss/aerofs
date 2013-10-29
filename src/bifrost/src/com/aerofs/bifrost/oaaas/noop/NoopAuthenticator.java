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

package com.aerofs.bifrost.oaaas.noop;

import com.sun.jersey.spi.container.ContainerRequest;
import com.aerofs.bifrost.oaaas.auth.AbstractAuthenticator;
import com.aerofs.bifrost.oaaas.auth.principal.AuthenticatedPrincipal;

/**
 * A minimalistic implementation of AbstractAuthenticator that contains no authentication but only fulfills the
 * contract of Authenticators.
 * Useful for testing and demonstration purposes only, of course not safe for production.
 */
public class NoopAuthenticator extends AbstractAuthenticator {

  @Override
  public boolean canCommence(ContainerRequest request) {
    return getAuthStateValue(request) != null;
  }

  @Override
  public void authenticate(ContainerRequest request, String authStateValue, String returnUri) {
    super.setAuthStateValue(request, authStateValue);
    AuthenticatedPrincipal principal = getAuthenticatedPrincipal();
    super.setPrincipal(request, principal);
  }

  protected AuthenticatedPrincipal getAuthenticatedPrincipal() {
    return new AuthenticatedPrincipal("noop");
  }
}
