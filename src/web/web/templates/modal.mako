## Utility library to create modals

## caller.id(): the id of the modal
## caller.title(), body(), footer(): modal title, body, and footer
## Optinally define caller.error() to return true for modals that indicate errors.
## Optinally define caller.modal_style() to specify CCS styles for the modal.
## Optinally define caller.body_style() to specify CCS styles for the modal body.
<%def name="modal()">
    <div id="${caller.id()}" class="modal hide" tabindex="-1" role="dialog"
        %if hasattr(caller, "modal_style"):
            style="${caller.modal_style()}"
        %endif
    >
        <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">×</button>
            <h4
            %if hasattr(caller, "error"):
                class="text-error"
            %endif
            >${caller.title()}</h4>
        </div>
        <div class="modal-body"
        %if hasattr(caller, "body_style"):
            style="${caller.body_style()}"
        %endif
        >
            ${caller.body()}
        </div>
        <div class="modal-footer">
            ${caller.footer()}
        </div>
    </div>
</%def>