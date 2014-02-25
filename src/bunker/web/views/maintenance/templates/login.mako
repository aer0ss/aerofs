<%inherit file="marketing_layout.mako"/>
<%! page_title = "Sign In to Manage Appliance" %>

<%namespace name="csrf" file="csrf.mako"/>

<div class="span8 offset2 text-center">
    %if is_initialized:
        <h3>Sign in to Manage Appliance</h3>
        <p>Please sign in with your license to manage this appliance.</p>
    %else:
        <h3>Welcome!</h3>
        <p>To setup this AeroFS Appliance, please upload your license to begin.</p>
    %endif

    ## The enctype is needed for file uploads
    <form method="post" enctype="multipart/form-data"
            action="${request.route_path('login_submit')}">
        ${csrf.token_input()}
        <input type="hidden" name="${url_param_next}" value="${next}">

        <input id="license-file" name="${url_param_license}" type="file" style="display: none">
        <div class="row-fluid" style="margin-top: 80px; margin-bottom: 100px;">
            <div class="span6 offset3">
                <p>
                    <button type="button" id='license-btn' class="btn btn-large btn-primary
                            input-block-level"
                            ## keep height consistent with create_or_restore_page.mako
                            style="height: 60px"
                            onclick="$('#license-file').click(); return false;">
                        Upload License File to
                        %if is_initialized:
                            Sign In
                        %else:
                            Begin
                        %endif
                    </button>
                </p>
                <p class="footnote">
                    Your license file ends in <em>.license</em>
                </p>
            </div>
        </div>
    </form>

    <div class="footnote">
        %if is_initialized:
            <p>Download your license file at
                <a href="https://privatecloud.aerofs.com" target="_blank">privatecloud.aerofs.com</a>.
            </p>
        %else:
            <p>You should have received a license with this appliance. If not,
                please <a href="https://privatecloud.aerofs.com/request_signup" target="_blank">
                request a license</a>.</p>
            <p><a href="https://support.aerofs.com/entries/25408319" target="_blank">
                What happens if the license expires?</a></p>
        %endif
    </div>
</div>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            $('#license-file').change(onLicenseFileChange);
        });

        function onLicenseFileChange() {
            var hasFile = $('#license-file').val() != "";
            if (hasFile) {
                var $btn = $('#license-btn');
                setEnabled($btn, false);
                $btn.text("Signing in...");
                $('form').submit();
            }
        }
    </script>
</%block>
