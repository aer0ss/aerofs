<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Shared Folders" %>

<%namespace name="shared_folder_modals" file="shared_folder_modals.mako" />
<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>
<%namespace name="spinner" file="spinner.mako"/>

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
    </style>
</%block>

<div class="row page_block">
    <div class="span8">
        <h2>${page_heading}</h2>
        <div class="well well-small footnote">
            This is a list of shared folder names only.  To see the actual
            content of your folders and files, please go to the AeroFS
            application.
        </div>

        <table id="folders_table" class="table table-hover">
            <thead><tr><th>Folder</th><th>Members</th><th></th></tr></thead>
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
    <%spinner:scripts/>
    <script src="${request.static_path('web:static/js/jquery.dataTables.min.js')}"></script>
    <script src="${request.static_path('web:static/js/datatables_extensions.js')}"></script>

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
                    { "mDataProp": "users" },
                    { "mDataProp": "options" }
                ],

                ## Callbacks
                "fnServerData": function(sUrl, aoData, fnCallback, oSettings) {
                    var cb = function(json) {
                        fnCallback(json);
                    }
                    dataTableAJAXCallback(sUrl, aoData, cb, oSettings);
                },
                "fnDrawCallback": function() {
                    ## A nasty hack to remove fixed column widths automatically
                    ## generated by dataTables. These widths make the table
                    ## looks horrible. dataTables is no good.
                    $('#folders_table').find('th').css("width", "");
                }
            });

            function refreshTable() {
                $('#folders_table').dataTable().fnDraw(false);
            }

            ## The Options link that opens the modal. It holds all the data
            ## required by the modal.
            var $link;
            var $mainModal = $('#modal');

            $('.${open_modal_class}').live('click', function () {
                $link = $(this);
                refreshModal();
                $mainModal.modal('show');
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

            function formatRoleMenuText(role, desc) {
                return '<div style="display: inline-block; width: 65px">' + role +
                        '</div>' + desc;
            }
            ## A map of numeric role id => [ <role name>, <role menu text> ]
            ## Note that the menu item description contains HTML
            var role2str = {
                "${owner_role}":  [ "Owner",  formatRoleMenuText("Owner", "download, upload, and manage")],
                "${editor_role}": [ "Editor", formatRoleMenuText("Editor", "download and upload")],
                "${viewer_role}": [ "Viewer", formatRoleMenuText("Viewer", "download only")]
            };

            var roleDisplayOrder = ["${owner_role}", "${editor_role}", "${viewer_role}"]

            ## Refresh the modal based using the current values in $link
            function refreshModal() {
                var privileged = modalPrivileged();

                ## Set the modal's title
                $('#modal-folder-title').text(
                        (privileged ? "Manage" : "View members of") +
                        " \"" + modalFolderName() + "\"");

                ## Set the modal title's tooltip. Reset it first otherwise it
                ## wouldn't be updated on subsequent calls.
                $('#modal-folder-title-info-icon').tooltip('destroy').tooltip({
                    ## '| n' to avoid escaping "'"s in the strings
                    title: privileged ?
                            "${privileged_modal_tooltip | n}" :
                            "${unprivileged_modal_tooltip | n}",
                    ## To avoid tooltips being cut off by the modal boundary.
                    ## See https://github.com/twitter/bootstrap/pull/6378
                    container: '#modal',
                    placement: 'bottom'
                });

                ## Clear the user-role table
                var $table = $('#modal-user-role-table');
                $table.find('tbody').find('tr').remove();

                ## Enable or disable the invitation form depending on the privilege
                var $inviteUserInputs = $("#invite-user-inputs");
                if (privileged) $inviteUserInputs.removeClass("hidden");
                else $inviteUserInputs.addClass("hidden");

                ## Populate the table
                var urs = modalUserAndRoleList();
                for (var email in urs) {
                    var ur = urs[email];
                    var role = ur.${user_and_role_role_key};
                    var name = ur.${user_and_role_first_name_key} + " " +
                            ur.${user_and_role_last_name_key};

                    var actions, roleStr;
                    if (email === '${session_user}') {
                        roleStr = role2str[role][0];
                        actions = '';
                    } else if (privileged) {
                        roleStr = renderActionableRole(email, role);
                        actions = renderPrivilegedUserActions(email, name);
                    } else {
                        roleStr = role2str[role][0];
                        actions = renderUnprivilegedUserActions(email);
                    }

                    $table.find('> tbody:last').append(
                        ## TODO (WW) use proper jQuery methods to add elements
                        '<tr>' +
                            '<td><span data-toggle="tooltip" class="tooltip_email" title="' + email + '">' +
                               name +
                            '</span></td>' +
                            '<td>' + roleStr + '</td>' +
                            '<td>' + actions + '</td>' +
                        '</tr>'
                    );
                }

                activateModelTableElements();
            }

            function initRoleMenuForInviteForm() {
                var $menu = $('#modal-invite-role-menu');
                var $id = $('#modal-invite-role');
                var $label = $('#modal-invite-role-label');

                ## Set the default Editor role
                setRole("${editor_role}");

                ## Populate the role menu
                $.each(roleDisplayOrder, function(idx, role) {
                    var $a = $('<a></a>').html(role2str[role][1]).attr("href", "#")
                            .on("click", function() { setRole(role); });
                    $menu.append($('<li></li>').append($a));
                });

                function setRole(role) {
                    $id.attr("value", role);
                    $label.text(role2str[role][0]);
                }
            }
            initRoleMenuForInviteForm();

            function renderActionableRole(email, currentRole) {
                var itemsStr = '';
                $.each(roleDisplayOrder, function(idx, role) {
                    itemsStr += '<li><a href="#" class="model-set-role" ' +
                            'data-email="' + email + '" data-role="' + role + '">' +
                            '<span' +
                            (currentRole == role ? '' : ' class="invisible"') +
                            '>&#x2713;&nbsp;</span>' + role2str[role][1] + '</a></li>';
                });

                return '<div class="dropdown">' +
                        ## pull-left so that the dropdown menu is vertically aligned with the
                        ## "Actions" dropdown menu.
                        '<a class="dropdown-toggle pull-left" data-toggle="dropdown" href="#">' +
                            role2str[currentRole][0] + '&nbsp;&#x25BE;' +
                        '</a>' +
                        '<ul class="dropdown-menu" role="menu">' +
                            itemsStr +
                        '</ul>' +
                    '</div>';
            }

            function renderUnprivilegedUserActions(email) {
                return '<span class="pull-right">' + renderSendEmailLink(email) + '</span>';
            }

            function renderPrivilegedUserActions(email, fullName) {
                return '<div class="dropdown">' +
                    '<a class="dropdown-toggle pull-right" ' +
                            'data-toggle="dropdown" href="#">Actions&nbsp;&#x25BE;</a>' +
                    ## pull-right otherwise the dropdown menu wouldn't be aligned
                    ## with the right if the dialog is wide.
                    '<ul class="dropdown-menu pull-right" role="menu">' +
                        '<li>' + renderSendEmailLink(email) + '</li>' +
                        '<li><a href="#" data-email="' + email + '" data-full-name="' + fullName +
                            '" class="modal-remove">Remove</a></li>' +
                    '</ul>' +
                '</div>';
            }

            function renderSendEmailLink(email) {
                return '<a href="mailto:' + email + '" target="_blank">Send Email</a>';
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

                $('.model-set-role').click(function() {
                    setRole($(this).data('email'), $(this).data('role'), false);
                });

                $('.modal-remove').click(function() {
                    confirmRemoveUser($(this).data('email'), $(this).data('full-name'));
                });
            }

            $mainModal.on('shown', function() {
                $("#modal-invitee-email").focus();
            });

            $mainModal.on('hidden', function() {
                stopModalSpinner();
                ## Remove previous invited email
                $("#modal-invitee-email").val('');
            });

            ## @param suppressSharedFolderRulesWarnings see sp.proto:updateACL()
            function setRole(email, role, suppressSharedFolderRulesWarnings) {
                startModalSpinner();

                var errorHeader = "Couldn't change role: ";
                $.post(
                    "${request.route_path('json.set_shared_folder_perm')}", {
                        ${self.csrf.token_param()}
                        ## TODO (WW) use variables to abstract parameter key strings
                        user_id: email,
                        store_id: modalSID(),
                        role: role,
                        suppress_shared_folders_rules_warnings: suppressSharedFolderRulesWarnings
                    }
                ).done(function() {
                    modalUserAndRoleList()[email]
                            .${user_and_role_role_key} = role;
                    refreshModal();
                })
                .fail(function(xhr) {
                    var type = getErrorTypeNullable(xhr);
                    if (type == "SHARED_FOLDER_RULES_WARNING_OWNER_CAN_SHARE_WITH_EXTERNAL_USERS") {
                        showOwnerCanShareExternallyWarningModal(xhr, function() {
                            setRole(email, role, true);
                        });
                    } else if (type == "SHARED_FOLDER_RULES_EDITORS_DISALLOWED_IN_EXTERNALLY_SHARED_FOLDER") {
                        showEditorsDisallowedErrorModal(xhr, false);
                    } else {
                        showErrorMessageFromResponse(xhr);
                    }
                })
                .always(function() {
                    stopModalSpinner();
                });
            }

            function confirmRemoveUser(email, fullName) {
                $('.remove-modal-full-name').text(fullName);
                $('.remove-modal-email').text(email);

                $mainModal.modal('hide');
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
                            user_id: email,
                            store_id: modalSID()
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
                $mainModal.modal('show');
            })

            $('#modal-invite-form').submit(function(ev) {
                ## Since IE doesn't support String.trim(), use $.trim()
                var email = $.trim($('#modal-invitee-email').val());
                var role = $('#modal-invite-role').attr("value");
                inviteToFolder(email, role, false);
                return false;
            });

            ## Initialize auxilary modals
            var $addExternalUserModal = $('#add-external-user-modal');
            var $ownerCanShareExternallyModal = $('#owner-can-share-externally-modal');
            var $editorDisallowedModal = $('#editor-disallowed-modal');
            setModalTransition($addExternalUserModal);
            setModalTransition($ownerCanShareExternallyModal);
            setModalTransition($editorDisallowedModal);

            function setModalTransition($modal) {
                $modal.on('show', function() { $mainModal.modal("hide"); })
                      .on('hidden', function() { $mainModal.modal("show"); })
            }

            ## This method passes the email as a parameter rather than fetching
            ## from the input field on its own, since paymentRequiredToInvite()
            ## hides the main modal before calling this method, which causes the
            ## field to be cleaned up.
            ##
            ## @param done and always are callbacks for AJAX success and completion.
            ## They can be None.
            ##
            ## @param suppressSharedFolderRulesWarnings see sp.proto:ShareFolderCall
            ##
            function inviteToFolder(email, role, suppressSharedFolderRulesWarnings, done, always) {
                startModalSpinner();
                var sid = modalSID();
                var name = modalFolderName();

                $.post("${request.route_path('json.add_shared_folder_perm')}", {
                        ${self.csrf.token_param()}
                        user_id: email,
                        role: role,
                        store_id: sid,
                        folder_name: name,
                        suppress_shared_folders_rules_warnings: suppressSharedFolderRulesWarnings
                    }
                ).done(function() {
                    showSuccessMessage('Invitation has been sent.');
                    $('#modal-invitee-email').val('');
                    if (done) done();
                }).fail(function (xhr) {
                    var type = getErrorTypeNullable(xhr);
                    if (type == "NO_STRIPE_CUSTOMER_ID") {
                        paymentRequiredToInvite(email, role);

                    } else if (type == "SHARED_FOLDER_RULES_WARNING_ADD_EXTERNAL_USER") {
                        showWarningModal($addExternalUserModal,
                                $('#add-external-user-confirm'), function() {
                            ## retry inviting with warnings suppressed
                            inviteToFolder(email, role, true, done, always);
                        });

                    } else if (type == "SHARED_FOLDER_RULES_WARNING_OWNER_CAN_SHARE_WITH_EXTERNAL_USERS") {
                        showOwnerCanShareExternallyWarningModal(xhr, function() {
                            ## retry inviting with warnings suppressed
                            inviteToFolder(email, role, true, done, always);
                        });

                    } else if (type == "SHARED_FOLDER_RULES_EDITORS_DISALLOWED_IN_EXTERNALLY_SHARED_FOLDER") {
                        showEditorsDisallowedErrorModal(xhr, true);

                    } else {
                        showErrorMessageFromResponse(xhr);
                    }
                }).always(function() {
                    if (always) always();
                    stopModalSpinner();
                });
            }

            function showEditorsDisallowedErrorModal(xhr, showSuggestAddAsViewers) {
                $editorDisallowedModal.modal('show');
                populateExternalUserList($('#editor-disallowed-user-list'),
                    getAeroFSErrorData(xhr));
                if (!showSuggestAddAsViewers) {
                    $('#suggest-add-as-viewers').hide();
                }
            }

            ## @param action the callback function to be called when the user clicks Okay
            function showOwnerCanShareExternallyWarningModal(xhr, action) {
                showWarningModal($ownerCanShareExternallyModal,
                    $('#owner-can-share-externally-confirm'), action);
                populateExternalUserList($('#owner-can-share-externally-user-list'),
                        getAeroFSErrorData(xhr));
            }

            ## @param action the callback function to be called when the user clicks Okay
            function showWarningModal($warningModal, $confirmBtn, action) {
                $warningModal.modal('show');
                ## Call off() to clear previously registered click handlers
                $confirmBtn.off('click').click(function() {
                    $warningModal.modal('hide');
                    action();
                    return false;
                })
            }

            ## @param jsonStr a JSON string passed as the error message from the
            ## server. The string specifies a list of external users. See
            ## EX_WARNING_* etc in common.proto for detail.
            ## @list the <ul> element to place the items in
            ##
            ## this function is referred to in a Java class named TestAbstractExSharedFolderRules
            function populateExternalUserList($list, jsonStr) {
                $list.empty();
                $.each($.parseJSON(jsonStr), function(email, fullname) {
                    ## N.B. the format expected here _must_ be covered in TestAbstractExSharedFolderRules
                    var str = fullname.first_name;
                    str += fullname.first_name && fullname.last_name ? ' ' : '';
                    str += fullname.last_name;
                    if (str) str += ' ';
                    str += '<' + email + '>';
                    $list.append($('<li></li>').text(str));
                });
            }

            ##################################
            ## Payment related functions
            ##################################

            ## Restore the main modal once sub-modals are hidden.
            %if is_admin:
                getCreditCardModal()
            %else:
                $("#ask-admin-modal")
            %endif
                .on("hidden", function() {
                    $mainModal.modal("show");
                });

            function paymentRequiredToInvite(email, role) {
                $mainModal.modal("hide");
                %if is_admin:
                    inputCreditCardInfoAndCreateStripeCustomer(function(done, always) {
                        inviteToFolder(email, role, false, done, always);
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

            function displayModalError(errorHeader, error) {
                showErrorMessage(errorHeader + error + ".");
            }

            ##################################
            ## Spinner related functions
            ##################################

            initializeSpinners();

            function startModalSpinner() {
                startSpinner($('#modal-spinner'), 3);
            }

            function stopModalSpinner() {
                stopSpinner($('#modal-spinner'));
            }
        });
    </script>
</%block>
