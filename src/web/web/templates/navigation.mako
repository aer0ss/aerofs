
## Render a link menu for navigation bars
## @param link: tuple (route_name, menu_label)
<%def name="link(link)">
    <li
        %if request.matched_route and request.matched_route.name == link[0]:
            class="active"
        %endif
    ><a href="${request.route_path(link[0])}">${link[1]}</a></li>
</%def>
