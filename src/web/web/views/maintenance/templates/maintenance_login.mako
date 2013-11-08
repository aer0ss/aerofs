<%inherit file="marketing_layout.mako"/>
<%! page_title = "Appliance Maintenance" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="upload_license_button" file="upload_license_button.mako"/>

<div class="span8 offset2">
    <div class="text-center">
        <h3>Sign in to Manage Appliance</h3>

        <p>Please sign in with your license to manage this appliance.</p>

        ## The enctype is needed for file uploads
        <form id="signin-form" method="post" enctype='multipart/form-data'
              action="${request.route_path('maintenance_login_submit')}">
            ${csrf.token_input()}
            <input type="hidden" name="${url_param_next}" value="${next}">

            ${upload_license_button.button('license-file', url_param_license)}

            <p>
                <button class="btn" id="next-btn" type="submit" style="width: 180px">
                    Continue</button>
            </p>
        </form>
    </div>
</div>

<%block name="scripts">
    ${upload_license_button.scripts('license-file', 'next-btn')}

    <script>
        $(document).ready(function() {
            $('#signin-form').submit(function() {
                setDisabled($('#next-btn'));
            });
        });
    </script>
</%block>
