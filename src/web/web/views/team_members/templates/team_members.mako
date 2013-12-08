<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Users" %>

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>
<%namespace name="modal" file="modal.mako"/>

<%block name="css">
    <link href="${request.static_path('web:static/css/datatables-bootstrap.css')}" rel="stylesheet">
</%block>

<div class="page-block">
    <h2>Users in my organization</h2>
    <table id="users_table" class="table table-hover">
        ## thead is required by datatables
        <thead style="display: none;"><tr><th></th><th></th><th></th><th></th></tr></thead>
        <tbody></tbody>
    </table>

    <h3>Invite people to organization</h3>
    <form class="form-inline" id="invite_form" method="post" onsubmit="inviteUser(); return false;">
        <input type="text" id="invite_user_email" placeHolder="Email address"/>
        <input id='invite_button' class="btn btn-primary" type="submit" value="Send Invite"/>
    </form>
    <table class="table"><tbody id='invited_users_tbody'></tbody></table>
</div>

<div id="remove_from_team_modal" class="modal hide">
    <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h4>Remove the user from your organization?</h4>
    </div>
    <div class="modal-body">
        <p>Are you sure that you want to remove <strong class="user_email"></strong> from your organization?</p>
        <p>This user will still have access to all folders currently shared with them, but their
            data will no longer be backed up on the AeroFS Team Server.</p>
        <p>If you would like to unlink or erase this user's devices, you may do so by visiting the
            <a href="#" class="device_link">devices</a> page before removing the user from your organization.</p>
    </div>
    <div class="modal-footer">
        <a href="#" class="btn" data-dismiss="modal" aria-hidden="true">Cancel</a>
        <a href="#" id="confirm_remove_user" class="btn btn-danger">Remove User From Team</a>
    </div>
</div>

<%modal:modal>
    <%def name="id()">deactivate_modal</%def>
    <%def name="title()">Delete user</%def>
    <%def name="footer()">
        <a href="#" class="btn" data-dismiss="modal">Cancel</a>
        <a href="#" id="confirm_deactivate" class="btn btn-danger">Delete User</a>
    </%def>

    <p>Are you sure that you want to delete <strong id="deactivate_email"></strong>?
        This user will no longer be able to sign in to AeroFS, and
        all the AeroFS clients owned by this user will be automatically unlinked.</p>
    <p>
        <label class="checkbox">
            <input type="checkbox" id="erase_devices">
            Also erase AeroFS files from the user's devices.
        </label>
    </p>
</%modal:modal>

<%credit_card_modal:html>
    <%def name="title()">
        <%credit_card_modal:default_title/>
    </%def>
    <%def name="description()">
        <p>
            The free plan allows <strong>three</strong> users. If you'd
            like to add additional users, please upgrade to the paid plan
            ($10/user/month).
            <a href="${request.route_path('pricing')}" target="_blank">Compare plans</a>.
        </p>

        <%credit_card_modal:default_description/>
    </%def>
    <%def name="okay_button_text()">
        <%credit_card_modal:default_okay_button_text/>
    </%def>
</%credit_card_modal:html>

