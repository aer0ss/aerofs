<%namespace name="spinner" file="../spinner.mako"/>

<%def name="next_button_id()">next-btn</%def>
<%def name="prev_button_id()">prev-btn</%def>

<%def name="render_previous_button(page)">
    <button
        onclick="if (!$(this).hasClass('disabled')) gotoPrevPage(); return false"
        id='${prev_button_id()}'
        class='btn'
        ## make it the last element in tab order (max allowed value is 32767)
        tabindex='10000'
        type='button'>Previous</button>
</%def>

<%def name="render_next_button(javascriptCallback)">
    <button
        onclick="if (!$(this).hasClass('disabled')) ${javascriptCallback}; return false;"
        id='${next_button_id()}'
        class='btn btn-primary pull-right'>
        Next ></button>
</%def>

<%def name="scripts(page)">
    <script>
        function hideAllModals() {
            $('div.modal').modal('hide');
        }

        function verifyAbsence(elementID) {
            var v = document.getElementById(elementID).value;
            return v == null || v == "";
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
            window.location.href = "${request.route_path('setup_authorized')}?page=" + page;
        }

        function disableNavButtons() {
            setEnabled($("#${next_button_id()}"), false);
            setEnabled($("#${prev_button_id()}"), false);
        }

        function enableNavButtons() {
            setEnabled($("#${next_button_id()}"), true);
            setEnabled($("#${prev_button_id()}"), true);
        }

        function displayError(error) {
            enableNavButtons();
            showErrorMessage(error);
        }
    </script>
</%def>
