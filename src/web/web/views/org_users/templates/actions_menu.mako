<div class="dropdown">
    <a class="dropdown-toggle" data-toggle="dropdown" href="#">Actions&nbsp;&#x25BE;</a>
    <ul class="dropdown-menu" role="menu">
        <li><a href="${shared_folders_url}">View Shared Folders</a></li>
        <li><a href="${devices_url}">View Devices</a></li>

        %if not user_is_session_user:
            ${items_for_other_users()}
        %endif
    </ul>
</div>

<%def name="items_for_other_users()">
    <li class="divider"></li>

    <%
        admin_link_text = "Remove as Admin"  # text displayed for an admin
        user_link_text = "Add as Admin"      # text displayed for a user
    %>
    <li><a href="#" onclick="
            toggleAdmin('${email}', '${admin_link_text}', '${user_link_text}', $(this));
            return false;
        ">${admin_link_text if is_admin else user_link_text}</a></li>

    %if not is_private:  ## We do not support removing users on enterprise deployment
        <li><a href="#" onclick="removeFromTeam('${email}', '${devices_url}', $(this)); return false;">Remove from Organization</a></li>
    %endif

    <%
        publisher_link_text = "Remove as Publisher"  # text displayed for a publisher
        not_publisher_link_text = "Add as Publisher"  # text displayed for a non-publisher
    %>
    %if use_restricted:
        <li><a href="#" onclick="
                togglepublisher('${email}', '${publisher_link_text}', '${not_publisher_link_text}', $(this));
                return false;
            ">${publisher_link_text if is_publisher else not_publisher_link_text}</a></li>
    %endif

    %if two_factor_enforced:
    <li><a href="#" onclick="disableTwoFactor('${email}', $(this)); return false;">Disable Two-Factor Auth</a></li>
    %endif

    <li><a href="#" onclick="deactivate('${email}', $(this)); return false;">Delete User</a></li>
</%def>
