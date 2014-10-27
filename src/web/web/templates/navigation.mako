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

## This method generates a side bar with only the Next Steps section.
<%def name="minimal_side_bar()">
    <%self:_side_bar/>
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
                                    <span class="glyphicon glyphicon-circle-arrow-right"></span> Sign Up Now</a>
                            </li>
                            <li>
                                <a href="${request.route_path('demo_videos')}">
                                    <span class="glyphicon glyphicon-film"></span> Watch Demos
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
