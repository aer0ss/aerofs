<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

<%block name="css">
    <link href="${request.static_url('web:static/css/datatables_bootstrap.css')}"
          rel="stylesheet">
</%block>

<div class="page_block">
    <h2>Team Name</h2>
    ## TODO (WW) use form-horizontal when adding new fields. see login.mako
    <form class="form-inline" action="${request.route_path('team_settings')}" method="post">
    <div class="input_container">
        ${self.csrf_token_input()}
        <input id="organization_name" type="text" name="organization_name" value="${organization_name}"/>
        <input class="btn" type="submit" name="form.submitted" value="Update" />
    </div>
</form>
</div>

<div class="row page_block">
    <div class="span6">
        <h2>Team Administrators</h2>
        <table id="admins_table" class="table">
            ## thead is required by datatables
            <thead style="display: none;"><tr><th></th><th></th><th></th></tr></thead>
            <tbody></tbody>
        </table>
        <form class="form-inline" id="add_admin_form" method="post">
            <input id="add-admin-input" type="text" placeholder="Member Email">
            <input class="btn" type="submit" value="Add Admin"/>
        </form>
    </div>
</div>

<div class="page_block">
    <h2>Subscription and Billing</h2>
    <p><a href="${request.route_path('manage_subscription')}">Manage Subscription</a></p>
    <p><a href="${request.route_path('manage_credit_card')}">Manage Credit Card</a></p>
    <p><a href="${request.route_path('payments')}">View Billing History</a></p>
</div>

<%block name="scripts">
    <script src="https://ajax.aspnetcdn.com/ajax/jquery.dataTables/1.8.2/jquery.dataTables.min.js"></script>
    <script src="${request.static_url('web:static/js/datatables_extensions.js')}"></script>

    <script type="text/javascript">
        $(document).ready(function() {

        ## Get all the admins and display them on the data table
            $("#admins_table").dataTable({
            ## Features
                "bProcessing": true,
                "bServerSide": true,
                "bFilter": false,
                "bPaginate": false,

            ## Parameters
                "sDom": "<'datatable_body't>r",
                "sAjaxSource": "${request.route_path('json.get_admins')}",
                "aoColumns": [
                    { "mDataProp": "name" },
                    { "mDataProp": "email" },
                    { "mDataProp": "action" }
                ],

            ## Callbacks
                "fnServerData": dataTableAJAXCallback
            });

        ## Typeahead search for adding an admin
            $("#add-admin-input").typeahead({"items": 4}).on("keyup", function(e) {
                e.stopPropagation();
                e.preventDefault();

                var UP_KEY = 40, DOWN_KEY = 38,
                        TAB_KEY = 9, ENTER_KEY = 13,
                        ESCAPE_KEY = 27,
                        KEY_LIST = [UP_KEY, DOWN_KEY, TAB_KEY, ENTER_KEY, ESCAPE_KEY];

                if ($.inArray(e.keyCode, KEY_LIST) != -1) {
                    return;
                }

                var $addAdminInput = $("#add-admin-input");
                var st = $addAdminInput.val();
                $addAdminInput.data('typeahead').source = [];

                if ((!$addAdminInput.data('active')) && (st.length > 0)) {
                    $addAdminInput.data('active', true);
                    $.get("${request.route_path('json.user_lookup')}",
                            {
                                'searchTerm': st,
                                'authLevel': "USER",
                                'count': 4,
                                'offset': 0
                            }
                    )
                            .done(function(output) {
                                var users = output.users;
                                $addAdminInput.data('active', true);
                                $addAdminInput.data('typeahead').source = users;
                                $addAdminInput.trigger('keyup');
                                $addAdminInput.data('active',false);
                            })
                            .fail(function(jqXHR, textStatus, errorThrown) {
                                showErrorMessage("Couldn't show admins. Please try again.");
                                console.log(textStatus + ": " + errorThrown);
                            });
                }
            });

        ## Adding an admin
            $("#add_admin_form").submit(function(e) {
                var userid = $("#add-admin-input").val(),
                        authlevel = "ADMIN";
                $.post("${request.route_path('json.set_authorization')}",
                        {
                        ${self.csrf_token_param()}
                            userid: userid,
                            authlevel: authlevel
                        }
                )
                        .done(function(output) {
                            if (output.success) {
                                showSuccessMessage(output.response_message);
                                $('#admins_table').dataTable().fnDraw(false);
                            } else {
                                showErrorMessage(output.response_message);
                            }
                            $("#add-admin-input").val("");
                        })
                        .fail(function(jqXHR, textStatus, errorThrown) {
                            showErrorMessage("Couldn't add admin. Please try again.");
                            console.log(textStatus + ": " + errorThrown);
                        });

                e.preventDefault();
            });

        ## Remove an admin from the table
            $("#admins_table").on("click", "a.remove-admin-btn", function() {
                var userid = $(this).data('email'),
                        authlevel = "USER";
                $.post("${request.route_path('json.set_authorization')}",
                        {
                        ${self.csrf_token_param()}
                            userid: userid,
                            authlevel: authlevel
                        }
                )
                        .done(function(output) {
                            if (output.success) {
                                showSuccessMessage(output.response_message);
                                $('#admins_table').dataTable().fnDraw(false);
                            } else {
                                showErrorMessage(output.response_message);
                            }
                        })
                        .fail(function(jqXHR, textStatus, errorThrown) {
                            showErrorMessage("Couldn't remove admins. Please try again.");
                            console.log(textStatus + ": " + errorThrown);
                        });
            });
        });
    </script>
</%block>