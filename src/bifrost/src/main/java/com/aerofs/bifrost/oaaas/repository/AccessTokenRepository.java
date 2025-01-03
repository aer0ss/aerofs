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

package com.aerofs.bifrost.oaaas.repository;

import com.aerofs.bifrost.oaaas.model.AccessToken;

import java.util.List;

public interface AccessTokenRepository
{
    AccessToken findByToken(String token);

    AccessToken findByRefreshToken(String refreshToken);

    AccessToken save(AccessToken token);

    void delete(AccessToken accessToken);
    void deleteAllTokensByOwner(String owner);
    void deleteDelegatedTokensByOwner(String owner);

    List<AccessToken> findByOwner(String owner);
}
