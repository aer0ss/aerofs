## A couple of links to public marketing pages used in the top navigation bar of
## the other pages.
<%def name="marketing_links()">
    <li><a href="http://support.aerofs.com" target="_blank">Support</a></li>
    <li><a href="http://blog.aerofs.com" target="_blank">Blog</a></li>
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

## This method generates a side bar with only the Next Steps section.
<%def name="minimal_side_bar()">
    <%self:_side_bar/>
</%def>

<%def name="product_side_bar()">
    <%self:_side_bar>
        <%def name="menu_title()">Product</%def>
        <%def name="main_items()">
            <%self:product_items highlight_current_item="True"/>
        </%def>
    </%self:_side_bar>
</%def>

<%def name="solutions_side_bar()">
    <%self:_side_bar>
        <%def name="menu_title()">Solutions</%def>
        <%def name="main_items()">
            <%self:solutions_items highlight_current_item="True"/>
        </%def>
    </%self:_side_bar>
</%def>

<%def name="security_side_bar()">
    <%self:_side_bar>
        <%def name="menu_title()">Security</%def>
        <%def name="main_items()">
            <%self:security_items highlight_current_item="True"/>
        </%def>
    </%self:_side_bar>
</%def>

## This side bar doesn't come with the "Next Steps" marketing plugs.
<%def name="developers_side_bar()">
    <%self:_side_bar>
        <%def name="exclude_next_steps_items()"></%def>
        <%def name="menu_title()">Developer Home</%def>
        <%def name="main_items()">
            <%self:_developers_items highlight_current_item="True"/>
        </%def>
    </%self:_side_bar>

    ${caller.body()}

    <div class="alert alert-success">
        API access is available for the AeroFS <strong>Private Cloud only</strong>.
        <a href="${request.route_path('developers_signup')}">Click here</a>
        to request a developer license for free.
    </div>
</%def>

<%def name="api_doc_url()">/docs/api</%def>

<%!
    def sub_item(text):
        return '<span style="margin-left: 12px; font-size: 95%">' + text + '</span>'
%>

<%def name="product_items(highlight_current_item=False)">
    <%
        items = [
            ('Overview', 'product_overview'),
            (sub_item('Low-cost Scalability'), 'product_low_cost_scalability'),
            (sub_item('Security & Control'), 'product_security_and_control'),
            (sub_item('Simple Experience'), 'product_simple_experience'),
            (sub_item('Flexible Storage'), 'product_flexible_storage'),
            ('Deployment Options', 'product_deployment_options'),
            (sub_item('Private Cloud'), 'product_deployment_private_cloud'),
        ]
    %>
    ${_render_items(items, highlight_current_item)}
</%def>

<%def name="solutions_items(highlight_current_item=False)">
    <%
        items = [
            ('Overview', 'solutions_overview'),
            (sub_item('Transfer Large Files'), 'solutions_transfer_large_files'),
            (sub_item('Share Securely'), 'solutions_secure_file_sharing'),
            (sub_item('Server Replacement'), 'solutions_server_replacement'),
            (sub_item('Data Recovery'), 'solutions_data_recovery'),
            ('Compliance & DLP', 'solutions_data_protection_policy')
        ]
    %>
    ${_render_items(items, highlight_current_item)}
</%def>

<%def name="security_items(highlight_current_item=False)">
    <%
        items = [
            ('Security & Control', 'security_overview'),
            ('Technical Details', 'security_spec')
        ]
    %>
    ${_render_items(items, highlight_current_item)}
</%def>

<%def name="_developers_items(highlight_current_item=False)">
    <%
        items = [
            ('Overview', 'developers_overview'),
            ('Getting Started', 'developers_getting_started'),
            ('Consistency Policies', 'developers_consistency'),
            ('Publish Your App', 'developers_publish'),
##            ('Security Guide', 'developers_security'),
        ]
    %>
    ${_render_items(items, highlight_current_item)}
    <li><a href="${api_doc_url()}">API Reference</a></li>
</%def>

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

## Define exclude_next_steps_items in the caller to hide the "Next steps" block
<%def name="_side_bar()">
    %if not hasattr(caller, 'exclude_next_steps_items'):
        <div class="accordion side-bar-accordion" id="left-side-bar0">
            <div class="accordion-group">
                <div class="accordion-heading">
                    <a class="accordion-toggle" data-toggle="collapse" data-parent="#left-side-bar0" href="#collapse0">
                        Next Steps <span class="accordion-down-arrow">&nbsp; &#x25BE;</span>
                    </a>
                </div>
                <div id="collapse0" class="accordion-body collapse in">
                    <div class="accordion-inner">
                        <ul class="nav nav-list left-nav">
                            <li>
                                <a class="sign-up-text-highlight" href="${request.route_path('pricing')}">
                                    <i class="icon-circle-arrow-right"></i> Sign Up Now</a>
                            </li>
                            <li>
                                <a href="${request.route_path('demo_videos')}">
                                    <i class="icon-film"></i> Watch Demos
                                </a>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    %endif

    ## Generate the main menu only if main_items() is defined
    %if hasattr(caller, 'main_items'):
        <div class="accordion side-bar-accordion" id="left-side-bar1">
            <div class="accordion-group">
                <div class="accordion-heading">
                    <a class="accordion-toggle" data-toggle="collapse" data-parent="#left-side-bar1" href="#collapse1">
                        ${caller.menu_title()} <span class="accordion-down-arrow">&nbsp; &#x25BE;</span><br>
                    </a>
                </div>
                <div id="collapse1" class="accordion-body collapse in">
                    <div class="accordion-inner">
                        <ul class="nav nav-list left-nav">
                            ${caller.main_items()}
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    %endif
</%def>
