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
        onclick='return ${javascriptCallback};'
        id='nextButton'
        class='btn btn-primary pull-right'
        type='submit'>Next ></button>
</%def>

<%def name="scripts(page)">
    <script type="text/javascript">
        function verifyAbsence(elementID) {
            ## TODO (WW) why doens't $('#' + elementID) work here?
            var v = document.getElementById(elementID).value;
            return v == null || v == "";
        }

        function verifyPresence(elementID, message) {
            ## TODO (WW) why doens't $('#' + elementID) work here?
            var v = document.getElementById(elementID).value;
            if (v == null || v == "") {
                displayError(message);
                return false;
            }

            return true;
        }

        ## @param onSuccess the function called when the request is successful
        ## @param onFailure the function called when the request failed. Note
        ##      that the function should NOT display errors. doPost() does so by
        ##      calling displayError().
        function doPost(postRoute, postData, onSuccess, onFailure) {
            ## TODO (WW) use the unified error displaying framework
            $.post(postRoute, postData)
            .done(function (response) {
                var error = response['error'];
                if (error) {
                    onFailure();
                    displayError(error);
                } else {
                    onSuccess(response);
                }
            }).fail(function (jqXHR, textStatus, errorThrown) {
                onFailure();
                displayError("Error: " + textStatus + " " + errorThrown);
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
            var $nextButton = $("#nextButton");
            $nextButton.attr("disabled", "disabled");

            var $previousButton = $("#previousButton");
            $previousButton.attr("disabled", "disabled");
        }

        function enableButtons() {
            var $nextButton = $("#nextButton");
            $nextButton.removeAttr("disabled");

            var $previousButton = $("#previousButton");
            $previousButton.removeAttr("disabled");
        }

        function displayError(error) {
            enableButtons();
            showErrorMessage(error);
        }
    </script>
</%def>
