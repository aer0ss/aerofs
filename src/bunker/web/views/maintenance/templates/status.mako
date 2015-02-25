<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Server Status" %>

<h2>Service status</h2>
%if possible_backup:
    <p>Any changes made on this appliance will <strong>not</strong> be reflected on other AeroFS appliances.
    If you make any changes through this appliance management portal, please download a backup file so you can synchronize these changes with other appliances.
    </p>
%endif

<div class="page-block footnote">
    %if possible_backup:
        Is this a backup appliance?
        <a href="${request.route_path('sync_settings')}">Update appliance settings here.</a>
        <br/>
    %endif

    Setting up automated service monitoring?
    <a href="${request.route_path('monitoring')}">Integrate this status with your existing sytems.</a>
</div>

<div class="page-block" id="server-status-div">
    <table class="table table-hover" style="border: 1px">
        <tbody id="server-status-tbody">
            % for server_status in server_statuses:
                ${render_server_row(server_status)}
            % endfor
        </tbody>
    </table>
</div>

<%def name="render_server_row(server_status)">
    <%
        service = server_status['service']
        is_healthy = server_status['is_healthy']
        message = server_status['message']

        if is_healthy:
            status_image = request.static_path("web:static/img/server_healthy.png")
        else:
            status_image = request.static_path("web:static/img/server_unhealthy.png")
    %>

    <tr>
        <td style="width: 16px;"><img src="${status_image}"/></td>
        <td>${service}</td>
        <td>${message}</td>
    </tr>
</%def>
