<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="common" file="backup_common.mako"/>
<%namespace file="modal.mako" name="modal"/>

<div class="page-block">
    <h2>Upgrade your AeroFS Appliance</h2>

    <p>This appliance is running version <strong>${current_version}</strong>.
        You may check the latest release notes
        <a href="https://support.aerofs.com/entries/23864878" target="_blank">here</a>.

    <p>To upgrade the appliance to a new version, please follow these steps:</p>

    <ol>
        <li>
            Click "Create Backup File" below to create and download a backup file.
        </li>
        <li>
            Shut down this appliance and bring up a new appliance.
        </li>
        <li>
            Configure any of the required network parameters for the new appliance.
        </li>
        <li>
            Select the restore option during the first setup step.
        </li>
    </ol>
</div>

<div class="page-block">
    <p class="alert alert-success"><strong>Note</strong>: when the upgrade is
        complete, all AeroFS clients and Team Servers will automatically update
        to the new version within one hour.</p>
</div>

<div class="page-block">
    <button class="btn btn-primary"
            onclick="backup(); return false;">
        Create Backup File
    </button>
</div>

<%modal:modal>
    <%def name="id()">shutdown-modal</%def>
    <%def name="title()">Shutdown appliance</%def>
    <%def name="no_close()"/>
    <%def name="footer()">
        <a href="#" class="btn btn-danger"
                onclick="shutdown(); return false;">
            Download Completed. Shutdown Appliance</a>
    </%def>

    <p>This appliance is now in maintenance mode to prevent further modifications
        to the system's state. After the download completes, click the button
        below to shutdown the system.</p>
</%modal:modal>

<%modal:modal>
    <%def name="id()">shutdown-done-modal</%def>
    <%def name="title()">Shutting down</%def>
    <%def name="no_close()"/>
    <p>The system is shutting down. Please close this page and boot up a
        new appliance.</p>

    <p class="footnote">If the system doesn't power off on its own, you may turn
        it off manually.</p>
</%modal:modal>

<%common:html/>

<%block name="scripts">
    ${common.scripts('promptShutdown')}

    <script>
        $(document).ready(function() {
            disableEsapingFromModal($('div.modal'));
        });

        function promptShutdown(onSuccess) {
            onSuccess();
            $('#shutdown-modal').modal('show');
        }

        function shutdown() {
            enqueueBootstrapTask('shutdown-system', showShutdownDone);
        }

        function showShutdownDone() {
            $('#shutdown-modal').modal('hide');
            $('#shutdown-done-modal').modal('show');
        }
    </script>
</%block>
