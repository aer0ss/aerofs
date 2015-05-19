<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Downloading" %>

<%
    # (WW) Ideally all python logic should go to views.py. But I'm not sure for
    # this case since all the logic here are very presentational. We can
    # refactor the code later if needed.

    win_data = {
        'id': 'win',
        'name': 'Windows',
        'url': request.static_path('web:installer/' + exe),
        'steps': [
            ('Run the installer', 'Click on the .exe file that just downloaded.'),
            ('Click Yes', 'Click Yes to accept the User Account Control settings dialog.'),
            ('Follow the Installation Instructions', 'Follow the instructions to get {} setup on your computer!'.format(program))
        ]
    }
    osx_data = {
        'id': 'osx',
        'name': 'Mac OS X',
        'url': request.static_path('web:installer/' + dmg),
        'steps': [
            ('Run the installer', 'Click on the .dmg file that just downloaded.'),
            ('Drag the icon', 'Drag the {} icon into your Applications folder to copy it to your computer.'.format(program)),
            ('Double-click the icon', 'Double click the {} icon in your Applications folder to launch the program!'.format(program))
        ]
    }
    deb_path = request.static_path('web:installer/' + deb)
    tgz_path = request.static_path('web:installer/' + tgz)
    linux_data = {
        'id': 'linux',
        'name': 'Linux',
        'url': deb_path,
        'header_note': 'Non-Ubuntu users can also download the <a href="{}"> tgz archive</a>.'.format(tgz_path),
        'steps': [
            ('Download ' + program, '<strong>Command-line users</strong>: please copy & paste this URL: <code><span class="host-url"></span>{}</code> or this URL for the tgz archive: <code><span class="host-url"></span>{}</code>'.format(deb_path, tgz_path)),
            ('Install ' + program, 'Use your favorite package manager to install the deb package, or simply uncompress the tgz archive.'),
            ('Run ' + program, 'Click Applications > Internet > {0} and run! Or use <code>$ {1}</code> to start {0} daemon process, and <code>$ {2}</code> to access its functions interactively.'.format(program, cli, sh))
        ]
    }

    if os == 'osx':
        data = osx_data
    elif os == 'linux':
        data = linux_data
    else:
        data = win_data
%>

<%block name="css">
    <link href="${request.static_path('web:static/css/download.css')}" rel="stylesheet">
</%block>

<h2>Downloading ${program}...</h2>
<p>
    ${program} download should automatically start within seconds. If it doesn't,
    <a href="${data['url']}">click here</a> to restart.
</p>

%if 'header_note' in data:
    <p>${data['header_note'] | n}</p>
%endif

## Use an iframe to start download automatically
## http://stackoverflow.com/questions/156686/how-to-start-automatic-download-of-a-file-in-internet-explorer
<iframe width="1" height="1" frameborder="0" src="${data['url']}"></iframe>

<div class="installation-instructions">
    <h3>Installation</h3>
    %for i in range(len(data['steps'])):
        ${instruction(data['steps'][i], i)}
    %endfor
</div>

<%def name="instruction(step, index)">
    <div class="row page-block">
        <div class="col-md-6 instruction">
            <img class="img-responsive" src="${request.static_path('web:static/img/download/{}{}.png'
            .format(data['id'], index))}">
        </div>
        <div class="col-md-6">
            <h4>${index + 1}. ${data['steps'][index][0]}</h4>
            <p>${data['steps'][index][1] | n}</p>
        </div>
    </div>
</%def>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            $('.host-url').text(window.location.protocol + "//" + window.location.hostname);
        });
    </script>
</%block>