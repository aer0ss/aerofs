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

package com.aerofs.bifrost.oaaas.model;

import com.google.common.collect.Sets;
import org.hibernate.validator.constraints.Email;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * Representation of the server hosting the protected resources, capable of
 * accepting and responding to protected resource requests using access tokens.
 */
@Entity
@Table(name="resourceserver", uniqueConstraints =
    @UniqueConstraint(columnNames = {"owner", "resourceServerName"})
)
@Inheritance(strategy =  InheritanceType.TABLE_PER_CLASS)
public class ResourceServer extends AbstractEntity {

  public static final String SCOPE_PATTERN = "^[^,]+$"; // anything but a comma
  private static final long serialVersionUID = 1;

  @Column(name = "resourceServerName")
  @NotNull
  private String name;

  @Column(unique = true, name = "resourceServerKey")
  @NotNull
  private String key;

  @Column
  private String description;


  @ElementCollection(fetch= FetchType.EAGER)
  @NotNull
  private Set<String> scopes = Sets.newHashSet();

  @Column
  @NotNull
  private String secret;

  @Column(nullable = false, updatable = false)
  @NotNull
  private String contactName;

  @Column
  private String owner;

  @Column
  @Email
  private String contactEmail;

  @OneToMany(fetch = FetchType.EAGER, cascade = {CascadeType.ALL}, orphanRemoval = true)
  @JoinColumn(name = "resourceserver_id", nullable = false)
  @Valid
  private Set<Client> clients;

  @Column
  private String thumbNailUrl;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Set<String> getScopes() {
    return scopes;
  }

  public void setScopes(Set<String> scopes) {
    this.scopes = scopes;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getContactName() {
    return contactName;
  }

  public void setContactName(String contactName) {
    this.contactName = contactName;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
  }

  /**
   * @return the thumbNailUrl
   */
  public String getThumbNailUrl() {
    return thumbNailUrl;
  }

  /**
   * @param thumbNailUrl the thumbNailUrl to set
   */
  public void setThumbNailUrl(String thumbNailUrl) {
    this.thumbNailUrl = thumbNailUrl;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getOwner() {
    return owner;
  }

  /**
   * @return the key
   */
  public String getKey() {
    return key;
  }

  /**
   * @param key the key to set
   */
  public void setKey(String key) {
    this.key = key;
  }

  /**
   * @return the clients
   */
  public Set<Client> getClients() {
    return clients;
  }

  /**
   * @param clients the clients to set
   */
  public void setClients(Set<Client> clients) {
    this.clients = clients;
  }

  /**
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * @param description the description to set
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   *
   * @param client the Client
   * @return if the Client is part of this ResourceServer
   */
  public boolean containsClient(Client client) {
    //first load them
    getClients();
    return clients != null && clients.contains(client);
  }

  @Override
  public boolean validate(ConstraintValidatorContext context) {
    boolean isValid = true;

    for (String scope : scopes) {
      if (!scope.matches(ResourceServer.SCOPE_PATTERN)) {
        violation(context, "Scope '" + scope + "' contains invalid characters");
        isValid = false;
      }
    }
    return isValid;
  }
}
