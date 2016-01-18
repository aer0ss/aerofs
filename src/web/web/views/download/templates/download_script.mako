<%block name="scripts">
    ##  Used in download.mako and downloading.mako.
    ##  We explicitly add in the left hand navigation class.
    ##  You can get to this page a variety of ways and we want to make sure the
    ##  correct tab is highlighted when navigating. Team Server should always
    ##  be highlighted if on the team server page. But My Devices should only
    ##  be highlighted if you came here from my devices, not from install or signup.

    %if is_team_server:

        <script type="text/javascript">
            $(function () {
                $("li.team_server_devices").addClass("active");
            });
        </script>

    %elif  msg_type == 'no_device':

        <script type="text/javascript">
            $(function (){
                $("li.my_devices").addClass("active");
            });
        </script>

    %endif

</%block>