<div class="page_block" id="server-status-div">
    <table class="table" style="border: 1px">
        <thead><th style="width:4%"></th><th style="width:12%"></th><th style:"width:84%"></th></thead>
        <tbody id="server-status-tbody">
            % for server_status in unified_server_statuses:
                ${render_server_row(server_status)}
            % endfor
        </tbody>
    </table>
</div>

## TODO (MP) move to a common spot.
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
        <td><img src=${status_image} /></td>
        <td>${service}</td>
        <td>${message}</td>
    </tr>
</%def>
