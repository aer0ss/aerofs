<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="common" file="backup_common.mako"/>

<h2>Upgrade your AeroFS Appliance</h2>

<p>This appliance runs version <strong>${current_version}</strong>.
    You may check the latest release notes
    <a href="https://support.aerofs.com/entries/23864878" target="_blank">here</a>.

    <p>To upgrade the appliance to a new version, please follow these steps:</p>

<ol>
    <li>
        Click the button below to create and download a backup file.
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

<p>Once you initiate the Backup process, some system services will
become unavailable until the next time this appliance is restarted.</p>

<hr/>

<p>
    <button class="btn btn-primary"
            onclick="backup(); return false;">
        Create Backup File
    </button>
</p>

<%common:html/>

<%block name="scripts">
    ${common.scripts(False)}
</%block>
