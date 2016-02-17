## The modal with a spinner. Usage:
## Utility function to create progress modals
##
## caller.id(): the id of the modal
## caller.title(), body(), footer(): modal title (optional), body, and footer (optional)
## Optionally define caller.error() to return true for modals that indicate errors.
## Optionally define caller.success() to return true for modals that indicate success.
## Optionally define caller.no_close() to hide the close button in the header (the X).

<%def name="progress_modal()">
    <div id="${caller.id()}" class="modal" tabindex="-1" role="dialog">
        <div class="modal-dialog">
            <div class="modal-content">
                %if hasattr(caller, "title"):
                    <div class="modal-header">
                        %if not hasattr(caller, "no_close"):
                            <button type="button" class="close" data-dismiss="modal">×</button>
                        %endif

                        <h4
                        %if hasattr(caller, "error"):
                            class="text-error"
                        %elif hasattr(caller, "success"):
                            class="text-success"
                        %endif
                        >${caller.title()}</h4>
                    </div>
                %endif
                <div class="modal-body">
                    <div class="progress-modal-spinner pull-left"
                          style="margin-right: 28px; padding-top: -10px">&nbsp;</div>
                    <div>
                        ${caller.body()}
                    </div>
                </div>
                %if hasattr(caller, "footer"):
                    <div class="modal-footer">
                        ${caller.footer()}
                    </div>
                %endif
            </div>
        </div>
    </div>
</%def>

<%def name="scripts()">
    <script>
        function initializeProgressModal() {

            initializeSpinners();
            var $spinner = $('.progress-modal-spinner');
            startSpinner($spinner, 0);
        }
    </script>
</%def>
