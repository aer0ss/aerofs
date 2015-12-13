<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Customization" %>

<%! from web.views.maintenance.maintenance_util import unformat_pem %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="bootstrap" file="bootstrap.mako"/>
<%namespace name="modal" file="modal.mako"/>

<div class="page-block">
    <h2>Customization</h2>

    <div class="alert">
    <p>AeroFS for Business includes support for customization, including white-labeling and custom
        banner text. <br/><a href="https://www.aerofs.com/pricing/" target="_blank">Contact us to
       upgrade your account</a>.</p>
    </div>
</div>
