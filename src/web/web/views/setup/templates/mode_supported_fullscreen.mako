<%inherit file="base_layout.mako"/>

<%block name="home_url">
    ${request.route_path('setup')}
</%block>

<div class="span8 offset2">
    <%include file="pages.mako"/>
</div>
