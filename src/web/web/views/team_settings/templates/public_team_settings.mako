<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Team Settings" %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<%block name="css">
    <link href="${request.static_path('web:static/css/datatables-bootstrap.css')}"
          rel="stylesheet">
</%block>

<div class="page_block">
    <h2>Team Settings</h2>
    ## TODO (WW) use form-horizontal when adding new fields. see login.mako
    Change team name:
    <form class="form-inline" id="update-name-form" action="${request.route_path('team_settings')}" method="post">
        <div class="input_container">
            ${self.csrf.token_input()}
            <input type="hidden" name="form.submitted">
            <input id="organization_name" type="text" name="organization_name" value="${organization_name}"/>
            <input class="btn" id="update-name-button" type="submit" value="Update" />
        </div>
    </form>
</div>

<div class="page_block">
    %if has_customer_id:
        <p><a href="${request.route_path('manage_subscription')}">
            Manage subscription and payment</a></p>
    %else:
        ${upgrade_plan()}
    %endif
</div>

<%def name="upgrade_plan()">
    <h2>Upgrade Plan</h2>

    <p>Your team is currently on the free AeroFS plan.
        For $10/team member/month, you will enjoy:</p>
    <ul>
        <li>Up to <strong>50</strong> team members</li>
        <li><strong>Unlimited</strong> external collaborators</li>
        <li>Priority email support</li>
    </ul>
    <p>
        <a class="btn btn-primary" href="#"
           onclick="upgrade(); return false;">Upgrade</a>
        <a class="btn btn-link" href="${request.route_path('pricing')}"
           target="_blank">Compare plans</a>
    </p>

    ## Note that this modal can be brought up by clicking the Upgrade button on this
    ## page, or by visiting request.route_path('start_subscription'). (Non-admins
    ## may ask admins to visit the latter page to upgrade the team's plan). The
    ## content of this modal should be catered for both cases.
    <%credit_card_modal:html>
        <%def name="title()">
            Upgrade your AeroFS plan
        </%def>
        <%def name="description()">
            <p>
                You are upgrading to a paid plan of $10/team member/month.<br>
                <a href="${request.route_path('pricing')}" target="_blank">More info on plans</a>.
            </p>

            <%credit_card_modal:default_description/>
        </%def>
        <%def name="okay_button_text()">
            <%credit_card_modal:default_okay_button_text/>
        </%def>
    </%credit_card_modal:html>
</%def>

<%block name="scripts">
    %if not has_customer_id:
        <%credit_card_modal:javascript/>
    %endif

    <script type="text/javascript">
        $(document).ready(function() {
            $('#organization_name').focus();

            %if not has_customer_id and upgrade:
                upgrade();
            %endif

            $("#update-name-form").submit(function() {
                $("#update-name-button").attr("disabled", "disabled");
                return true;
            });
        });

        function upgrade() {
            inputCreditCardInfoAndCreateStripeCustomer(function() {
                window.location.href = "${request.route_path('start_subscription_done')}";
            });
        }
    </script>
</%block>