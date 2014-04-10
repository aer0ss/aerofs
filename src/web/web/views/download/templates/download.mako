<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Download" %>

<%block name="css">
    <style type="text/css">
        .btn-large {
            margin-top: 60px;
            padding-left: 70px;
            padding-right: 70px;
        }
        .subtitle {
            font-size: 70%;
        }
        .download-message {
            font-style: italic;
            font-size: 120%;
        }
        .description-block {
            margin-top: 60px;
            padding-top: 20px;
        }
    </style>
</%block>

<%
    if is_team_server:
        program = 'Team Server'
        downloading_path = request.route_path('downloading_team_server')
        description_mako = 'download_team_server_description.mako'
    else:
        program = 'AeroFS'
        downloading_path = request.route_path('downloading')
        description_mako = 'download_description.mako'

    os_names = {
        'osx': 'Mac OS X',
        'win': 'Windows',
        'linux': 'Linux'
    }

    def downloading_url(os):
        return '{}?{}={}'.format(downloading_path, url_param_os, os)

    # don't use params[] to avoid KeyError exceptions
    # N.B. this string is hard coded in some source files. Search for 'msg_type'
    # to find them
    msg_type = request.params.get('msg_type')
    if msg_type == 'signup':
        headline = False
        signup_tagline = True
        no_device_tagline = False
    elif msg_type == 'no_device':
        headline = True
        signup_tagline = False
        no_device_tagline = True
    else:
        headline = True
        signup_tagline = False
        no_device_tagline = False
%>

## Header and sub-header
%if headline:
    <h2>Install ${program}</h2>
%endif

%if signup_tagline:
    <p class="download-message text-success" style="margin-top: 30px;">
        Way to go! You've signed up for AeroFS. Install it now.
    </p>
%endif

%if no_device_tagline:
    <p class="download-message muted">
        %if is_team_server:
            Your team doesn't have ${program} installed. Install it now?
        %else:
            You don't have devices installed with ${program} yet. Install it now?
        %endif
    </p>
%endif

## The big button
<p>
    <a class="btn btn-primary btn-large" href="${downloading_url(os)}">
        Download ${program}<br><span class="subtitle">for ${os_names[os]}</span></a>
</p>

## Also available for...
<p>
    <%
        if os == 'osx':
          also_available = ('win', 'linux')
        elif os == 'linux':
            also_available = ('win', 'osx')
        else:
            also_available = ('osx', 'linux')

        avail1 = '<a href="{}">{}</a>'.format(downloading_url(also_available[0]),
            os_names[also_available[0]])
        avail2 = '<a href="{}">{}</a>'.format(downloading_url(also_available[1]),
            os_names[also_available[1]])
    %>
    Also available for
    %if is_team_server:
        ${avail1 | n} and ${avail2 | n}.
    %else:
        ${avail1 | n}, ${avail2 | n}, and
        <a href="${request.route_path('add_mobile_device')}">mobile devices</a>.
    %endif
</p>


## Descriptions
<div class="top-divider description-block">
    <%include file="${description_mako}"/>
</div>
