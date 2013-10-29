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
package com.aerofs.bifrost.oaaas.noop;

import com.sun.jersey.spi.container.ContainerRequest;
import com.aerofs.bifrost.oaaas.auth.AbstractUserConsentHandler;
import com.aerofs.bifrost.oaaas.auth.UserConsentFilter;
import com.aerofs.bifrost.oaaas.model.Client;

/**
 * A noop implementation of {@link AbstractUserConsentHandler} that
 * contains no consent handling but only fulfills the contract of the
 * {@link UserConsentFilter}. Useful for testing and demonstration purposes
 * only, of course not safe for production.
 * 
 */
public class NoopUserConsentHandler extends AbstractUserConsentHandler {

  @Override
  public void handleUserConsent(ContainerRequest request, String authStateValue, String returnUri, Client client) {
    super.setAuthStateValue(request, authStateValue);
    super.setGrantedScopes(request, client.getScopes().isEmpty() ? new String[]{ } : client.getScopes().toArray(new
        String[client.getScopes().size()]));
  }

}
