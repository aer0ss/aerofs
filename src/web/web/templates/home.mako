<%inherit file="layout.mako"/>

<%
    from aerofs.gen.sp_pb2 import ADMIN, USER
%>

<h1>AeroFS Web Interface</h1>
<p>Welcome to the AeroFS Web Interface! Use the links below to get started.</p>
<ul class="nav">
    <li><a href="${request.route_path('request_password_reset')}">Reset Your Password</a></li>
    %if request.session['group'] == ADMIN:
        <li><a href="${request.route_path('dashboard')}">Admin Panel</a></li>
    %else:
        <li><a href="${request.route_path('register_organization')}">Register Your Organization for Enterprise AeroFS</a></li>
    %endif
</ul>
