<%inherit file="marketing_layout.mako"/>
<%! page_title = "Sign In to Manage Appliance" %>

<%namespace name="csrf" file="csrf.mako"/>

<div class="col-sm-8 col-sm-offset-2 text-center">
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
        <div class="row" style="margin-top: 40px; margin-bottom: 40px;">
            <div class="col-sm-6 col-sm-offset-3">
                <p>
                    <button type="button" id='license-btn' class="btn btn-large btn-primary
                            "
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
                <p>
                    Your license file ends in <em>.license</em>
                </p>
            </div>
        </div>
    </form>

    <div>
        %if is_initialized:
            <p>Download your license file via your
                <a href="https://enterprise.aerofs.com" target="_blank">Private Cloud Dashboard</a>.
            </p>
        %else:
            <p>You should have received a license with this appliance. If not,
                please <a href="https://enterprise.aerofs.com/" target="_blank">
                request a license</a>.</p>
            <p><a href="https://support.aerofs.com/hc/en-us/articles/204951110" target="_blank">
                What happens if the license expires?</a></p>
        %endif
    </div>
</div>

<%block name="custom_banner_display">
    <div style="display:none; background:#EEE;" id="flash-msg-info" class="alert alert-block">
        <span id="flash-msg-info-body">
            You are using an insecure interface. It is recommended that you use our
            <a id="secure-link" href="">secure site</a>. Browser warnings may appear if this is a
            new installation and you have not yet configured a pubicly signed certificate and key.
        </span>
    <br/>
    </div>
</%block>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            $('#license-file').change(onLicenseFileChange);

            if (location.port == 8484) {
                ## Can't use config, as it might not have been initialized.
                var secure = "https://" + window.location.hostname + "/admin";

                var mngLink = document.getElementById("secure-link");
                if (mngLink != null) {
                    mngLink.setAttribute("href", secure);
                }

                $('#flash-msg-info').show();
            }
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
