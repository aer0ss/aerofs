<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Invitations" %>

<%block name="css">
    <style type="text/css">
        ## Buttons are vertically placed when the horizontal space is too small.
        ## This style gives vertical spacing between buttons.
        .btn-vspacing {
            margin-bottom: 5px;
        }
    </style>
</%block>

<div class="hidden page-block" id="no-invitation-div">
    <h2>No pending invitations</h2>
</div>

<div class="hidden page-block" id="team-invitations-div">
    <h2 style="margin-bottom: 15px;">Invitations to organizations</h2>
    <table class="table" style="border: 1px dotted #ccc;">
        <tbody id="team-invitations-tbody">
            % for invite in team_invitations:
                ${render_team_invitation_row(invite)}
            % endfor
        </tbody>
    </table>
</div>

<div class="hidden page-block" id="folder-invitations-div">
    <h2 style="margin-bottom: 15px;">Invitations to shared folders</h2>
    <table class="table" style="border: 1px dotted #ccc;">
        <tbody id="folder-invitations-tbody">
                % for invite in folder_invitations:
                ${render_folder_invitation_row(invite)}
                % endfor
        </tbody>
    </table>
</div>

<%def name="render_team_invitation_row(invite)">
    <%
        inviter = invite['inviter']
        org_id = invite['organization_id']
        org_name = invite['organization_name']
    %>

    <tr>
        <td>
            <span class="invitation_title" style="display: block; margin-bottom: 3px;">
                Invitation to organization "${org_name | h}"
            </span>
            <span style="margin-left: 20px;">
                by ${inviter | h}
            </span>
        </td>
        <td style="text-align: right; vertical-align: middle;">
            <a class="btn btn-primary btn-vspacing accept-team-invite" href="#"
               data-org-id="${org_id}" data-org-name="${org_name | h}">Accept</a>
            <a class="btn btn-vspacing ignore-team-invite" href="#" data-org-id="${org_id}">Ignore</a>
        </td>
    </tr>
</%def>

<%def name="render_folder_invitation_row(invite)">
    <%
        sharer = invite['sharer']
        share_id = invite['share_id']
        folder_name = invite['folder_name']
        from_team_server = invite['from_team_server']
    %>

    <tr>
        <td>
            <span class="invitation_title" style="display: block; margin-bottom: 3px;">
                Invitation to folder "${folder_name | h}"
            </span>
            <span style="margin-left: 20px;">
                %if from_team_server:
                    by an organization administrator
                %else:
                    by ${sharer | h}
                %endif
            </span>
        </td>
        <td style="text-align: right; vertical-align: middle;">
            <a class="btn btn-primary btn-vspacing accept-folder-invite" href="#"
               data-share-id="${share_id}" data-folder-name="${folder_name | h}">Accept</a>
            <a class="btn btn-vspacing ignore-folder-invite" href="#" data-share-id="${share_id}">Ignore</a>
        </td>
    </tr>
</%def>

<div id="join-team-modal" class="modal fade" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">×</button>
                <h4>Leave the Current Organization</h4>
            </div>
            <div class="modal-body">
                <p>
                    Accepting this invitation will require leaving your current organization.
                    Are you sure you want to proceed?
                </p>
                <p>
                    If you continue,
                    %if i_am_admin:
                        you will no longer be able to administrate the current organization.
                        Additionally,
                    %endif
                    the Team Servers of your current organization will
                    automatically delete your files that are not shared with other
                    users of the current organization. The Team Servers of the new organization
                    will sync all your files once this change is complete.
                </p>
                <p>
                    Files and shared folders on your own AeroFS devices will not be
                    affected.
                </p>
            </div>
            <div class="modal-footer">
                <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
                <a href="#" id="join-team-model-confirm" class="btn btn-primary">
                    Leave my organization and join the new one</a>
            </div>
        </div>
    </div>
</div>

<div id="no-admin-for-team-modal" class="modal fade" tabindex="-1" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">×</button>
                <h4 class="text-error"><img class="icon-vertical-align-fix"
                        src="${request.static_path('web:static/img/warning_16.png')}"
                        width="16px" height="16px">
                    Please Assign an Administrator</h4>
            </div>
            <div class="modal-body">
                <p>
                    Unfortunately, you can't leave your current organization since you
                    are the only administrator of the organization. Please assign another
                    user as an admin before accepting the invitation.
                </p>

                <p class="footnote">Organizations with no admins will be eaten by dinosaurs.</p>
            </div>
            <div class="modal-footer">
                <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
                <a href="${request.route_path('org_users')}" class="btn btn-primary">
                    View Users</a>
            </div>
        </div>
    </div>
</div>


<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            refreshElements();

            var $acceptTeamInviteButton;

            $(".accept-team-invite").click(function() {
                $acceptTeamInviteButton = $(this);
                $("#join-team-modal").modal("show");
            });

            $("#join-team-model-confirm").click(function() {
                $.post("${request.route_path('json.accept_team_invitation')}",
                    {
                        "${url_param_org_id}": $acceptTeamInviteButton.data('org-id')
                    }
                )
                .done(function() {
                    ## Since the user's auth level may have changed resulting in
                    ## changes in the navigation menu, refresh the entire page
                    ## instead of only removing the invitation row.
                    ## Don't use "location.href =". It's not supported by old Firefox.
                    window.location.assign(
                        "${request.route_path('accept_team_invitation_done')}?" +
                        "${url_param_joined_team_name}=" +
                        encodeURIComponent($acceptTeamInviteButton.data('org-name')));
                })
                .fail(function(xhr) {
                    $("#join-team-modal").modal("hide");
                    if (getErrorTypeNullable(xhr) == 'NO_ADMIN_OR_OWNER') {
                        $("#no-admin-for-team-modal").modal("show");
                    } else {
                        showErrorMessageFromResponse(xhr);
                    }
                });

                return false;
            });

            $(".ignore-team-invite").click(function() {
                var $this = $(this);
                $.post("${request.route_path('json.ignore_team_invitation')}",
                    {
                        "${url_param_org_id}": $(this).data('org-id')
                    }
                )
                .done(function() {
                    showSuccessMessage("The invitation has been ignored.");
                    removeRow($this);
                })
                .fail(showErrorMessageFromResponse);

                return false;
            });

            $(".accept-folder-invite").click(function() {
                var $this = $(this);
                $.post("${request.route_path('json.accept_folder_invitation')}",
                    {
                        "${url_param_share_id}": $this.data('share-id')
                    }
                )
                .done(function() {
                    showSuccessMessage("You have joined the folder \"" +
                            $this.data('folder-name') + "\".");
                    removeRow($this);
                })
                .fail(showErrorMessageFromResponse);

                return false;
            });

            $('.ignore-folder-invite').click(function() {
                var $this = $(this);
                $.post("${request.route_path('json.ignore_folder_invitation')}",
                    {
                        "${url_param_share_id}": $(this).data('share-id')
                    }
                )
                .done(function() {
                    showSuccessMessage("The invitation has been ignored.");
                    removeRow($this);
                })
                .fail(showErrorMessageFromResponse);

                return false;
            });
        });

        function removeRow($elem) {
            $elem.closest('tr').remove();
            refreshElements();
        }

        function refreshElements() {
            var teamInvites = $("#team-invitations-tbody").find("tr").length;
            var folderInvites = $("#folder-invitations-tbody").find("tr").length;

            setVisible($("#team-invitations-div"), teamInvites > 0);
            setVisible($("#folder-invitations-div"), folderInvites > 0);
            setVisible($("#no-invitation-div"),
                    teamInvites == 0 && folderInvites == 0);
        }
    </script>
</%block>
