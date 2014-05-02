## Error message support for appliance management pages. This file extend the
## showErrorMessage() message to show the link to download log files.
##
## All but few maintenance pages should include this file. One exception is
## the maintenance login page: because the user needs to log in to downoad the
## logs, it doesn't make sense to provide that link in errors on that page.

<%def name="scripts()">
    <script>
        $(document).ready(function() {
            showErrorMessage = function(message) {
                showErrorMessageUnnormalizedUnsafe(nltobr(escapify(normalizeMessage(message))) +
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
