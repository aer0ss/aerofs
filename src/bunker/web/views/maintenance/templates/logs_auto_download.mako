## This page is used when the user clicks "Download appliance logs" from other
## pages. See maintenance_error_message.mako.
##

<%inherit file="marketing_layout.mako"/>
<%! page_title = "Downloading server Logs" %>

<%namespace name="common" file="logs_common.mako"/>

<div class="col-sm-6 col-sm-offset-3">
    <h3>Downloading logs</h3>
    <p>${common.submit_logs_text(False)}</p>
</div>

<%common:html/>

<%block name="scripts">
    <%common:scripts/>

    <script>
        $(document).ready(archiveAndDownloadLogs);
    </script>
</%block>