<%block name="scripts">
    <%credit_card_modal:javascript/>

    <script src="${request.static_path('web:static/js/jquery.dataTables.min.js')}"></script>
    <script src="${request.static_path('web:static/js/datatables_extensions.js')}"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            $('#invite_user_email').focus();

            $('#users_table').dataTable({
                ## Features
                "bProcessing": true,
                "bServerSide": true,
                "bFilter": false,
                "bLengthChange": false,

                ## Parameters
                "sDom": "<'datatable_body't><'row'<'span1'r><'span7'pi>>",
                "sAjaxSource": "${request.route_path("json.list_team_members")}",
                "sPaginationType": "bootstrap",
                "iDisplayLength": 20,
                "oLanguage": {
                    ## TODO (WW) create a common function for this?
                    "sInfo": "_START_-_END_ of _TOTAL_"
                },
                "aoColumns": [
                    { "mDataProp": 'name', "sClass": "full_name" },
                    { "mDataProp": 'label' },
                    { "mDataProp": 'email' },
                    { "mDataProp": 'options' }
                ],

                ## Callbacks
                "fnServerData": function (sSource, aoData, fnCallback, oSettings) {
                    var fnCallbackWrapper = function(param) {
                        fnCallback(param);
                        ## Registers for tooltips _after_ data is fully loaded.
                        ## Otherwise registration would not take effect.
                        registerUserRowTooltips();
                    };
                    dataTableAJAXCallback(sSource, aoData, fnCallbackWrapper, oSettings);
                }
            });

            ## Populate the invited user list
            %for user_id in invited_users:
                addInvitedUserRow("${user_id}");
            %endfor
        });

        function registerUserRowTooltips() {
            $('.tooltip_admin').tooltip({placement: 'top', 'title' : 'An admin has access to ' +
                    'administrative functions for your organization: provision Team Servers, manage users and shared ' +
                    'folders, add/remove other admins, manage payment, and so on.'});
        }

        ## done and always are callbacks for AJAX success and completion. They can be undefined.
        function inviteUser(done, always) {
            var inviteButton = $('#invite_button');
            inviteButton.attr('disabled', 'disabled');

            ## Since IE doesn't support String.trim(), use $.trim()
            var email = $.trim($('#invite_user_email').val());

            $.post("${request.route_path('json.invite_user')}", {
                ${self.csrf.token_param()}
                "${url_param_user}": email
            }).done(function(data) {
                showSuccessMessage("The invitation has been sent.");
                ## List the user as a pending invitation only if the user is
                ## locally managed. Since externally managed users can always
                ## sign up on their own, listing and being able to remove them
                ## from the invitation list doesn't make sense.
                ## See also SPService.listOrganizationInvitedUsers()
                if (data['locally_managed']) addInvitedUserRow(email);
                $('#invite_user_email').val('');
                if (done) done();

            }).fail(function (xhr) {
                if (getErrorTypeNullable(xhr) == 'NO_STRIPE_CUSTOMER_ID') {
                    inputCreditCardInfoAndCreateStripeCustomer(inviteUser);
                } else {
                    showErrorMessageFromResponse(xhr);
                }
            }).always(function() {
                inviteButton.removeAttr('disabled');
                if (always) always();
            });
        }

        function addInvitedUserRow(user_id) {
            var remove_link = $('<a>', {
                    'text': 'Remove Invitation',
                    'href': '#',
                    'click': function() {removeInvitation(user_id, $(this)); return false;}
            });

            $('#invited_users_tbody').append(
                    $('<tr></tr>').append(
                            $('<td></td>').text(user_id),
                            $('<td></td>').append(remove_link)
                    )
            );
        }

        function removeInvitation(user, link) {
            $.post("${request.route_path('json.delete_team_invitation')}", {
                    ${self.csrf.token_param()}
                    "${url_param_user}": user
                }
            )
            .done(function() {
                showSuccessMessage("The invitation has been removed.");
                link.closest('tr').remove();
            })
            .fail(showErrorMessageFromResponse);
        }

        ## Toggles an user between ADMIN and USER level
        ##
        ## user: the user id
        ## becomeAdmin: true to make the user an admin, false to make it a regular user
        ## newLinkText: the new text that should be displayed in the link if the change was successful
        ## link: the jquery object of the calling link
        function toggleAdmin(user, becomeAdmin, newLinkText, $link) {
            $.post("${request.route_path('json.set_level')}", {
                    ${self.csrf.token_param()}
                    "${url_param_user}": user,
                    "${url_param_level}": becomeAdmin ? ${admin_level} : ${user_level}
                }
            )
            .done(function() {
                showSuccessMessage(becomeAdmin ? "The user is now an admin." : "The user is no longer an admin.");

                ## toggle the 'admin' label in the user row
                $link.closest('tr').find('.admin_label').toggleClass('hidden', !becomeAdmin);

                ## replace the onclick handler of the link to do the opposite operation now
                ## note: trust me, doing link[0].onclick is the best way to do it. Do not use .click() or .on('click'),
                ## as this will cause the dropdown menu to stay open (as well as other problems)
                $link[0].onclick = function() { toggleAdmin(user, !becomeAdmin, $link.html(), $link); return false; };

                ## update the link text
                $link.html(newLinkText);
            })
            .fail(showErrorMessageFromResponse);
        }

        function removeFromTeam(user, viewDevicesUrl, $link) {
            var modal = $("#remove_from_team_modal");
            modal.find(".device_link").prop("href", viewDevicesUrl);
            modal.find(".user_email").text(user);
            modal.find("#confirm_remove_user").off().on('click', function() {
                modal.modal('hide');
                $.post("${request.route_path('json.remove_from_team')}", {
                        ${self.csrf.token_param()}
                        "${url_param_user}": user
                    }
                )
                .done(function() {
                    showSuccessMessage("The user " + user + " has been removed from your organization.");
                    $link.closest('tr').remove();
                })
                .fail(showErrorMessageFromResponse);
            });

            modal.modal();
        }

        function deactivate(user, $link) {
            var modal = $("#deactivate_modal");
            modal.find("#deactivate_email").text(user);
            modal.find("#confirm_deactivate").off().on('click', function() {
                modal.modal('hide');
                $.post("${request.route_path('json.deactivate_user')}", {
                        ${self.csrf.token_param()}
                        "${url_param_user}": user,
                        "${url_param_erase_devices}": $('#erase_devices').is(':checked')
                    }
                )
                .done(function() {
                    showSuccessMessage("The user " + user + " has been deleted.");
                    $link.closest('tr').remove();
                })
                .fail(showErrorMessageFromResponse);
            });

            modal.modal('show');
        }
    </script>
</%block>
