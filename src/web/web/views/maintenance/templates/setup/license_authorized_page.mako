## This page is used when the system's license is valid. In this case,
## updating the license requires authentication.
## See docs/design/pyramid_auth.md, setup() in setup.py, and setup.mako.

<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="common" file="common.mako"/>
<%namespace name="license_page" file="license_page.mako"/>

<form method="post" onsubmit="submitForm(); return false;">
    <h3>Set up AeroFS Appliance</h3>

    <p>This page will guide you through setting up the appliance.
        Review your license before continuing.</p>

    <h4>Your license:</h4>
    <dl class="dl-horizontal">
        <dt>Licensed to:</dt>
        <dd>${render_license_field('license_company')}</dd>
        <dt>Type:</dt>
        <dd>${render_license_field('license_type')}</dd>
        <dt>Valid until:</dt>
        <dd>${render_license_field('license_valid_until')}</dd>
        <dt>Allowed seats:</dt>
        <dd>${render_license_field('license_seats')}</dd>
    </dl>

    <h4>Update your license:</h4>
    <p><a href="mailto:support@aerofs.com">Contact us</a> to request a new license.</p>
    <p><input id="license-file" type="file"></p>

    <hr />
    ${common.render_next_button()}
</form>

<%def name='render_license_field(key)'>
    %if key in current_config:
        ${current_config[key].capitalize()}
    %else:
        -
    %endif
</%def>

<%def name="scripts()">
    ${license_page.submit_scripts('license-file')}
</%def>