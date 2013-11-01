## This template is used for non-dashboard pages.

<%!
    # Inherit from appropriate templates based on the deployment mode
    from web.util import is_private_deployment
    import pyramid
    if is_private_deployment(pyramid.threadlocal.get_current_registry().settings):
        inherit = "private_marketing_layout.mako"
    else:
        inherit = "public_marketing_layout.mako"
%>

<%inherit file="${inherit}"/>

## Main body
${next.body()}
