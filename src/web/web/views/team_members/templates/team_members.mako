<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<%block name="css">
    <link href="${request.static_url('web:static/css/datatables_bootstrap.css')}"
          rel="stylesheet">
    <link href="${request.static_url('web:static/css/datatables.css')}"
          rel="stylesheet">
</%block>

<div class="page_block">
    <h2>Team Members</h2>
    <table id="users_table" class="table">
        ## thead is required by datatables
        <thead style="display: none;"><tr><th></th><th></th><th></th></tr></thead>
        <tbody></tbody>
    </table>

    <h2>Users Invited to Team</h2>
    <table class="table"><tbody id='invited_users_tbody'>
    </tbody></table>

    <form class="form-inline" id="invite_form" method="post">
        <input type="text" id="invite_user_email" placeHolder="Add email"/>
        <input id='invite_button' class="btn" type="submit" value="Send Invite"/>
    </form>
</div>

<%credit_card_modal:html>
    <%def name="title()">
        Please activate your AeroFS subscription
    </%def>
    <%def name="description()">
        <p>
            A subscription is required for a team with more than three members.
            We do our best to keep pricing simple &mdash; $10/user/month.
            That means that you'll pay $40/month for four users, $50/month
            for five users, and so on.
            <a href="https://www.aerofs.com/pricing" target="_blank">More info on pricing</a>.
        </p>

        <p>
            To proceed, please enter your payment method below. We will adjust your
            subscription automatically as you add or remove team members, so you
            never have to worry!
        </p>
    </%def>
    <%def name="okay_button_text()">
        Continue
    </%def>
</%credit_card_modal:html>

<%block name="scripts">
    <%credit_card_modal:javascript/>

    <script src="https://ajax.aspnetcdn.com/ajax/jquery.dataTables/1.8.2/jquery.dataTables.min.js"></script>
    <script src="${request.static_url('web:static/js/datatables_extensions.js')}"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            $('#users_table').dataTable({
                ## Features
                "bProcessing": true,
                "bServerSide": true,
                "bFilter": false,
                "bLengthChange": false,

                ## Parameters
                "sDom": "<'datatable_body't><'row'<'span1'r><'span7'pi>>",
                "sAjaxSource": "${request.route_path("json.get_users")}",
                "sPaginationType": "bootstrap",
                "iDisplayLength": 20,
                "oLanguage": {
                    ## TODO (WW) create a common function for this?
                    "sInfo": "_START_-_END_ of _TOTAL_"
                },
                "aoColumns": [
                    { "mDataProp": 'name' },
                    { "mDataProp": 'email' },
                    { "mDataProp": 'options' }
                ],

                ## Callbacks
                "fnServerData": function (sSource, aoData, fnCallback, oSettings) {
                    var fnCallbackWrapper = function(param) {
                        fnCallback(param);
                        ## Registers for tooltips _after_ data is fully loaded.
                        ## Otherwise registration would not take effect.
                        ## The coming_soon_link class is generated by views.py
                        $('.coming_soon_link').tooltip({placement: 'right'});
                    };
                    dataTableAJAXCallback(sSource, aoData, fnCallbackWrapper, oSettings);
                }
            });

            $('#invite_form').submit(function(e) {
                inviteUser();
                return false;
            });

            ## done and fail are callbacks for successful and failed cases. They
            ## can be None.
            function inviteUser(done, fail) {
                $('#invite_button').attr('disabled', 'disabled');
                var $email = $('#invite_user_email')
                var email = $email.val().trim();

                $.post("${request.route_path('json.invite_user')}", {
                    ${self.csrf.token_param()}
                    "${url_param_user}": email
                })
                .done(function(data) {
                    addInvitedUserRow(email);
                    $email.val('');
                    showSuccessMessage("The user has been invited.");
                    if (done) done();

                    mixpanel.track("Invited User to Team");
                })
                .fail(function (xhr) {
                    if (getErrorTypeNullable(xhr) == 'NO_STRIPE_CUSTOMER_ID') {
                        inputCreditCardInfo(newStripeCustomer);
                    } else {
                        showErrorMessageFromResponse(xhr);
                    }
                    if (fail) fail();
                })
                .always(function() {
                    ## Note: the button is enabled even if the payment dialog is
                    ## brought up (by inputCreditCardInfo() above).
                    $('#invite_button').removeAttr('disabled');
                });
            }

            ## This method follows the contract defined by inputCreditCardInfo()
            function newStripeCustomer(token, done, fail) {
                $.post("${request.route_path('json.new_stripe_customer')}", {
                    ${self.csrf.token_param()}
                    "${url_param_stripe_card_token}": token
                })
                .done(function() {
                    ## retry inviting after the Stripe customer ID is set
                    inviteUser(done, fail);
                })
                .fail(function(xhr) {
                    showErrorMessageFromResponse(xhr);
                    fail();
                });
            }

            $('.remove_invitation').live("click", function() {
                var $link = $(this);
                var email = $(this).data("user");
                $.post(
                    "${request.route_path('json.delete_team_invitation')}",
                    {
                        ${self.csrf.token_param()}
                        "${url_param_user}": email
                    }
                )
                .done(function() {
                    ## Remove the user row
                    $link.closest('tr').remove();
                    showSuccessMessage("The user has been removed.");
                })
                .fail(showErrorMessageFromResponse);

                return false;
            });

            function addInvitedUserRow(user_id) {
                var $remove = $('<a></a>')
                        .text('Remove')
                        .data('user', user_id)
                        .attr({
                            class: 'remove_invitation',
                            href: '#'
                        });

                $('#invited_users_tbody').append(
                        $('<tr></tr>').append(
                                $('<td></td>').text(user_id),
                                $('<td></td>').append($remove)
                        )
                );
            }

            ## Populate the invited user list
            %for user_id in invited_users:
                addInvitedUserRow("${user_id}");
            %endfor
        });
    </script>
</%block>
