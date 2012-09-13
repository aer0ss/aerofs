<%inherit file="layout.mako"/>

<%
    from aerofs.gen.sp_pb2 import ADMIN, USER
%>

<h1>AeroFS Web Interface</h1>
<p>Welcome to the AeroFS Web Interface!</p>
<ul class="nav">
    <li><a href="${request.route_path('request_password_reset')}">Reset Your Password</a></li>
    %if request.session['group'] == ADMIN:
        <li><a href="${request.route_path('dashboard')}">Admin Panel</a></li>
    %endif
</ul>

%if request.session['group'] == USER:
    <p>Sorry! AeroFS's web interface isn't yet fully functional for general users. Please
    check the <a href="http://blog.aerofs.com/">AeroFS Blog</a> for new feature announcements
    and updates from our team.</p>
%endif
