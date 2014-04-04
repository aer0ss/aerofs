package com.aerofs.webhooks.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.arrowfs.type.AbstractId;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table( name = "sp_user" )
public class User extends AbstractId<String, org.arrowfs.type.User> {

    private String firstName;
    private String lastName;
    private boolean administrator;
    private Organization organization;

    User() {
    }

    @Id
    @Column( name = "u_id" )
    @Override
    public String getId() {
        return id;
    }

    public void setId( final String id ) {
        this.id = id;
    }

    @Column( name = "u_first_name" )
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName( final String firstName ) {
        this.firstName = firstName;
    }

    @Column( name = "u_last_name" )
    public String getLastName() {
        return lastName;
    }

    public void setLastName( final String lastName ) {
        this.lastName = lastName;
    }

    @Column( name = "u_auth_level" )
    public boolean isAdministrator() {
        return administrator;
    }

    public void setAdministrator( final boolean administrator ) {
        this.administrator = administrator;
    }

    @ManyToOne
    @JoinColumn( name = "u_org_id" )
    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization( final Organization organization ) {
        this.organization = organization;
    }

    @Override
    @Transient
    @JsonIgnore
    public Class<org.arrowfs.type.User> getBaseType() {
        return org.arrowfs.type.User.class;
    }
}
