<%inherit file="marketing_layout.mako"/>
<%! page_title = "Oops" %>

<div class="row">
    <div class="col-sm-6 col-sm-offset-3">
        <h2>500 Internal Server Error</h2>
        <p id="error_text"></p>
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $("#error_text").html(getInternalErrorText());
        });
    </script>
</%block>