<div class="dropdown">
    <a class="dropdown-toggle" data-toggle="dropdown" href="#">Actions&nbsp;&#x25BE;</a>
    <ul class="dropdown-menu" role="menu">
        <li><a href="${shared_folders_url}">View Shared Folders</a></li>
        <li><a href="${devices_url}">View Devices</a></li>

        % if not user_is_session_user:
            <%
                become_admin = 'false' if is_admin else 'true'
                currentText = "Add as Admin"
                newText = "Remove as Admin"
                # swap currentText and newText if user already is an admin
                if is_admin: newText, currentText = currentText, newText
            %>
            <li class="divider"></li>
            <li><a href="#" onclick="toggleAdmin('${email}', ${become_admin}, '${newText}', $(this)); return false;">${currentText}</a></li>

            % if not is_enterprise:  ## We do not support removing users on enterprise deployment
                <li><a href="#" onclick="removeFromTeam('${email}', '${devices_url}', $(this)); return false;">Remove from Team</a></li>
            % endif
        % endif
    </ul>
</div>