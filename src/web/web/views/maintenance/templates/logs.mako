<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Server Logs" %>

<%namespace name="common" file="logs_common.mako"/>

<h2>Server Logs</h2>

<p>Logs may be helpful when things go wrong. Click the button below to
    download this appliance's log files.</p>

<p><strong>We are here to help!</strong> Sumbit your logs at
    <a href="https://support.aerofs.com/anonymous_requests/new" target="_blank">
    support.aerofs.com</a> with a brief description of the problem. We will
    get back to you within one business day!</p>

<hr/>

<p>
    <a href="#" class="btn btn-primary"
            onclick="archiveAndDownloadLogs(); return false;">
        Download Appliance Logs
    </a>
</p>

<%common:html/>

<%block name="scripts">
    <%common:scripts/>
</%block>
