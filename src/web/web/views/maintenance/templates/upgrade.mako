<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="common" file="backup_common.mako"/>

<h2>Upgrade your AeroFS Appliance</h2>

<p>You may upgrade your AeroFS Appliance to a new version by following
    these steps:</p>

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

<p>During the backup process, some system services will be unavailable until
    the next time this appliance restarts.</p>

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
