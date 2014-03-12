<%inherit file="marketing_layout.mako"/>
<%! page_title = "Maintenance Mode" %>

<div class="offset2 span8">
    <h3 class="text-center">Sorry. AeroFS License Expired</h3>
    <p>Your organization's AeroFS license has expired. Please contact <a href="mailto:${support_email}">${support_email}</a>.</p>
    <p>If you are an admin of the organization, please <a href="mailto:support@aerofs.com"
        target="_blank">renew your license</a> and then upload the new license
        <a href="${request.route_path('setup')}">here</a>.</p>
</div>