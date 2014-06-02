<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Shared Folders" %>

<%namespace name="shared_folder_modals" file="shared_folder_modals.mako" />
<%namespace name="credit_card_modal" file="credit_card_modal.mako"/>
<%namespace name="spinner" file="spinner.mako"/>

<%block name="css">
    <link href="${request.static_path('web:static/css/datatables-bootstrap.css')}"
          rel="stylesheet">
</%block>

<%! from web.util import is_private_deployment %>

<div class="row page-block">
    <div class="col-sm-12">
        <h2>${page_heading}</h2>
            %if not is_private_deployment(request.registry.settings):
              <div class="well well-small footnote">
                  This is a list of shared folder names only. Please use an AeroFS
                  client to see the actual content of your folders and files.
              </div>
            %endif
        <table id="folders_table" class="table table-hover">
            <thead>
                <tr>
                    <th>Folder</th>
                    <th>Owners</th>
                    <th>Viewers and Editors</th>
                    <th></th>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
    </div>
</div>

<%shared_folder_modals:main_modals/>

<%block name="scripts">

    ## Only admins can input credit card
    %if is_admin:
        <%credit_card_modal:javascript/>
    %endif
    <%spinner:scripts/>
    <script src="${request.static_path('web:static/js/jquery.dataTables.min.js')}"></script>
    <script src="${request.static_path('web:static/js/compiled/datatables_extensions.js')}"></script>

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
                    { "mDataProp": "owners" },
                    { "mDataProp": "members" },
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
            var $manageModal = $('#manage-modal');
            var $leaveModal = $('#leave-folder-modal');
            var $destroyModal = $('#destroy-folder-modal');

            $('.${open_modal_class}').live('click', function () {
                $link = $(this);
                var myModal = $manageModal;
                if ($link.data('action') == 'manage') {
                    refreshManageModal();
                } else if ($link.data('action') == 'leave') {
                    refreshLeaveModal('left');
                    myModal = $leaveModal;
                } else if ($link.data('action') == 'destroy') {
                    refreshLeaveModal('destroyed');
                    myModal = $destroyModal;
                }
                myModal.modal('show');
            });

            ## N.B. updates to the return value will be propagated back to the
            ## Options link and persists throughout the link's lifecycle.
            function modalUserAndPermissionsList() {
                return $link.data('${link_data_user_permissions_and_state_list}');
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

            ## TODO: template that out
            var BASE_PERMISSION = "download";
            var PERMISSIONS = [
                {"name": "WRITE",  "description": "upload"},
                {"name": "MANAGE", "description": "manage"}
            ];

            var DEFAULT_ROLE = 1;
            var ROLES = [
                { "name": "Owner",   "permissions": ["MANAGE", "WRITE"] },
                { "name": "Editor",  "permissions": ["WRITE"] },
            %if use_restricted:
                { "name": "Manager", "permissions": ["MANAGE"] },
            %endif
                { "name": "Viewer",  "permissions": [] }
            ];

            function roleDescription(permissions) {
                var desc = BASE_PERMISSION;
                if (permissions.length == 0) return desc + " only";
                var n = 0;
                for (var i = 0; i < PERMISSIONS.length && n < permissions.length; ++i) {
                    var p = PERMISSIONS[i];
                    if (permissions.indexOf(p.name) != -1) {
                        ++n;
                        desc = desc
                            + (permissions.length == n ? (n > 1 ? "," : "") + " and " : ", ")
                            + p.description;
                    }
                }
                return desc;
            }

            ## compare to array of permissions
            function samePermissions(a, b) {
                if (a.length != b.length) return false;
                a.sort();
                b.sort();
                for (var i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
                return true;
            }

            function roleName(permissions) {
                for (var i = 0; i < ROLES.length; ++i) {
                    var role = ROLES[i];
                    if (samePermissions(role.permissions, permissions)) return role.name;
                }
                ## Fallback
                return roleDescription(permissions);
            }

            function roleMenuText(role) {
                ## TODO: hardcoded width seems like a terrible idea, at the very least make it relative to font-size
                return '<div style="display: inline-block; width: 70px">' + role.name + "</div>"
                        + roleDescription(role.permissions);
            }

            ## Refresh the modal based using the current values in $link
            function refreshModal() {
                if ($link.data('data-action') == 'manage') {
                    refreshManageModal();
                } else if ($link.data('data-action') == 'leave') {
                    refreshLeaveModal('left');
                } else if ($link.data('data-action') == 'destroy') {
                    refreshLeaveModal('destroyed');
                }
            }

            function refreshLeaveModal(folder) {
                $('#' + folder + '-folder-name').text(modalFolderName());
            }

            function refreshManageModal() {
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

                ## Turn JSON blob into sortable list
                ## and sort by pending/not pending, then by permissions
                var sortable_users = [];
                $.each(modalUserAndPermissionsList(), function(email, ur) {
                    ur.email = email;
                    sortable_users.push(ur);
                });
                sortable_users.sort(function(a,b){
                    if (b.state == a.state) {
                        return b.permissions.length > a.permissions.length;
                    } else {
                        return b.state > a.state;
                    }
                });

                ## Populate the table
                $.each(sortable_users, function(index, ur) {
                    var tooltip = true;
                    var permissions = ur.permissions;
                    var name = ur.first_name + " " + ur.last_name;
                    if (name === " ") {
                        name = ur.email;
                    }

                    var actions, roleStr;
                    if (ur.email === '${session_user}') {
                        roleStr = roleName(permissions);
                        actions = '';
                    } else if (privileged) {
                        roleStr = renderActionableRole(ur.email, permissions);
                        actions = renderPrivilegedUserActions(ur.email, name);
                    } else {
                        roleStr = roleName(permissions);
                        actions = renderUnprivilegedUserActions(ur.email);
                    }

                    var $row = $("<tr></tr>");
                    if (ur.state === 0) {
                        $row.addClass('pending');
                        tooltip = false;

                    }
                    var nameSpan = $("<span></span>");
                    if (tooltip){
                        $(nameSpan).text(name)
                                    .addClass("tooltip_email")
                                    .attr("data-toggle", "tooltip")
                                    .attr("title", ur.email);
                    } else {
                        $(nameSpan).text(name + ' (pending)');
                    }
                    $row.append($("<td></td>").append(nameSpan));
                    $row.append($("<td></td>").append(roleStr));
                    $row.append($("<td></td>").append(actions));

                    $table.find('> tbody:last').append($row);
                });

                activateModelTableElements();
            }

            function initRoleMenuForInviteForm() {
                var $menu = $('#modal-invite-role-menu');
                var $id = $('#modal-invite-role');
                var $label = $('#modal-invite-role-label');

                ## Set the default role
                setPermissions(ROLES[DEFAULT_ROLE].permissions);

                ## Populate the role menu
                $.each(ROLES, function(idx, role) {
                    var $a = $('<a></a>')
                            .html(roleMenuText(role))
                            .attr("href", "#")
                            .on("click", function() { setPermissions(role.permissions); });
                    $menu.append($('<li></li>').append($a));
                });

                function setPermissions(permissions) {
                    $id.data("permissions", permissions);
                    $label.text(roleName(permissions));
                }
            }
            initRoleMenuForInviteForm();

            function renderActionableRole(email, currentPermissions) {
                var $roles = $("<ul></ul>")
                    .attr("class", "dropdown-menu")
                    .attr("role", "menu");

                $.each(ROLES, function(idx, role) {
                    var $check = $("<span></span>").html('&#x2713;&nbsp;');
                    if (!samePermissions(currentPermissions, role.permissions)) $check.addClass("invisible");

                    $roles.append($("<li></li>")
                            .append($("<a></a>")
                                    .attr("href", "#")
                                    .addClass("model-set-role")
                                    .data("email", email)
                                    .data("permissions", role.permissions)
                                    .append($check)
                                    .append(roleMenuText(role))));
                });

                return $("<div></div>")
                        .addClass("dropdown")
                        .append($("<a></a>")
                                .addClass("dropdown-toggle")
                                .addClass("pull-left")
                                .attr("data-toggle", "dropdown")
                                .attr("href", "#")
                                .html(roleName(currentPermissions) + '&nbsp;&#x25BE;'))
                        .append($roles);
            }

            function renderUnprivilegedUserActions(email) {
                return $("<span></span>")
                        .addClass("pull-right")
                        .append(renderSendEmailLink(email));
            }

            function renderPrivilegedUserActions(email, fullName) {
                return $("<div></div>")
                        .addClass("dropdown")
                        .append($("<a></a>")
                                .addClass("dropdown-toggle")
                                .addClass("pull-right")
                                .attr("data-toggle", "dropdown")
                                .attr("href", "#")
                                .html("Actions&nbsp;&#x25BE;"))
                        .append($("<ul></ul>")
                                .attr("class", "dropdown-menu")
                                .attr("role", "menu")
                                .append($("<li></li>")
                                        .append(renderSendEmailLink(email)))
                                .append($("<li></li>")
                                        .append($("<a></a>")
                                                .addClass("modal-remove")
                                                .attr("data-email", email)
                                                .attr("data-full-name", fullName)
                                                .attr("href", "#")
                                                .text("Remove"))));
            }

            function renderSendEmailLink(email) {
                return $("<a></a>")
                        .attr("href", 'mailto:"' + email + '"')
                        .attr("target", "_blank")
                        .text("Send Email");
            }

            function registerTooltips() {
                $('.tooltip_email').tooltip({
                    ## To avoid tooltips being cut off by the modal boundary.
                    ## See https://github.com/twitter/bootstrap/pull/6378
                    container: '#modal'
                });
            }

            ## Set event handlers and activities for elements in the table
            function activateModelTableElements() {
                registerTooltips();

                $('.model-set-role').click(function() {
                    setPermissions($(this).data('email'), $(this).data('permissions'), false);
                });

                $('.modal-remove').click(function() {
                    confirmRemoveUser($(this).data('email'), $(this).data('full-name'));
                });
            }

            $manageModal.on('shown', function() {
                $("#modal-invitee-email").focus();
            });

            $manageModal.on('hidden', function() {
                stopModalSpinner();
                ## Remove previous invited email
                $("#modal-invitee-email").val('');
            });

            ## @param suppressSharedFolderRulesWarnings see sp.proto:updateACL()
            function setPermissions(email, permissions, suppressSharingRulesWarnings) {
                startModalSpinner();

                var errorHeader = "Couldn't change role: ";
                $.postJSON(
                    "${request.route_path('json.set_shared_folder_perm')}",
                    {
                        user_id: email,
                        store_id: modalSID(),
                        permissions: permissions,
                        suppress_sharing_rules_warnings: suppressSharingRulesWarnings
                    }
                ).done(function() {
                    modalUserAndPermissionsList()[email].permissions = permissions;
                    refreshModal();
                })
                .fail(function(xhr) {
                    handleException(xhr, function() {
                        setPermissions(email, permissions, true);
                    });
                })
                .always(function() {
                    stopModalSpinner();
                });
            }

            function confirmRemoveUser(email, fullName) {
                $('.remove-modal-full-name').text(fullName);
                $('.remove-modal-email').text(email);

                $manageModal.modal('hide');
                $('#remove-modal').modal('show');

                var $confirm = $('#remove-model-confirm');
                $confirm.off('click');
                $confirm.click(function() {
                    ## the 'hidden' handler below brings up the main modal once
                    ## the remove modal is hidden
                    $('#remove-modal').modal('hide');
                    startModalSpinner();

                    var errorHeader = "Couldn't remove: ";
                    $.postJSON(
                        "${request.route_path('json.delete_shared_folder_perm')}",
                        {
                            user_id: email,
                            store_id: modalSID()
                        }
                    )
                    .done(function(response) {
                        if (handleAjaxReply(response, errorHeader)) {
                            ## Update modal data
                            delete modalUserAndPermissionsList()[email];
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
                $manageModal.modal('show');
            })

            $('#modal-leave-form').submit(function(ev){
                var sid = modalSID();
                var name = modalFolderName();
                var permissions = $('#modal-invite-role').data("permissions");

                $.postJSON(
                    "${request.route_path('json.leave_shared_folder')}",
                    {
                        permissions: permissions,
                        store_id: sid,
                        folder_name: name,
                    }
                ).done(function(e) {
                    showSuccessMessage('You have left folder "'+ name +'".');
                }).fail(showErrorMessageFromResponse);
                /* Table refresh and modal hiding ought to be inside an .always(), but 
                   for some reason they won't fire if I do that. :/ */
                refreshTable();
                $leaveModal.modal('hide');
                return false;
            });

            $('#modal-destroy-form').submit(function(ev){
                var sid = modalSID();
                var name = modalFolderName();
                var permissions = $('#modal-invite-role').data("permissions");
                $.postJSON(
                    "${request.route_path('json.destroy_shared_folder')}",
                    {
                        permissions: permissions,
                        store_id: sid,
                        folder_name: name,
                        user_id: '${session_user}',
                    }
                ).done(function(e) {
                    showSuccessMessage('You have deleted folder "'+ name +'".');
                }).fail(showErrorMessageFromResponse);
                /* Table refresh and modal hiding ought to be inside an .always(), but 
                   for some reason they won't fire if I do that. :/ */
                refreshTable();
                $destroyModal.modal('hide');
                return false;
            });

            $('#modal-invite-form').submit(function(ev) {
                ## Since IE doesnt support String.trim(), use $.trim()
                var email_list = parseEmails($.trim($('#modal-invitee-email').val()));
                var permissions = $('#modal-invite-role').data("permissions");
                $.each(email_list, function(i, email) {
                    inviteToFolder(email, permissions, false, false, false, true);
                });
                return false;
            });

            var parseEmails = function(emailStr) {
                /* Split on commas, semicolons, or spaces, with or without 
                any number of spaces, and filter out substrings that don't
                have an @-sign for super basic junk removal */
                return emailStr.split(/\s*[,;\s]\s*/).filter(function(item){
                    return item.indexOf('@') != -1;
                });
            }

            ## Initialize auxiliary modals
            var $sharingWarningModal = $('#sharing-warning-modal');
            var $sharingErrorModal = $('#sharing-error-modal');
            setModalTransition($sharingWarningModal);
            setModalTransition($sharingErrorModal);

            function setModalTransition($modal) {
                $modal.on('show', function() { $manageModal.modal("hide"); })
                      .on('hidden', function() { $manageModal.modal("show"); })
            }

            ## This method passes the email as a parameter rather than fetching
            ## from the input field on its own, since paymentRequiredToInvite()
            ## hides the main modal before calling this method, which causes the
            ## field to be cleaned up.
            ##
            ## @param done and always are callbacks for AJAX success and completion.
            ## They can be None.
            ##
            ## @param suppressSharingRulesWarnings see sp.proto:ShareFolderCall
            ##
            function inviteToFolder(email, permissions, suppressSharingRulesWarnings, done, always, multipleInvites) {
                startModalSpinner();
                var sid = modalSID();
                var name = modalFolderName();

                $.postJSON(
                    "${request.route_path('json.add_shared_folder_perm')}",
                    {
                        user_id: email,
                        permissions: permissions,
                        store_id: sid,
                        folder_name: name,
                        suppress_sharing_rules_warnings: suppressSharingRulesWarnings
                    }
                ).done(function() {
                    if (multipleInvites) {
                        showSuccessMessage('Invitations have been sent.');
                    } else {
                        showSuccessMessage('Invitation has been sent.');
                    }
                    $('#modal-invitee-email').val('');
                    if (done) done();
                }).fail(function (xhr) {
                    var type = getErrorTypeNullable(xhr);
                    if (type == "NO_STRIPE_CUSTOMER_ID") {
                        paymentRequiredToInvite(email, permissions);
                    } else {
                        handleException(xhr, function() {
                            ## retry inviting with warnings suppressed
                            inviteToFolder(email, permissions, true, done, always);
                        });
                    }
                }).always(function() {
                    if (always) always();
                    stopModalSpinner();
                });
            }

            function handleException(xhr, proceed) {
                var type = getErrorTypeNullable(xhr);
                if (type == "SHARING_RULES_WARNINGS") {
                    showSharingWarningsRecursiveModal($.parseJSON(getAeroFSErrorData(xhr)), proceed);
                } else if (type == "SHARING_RULES_ERROR") {
                    showSharingModal($sharingErrorModal, $.parseJSON(getAeroFSErrorData(xhr)));
                } else {
                    showErrorMessageFromResponse(xhr);
                }
            }

            function showSharingWarningsRecursiveModal(warnings, action) {
                if (warnings.length == 0) {
                    ## all warnings dismissed, proceed
                    action();
                } else {
                    showSharingWarningModal(warnings[0], function() {
                        ## show next next warning
                        showSharingWarningsRecursiveModal(warnings.slice(1), action);
                    })
                }
            }

            ## @param action the callback function to be called when the user clicks Proceed
            function showSharingWarningModal(warning, action) {
                showSharingModal($sharingWarningModal, warning);

                ## Call off() to clear previously registered click handlers
                $('#sharing-warning-confirm').off('click').click(function() {
                    $sharingWarningModal.modal('hide');
                    action();
                    return false;
                });
            }

            ## See SHARING_RULES_WARNING and SHARING_RULES_ERROR in common.proto for detail
            ## on the format of the data
            function showSharingModal($sharingModal, data) {
                $sharingModal.modal('show');
                $sharingModal.find('.modal-header>h4').text(data.title);
                var $content = $sharingModal.find('.modal-body').empty();
                ## simple html-ification of the description
                ## TODO: consider handling a subset of markdown (bold and urls would be nice)
                $.each(data.description.split("\n"), function(index, elem) {
                    if (elem == "{}") {
                        populateUserList($content.append($('<ul></ul>')), data.users);
                    } else {
                        $content.append($('<p></p>').text(elem));
                    }
                });
            }

            ## @param users list of users
            ## @param list the <ul> element to place the items in, assumed to be empty
            function populateUserList($list, users) {
                $.each(users, function(email, fullname) {
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
                    $manageModal.modal("show");
                });

            function paymentRequiredToInvite(email, permissions) {
                $mainModal.modal("hide");
                %if is_admin:
                    inputCreditCardInfoAndCreateStripeCustomer(function(done, always) {
                        inviteToFolder(email, permissions, false, done, always);
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
