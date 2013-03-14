<%inherit file="layout.mako"/>

<%block name="css">
    <style type="text/css">
        .thumbnail {
            text-align: center;
            padding-top: 10px;
        }
        .thumbnail a, span {
            margin: 0 auto;
        }
        .thumbnail .badge {
            word-spacing: 1em;
        }
        .step_header {
            font-size: 1.5em;
        }
    </style>
</%block>

<div class="span9 offset2">
    <h2 class="page_block">Thank you for signing up! Get started with your business account.</h2>
    <ul class="thumbnails">
        <li class="span3">
            <div class="thumbnail">
                <p class="badge">STEP 1</p>
                <a href="${request.route_url('install')}" target="_blank">
                    <h2 class="step_header">Install Free Clients</h2>
                    <p>if you haven't done so.<br>&nbsp;</p>
                </a>
            </div>
        </li>
        <li class="span3">
            <div class="thumbnail">
                <p class="badge">STEP 2</p>
                <a href="${request.route_url('team_members')}" target="_blank">
                    <h2 class="step_header">Add Team Members</h2>
                    <p>to manage their permissions and shared folders.</p>
                </a>
            </div>
        </li>
        <li class="span3">
            <div class="thumbnail">
                <p class="badge">STEP 3</p>
                <a href="${request.route_url('install_team_server')}" target="_blank">
                    <h2 class="step_header">Install Team Servers</h2>
                    <p>to sync and access all the team data centrally.</p>
                </a>
            </div>
        </li>
    </ul>
    <p style="margin-top: 30px;">Find FAQ and community at
        <a href="http://support.aerofs.com" target="_blank">support.aerofs.com</a>.
        Email <a href="mailto:business@aerofs.com" target="_blank">support@aerofs.com</a>
        for priority support.
    </p>
</div>