<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Server Status" %>

<h2>Service Statuses</h2>
<br/>

## TODO (MP) instead of "is healthy" boolean, use an icon (green check or red "x").

<h4>Configuration Box</h4>
<div class="page_block" id="server-status-div">
    <table class="table" style="border: 1px">
        <thead><th style="width:4%"></th><th style="width:12%"></th><th style:"width:84%"></th></thead>
        <tbody id="server-status-tbody">
            % for server_status in config_ca_server_statuses:
                ${render_server_status_row(server_status)}
            % endfor
        </tbody>
    </table>
</div>

<h4>Database Box</h4>
<div class="page_block" id="server-status-div">
    <table class="table" style="border: 1px">
        <thead><th style="width:4%"></th><th style="width:12%"></th><th style:"width:84%"></th></thead>
        <tbody id="server-status-tbody">
            % for server_status in database_server_statuses:
                ${render_server_status_row(server_status)}
            % endfor
        </tbody>
    </table>
</div>

<h4>Transient Box</h4>
<div class="page_block" id="server-status-div">
    <table class="table" style="border: 1px">
        <thead><th style="width:4%"></th><th style="width:12%"></th><th style:"width:84%"></th></thead>
        <tbody id="server-status-tbody">
            % for server_status in transient_server_statuses:
                ${render_server_status_row(server_status)}
            % endfor
        </tbody>
    </table>
</div>

<%def name="render_server_status_row(server_status)">
    <%
        service = server_status['service']
        is_healthy = server_status['is_healthy']
        message = server_status['message']

        if is_healthy == True:
            status_image = request.static_path("web:static/img/server_healthy.png")
        else:
            status_image = request.static_path("web:static/img/server_unhealthy.png")
    %>

    ## TODO (MP) need logos here and better formatting.
    <tr>
        <td><img src=${status_image} /></td>
        <td>${service}</td>
        <td>${message}</td>
    </tr>
</%def>
