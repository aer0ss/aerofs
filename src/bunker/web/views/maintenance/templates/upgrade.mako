<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Upgrade" %>

<%namespace name="common" file="backup_common.mako"/>
<%namespace file="modal.mako" name="modal"/>

<div class="page-block">
    <h2>Upgrade your AeroFS Appliance</h2>

    <p>This appliance is running version <strong>${current_version}</strong>.
        You may check the latest release notes
        <a href="https://support.aerofs.com/hc/en-us/articles/201439644" target="_blank">here</a>.

    <p>To upgrade the appliance to a new version, please follow these steps:</p>

    <ol>
        <li>
            Write down network configuration found in the appliance console.
        </li>
        <li>
            Click the button below to download the backup file and shut down
            the appliance.
        </li>
        <li>
            Launch a new appliance image and select the restore option during
            setup.
        </li>
    </ol>
</div>

<div class="page-block">
    <p class="alert alert-success"><strong>Note</strong>: when the upgrade is
        done, AeroFS clients and Team Servers will automatically update
        in one hour.</p>
</div>

<div class="page-block">
    <button class="btn btn-primary"
            onclick="backup(promptShutdown); return false;">
        Download Backup File
    </button>
</div>

<%modal:modal>
    <%def name="id()">shutdown-modal</%def>
    <%def name="title()">Please shut down appliance</%def>
    <%def name="no_close()"/>

    <p>After the download completes, please shut down the system and boot up a new appliance.
        This appliance is now in maintenance mode and no further operations should be made.
    </p>
</%modal:modal>

<%common:html/>

<%block name="scripts">
    <%common:scripts/>

    <script>
        $(document).ready(function() {
            disableEscapingFromModal($('div.modal'));
        });

        function promptShutdown(onSuccess) {
            onSuccess();
            $('#shutdown-modal').modal('show');
        }
    </script>
</%block>
