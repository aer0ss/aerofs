<%inherit file="dashboard_layout.mako"/>

<%inherit file="dashboard_layout.mako"/>

<h2>Install AeroFS Team Server</h2>

## The text should be consistent with no_devices.mako
<p class="page_block">
    Your team doesn't have AeroFS Team Server installed.
    Click the button to download it.
</p>
<p class="page_block">
    <a class="btn btn-primary btn-large" href="${request.route_path('download')}">Download AeroFS Team Server</a>
</p>
<div class="page_block">
    <h4>What is Team Server?</h4>
    <p>
        AeroFS Team Server is an software application that can run on <strong>Windows</strong>,
        <strong>Mac OS X</strong>, or <strong>Linux</strong>. It securely backs
        up your entire team's files at a central location, either on premise or
        at Amazon S3. It provides a central access point for your team's files.
    </p>

    <h4>How to install Team Server?</h4>
    <p>
        It is as easy as installing AeroFS client applications:
    </p>
    <ol>
        <li>Download and run the installer.</li>
        <li>Enter your AeroFS credentials.</li>
        <li>Done!</li>
    </ol>

    <h4>How to add team members to sync?</h4>
    <p>
        Once installed, the server will immediately sync with your own AeroFS clients.
        To sync with colleagues and clients, simply
        <a href="${request.route_path('team_members')}">invite them to your team</a>.
        After they accept the invitation, all their AeroFS files will be synced
        to the Team Server.
    </p>

    <h4>What if I install multiple Team Servers?</h4>
    <p>
        Multiple servers installed under the same account will automatically
        replicate data amongst each other, whether they are in an office LAN or over
        the Internet.
    </p>

    <h4>For more information</h4>
    <p>
        To learn more about Team Server, take a look at our <a href="${request.route_path('features')}" target="_blank">Features</a> page, or read our white paper: <a href="${request.static_path('web:static/docs/what_aerofs_can_do_for_your_team.pdf')}" target="_blank">
            <em>What AeroFS can do for your team</em></a>.
    </p>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('.more_info_for_linux').popover({
                placement: 'bottom',
                html: true
            });
        });
    </script>
</%block>