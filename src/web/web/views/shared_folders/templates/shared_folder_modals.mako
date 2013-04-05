## TODO (WW) move JS code that refers to names in this file to here

<%def name="main_modals()">
    <div id="modal" class="modal hide" tabindex="-1" role="dialog">
        <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">×</button>
            <h4>Options for folder &quot;<span id="modal-folder-name"></span>&quot;</h4>
        </div>
        <div class="modal-body">
            <table id="modal-user-role-table" class="table">
                <thead><tr><th>Member</th><th></th></tr></thead>
                <tbody></tbody>
            </table>
        </div>
        <div class="modal-footer">
            <form id="modal-invite-form" class="form-inline" method="post">
                <span id="invite-user-inputs" class="pull-left">
                    <input type="text" id="modal-invitee-email" placeholder="Add email">
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
                <span class="remove-modal-full-name"></span>
                (<span class="remove-modal-email"></span>) from the shared folder?</p>
            <p class="footnote">
                This will delete the folder from the person's computers.
                However, old content may be still accessible from the
                person's version history.
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