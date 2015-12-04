<%inherit file="marketing_layout.mako"/>
<%! page_title = "Maintenance Mode" %>

<div class="col-sm-12 text-center">
    <h3>Down for maintenance</h3>
    <p>Your AeroFS administrators are performing maintenance.</p>

    <%
        mng_url = 'https://' + str(request.registry.settings['base.host.unified']) + '/admin'
    %>

    <p>Administrators please visit your
    <a href="${mng_url}">Appliance Management Interface</a> to configure
    this system.</p>
</div>
