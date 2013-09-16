<%!
    import pyramid
    mode = pyramid.threadlocal.get_current_registry().settings['deployment.mode']

    if mode == 'private':
        include = "mode_private.mako"
    else:
        include = "mode_unsupported.mako"
%>

<%inherit file="dashboard_layout.mako"/>

<%! page_title = "Server Status" %>
<h2>Service Status</h2>
<br/>

<%include file="${include}"/>
