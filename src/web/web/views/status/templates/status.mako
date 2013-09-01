<%!
    import pyramid
    if pyramid.threadlocal.get_current_registry().settings['deployment.mode'] == 'modular':
        inherit = "private_status.mako"
    else:
        inherit = "public_status.mako"
%>

<%inherit file="${inherit}"/>

## Main body
${next.body()}
