<%inherit file="base_layout.mako"/>

<%block name="home_url">
    ${request.route_path('setup')}
</%block>

<div class="span9 offset1">
    <%include file="configurator.mako"/>
</div>
