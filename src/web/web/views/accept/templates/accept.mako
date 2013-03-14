<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

% if len(team_invitations) == 0 and len(folder_invitations) == 0:
    ${render_no_invitation()}
% else:
    % if len(team_invitations) != 0:
        ${render_team_invitations()}
    % endif

    % if len(folder_invitations) != 0:
        ${render_folder_invitations()}
    % endif
% endif

<%def name="render_no_invitation()">
    <h2>No Pending Invitations</h2>
</%def>

<%def name="render_team_invitations()">
    <div class="row page_block">
        <div class="span6">
            <h2 style="margin-bottom: 15px;">Team Invitations (${len(team_invitations)})</h2>
            <table class="table" style="border: 1px dotted #ccc;">
                <tbody>
                    % for invite in team_invitations:
                        ${render_team_invitation_row(invite)}
                    % endfor
                </tbody>
            </table>
        </div>
    </div>
</%def>

<%def name="render_team_invitation_row(invite)">
    <%
        inviter = invite['inviter']
        orgID = invite['organization_id']
        orgName = invite['organization_name']
    %>

    <tr>
        <td>
            <span class="invitation_title" style="display: block; margin-bottom: 3px;">
                Invitation to team "${orgName}"
            </span>
            <span style="margin-left: 20px;">
                by ${inviter}
            </span>
        </td>
        <td style="text-align: right; vertical-align: middle;">
            <a class="btn" href="#" onclick="acceptOrganizationInvite('${orgID}', '${orgName}')">Accept</a>
            <a class="btn" href="#" onclick="ignoreOrganizationInvite('${orgID}')">Ignore</a>
        </td>
    </tr>
</%def>

<%def name="render_folder_invitations()">
    <div class="row page_block">
        <div class="span6">
            <h2 style="margin-bottom: 15px;">Shared Folder Invitations (${len(folder_invitations)})</h2>
            <table class="table" style="border: 1px dotted #ccc;">
                <tbody>
                    % for invite in folder_invitations:
                        ${render_folder_invitation_row(invite)}
                    % endfor
                </tbody>
            </table>
        </div>
    </div>
</%def>

<%def name="render_folder_invitation_row(invite)">
    <%
        sharer = invite['sharer']
        shareID = invite['share_id']
        folderName = invite['folder_name']
    %>

    <tr>
        <td>
            <span class="invitation_title" style="display: block; margin-bottom: 3px;">
                Invitation to folder "${folderName}"
            </span>
            <span style="margin-left: 20px;">
                by ${sharer}
            </span>
        </td>
        <td style="text-align: right; vertical-align: middle;">
            <a class="btn" href="#" onclick="acceptFolderInvite('${shareID}', '${folderName}')">Accept</a>
            <a class="btn" href="#" onclick="ignoreFolderInvite('${shareID}', '${folderName}')">Ignore</a>
        </td>
    </tr>
</%def>

<%block name="scripts">
    <script type="text/javascript">

        function acceptOrganizationInvite(orgID, orgName)
        {
            $.post("${request.route_path('json.accept_organization_invitation')}",
                {
                    ${self.csrf_token_param()}
                    id: orgID,
                    orgname: orgName
                }, handleAjaxReply);
        }

        function ignoreOrganizationInvite(orgID)
        {
            $.post("${request.route_path('json.ignore_organization_invitation')}",
                {
                    ${self.csrf_token_param()}
                    id: orgID
                }, handleAjaxReply);
        }

        function acceptFolderInvite(shareID, folderName)
        {
            $.post("${request.route_path('json.accept_folder_invitation')}",
                {
                    ${self.csrf_token_param()}
                    id: shareID,
                    foldername: folderName
                }, handleAjaxReply);
        }

        function ignoreFolderInvite(shareID, folderName)
        {
            $.post("${request.route_path('json.ignore_folder_invitation')}",
                {
                    ${self.csrf_token_param()}
                    id: shareID,
                    foldername: folderName
                }, handleAjaxReply);
        }

        function handleAjaxReply(data) {
            if (data.success) {
                ## alert(data.response_message);
                window.location.reload();
            } else {
                showErrorMessage(data.response_message);
            }
        }

    </script>
</%block>
