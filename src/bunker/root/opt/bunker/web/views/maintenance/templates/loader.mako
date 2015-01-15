<%def name="scripts()">
    <script>
        ## Reboot the app then call callback if it's non-null.
        ## Expected signatures:
        ##          function onSuccess()
        ##          function onFailure(xhr)
        function reboot(repo, tag, target, onSuccess, onFailure) {
            ## Get current boot ID before rebooting
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var bootID = resp['id'];
                var args = repo + "/" + tag + "/" + target;
                console.log("reboot to " + args + ". previous boot id: " + bootID);
                $.post("/json-boot/" + args)
                .done(function() {
                    waitForReboot(bootID, onSuccess);
                }).fail(function(xhr, textStatus, errorThrown) {
                    ## Ignore errors as the server might be killed before replying
                    console.log("ignore json-boot failure: " + xhr.status + " " + textStatus + " " + errorThrown);
                    waitForReboot(bootID, onSuccess);
                });

            }).fail(function(xhr) {
                if (onFailure) onFailure(xhr);
            });
        }

        function waitForReboot(oldBootID, onSuccess) {
            console.log('wait for reboot to finish');
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-get-boot')}")
                .done(function(resp) {
                    var bootID = resp['id'];
                    console.log("boot id: " + bootID);
                    if (oldBootID != bootID) {
                        window.clearInterval(interval);
                        if (onSuccess) onSuccess();
                    }
                }).fail(function(xhr, textStatus, errorThrown) {
                    console.log("ignore GET boot failure: " + xhr.status + " " + textStatus + " " + errorThrown);
                });
            }, 1000);
        }
    </script>
</%def>
