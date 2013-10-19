<%!
    from web.util import is_private_deployment, is_configuration_initialized
    import pyramid

    # Because <%inherit> works at the module level rather than request level,
    # we can't use template parameters to determine which file to inherit but
    # call Python methods directly.
    settings = pyramid.threadlocal.get_current_registry().settings
    if is_private_deployment(settings):
        if is_configuration_initialized(settings):
            inherit = "mode_supported_dashboard.mako"
        else:
            inherit = "mode_supported_fullscreen.mako"
    else:
        inherit = "mode_unsupported.mako"
%>

<%! page_title = "Setup" %>

<%inherit file="${inherit}"/>
