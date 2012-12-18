<%inherit file="layout.mako"/>

<%
    from aerofs_sp.gen.sp_pb2 import ADMIN, USER
%>

<h1>AeroFS Web Interface</h1>
<ul class="nav">
    <li><a href="${request.route_path('accept')}">View Pending Invitations</a></li>
    %if request.session['group'] == ADMIN:
        <li><a href="${request.route_path('users')}">Manage Team</a></li>
    %endif
</ul>
