## A couple of links to public marketing pages used in the top navigation bar of
## the other pages.
<%def name="marketing_links()">
    <li class="marketing-link"><a href="http://support.aerofs.com" target="_blank">Support</a></li>
    <li class="marketing-link"><a href="http://www.aerofs.com/blog" target="_blank">Blog</a></li>
</%def>

## Render a link menu for navigation bars
## @param link: tuple (route_name, menu_label, menu_badge)
<%def name="link(link)">
    <li
        %if request.matched_route and request.matched_route.name == link[0]:
            class="active"
        %endif
    ><a href="${request.route_path(link[0])}">${link[1]}
    <%
    	try:
    		badge = link[2]
    	except:
    		badge = ""
    %>
    %if badge:
    	<span class="badge">${badge}</span>
    %endif
    </a></li>
</%def>

<%def name="api_doc_url()">/docs/api</%def>

<%!
    def sub_item(text):
        return '<span style="margin-left: 12px; font-size: 95%">' + text + '</span>'
%>
