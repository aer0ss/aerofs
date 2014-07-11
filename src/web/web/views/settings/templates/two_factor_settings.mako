<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Two-Factor Authentication Settings" %>

<%namespace file="modal.mako" name="modal"/>

<h2>Two-Factor Authentication Settings</h2>

% if two_factor_enforced:
<div class="row">
    <div class="col-sm-6">
        Status: <strong>Enabled</strong>
    </div>
    <div class="col-sm-6 col-sm-6-offset-6">
        <form class="form-horizontal" role="form" id="disable-form" method="post" action="${request.route_path('two_factor_disable')}">
            ${self.csrf.token_input()}
            <fieldset>
                <div class="form-group">
                    <div class="col-sm-12">
                        <button class="btn btn-danger" class="form-control" id="disable-btn">Disable</button>
                    </div>
                </div>
            </fieldset>
        </form>
    </div>
</div>

<div class="row">
    <div class="col-sm-12">
        <h4>Backup codes</h4>
        <p>Backup codes can be used to access your account in the event you
        lose access to your mobile device.</p>
        <p>For security reasons, AeroFS support cannot restore or reset access
        to accounts with two-factor authentication enabled.
        <strong>Saving your backup codes in a safe place (like a wallet) can
        keep you from being locked out of your account.</strong></p>
        <p><a href="${request.route_path('two_factor_download_backup_codes')}">Download backup codes</a></p>
        <p><a href="#" onclick="showBackupCodes(); return false;">View backup codes</a></p>
    </div>
</div>

% else:

<div class="row">
    <div class="col-sm-12">
        <p>Status: Disabled</p>
        <p><a href="${request.route_path('two_factor_intro')}">Set up &raquo;</a></p>
    </div>
</div>

% endif

<div class="row">
    <hr>
    <div class="col-sm-12">
        <a href="${request.route_path('settings')}">Back to settings</a>
    </div>
</div>

<%modal:modal>
    <%def name="id()">show-backup-codes-modal</%def>
    <%def name="title()">Backup Codes</%def>
    <%def name="footer()">
        <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
    </%def>
    <ul>
    % for code in backup_codes:
        <li>
        % if code["date_used"] == 0:
            ${code["code"]}
        % else:
            <s>${code["code"]}</s>
        % endif
        </li>
    % endfor
    </ul>
</%modal:modal>

<%block name="scripts">
    <script>
    function showBackupCodes() {
        $('#show-backup-codes-modal').modal('show');
    }
    </script>
</%block>