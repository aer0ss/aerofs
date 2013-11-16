## I prefer to use this simple alert() dialog rather than more complicated
## bootstrap modals. God knows if all IEs work well with bootstrap.
<%def name="scripts()">
    <!--[if IE]>
        <script type="text/javascript">
            window.onload = function() {
                alert('Sorry, appliance setup pages do not currently support IE.' +
                  ' Once the appliance is set up, your users will be able to use' +
                  ' IE for self-service administration.');
            }
        </script>
    <![endif]-->
</%def>