## TODO (WW) move JS code that refers to names in this file to here?

<%namespace file="modal.mako" name="modal"/>

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

    <%modal:modal>
        <%def name="id()">remove-modal</%def>
        <%def name="title()">Remove from folder</%def>
        <%def name="footer()">
            <a href="#" class="btn" data-dismiss="modal">Cancel</a>
            <a href="#" id="remove-model-confirm" class="btn btn-danger">Remove</a>
        </%def>

        ## Note: the following text should be consistent with the text in
        ## RoleMenu.java.
        <p>Are you sure you want to remove
            <strong><span class="remove-modal-full-name"></span></strong>
            (<span class="remove-modal-email"></span>) from the shared folder?</p>
        <p class="footnote">
            This will delete the folder from the person's computers.
            However, old content may be still accessible from the
            person's sync <history class=""></history>
        </p>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">convert-to-external-modal</%def>
        <%def name="title()">Be careful with externally shared folders</%def>
        <%def name="footer()">
            <a href="#" class="btn" data-dismiss="modal">Cancel</a>
            <a href="#" id="convert-to-external-confirm" class="btn btn-danger">Confirm</a>
        </%def>

        <p>You are about to share this folder with external users.</p>
        <p>Editors of this folder will be automatically converted to Viewers.</p>
        <p>Please ensure that this folder <strong>contains
            no confidential material</strong> before proceeding.</p>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">owner-can-share-externally-modal</%def>
        <%def name="title()">Be careful with externally shared folders</%def>
        <%def name="footer()">
            <a href="#" class="btn" data-dismiss="modal">Cancel</a>
            <a href="#" id="owner-can-share-externally-confirm" class="btn btn-danger">Confirm</a>
        </%def>

        <p>You are adding a new Owner to this folder, which is shared with the
            following external users:</p>
        <ul id="owner-can-share-externally-user-list"></ul>
        <p>Please advise the Owner to be mindful and <strong>not to place
            confidential material into this folder</strong>.</p>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">editor-disallowed-modal</%def>
        <%def name="error()"></%def>
        <%def name="title()">Editors are not allowed</%def>
        <%def name="footer()">
            <a href="#" class="btn" data-dismiss="modal">Close</a>
        </%def>

        <p>Editors are not allowed in folders shared with external users.
            This folder is shared with the following external users:</p>
        <ul id="editor-disallowed-user-list"></ul>
        <p id="suggest-add-as-viewers">Please reinvite the new user as
            <strong>Viewer</strong> instead.</p>
    </%modal:modal>
</%def>

<%def name="ask_admin_modal()">
    <%modal:modal>
        <%def name="id()">ask-admin-modal</%def>
        <%def name="title()">Please contact your administrator</%def>
        <%def name="footer()">
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
        </%def>

        ## Note: the following text should be consistent with the text in
        ## CompInviteUsers.java.
        <p>To add more collaborators to this folder, a paid AeroFS plan is
            required for your team. Please contact your team administrator to
            upgrade the plan.
        </p>
    </%modal:modal>
</%def>