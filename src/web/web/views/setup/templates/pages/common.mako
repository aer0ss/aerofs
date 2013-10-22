<%def name="render_previous_button(page)">
    <button
        onclick="gotoPrevPage(); return false"
        id='previousButton'
        class='btn'
        ## make it the last element in tab order (max allowed value is 32767)
        tabindex='10000'
        type='button'>Previous</button>
</%def>

<%def name="render_next_button(javascriptCallback)">
    <button
        onclick='${javascriptCallback}; return false;'
        id='nextButton'
        class='btn btn-primary pull-right'>
        Next ></button>
</%def>

<%def name="scripts(page)">
    <script type="text/javascript">
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
            gotoPage(parseInt("${page}") - 1);
        }

        function gotoNextPage() {
            gotoPage(parseInt("${page}") + 1);
        }

        function gotoPage(page) {
            window.location.href = "${request.route_path('setup')}" +
                    "?page=" + page;
        }

        function disableButtons() {
            setEnabled($("#nextButton"), false);
            setEnabled($("#previousButton"), false);
        }

        function enableButtons() {
            setEnabled($("#nextButton"), true);
            setEnabled($("#previousButton"), true);
        }

        function displayError(error) {
            enableButtons();
            showErrorMessage(error);
        }
    </script>
</%def>
