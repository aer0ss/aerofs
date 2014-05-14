## Utility function to create modals
##
## caller.id(): the id of the modal
## caller.title(), body(), footer(): modal title, body, and footer (optional)
## Optionally define caller.error() to return true for modals that indicate errors.
## Optionally define caller.success() to return true for modals that indicate success.
## Optionally define caller.no_close() to hide the close button in the header (the X).
##
## Style Guide: see README.style.txt
##
<%def name="modal()">
    <div id="${caller.id()}" class="modal fade" tabindex="-1" role="dialog"
    >
        <div class="modal-dialog">
            <div class="modal-content">
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
                <div class="modal-body"
                >
                    ${caller.body()}
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