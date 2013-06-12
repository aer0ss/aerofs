<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Shared Folders" %>

<%namespace name="shared_folder_modals" file="shared_folder_modals.mako" />

<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>

<%block name="css">
    <link href="${request.static_path('web:static/css/datatables-bootstrap.css')}"
          rel="stylesheet">
    <style type="text/css">
        #modal .modal-body {
            ## Set a min-height to accommendate the dropdown menu when there are
            ## very few user rows in the dialog.
            min-height: 200px;
        }
        #modal .dropdown-menu {
            ## Override bootstrap's min-width by not specifying a min-width.
            min-width: inherit;
        }
        #modal .modal-footer .form-inline {
            margin-bottom: 0;
        }
        ## this class is generated by spin.js. see the spinnerOpts variable below.
        .spinner {
            display: inline;
        }
        #modal-spinner {
            margin-right: 20px;
            color: Green;
        }
    </style>
</%block>

<div class="row page_block">
    <div class="span8">
        <h2>${page_title}</h2>
        <div class="well well-small footnote">
            This is a list of shared folder names only.  To see the actual
            content of your folders and files, please go to the AeroFS
            application.
        </div>

        <table id="folders_table" class="table">
            <thead><tr><th>Folder</th><th></th><th>Members</th><th></th></tr></thead>
            <tbody></tbody>
        </table>
    </div>
</div>

<%shared_folder_modals:main_modals/>

%if is_admin:
    ## Admins can input credit card on their own
    <%credit_card_modal:html>
        <%def name="title()">
            <%credit_card_modal:default_title/>
        </%def>
        <%def name="description()">
            <p>
                ## Note: the following text should be consistent with the text
                ## in CompInviteUsers.java.
                The free plan allows <strong>one</strong> external collaborator
                per shared folder. If you'd like to invite unlimited external
                collaborators, please upgrade to the paid plan
                ($10/team member/month).
                <a href="${request.route_path('pricing')}" target="_blank">Compare plans</a>.
            </p>

            <%credit_card_modal:default_description/>
        </%def>
        <%def name="okay_button_text()">
            <%credit_card_modal:default_okay_button_text/>
        </%def>
    </%credit_card_modal:html>

%else:
    ## Non-admins must admins to input credit card
    <%shared_folder_modals:ask_admin_modal/>
%endif

