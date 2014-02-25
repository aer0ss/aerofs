<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="setup_common" file="setup_common.mako"/>

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
    <p><a target="_blank" href="https://support.aerofs.com/entries/25408319">
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
    <script>
        ## @param postLicenseUpload a callback function after the license file
        ##  is uploaded. May be null. Expected signature:
        ##      postLicenseUpload(onSuccess, onFailure).
        function submitForm(postLicenseUpload) {
            ## Go to the next page if no license file is specified. This is
            ## needed for license_page.mako to skip license upload if
            ## the license already exists.
            if (!$('#license-file').val()) {
                gotoNextPage();
                return;
            }

            disableNavButtons();

            ## TODO (WW) use multipart/form-data as in login.mako
            var file = document.getElementById('license-file').files[0];
            var reader = new FileReader();
            reader.onload = function() {
                submitLicenseFile(this.result, postLicenseUpload);
            };
            reader.readAsBinaryString(file);
        }

        function submitLicenseFile(license, postLicenseUpload) {
            var next;
            if (postLicenseUpload) {
                next = function() {
                    postLicenseUpload(gotoNextPage, enableNavButtons);
                };
            } else {
                next = gotoNextPage;
            }

            doPost("${request.route_path('json_set_license')}", {
                'license': license
            }, next, enableNavButtons);
        }
    </script>
</%def>
