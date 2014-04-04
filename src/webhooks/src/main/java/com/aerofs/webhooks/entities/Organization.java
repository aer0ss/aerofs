package com.aerofs.webhooks.entities;

import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Where;

@Entity
@Table( name = "sp_organization" )
public class Organization {

    private int id;
    private String name;
    private String stripeCustomerId;
    private Set<User> administrators;
    private Set<User> members;

    Organization() {
    }

    @Id
    @Column( name = "o_id" )
    public int getId() {
        return id;
    }

    public void setId( final int id ) {
        this.id = id;
    }

    @Column( name = "o_name" )
    public String getName() {
        return name;
    }

    public void setName( final String name ) {
        this.name = name;
    }

    @Column( name = "o_stripe_customer_id" )
    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId( final String stripeCustomerId ) {
        this.stripeCustomerId = stripeCustomerId;
    }

    @OneToMany( mappedBy = "organization" )
    public Set<User> getMembers() {
        return members;
    }

    public void setMembers( final Set<User> members ) {
        this.members = members;
    }

    @OneToMany( mappedBy = "organization" )
    @Where( clause = "u_auth_level=1" )
    @Immutable
    public Set<User> getAdministrators() {
        return administrators;
    }

    public void setAdministrators( final Set<User> administrators ) {
        this.administrators = administrators;
    }

}
