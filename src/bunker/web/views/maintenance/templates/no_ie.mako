## I prefer to use this simple alert() dialog rather than more complicated
## bootstrap modals. God knows if all IEs work well with bootstrap.
<%def name="scripts()">
    <script type="text/javascript">
        window.onload = function() {
            if (navigator.userAgent.indexOf("MSIE ") != -1 ||
                !!navigator.userAgent.match(/Trident.*rv\:11\./) ||
                !!navigator.userAgent.match(/Edge\/\d+/)){
                alert('Sorry, but our appliance setup pages do not currently support Internet Explorer or Microsoft Edge.' +
                  ' Please use a different browser for setup.\n\n' +
                  'Once the appliance is set up, your users will be able to use' +
                  ' Internet Explorer or Microsoft Edge for self-service administration.');
            }
        };
    </script>
</%def>