<%block name="scripts">

    ## Only admins can input credit card
    %if is_admin:
        <%credit_card_modal:javascript/>
    %endif

    <script src="${request.static_path('web:static/js/jquery.dataTables.min.js')}"></script>
    <script src="${request.static_path('web:static/js/datatables_extensions.js')}"></script>
    <script src="${request.static_path('web:static/js/spin.min.js')}"></script>
    <script type="text/javascript">
        $(document).ready(function() {
            $('#folders_table').dataTable({
                ## Features
                "bProcessing": true,
                "bServerSide": true,
                "bFilter": false,
                "bLengthChange": false,

                ## Parameters
                %if datatables_paginate:
                    "sDom": "<'datatable_body't>pir",
                %else:
                    "sDom": "<'datatable_body't>r",
                    "bPaginate": false,
                %endif
                "sAjaxSource": "${datatables_request_route_url}",
                "sPaginationType": "bootstrap",
                "iDisplayLength": 20,
                "oLanguage": {
                    ## TODO (WW) create a common function for this?
                    "sInfo": "_START_-_END_ of _TOTAL_",
                    "sInfoEmpty": "",
                    "sEmptyTable": "No shared folders"
                },
                "aoColumns": [
                    { "mDataProp": "name" },
                    { "mDataProp": "label" },
                    { "mDataProp": "users" },
                    { "mDataProp": "options" }
                ],

                ## Callbacks
                "fnServerData": function(sUrl, aoData, fnCallback, oSettings) {
                    var cb = function(json) {
                        fnCallback(json);
                        registerOwnedByMeTooltips();
                        registerOwnedByTeamTooltips();
                    }
                    dataTableAJAXCallback(sUrl, aoData, cb, oSettings);
                }
            });

            function refreshTable() {
                $('#folders_table').dataTable().fnDraw(false);
            }

            ## The Options link that opens the modal. It holds all the data
            ## required by the modal.
            var $link;
            var $modal = $('#modal');

            $('.${open_modal_class}').live('click', function () {
                $link = $(this);
                refreshModal();
                $modal.modal('show');
            });

            ## N.B. updates to the return value will be propagated back to the
            ## Options link and persists throughout the link's lifecycle.
            function modalUserAndRoleList() {
                return $link.data('${link_data_user_and_role_list}');
            }

            function modalSID() {
                return $link.data('${link_data_sid}');
            }

            function modalPrivileged() {
                return $link.data('${link_data_privileged}');
            }

            function modalFolderName() {
                return $link.data('${link_data_name}');
            }

            ########
            ## Functions for the Modal

            ## Refresh the modal based using the current values in $link
            function refreshModal() {
                ## Update the folder name
                $('#modal-folder-name').text(modalFolderName());

                ## Clear the user-role table
                var $table = $('#modal-user-role-table');
                $table.find('tbody').find('tr').remove();

                var privileged = modalPrivileged();

                ## Enable or disable the invitation form depending on the privilege
                var $inviteUserInputs = $("#invite-user-inputs");
                if (privileged) $inviteUserInputs.removeClass("hidden");
                else $inviteUserInputs.addClass("hidden");

                ## Populate the table
                var urs = modalUserAndRoleList();
                for (var email in urs) {
                    var ur = urs[email];
                    var isOwner = ur.${user_and_role_is_owner_key};
                    var name = ur.${user_and_role_first_name_key} + " " +
                            ur.${user_and_role_last_name_key};
                    ## label text and style must match the labels generated in
                    ## shared_folders_view.py:_session_user_labeler
                    var label = isOwner ? '<span data-toggle="tooltip"' +
                            ' class="label tooltip_owner">owner</label>' : "";

                    var options;
                    if (email === '${session_user}') {
                        ## Show no options for the session user
                        options = '';
                    } else {
                        options = privileged ?
                            renderPrivilegedUserOptions(email, name, isOwner) :
                            renderUnprivilegedUserOptions(email);
                    }

                    $table.find('> tbody:last').append(
                        ## TODO (WW) use proper jQuery methods to add elements
                        '<tr>' +
                            '<td>' +
                                '<span data-toggle="tooltip" class="tooltip_email" title="' + email + '">' +
                                    name +
                                '</span>' + label + '</td>' +
                            '<td>' + options + '</td>' +
                        '</tr>'
                    );
                }

                activateModelTableElements();
            }

            function renderUnprivilegedUserOptions(email) {
                return renderOptionsDropDown(renderSendEmailMenuItem(email));
            }

            function renderPrivilegedUserOptions(email, fullName, isOwner) {
                var common = ' href="#" data-email="' + email + '" ';
                var toggleRole = isOwner?
                    '<a' + common + 'class="modal-make-editor">Remove as Owner</a>' :
                    '<a' + common + 'class="modal-make-owner">Make Owner</a>';

                return renderOptionsDropDown(
                    '<li>' + toggleRole + '</li>' +
                    renderSendEmailMenuItem(email) +
                    '<li class="divider"></li>' +
                    '<li><a' + common + 'data-full-name="' + fullName +
                        '" class="modal-remove">Remove</a></li>'
                );
            }

            function renderOptionsDropDown(menuItems) {
                return '<div class="dropdown">' +
                    '<a class="dropdown-toggle pull-right" ' +
                            'data-toggle="dropdown" href="#">Options</a>' +
                    '<ul class="dropdown-menu pull-right" role="menu">' +
                        menuItems +
                    '</ul>' +
                '</div>';
            }

            function renderSendEmailMenuItem(email) {
                return '<li><a href="mailto:' + email + '" target="_blank">Send Email</a></li>';
            }

            function registerOwnedByMeTooltips() {
                $('.tooltip_owned_by_me').tooltip({'title' :
                        'You are an owner of this folder. You can add and' +
                        ' remove other members and owners of the folder.'});
            }

            function registerOwnedByTeamTooltips() {
                $('.tooltip_owned_by_team').tooltip({'title' :
                        'This folder is owned by at least one member of your' +
                        ' team. As a team admin, you can' +
                        ' manage this folder as if your are a folder owner.'});
            }

            function registerOwnerTooltips() {
                $('.tooltip_owner').tooltip({
                    ## To avoid tooltips being cut off by the modal boundary.
                    ## See https://github.com/twitter/bootstrap/pull/6378
                    container: '#modal',
                    'title' : 'An owner of a folder can add and remove other' +
                        ' members and owners of the folder.'
                });
            }

            function regiterEmailTooltips() {
                $('.tooltip_email').tooltip({
                    ## To avoid tooltips being cut off by the modal boundary.
                    ## See https://github.com/twitter/bootstrap/pull/6378
                    container: '#modal'
                });
            }

            ## Set event handlers and activities for elements in the table
            function activateModelTableElements() {
                regiterEmailTooltips();
                registerOwnerTooltips();

                $('.modal-make-editor').click(function() {
                    var email = $(this).data('email');
                    setRole(email, 'EDITOR', function() {
                        modalUserAndRoleList()[email]
                                .${user_and_role_is_owner_key} = 0;
                    });
                });

                $('.modal-make-owner').click(function() {
                    var email = $(this).data('email');
                    setRole(email, 'OWNER', function() {
                        modalUserAndRoleList()[email]
                                .${user_and_role_is_owner_key} = 1;
                    });
                });

                $('.modal-remove').click(function() {
                    confirmRemoveUser($(this).data('email'), $(this).data('full-name'));
                });
            }

            $modal.on('shown', function() {
                $("#modal-invitee-email").focus();
            });

            $modal.on('hidden', function() {
                ## Stop spinner
                resetModalSpinner();
                ## Remove previous invited email
                $("#modal-invitee-email").val('');
            });

            ## @param dataUpdater the function that updates HTML data on
            ## successful RPC calls
            function setRole(email, role, dataUpdater) {
                startModalSpinner();

                var errorHeader = "Couldn't change role: ";
                $.post(
                    "${request.route_path('json.set_shared_folder_perm')}",
                    {
                        ${self.csrf.token_param()}
                        ## TODO (WW) use variables to abstract parameter key strings
                        "userid": email,
                        "storeid": modalSID(),
                        "perm": role
                    }
                )
                .done(function(response) {
                    if (handleAjaxReply(response, errorHeader)) {
                        dataUpdater();
                        refreshModal();
                    }
                })
                .fail(function (jqXHR, textStatus, errorThrown) {
                    displayModalError(errorHeader, errorThrown);
                })
                .always(function() {
                    stopModalSpinner();
                });
            }

            function confirmRemoveUser(email, fullName) {

                $('.remove-modal-full-name').text(fullName);
                $('.remove-modal-email').text(email);

                $modal.modal('hide');
                $('#remove-modal').modal('show');

                var $confirm = $('#remove-model-confirm');
                $confirm.off('click');
                $confirm.click(function() {
                    ## the 'hidden' handler below brings up the main modal once
                    ## the remove modal is hidden
                    $('#remove-modal').modal('hide');
                    startModalSpinner();

                    var errorHeader = "Couldn't remove: ";
                    $.post("${request.route_path('json.delete_shared_folder_perm')}",
                        {
                            ${self.csrf.token_param()}
                            ## TODO (WW) use variables to abstract parameter key strings
                            userid: email,
                            storeid: modalSID()
                        }
                    )
                    .done(function(response) {
                        if (handleAjaxReply(response, errorHeader)) {
                        ## Update modal data
                        delete modalUserAndRoleList()[email];
                            refreshModal();
                        }
                    })
                    .fail(function (jqXHR, textStatus, errorThrown) {
                        displayModalError(errorHeader, errorThrown);
                    })
                    .always(function() {
                        stopModalSpinner();
                    });
                });
            }

            $('#remove-modal').on('hidden', function() {
                $modal.modal('show');
            })

            $('#modal-invite-form').submit(function(ev) {
                ## Since IE doesn't support String.trim(), use $.trim()
                var email = $.trim($('#modal-invitee-email').val());
                inviteToFolder(email);
                return false;
            });

            ## done and always are callbacks for AJAX success and completion.
            ## They can be None.
            ## Pass the email as a parameter rather than the method extracting
            ## from the input field on its own, since paymentRequiredToInvite()
            ## hides the main modal before calling this method, which causes the
            ## field to be cleaned up.
            function inviteToFolder(email, done, always) {
                startModalSpinner();
                var sid = modalSID();
                var name = modalFolderName();

                var errorHeader = "Couldn't invite: ";
                $.post("${request.route_path('json.add_shared_folder_perm')}", {
                        ${self.csrf.token_param()}
                        "user_id": email,
                        "store_id": sid,
                        "folder_name": name
                    }
                ).done(function(response) {
                    showSuccessMessage('Invitation has been sent.');
                    $('#modal-invitee-email').val('');
                    if (done) done();
                }).fail(function (xhr) {
                    if (getErrorTypeNullable(xhr) == "NO_STRIPE_CUSTOMER_ID") {
                        paymentRequiredToInvite(email);
                    } else {
                        showErrorMessageFromResponse(xhr);
                    }
                }).always(function() {
                    if (always) always();
                    stopModalSpinner();
                });
            }

            ## Restore the main modal once sub-modals are hidden.
            %if is_admin:
                getCreditCardModal()
            %else:
                $("#ask-admin-modal")
            %endif
                .on("hidden", function() {
                    $modal.modal("show");
                });

            function paymentRequiredToInvite(email) {
                $modal.modal("hide");
                %if is_admin:
                    inputCreditCardInfoAndCreateStripeCustomer(function(done, always) {
                        inviteToFolder(email, done, always);
                    });
                %else:
                    $("#ask-admin-modal").modal("show");
                %endif
            }

            function handleAjaxReply(response, errorHeader) {
                ## Expects two parameters in server response, 'success' and
                ## 'response_message'
                if (response.success) {
                    ## Refresh the shared folder table behind the modal.
                    refreshTable();
                    return true;
                } else {
                    displayModalError(errorHeader, response.response_message);
                    return false;
                }
            }

            function resetModalSpinner() {
                var spin = $('#modal-spinner');
                if (spin.data().spinner) spin.data().spinner.stop();
            }

            function startModalSpinner() {
                $('#modal-spinner').spin(spinnerOpts);
            }

            function stopModalSpinner() {
                $('#modal-spinner').data().spinner.stop();
            }

            function displayModalError(errorHeader, error) {
                showErrorMessage(errorHeader + error + ".");
            }

            ## spin.js jQuery plugin copied from http://fgnass.github.com/spin.js/
            $.fn.spin = function(opts) {
                this.each(function() {
                    var $this = $(this), data = $this.data();

                    if (data.spinner) {
                        data.spinner.stop();
                        delete data.spinner;
                    }
                    if (opts !== false) {
                        data.spinner = new Spinner($.extend({color: $this.css('color')}, opts)).spin(this);
                    }
                });
                return this;
            };

            var spinnerOpts = {
                lines: 11,
                length: 5,
                width: 1.6,
                radius: 4,
                corners: 1,
                rotate: 0,
                color: '#000',
                speed: 1,
                trail: 60,
                shadow: false,
                hwaccel: true,
                className: 'spinner',
                zIndex: 2e9,
                top: 3,
                left: -155
            };
        });
    </script>
</%block>
