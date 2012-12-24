<%inherit file="layout.mako"/>

<%
    from aerofs_sp.gen.sp_pb2 import ADMIN, USER
%>

<div class="span12 home_nav">
        <h2><a href="${request.route_path('accept')}">View Pending Invitations</a></h2>

    %if request.session['group'] == ADMIN:
        <h2><a href="${request.route_path('users')}">Manage Team</a></h2>
    %endif
</div>
