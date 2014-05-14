## Note: Some CSS relevant to these modals are specified in shared_folders.mako
##
## TODO (WW) move JS and CSS code that refers to names in this file to here?

<%namespace file="modal.mako" name="modal"/>

<%def name="main_modals()">
    <%modal:modal>
        <%def name="id()">modal</%def>
        <%def name="title()">
            <span id="modal-folder-title"></span>&nbsp;
            <span id="modal-folder-title-info-icon" class="glyphicon glyphicon-info-sign"></span>
        </%def>
        <%def name="footer()">
            <form id="modal-invite-form" class="form-inline" method="post">
                <span id="invite-user-inputs" class="pull-left">
                    <input type="text" id="modal-invitee-email" placeholder="Add email">
                    as
                    <input type="hidden" id="modal-invite-role">
                    ## .btn-group is needed for the button drop-down menu
                    <div class="btn-group">
                        <a class="btn btn-default dropdown-toggle" data-toggle="dropdown" href="#">
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
                <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
            </form>
        </%def>

        <table id="modal-user-role-table" class="table table-hover">
            ## min-width to avoid table cell shifting due to different lengths of role
            ## strings, when the user updates members roles.
            <thead><tr><th>Member</th><th style="min-width: 64px">Role
                <a href="https://support.aerofs.com/entries/22831810" target="_blank"><span class="glyphicon glyphicon-question-sign"></span></a></th>
                <th></th></tr></thead>
            <tbody></tbody>
        </table>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">remove-modal</%def>
        <%def name="title()">Remove from folder</%def>
        <%def name="footer()">
            <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
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
        <%def name="id()">sharing-warning-modal</%def>
        <%def name="title()">Error</%def>
        <%def name="footer()">
            <a href="#" class="btn btn-default" data-dismiss="modal">Cancel</a>
            <a href="#" id="sharing-warning-confirm" class="btn btn-danger">Proceed</a>
        </%def>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">sharing-error-modal</%def>
        <%def name="title()">Warning</%def>
        <%def name="error()"></%def>
        <%def name="footer()">
            <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
        </%def>
    </%modal:modal>
</%def>

