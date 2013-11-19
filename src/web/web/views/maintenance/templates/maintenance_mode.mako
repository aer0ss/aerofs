<%inherit file="marketing_layout.mako"/>
<%! page_title = "Maintenance Mode" %>

<%! from web.util import is_maintenance_mode %>

<% support_email = request.registry.settings['base.www.support_email_address'] %>

<div class="span8 offset2">
    <h3 class="text-center">Down for maintenance</h3>
    <p>Your AeroFS adminstrators are performing scheduled maintenance.
        Please contact <a href="mailto:${support_email}">${support_email}</a>
        if you have any questions.</p>
</div>

<%def name='alert()'>
    %if is_maintenance_mode():
        <div class="alert">
            <strong>The system is in maintenance mode.</strong><br>Remember to
            exit the mode once maintenance is done.
        </div>
    %endif
</%def>