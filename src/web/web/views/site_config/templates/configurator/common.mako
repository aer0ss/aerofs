<%def name="render_previous_button(page)">
    <button
        onclick="gotoPrevPage(); return false"
        id='previousButton'
        class='btn'
        type='button'>Previous</button>
</%def>

<%def name="render_next_button(javascriptCallback)">
    <button
        onclick='return ${javascriptCallback};'
        id='nextButton'
        class='btn btn-primary'
        type='submit'>Next</button>
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

        function doPost(postRoute, postData, callback) {
            $.post(postRoute, postData)
            .done(function (response)
            {
                var error = response['error'];
                if (error)
                {
                    displayError(error);
                }
                else
                {
                    callback(response);
                }
            })
            .fail(function (jqXHR, textStatus, errorThrown)
            {
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
            window.location.href = "${request.route_path('site_config')}" +
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