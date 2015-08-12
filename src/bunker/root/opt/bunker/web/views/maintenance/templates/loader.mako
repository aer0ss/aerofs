<%def name="scripts()">
    <script>
        ## Reboot the app then call callback if it's non-null.
        ## Expected signatures:
        ##          function onSuccess()
        ##          function onFailure(xhr)
        function reboot(target, onSuccess, onFailure) {
            ## Get current boot ID before rebooting
            $.get("${request.route_path('json-get-boot')}")
            .done(function(resp) {
                var bootID = resp['id'];
                console.log("Reboot to /" + target + ". previous boot id: " + bootID);
                $.post("/json-boot/" + target)
                .done(function() {
                    waitForReboot(bootID, onSuccess);
                }).fail(function(xhr, textStatus, errorThrown) {
                    ## Ignore errors as the server might be killed before replying
                    console.log("Ignore json-boot failure: " + xhr.status + " " + textStatus + " " + errorThrown);
                    waitForReboot(bootID, onSuccess);
                });

            }).fail(function(xhr) {
                if (onFailure) onFailure(xhr);
            });
        }

        function waitForReboot(oldBootID, onSuccess) {
            console.log('Wait for reboot to finish');
            var interval = window.setInterval(function() {
                $.get("${request.route_path('json-get-boot')}")
                .done(function(resp) {
                    var bootID = resp['id'];
                    console.log("Boot id: " + bootID);

                    ## Track old vs new boot ID. Used to avoid race conditions where we check
                    ## system state before the box manages to go offline.
                    if (oldBootID != bootID) {
                        window.clearInterval(interval);
                        if (onSuccess) onSuccess();
                    }
                }).fail(function(xhr, textStatus, errorThrown) {
                    console.log("Ignore GET boot failure: " + xhr.status + " " + textStatus + " " + errorThrown);
                });
            }, 1000);
        }
    </script>
</%def>
