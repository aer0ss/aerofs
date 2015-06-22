## Error message support for appliance management pages. This file extend the
## showErrorMessageUnsafe() message to show the link to download log files.
##
## All but few maintenance pages should include this file. One exception is
## the maintenance login page: because the user needs to log in to download the
## logs, it doesn't make sense to provide that link in errors on that page.

<%def name="scripts()">
    <script>
        $(document).ready(function() {
            ## extending showErrorMessageUnsafe() to prompt link for downloading logs. This is required because
            ## web and bunker share aerofs.js. We only want to extend the download log prompt for bunker setup only
            ## since after setup, user can manually download logs from the web interface.
            showErrorMessageUnsafe = function(message) {
                showErrorMessageWithDownloadLogPrompt(message);
            };

            showErrorMessageWithDownloadLogPrompt = function(message) {
                showErrorMessageUnnormalizedUnsafe(normalizeMessage(message) +
                    "<div class='footnote' style='margin-top: 10px'>" +
                        "If you need more information, " +
                        "<a href='${request.route_path('logs_auto_download')}' target='_blank'>" +
                        "download and view server logs</a>." +
                    "</div>"
                );
            };
        });
    </script>
</%def>
