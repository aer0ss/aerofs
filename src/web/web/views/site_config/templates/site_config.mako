<%!
    import pyramid
    mode = pyramid.threadlocal.get_current_registry().settings['deployment.mode']

    dashboard = False
    if 'base.configuration.initialized' in pyramid.threadlocal.get_current_registry().settings:
        initialized = pyramid.threadlocal.get_current_registry().settings['base.configuration.initialized']
        if initialized == 'true':
            dashboard = True

    if mode == 'private':
        if dashboard:
            include = "mode_supported_dashboard.mako"
        else:
            include = "mode_supported_fullscreen.mako"
    else:
        include = "mode_unsupported.mako"

%>

<%! page_title = "Site Configuration" %>

<%inherit file="${include}"/>
