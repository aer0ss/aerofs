<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="common" file="backup_common.mako"/>

<h2>Back up your AeroFS Appliance</h2>

<p>Backing up your AeroFS Appliance is easy. Simply click the button below to
    download the appliance's user and configuration data as a single file.</p>

<p>The backup process may take a while, and during this time
    the system will enter maintenance mode. At the end of the process your
    browser will automatically download the backup file.</p>

<p>To restore an AeroFS Appliance from backup, boot up a new appliance
    and select the restore option during the first step.</p>

<hr/>

<p>
    <button class="btn btn-primary"
            onclick="backup(); return false;">
        Backup Now
    </button>
</p>

<%common:html/>

<%block name="scripts">
    ${common.scripts('maintenanceExit')}

    <script>
        function maintenanceExit(onSuccess, onFailure) {
            runBootstrapTask('maintenance-exit', onSuccess, onFailure);
        }
    </script>
</%block>
