## Utility library to create modals

## Define caller.error() to return true for modals that indicate errors.
<%def name="modal()">
    <div id="${caller.id()}" class="modal hide" tabindex="-1" role="dialog">
        <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">×</button>
            <h4
            %if hasattr(caller, "error"):
                class="text-error"
            %endif
                >${caller.title()}</h4>
        </div>
        <div class="modal-body">
            ${caller.body()}
        </div>
        <div class="modal-footer">
            ${caller.footer()}
        </div>
    </div>
</%def>