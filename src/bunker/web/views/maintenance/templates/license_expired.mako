<%inherit file="marketing_layout.mako"/>
<%! page_title = "Maintenance Mode" %>

<div class="col-sm-8 col-sm-offset-2">
    <h3 class="text-center">AeroFS License Expired</h3>
    <p>
        Your organization's AeroFS license has expired. Please contact
        <a href="mailto:${support_email}">${support_email}</a>.
    </p>
    <p>
        If you are an administrator of this instance, please
        <a href="https://privatecloud.aerofs.com/" target="_blank">request a renewal</a> on our
        private cloud portal and <a href="${request.route_path('setup')}">upload the new license</a>
        to this system.
    </p>
    <p>
        For assistance with this process please contact the AeroFS
        <a href="sales@aerofs.com">sales team</a>.
    </p>
</div>
