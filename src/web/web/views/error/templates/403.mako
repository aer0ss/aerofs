<%inherit file="marketing_layout.mako"/>
<%! page_title = "Forbidden" %>

<div class="row">
    <div class="span6 offset3">
        <h2>403: Forbidden Action Error</h2>
        <p>Sorry! It looks like you aren't allowed to perform this operation.</p>
        <p>Are you an admin of your organization in AeroFS? If so, <a href="${request.route_path('dashboard_home')}">please sign in</a>.</p>
    </div>
</div>
