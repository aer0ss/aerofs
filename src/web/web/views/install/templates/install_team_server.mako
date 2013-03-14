<%inherit file="layout.mako"/>
<%! navigation_bars = True; %>

<%block name="css">
    <style type="text/css" xmlns="http://www.w3.org/1999/html"
           xmlns="http://www.w3.org/1999/html"
           xmlns="http://www.w3.org/1999/html">
        .thumbnail {
            text-align: center;
            padding-top: 10px;
        }
        .thumbnail a, span {
            margin: 0 auto;
        }
        .step_header {
            font-size: 1.5em;
            line-height: 1.5em;
            margin-bottom: 0.5em;
        }
    </style>
</%block>

<div class="row page_block">
    <div class="span8">
        <h2>
            Download AeroFS Team Server
        </h2>
    </div>
</div>

<div class="row page_block">
    <div class="span8">
        <ul class="thumbnails">
            <li class="span2">
                <div class="thumbnail">
                    <p class="badge badge-info">Windows</p>
                    <h2 class="step_header"><a href="http://cache.client.aerofs.com/AeroFSTeamServerInstall.exe">
                        Download<br>for Windows
                    </a></h2>
                    <p>Windows XP +</p>
                </div>
            </li>
            <li class="span2">
                <div class="thumbnail">
                    <p class="badge">Mac OS X</p>
                    <h2 class="step_header"><a href="http://cache.client.aerofs.com/AeroFSTeamServerInstall.dmg">
                        Download<br>for Mac OS X
                    </a></h2>
                    <p>Snow Leopard +</p>
                </div>
            </li>
            <li class="span2">
                <div class="thumbnail">
                    <p class="badge badge-success">Linux</p>
                    <h2 class="step_header"><a href="http://cache.client.aerofs.com/aerofsts-installer.deb">
                        Download<br>for Ubuntu
                    </a></h2>
                    <p>
                        <a class="more_info_for_linux" href="#"
                            data-title="Linux .deb Package"
                            data-content="
                                ${linux_cli_instructions()}<br>
                                <br>
                                Support Ubuntu 8.04 +
                                "
                        >More Info</a>
                    </p>
                </div>
            </li>
            <li class="span2">
                <div class="thumbnail">
                    <p class="badge badge-success">Linux .tgz</p>
                    <h2 class="step_header"><a href="http://cache.client.aerofs.com/aerofsts-installer.tgz">
                        Download<br>for other Linux
                    </a></h2>
                    <p>
                        <a class="more_info_for_linux" href="#"
                            data-title="Linux .tgz Package"
                            data-content="
                                This package doesn't require installation of .deb packages.
                                While we only support Ubuntu, users have reported successful installs on some non-Ubuntu distributions.<br>
                                <br>
                                ${linux_cli_instructions()}
                            "
                        >More Info</a>
                    </p>
                </div>
            </li>
        </ul>
    </div>
</div>

<%def name="linux_cli_instructions()">
    This package includes both graphical and headless interfaces. To launch the Team Server in command line, run <code>aerofsts-cli</code>.
</%def>

<div class="page_block">
    <p>
        AeroFS Team Server securely backs up your entire team's files at a central
        location, either on on-premise storage or Amazon S3. It provides a
        central access point for your team's files.
        <a href="https://aerofs.com/business" target="_blank">Read more</a>.
    </p>

    <h3>Installing is easy</h3>
    <ol>
        <li>Download and run the program.</li>
        <li>Enter your AeroFS credentials.</li>
        <li>Done!</li>
    </ol>

    <h3>Adding team members to sync</h3>
    <p>
        Once installed, the server will immediately sync with your own AeroFS clients.
        To sync with colleagues and clients, simply
        <a href="${request.route_url('admin_users')}">invite them to your team</a>.
        After they accept the invitation, all their AeroFS files will be synced
        to the Team Server.
    </p>

    <h3>Server replication</h3>
    <p>
        Multiple servers installed under the same account will automatically
        replicate data amongst each other, whether they are in an office LAN or over
        the Internet.
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