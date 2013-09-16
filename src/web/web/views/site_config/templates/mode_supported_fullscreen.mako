<%inherit file="base_layout.mako"/>

<%block name="home_url">
    ${request.route_path('site_config')}
</%block>

<div class="span9 offset1">
    <%include file="configurator.mako"/>
</div>
