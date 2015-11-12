<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Organization Settings" %>

<h2>Organization settings</h2>

<form id="org-settings" class="page-block form-horizontal" action="${request.route_path('org_settings')}" method="post" role="form">
    ${self.csrf.token_input()}

    <div class="form-group">
        <label for="tfa-setting" class="col-sm-3 control-label">Two-factor authentication:</label>
        <div class="col-sm-9">
            <div class="radio">
              <label>
                <input type="radio" name="tfa-setting" id="tfa-setting-mandatory" value="2"
                %if tfa_level == 2:
                    checked
                %endif
                >
                Mandatory
                <p class="help-block">All users must use two-factor authentication.
                <strong>Users will not be able to use the AeroFS web panel nor set up new devices until they set up their phone as a second authentication factor.</strong></p>
              </label>
            </div>
            <div class="radio">
              <label>
                <input type="radio" name="tfa-setting" id="tfa-setting-optin" value="1"
                %if tfa_level == 1:
                    checked
                %endif
                >
                Opt-in
                <p class="help-block">Users can choose to use two-factor authentication
                with their AeroFS account.</p>
              </label>
            </div>
            <div class="radio">
              <label>
                <input type="radio" name="tfa-setting" id="tfa-setting-disallowed" value="0"
                %if tfa_level == 0:
                    checked
                %endif
                >
                Disabled
                <p class="help-block">Users cannot use two-factor authentication for their AeroFS
                    account. <strong>You should only choose this option if you are using a separate
                    multiple-factor authentication system with AeroFS.</strong></p>
              </label>
            </div>
        </div>
    </div>

    %if show_quota_options:
        <div class="form-group">
            <div class="col-sm-6 col-sm-offset-3">
                <div class="checkbox">
                    <label>
                        <input type="checkbox" id="enable_quota" name="enable_quota"
                               %if quota_enabled:
                                   checked
                               %endif
                                >
                        Limit data usage on Team Servers to
                    </label>
                </div>
            </div>
        </div>
        <div class="form-group">
            <div class="input-append col-sm-6 col-sm-offset-3">
              <input type="text" size=4 maxlength=4 id="quota" name="quota" required
                     %if quota_enabled:
                         value="${quota}"
                     %else:
                         value="5"
                     %endif
                     >
              <span class="add-on">GB per user</span>
            </div>
        </div>
    %endif

    <div class="form-group">
        <div class="col-sm-6 col-sm-offset-3">
            <button class="btn btn-primary" id="update-button">Update</button>
        </div>
    </div>
</form>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            updateQuotaUI();
            $('#enable_quota').click(updateQuotaUI);

            $("#org-settings").submit(function() {
                $("#update-button").prop("disabled", true);
                return true;
            });
        });

        function updateQuotaUI() {
            var enableQuota = ($('#enable_quota').is(':checked'));
            $('#quota').prop('disabled', !enableQuota);
        }
    </script>
</%block>
