## I prefer to use this simple alert() dialog rather than more complicated
## bootstrap modals. God knows if all IEs work well with bootstrap.
<%def name="scripts()">
    <script type="text/javascript">
        window.onload = function() {
            // Verified that this works with IE7-10
            if ($.browser.msie) {
                alert('Sorry, appliance setup pages do not currently support IE.' +
                  ' Once the appliance is set up, your users will be able to use' +
                  ' IE for self-service administration.');
            }
        }
    </script>
</%def>
