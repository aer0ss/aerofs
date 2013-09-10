## This template is used for non-dashboard pages.

<%!
    # Inherit from appropriate templates based on the deployment mode
    import pyramid
    if pyramid.threadlocal.get_current_registry().settings['deployment.mode'] == 'prod':
        inherit = "public_marketing_layout.mako"
    else:
        inherit = "private_marketing_layout.mako"
%>

<%inherit file="${inherit}"/>

## Main body
${next.body()}
