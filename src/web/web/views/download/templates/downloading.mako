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
            ('Double-Click the icon', 'Double click the {} icon in your Applications folder to launch the program!'.format(program))
        ]
    }
    deb_url = request.static_path('web:installer/' + deb)
    tgz_url = request.static_path('web:installer/' + tgz)
    linux_data = {
        'id': 'linux',
        'name': 'Linux',
        'url': deb_url,
        'header_note': 'Non-Ubuntu users can also download the <a href="{}"> tgz archive</a>.'.format(tgz_url),
        'steps': [
            ('Download ' + program, '<strong>Command-line users</strong>: please copy & paste this URL: <code>{}</code> or this URL for the tgz archive: <code>{}</code>'.format(deb_url, tgz_url)),
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

## Use an iframe to start download automatically
## http://stackoverflow.com/questions/156686/how-to-start-automatic-download-of-a-file-in-internet-explorer
<iframe width="1" height="1" frameborder="0" src="${data['url']}"></iframe>

<h2>Downloading ${program}...</h2>
<p>
    ${program} download should automatically start within seconds. If it doesn't,
    <a href="${data['url']}">click here</a> to restart.
</p>
%if 'header_note' in data:
    <p>${data['header_note'] | n}</p>
%endif

<div class="row-fluid top-divider top-divider-margin" style="padding-top: 40px;"></div>

%for i in range(len(data['steps'])):
    ${instruction(data['steps'][i], i)}
%endfor

<%def name="instruction(step, index)">
    <div class="row-fluid page_block">
        <div class="span6">
            <img src="${request.static_path('web:static/img/download/{}{}.png'
            .format(data['id'], index))}">
        </div>
        <div class="span6">
            <h4>${index + 1}. ${data['steps'][index][0]}</h4>
            <p>${data['steps'][index][1] | n}</p>
        </div>
    </div>
</%def>
