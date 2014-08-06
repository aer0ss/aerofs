## Utility library to create spinners
## TODO: eliminate this file, just import the JS where appropriate.

<%def name="scripts()">
    ## The script file is copied from http://fgnass.github.com/spin.js/
    <script src="${request.static_path('web:static/js/spin.min.js')}"></script>
    <script src="${request.static_path('web:static/js/compiled/spinner.js')}"></script>
</%def>