<%namespace name="csrf" file="../csrf.mako"/>
<%namespace name="setup_common" file="setup_common.mako"/>

<form method="post" onsubmit="gotoNextPage(); return false;">
    <h3>Set up AeroFS Appliance</h3>

    <p>This page will guide you through setting up the appliance.
        Review your license before continuing.</p>
    <h4>Your license:</h4>
    <%namespace name="license_details" file="license_details.mako"/>
    <div id="license-body">
        <%license_details:body/>
    </div>

    <h4>Update your license:</h4>
    <p><a href="mailto:sales@aerofs.com">Contact us</a> to request a new license.</p>
    <p><input id="license-file" type="file" onchange="submitNewFile(); return false;"></p>
    <p><a target="_blank" href="https://support.aerofs.com/hc/en-us/articles/204951110">
        What happens if the license expires?</a></p>

    <hr />
    ${setup_common.render_next_button()}
</form>


<%def name="scripts()">
    <script>

        ## submit a new license file when the selection changes
        function submitNewFile() {
            hideAllMessages();

            ## only submit the new file if one has been chosen
            if (!$('#license-file').val()) {
                return;
            }

            ## disable navigation while the file uploads
            disableNavButtons();

            ## TODO (WW) use multipart/form-data as in login.mako
            var file = document.getElementById('license-file').files[0];
            var reader = new FileReader();
            reader.onload = function() {
                doPost("${request.route_path('json_set_license')}", {
                    'license': this.result
                }, displayNewLicense, function() {
                    ## if upload fails, restore the navigation buttons and clear out the selected
                    ## file from the form
                    enableNavButtons();
                    $('#license-file').val('');
                });
            };
            reader.readAsBinaryString(file);
        }

        ## Request details about the updated license to render as a partial
        function displayNewLicense() {
            enableNavButtons();
            $.get("${request.route_path('preview_license')}")
            .done(function (response) {
                showSuccessMessage('Your license was successfully updated.');
                $('#license-body').html(response);
                return;
            }).fail(function (xhr) {
                showAndTrackErrorMessageFromResponse(xhr);
                return;
            });
        }

    </script>
</%def>
