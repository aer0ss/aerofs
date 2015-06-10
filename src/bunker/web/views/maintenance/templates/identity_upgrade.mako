<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Identity" %>

<%! from web.views.maintenance.maintenance_util import unformat_pem %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="modal" file="modal.mako"/>

<div class="page-block">
    <h2>Identity Management</h2>

    <p>You may choose AeroFS or a 3rd-party identity provider to manage user accounts. Switching between them has
        minimal disruption to your user base. <a href="https://support.aerofs.com/hc/en-us/articles/204592834" target="_blank">
        Learn more</a>.</p>

    <div class="alert">
    <p>AeroFS for Business includes support for ActiveDirectory and other existing identity providers
       that use LDAP. <a href="https://www.aerofs.com/pricing/" target="_blank">Contact us to upgrade
       your appliance</a>.</p>
    </div>
</div>

