<%! from web.util import is_maintenance_mode %>

<%def name='html()'>
    %if is_maintenance_mode():
        <div class="alert">
            <strong>The system is in maintenance mode.</strong><br>Remember to
                <a href="${request.route_path('toggle_maintenance_mode')}">
                exit the mode</a> once maintenance is done.
        </div>
    %endif
</%def>