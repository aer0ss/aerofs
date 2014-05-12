<%inherit file="marketing_layout.mako"/>
<%! page_title = "Oops" %>

<div class="row">
    <div class="span6 offset3">
        <h2>500: Internal Server Error</h2>
        <div id="error_text"></div>
    </div>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $("#error_text").html(getInternalErrorText());
        });
    </script>
</%block>