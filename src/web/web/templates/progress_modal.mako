## The modal with a spinner. Usage:
##
##      <%namespace name="spinner" file="spinner.mako"/>
##      <%namespace name="progress_modal" file="progress_modal.mako"/>
##
##      <%progress_modal:html>
##          Please wait while blah...
##      </%progress_modal:html>
##
##      <%progress_modal:scripts/>
##      ## spinner support is required by progress_modal
##      <%spinner:scripts/>
##
##      <script>
##          $(document).ready(function() {
##              initializeProgressModal();
##          });
##          function start() {
##              $('#${progress_modal.id()}').modal('show');
##          }
##      </script>
##
## The client of this file must include spinner.mako's script.
##
## N.B. there can be at most one progress modal on each HTML page because of ID
## conflicts.
##

<%def name="id()">progress-modal</%def>

<%def name="html()">
    <div id="${id()}" class="modal hide" tabindex="-1" role="dialog"
            style="top: 200px">
        <div class="modal-body">
            ## "display: table-cell" is needed for caller.body()s that have more
            ## than one line of text.
            ## TODO (WW) a better approach
            <div id="progress-modal-spinner" class="pull-left"
                  style="display: table-cell; margin-right: 28px; padding-top: -10px">&nbsp;</div>
            <div style="display: table-cell;">
                ${caller.body()}
            </div>
        </div>
    </div>
</%def>

<%def name="scripts()">
    <script>
        function initializeProgressModal() {
            initializeSpinners();
            var $spinner = $('#progress-modal-spinner');

            var $modal = $('#${id()}');
            disableEsapingFromModal($modal);

            $modal.on('shown', function() {
                startSpinner($spinner, 0);
            }).on('hidden', function() {
                stopSpinner($spinner);
            });
        }
    </script>
</%def>
