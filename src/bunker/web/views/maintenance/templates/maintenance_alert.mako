<%! from web.util import is_maintenance_mode %>

<%def name='html()'>
    %if is_maintenance_mode():
        <div class="alert">
            <strong>The system is in maintenance mode.</strong><br>Remember to
            exit the mode once maintenance is done.
        </div>
    %endif
</%def>