<%! from web.views.maintenance.maintenance_util import is_maintenance_mode %>

<%def name='html()'>
    %if is_maintenance_mode(request.registry.settings):
        <div class="alert alert-danger">
            <strong>The system is in maintenance mode.</strong><br>Remember to
                <a href="${request.route_path('toggle_maintenance_mode')}">
                exit the mode</a> when maintenance is done.
        </div>
    %endif
</%def>
