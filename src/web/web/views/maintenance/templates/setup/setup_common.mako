<%namespace name="spinner" file="../spinner.mako"/>

<%def name="_next_button_id()">next-btn</%def>
<%def name="_prev_button_id()">prev-btn</%def>

<%def name="render_previous_button()">
    <button
        onclick="if (!$(this).hasClass('disabled')) gotoPrevPage(); return false"
        id='${_prev_button_id()}'
        ## The default type is 'submit'
        type='button'
        class='btn'
        ## make it the last element in tab order (max allowed value is 32767)
        tabindex='10000'>Previous</button>
</%def>

## Include this next button in a form.
##
## N.B. always place this line _before_ the previous button to prevent the
## browser from placing the next button on a different row than the previous button.
<%def name="render_next_button()">
    <button
        type="submit"
        id='${_next_button_id()}'
        class='btn btn-primary pull-right'>
        Next ></button>
</%def>

<%def name="scripts(page)">
    <script>
        function hideAllModals() {
            $('div.modal').modal('hide');
        }

        function verifyPresence(elementID, message) {
            var v = document.getElementById(elementID).value;
            if (v == null || v == "") {
                displayError(message);
                return false;
            }

            return true;
        }

        ## @param onSuccess the function called when the request is successful
        function doPost(postRoute, postData, onSuccess, onFailure) {
            $.post(postRoute, postData)
            .done(function (response) {
                onSuccess(response);
            }).fail(function (xhr) {
                if (onFailure) onFailure();
                showErrorMessageFromResponse(xhr);
            });
        }

        function gotoPrevPage() {
            gotoPage(${page - 1});
        }

        function gotoNextPage() {
            gotoPage(${page + 1});
        }

        function gotoPage(page) {
            ## If the user can navigate cross pages, he's guaranteed to be authenticated.
            ## Therefore we use setup_authorized rather than setup to avoid redirects.
            ## Don't use "location.href =". It's not supported by old Firefox.
            window.location.assign("${request.route_path('setup_authorized')}?page=" + page);
        }

        function disableNavButtons() {
            setEnabled($("#${_next_button_id()}"), false);
            setEnabled($("#${_prev_button_id()}"), false);
        }

        function enableNavButtons() {
            setEnabled($("#${_next_button_id()}"), true);
            setEnabled($("#${_prev_button_id()}"), true);
        }

        function displayError(error) {
            enableNavButtons();
            showErrorMessage(error);
        }
    </script>
</%def>
