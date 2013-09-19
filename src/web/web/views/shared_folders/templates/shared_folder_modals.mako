## TODO (WW) move JS code that refers to names in this file to here

<%def name="main_modals()">
    <div id="modal" class="modal hide" tabindex="-1" role="dialog" style="width: 700px">
        <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">×</button>
            <h4><span id="modal-folder-title"></span>&nbsp;
                <i id="modal-folder-title-info-icon" class="icon-info-sign"></i></h4>
        </div>
        <div class="modal-body" style="min-height: 300px">
            <table id="modal-user-role-table" class="table table-hover">
                ## min-width to avoid table cell shifting due to different lengths of role
                ## strings, when the user updates members roles.
                <thead><tr><th>Member</th><th style="min-width: 64px">Role</th><th></th></tr></thead>
                <tbody></tbody>
            </table>
        </div>
        <div class="modal-footer">
            <form id="modal-invite-form" class="form-inline" method="post">
                <span id="invite-user-inputs" class="pull-left">
                    <input type="text" id="modal-invitee-email" placeholder="Invite email">
                    as
                    <select id="modal-invite-role-select" style="width: 100px;">
                    </select>
                    <input class="btn btn-primary" type="submit" value="Send Invite">
                </span>
                ## Use a space to keep the spinner in place
                ## TODO (WW) it doesn't work well with IE
                <span id="modal-spinner">&nbsp;</span>
                <a href="#" class="btn" data-dismiss="modal">Close</a>
            </form>
        </div>
    </div>

    <div id="remove-modal" class="modal hide" tabindex="-1" role="dialog">
        <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">×</button>
            <h4>Remove from folder</h4>
        </div>
        <div class="modal-body">
            ## Note: the following text should be consistent with the text in
            ## RoleMenu.java.
            <p>Are you sure you want to remove
                <strong><span class="remove-modal-full-name"></span></strong>
                (<span class="remove-modal-email"></span>) from the shared folder?</p>
            <p class="footnote">
                This will delete the folder from the person's computers.
                However, old content may be still accessible from the
                person's sync history.
            </p>

        </div>
        <div class="modal-footer">
            <a href="#" class="btn" data-dismiss="modal">Cancel</a>
            <a href="#" id="remove-model-confirm" class="btn btn-danger">Remove</a>
        </div>
    </div>
</%def>

<%def name="ask_admin_modal()">
    <div id="ask-admin-modal" class="modal hide" tabindex="-1" role="dialog">
        <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">×</button>
            <h4>Please contact your admin</h4>
        </div>
        <div class="modal-body">
            ## Note: the following text should be consistent with the text in
            ## CompInviteUsers.java.
            <p>To add more collaborators to this folder, a paid AeroFS plan is
                required for your team. Please contact your team administrator to
                upgrade the plan.
            </p>
        </div>
        <div class="modal-footer">
            <a href="#" class="btn" data-dismiss="modal">Close</a>
            <%
                # Note: the following text should be consistent with the text in
                # CompInviteUsers.java.
                subject = 'Upgrade our AeroFS plan'
                body = "Hi,\n\nI would like to invite more external collaborators to a shared" \
                    " folder, which requires a paid AeroFS plan. Could we upgrade" \
                    " the plan for our team?" \
                    " We can upgrade through this link:\n\n{}\n\nThank you!"\
                    .format(request.route_url('start_subscription'))
            %>
            <a target="_blank" href="mailto:?subject=${subject | u}&body=${body | u}"
               class="btn btn-primary">Email Admin with Instructions...</a>
        </div>
    </div>
</%def>