<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Organization Settings" %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<h2>Organization settings</h2>

<form id="org-settings" class="page-block form-horizontal" action="${request.route_path('org_settings')}" method="post" role="form">
    ${self.csrf.token_input()}

    <div class="form-group">
        <label for="organization_name" class="col-sm-3 control-label">Organization name:</label>
        <div class="col-sm-6">
            <input type="text" id="organization_name" class="form-control" name="organization_name"
                value="${organization_name}">
        </div>
    </div>
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


<%! from web.util import is_private_deployment %>

## Include subscription management only for public deployment
%if not is_private_deployment(request.registry.settings):
    <hr>
    <div class="page-block">
        %if has_customer_id:
            <p><a href="${request.route_path('manage_subscription')}">
                Manage subscription and payment</a></p>
        %else:
            ${upgrade_plan()}
        %endif
    </div>

    <%def name="upgrade_plan()">
        <h3>Upgrade plan</h3>

        <p>Your organization is currently on the free AeroFS plan.
            For $10/user/month, you will enjoy <strong>unlimited</strong> users.
            <a href="/pricing" target="_blank">View pricing.</a>
        </p>
        <p style="margin-top: 20px">
            <a class="btn btn-primary" href="#" onclick="upgrade(); return false;">Upgrade</a>
        </p>

        ## Note that this modal can be brought up by clicking the Upgrade button on this
        ## page, or by visiting request.route_path('start_subscription'). (Non-admins
        ## may ask admins to visit the latter page to upgrade the plan). The
        ## content of this modal should be catered for both cases.
        <%credit_card_modal:html>
            <%def name="title()">
                Upgrade your AeroFS plan
            </%def>
            <%def name="description()">
                <p>
                    You are upgrading to a paid plan of $10/user/month.<br>
                    <a href="/pricing" target="_blank">More info on plans</a>.
                </p>

                <%credit_card_modal:default_description/>
            </%def>
            <%def name="okay_button_text()">
                <%credit_card_modal:default_okay_button_text/>
            </%def>
        </%credit_card_modal:html>
    </%def>

%endif

<%block name="scripts">
    %if not has_customer_id:
        <%credit_card_modal:javascript/>
    %endif

    <script>
        $(document).ready(function() {
            $('#organization_name').focus();

            updateQuotaUI();
            $('#enable_quota').click(updateQuotaUI);

            $("#org-settings").submit(function() {
                $("#update-button").prop("disabled", true);
                return true;
            });

            %if not has_customer_id and upgrade:
                upgrade();
            %endif
        });

        function updateQuotaUI() {
            var enableQuota = ($('#enable_quota').is(':checked'));
            $('#quota').prop('disabled', !enableQuota);
        }

        function upgrade() {
            inputCreditCardInfoAndCreateStripeCustomer(function() {
                ## Don't use "location.href =". It's not supported by old Firefox.
                window.location.assign("${request.route_path('start_subscription_done')}");
            });
        }
    </script>
</%block>
