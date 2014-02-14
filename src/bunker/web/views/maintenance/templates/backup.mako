<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="common" file="backup_common.mako"/>

<h2>Back up your AeroFS Appliance</h2>

<p>Backup and restore your AeroFS Appliance with the following steps:</p>

<ol>
    <li>
        Write down network configuration found in the appliance console.
    </li>
    <li>
        Click the button below to download the backup file.
    </li>
    <li>
        To restore, launch a new appliance image and select the restore option
        during the setup.
    </li>
</ol>

<p>The backup process may take a while. During this time, the system will enter
    maintenance mode.</p>

<hr/>

<p>
    <button class="btn btn-primary"
            onclick="backup(); return false;">
        Download Backup File
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
