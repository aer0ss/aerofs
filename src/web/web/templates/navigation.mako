## A couple of links to public marketing pages used in the top navigation bar of
## the other pages.
<%def name="marketing_links()">
    <li class="marketing-link"><a href="http://support.aerofs.com" target="_blank">Support</a></li>
    <li class="marketing-link"><a href="http://blog.aerofs.com" target="_blank">Blog</a></li>
</%def>

## Render a link menu for navigation bars
## @param link: tuple (route_name, menu_label)
<%def name="link(link)">
    <li
        %if request.matched_route and request.matched_route.name == link[0]:
            class="active"
        %endif
    ><a href="${request.route_path(link[0])}">${link[1]}</a></li>
</%def>

<%def name="api_doc_url()">/docs/api</%def>

<%!
    def sub_item(text):
        return '<span style="margin-left: 12px; font-size: 95%">' + text + '</span>'
%>

## @items a list of tuples of (title, route).
<%def name="_render_items(items, highlight_current_item)">
    %for item in items:
        <li>
            <% item_url = request.route_url(item[1]) %>
            %if highlight_current_item and request.url == item_url:
                <a href="${item_url}">${item[0] | n} &#x25B8;</a>
            %else:
                <a href="${item_url}">${item[0] | n}</a>
            %endif
        </li>
    %endfor
</%def>