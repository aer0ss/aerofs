<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Backup" %>

<%namespace name="common" file="backup_common.mako"/>

<h2>Update your AeroFS appliance</h2>

<p>TOOD (WW) complete the UI</p>

<p>
    Download the appliance's user data as a single file.
</p>

<button class="btn btn-primary"
        onclick="backup(); return false;">
    Create Back Up File
</button>

<%common:html/>

<%block name="scripts">
    ${common.scripts(False)}
</%block>
