## Note: Some CSS relevant to these modals are specified in shared_folders.mako
##
## TODO (WW) move JS and CSS code that refers to names in this file to here?

<%namespace file="modal.mako" name="modal"/>

<%def name="main_modals()">
    <%modal:modal>
        <%def name="id()">modal</%def>
        <%def name="title()">
            <span id="modal-folder-title"></span>&nbsp;
            <i id="modal-folder-title-info-icon" class="icon-info-sign"></i>
        </%def>
        <%def name="footer()">
            <form id="modal-invite-form" class="form-inline" method="post">
                <span id="invite-user-inputs" class="pull-left">
                    <input type="text" id="modal-invitee-email" placeholder="Add email">
                    as
                    <input type="hidden" id="modal-invite-role">
                    ## .btn-group is needed for the button drop-down menu
                    <div class="btn-group">
                        <a class="btn dropdown-toggle" data-toggle="dropdown" href="#">
                            ## do not use .caret as it doesn't align correctly
                            ## in the vertical direction -- I don't know why
                            <span id="modal-invite-role-label"></span>&nbsp;&#x25BE;
                        </a>
                        ## use .text-left otherwise menu items would be right aligned
                        ## -- I don't know why
                        <ul id="modal-invite-role-menu" class="dropdown-menu text-left"></ul>
                    </div>
                    <input class="btn btn-primary" type="submit" value="Invite to Folder">
                </span>
                ## Use a space to keep the spinner in place
                <span id="modal-spinner" class="pull-left" style="margin-left: 30px;">&nbsp;</span>
                <a href="#" class="btn" data-dismiss="modal">Close</a>
            </form>
        </%def>
        <%def name="modal_style()">width: 700px</%def>
        <%def name="body_style()">min-height: 300px</%def>

        <table id="modal-user-role-table" class="table table-hover">
            ## min-width to avoid table cell shifting due to different lengths of role
            ## strings, when the user updates members roles.
            <thead><tr><th>Member</th><th style="min-width: 64px">Role
                <a href="https://support.aerofs.com/entries/22831810" target="_blank"><i class="icon-question-sign"></i></a></th>
                <th></th></tr></thead>
            <tbody></tbody>
        </table>
    </%modal:modal>

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
            person's sync history.
        </p>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">add-external-user-modal</%def>
        <%def name="title()">Be careful with externally shared folders</%def>
        <%def name="footer()">
            <a href="#" class="btn" data-dismiss="modal">Cancel</a>
            <a href="#" id="add-external-user-confirm" class="btn btn-danger">Proceed</a>
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
            <a href="#" id="owner-can-share-externally-confirm" class="btn btn-danger">Proceed</a>
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

