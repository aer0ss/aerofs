## This template is used for non-dashboard pages.

<%!
    # Inherit from appropriate templates based on the deployment mode
    import pyramid
    if pyramid.threadlocal.get_current_registry().settings['deployment.mode'] \
            == 'private':
        inherit = "private_team_settings.mako"
    else:
        inherit = "public_team_settings.mako"
%>

<%inherit file="${inherit}"/>

## Main body
${next.body()}
