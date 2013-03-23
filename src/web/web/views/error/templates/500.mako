<%inherit file="layout.mako"/>

<div class="span6 offset3">
    <h2>500 Internal Server Error</h2>
    <p id="error_text"></p>
</div>

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $("#error_text").text(getInternalErrorText());
        });
    </script>
</%block>