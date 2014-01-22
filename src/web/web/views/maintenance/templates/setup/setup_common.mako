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

## Log an event into Segment.io. Available only during the initial setup with
## a trial license. The function is a no-op in other cases. Usage:
##
##  <script>
##      ...
##      ${trackInitialTrialSetup('Completed Foo')}
##      ...
##  </script>
##
## Note that the availability is determined at page rendering time.
##
<%def name="trackInitialTrialSetup(event)">
    %if enable_data_collection:
        <%
            from web.version import get_current_version
            customer_id = current_config['customer_id']
            if not customer_id: customer_id = 'unknown customer'
        %>
        analytics.identify("${customer_id}");
        analytics.track("${event}", {
            ## Even though we identify the session using the customer id
            ## (see above), the analytics tool may not expose it to us.
            ## Hence we attach the id as a property.
            customer_id: "${customer_id}",
            ## The appliance's version
            version: "${get_current_version()}"
        });
    %endif
</%def>

<%def name="scripts(page)">
    <script>
        function hideAllModals() {
            $('div.modal').modal('hide');
        }

        function verifyPresence(elementID, message) {
            var v = document.getElementById(elementID).value;
            if (v == null || v == "") {
                enableNavButtons();
                showErrorMessage(message);
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
    </script>
</%def>
