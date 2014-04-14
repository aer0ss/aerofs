<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Organization Settings" %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<%block name="css">
    <link href="${request.static_path('web:static/css/datatables-bootstrap.css')}"
          rel="stylesheet">
</%block>

<h2 class="page-block">Organization settings</h2>

<form class="form-inline page-block" id="update-name-form"
        action="${request.route_path('org_settings')}" method="post">
    ${self.csrf.token_input()}
    <label class="control-label" for="organization_name">Organization name:</label>
        <input type="text" id="organization_name" name="organization_name"
                value="${organization_name}">
    <button class="btn" id="update-name-button">Update</button>
</form>


<%! from web.util import is_private_deployment %>

## Include subscription management only for public deployment
%if not is_private_deployment(request.registry.settings):

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
            <a href="${request.route_path('pricing')}" target="_blank">View pricing.</a>
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
                    <a href="${request.route_path('pricing')}" target="_blank">More info on plans</a>.
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

    <script type="text/javascript">
        $(document).ready(function() {
            $('#organization_name').focus();

            $("#update-name-form").submit(function() {
                $("#update-name-button").attr("disabled", "disabled");
                return true;
            });

            %if not has_customer_id and upgrade:
                upgrade();
            %endif
        });

        function upgrade() {
            inputCreditCardInfoAndCreateStripeCustomer(function() {
                ## Don't use "location.href =". It's not supported by old Firefox.
                window.location.assign("${request.route_path('start_subscription_done')}");
            });
        }
    </script>
</%block>
