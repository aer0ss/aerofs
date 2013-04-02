<%inherit file="dashboard_layout.mako"/>

<h2>Install AeroFS</h2>

## The text should be consistent with no_team_server_devices.mako
<p class="page_block">
    You don't have devices installed with AeroFS yet. Click the button to install it.
    AeroFS supports <strong>Windows</strong>, <strong>Mac OS X</strong>,
    <strong>Linux</strong>, and <strong>Android</strong>.
</p>
<p class="page_block">
    <a class="btn btn-primary btn-large" href="${request.route_path('download')}">Download AeroFS Now</a>
</p>
