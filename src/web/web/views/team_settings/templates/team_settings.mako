<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<%block name="css">
    <link href="${request.static_url('web:static/css/datatables_bootstrap.css')}"
          rel="stylesheet">
</%block>

<div class="page_block">
    <h2>Team Settings</h2>
</div>

<div class="page_block">
    ## TODO (WW) use form-horizontal when adding new fields. see login.mako
    Change team name:
    <form class="form-inline" action="${request.route_path('team_settings')}" method="post">
        <div class="input_container">
            ${self.csrf.token_input()}
            <input id="organization_name" type="text" name="organization_name" value="${organization_name}"/>
            <input class="btn" type="submit" name="form.submitted" value="Update" />
        </div>
    </form>
</div>

<div class="page_block">
    %if show_billing:
        <p><a href="${request.route_path('manage_payment')}">Manage subscription and payment</a></p>
    %endif
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('#organization_name').focus();
        });
    </script>
</%block>