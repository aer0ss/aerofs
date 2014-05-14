## The modal with a spinner. Usage:
##
##      <%namespace name="spinner" file="spinner.mako"/>
##      <%namespace name="progress_modal" file="progress_modal.mako"/>
##
##      <%progress_modal:html>
##          Doing something...
##      </%progress_modal:html>
##
##      <%progress_modal:scripts/>
##
##      ########
##      ## N.B. spinner support is required by progress_modal
##      ########
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
## If the modal contains multiple lines, don't use <p> for the last line:
##
##      <%progress_modal:html>
##          <p>Please wait while blah...</p>
##          ## Don't use <p> to wrap the following line to avoid an ugly, big padding
##          ## between the line and the bottom of the modal.
##          Blah Blah.
##      </%progress_modal:html>
##
## N.B. there can be at most one progress modal on each HTML page because of ID
## conflicts.
##

<%def name="id()">progress-modal</%def>

<%def name="html()">
    <div id="${id()}" class="modal fade" tabindex="-1" role="dialog"
            style="top: 200px">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-body">
                    <div id="progress-modal-spinner" class="pull-left"
                          style="margin-right: 28px; padding-top: -10px">&nbsp;</div>
                    <div>
                        ${caller.body()}
                    </div>
                </div>
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
            startSpinner($spinner, 0);
        }
    </script>
</%def>
