<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="setup_common" file="setup_common.mako"/>
<%namespace name="license_common" file="../license_common.mako"/>

<form method="post" onsubmit="submitForm(); return false;">
    <h3>Set up AeroFS Appliance</h3>

    <p>This page will guide you through setting up the appliance.
        Review your license before continuing. Information on network and
        firewall requirements can be found <a target="_blank"
        href="https://support.aerofs.com/entries/22661589-Things-to-know-before-deploying-AeroFS-Private-Cloud">
        here</a>.</p>
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
    <p><a target="_blank" href="https://support.aerofs.com/entries/25408319-What-happens-if-my-Private-Cloud-license-expires-">
        You can read here what happens if a license expires.</a></p>

    <hr />
    ${setup_common.render_next_button()}
</form>

<%def name='render_license_field(key)'>
    %if key in current_config:
        ${current_config[key].capitalize()}
    %else:
        -
    %endif
</%def>

<%def name="scripts()">
    ${license_common.submit_scripts('license-file')}
</%def>
