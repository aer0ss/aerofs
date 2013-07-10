<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Team Settings" %>

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

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('#organization_name').focus();

            $("#update-name-form").submit(function() {
                $("#update-name-button").attr("disabled", "disabled");
                return true;
            });
        });
    </script>
</%block>
