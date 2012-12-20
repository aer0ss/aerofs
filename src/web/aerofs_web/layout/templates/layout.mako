<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>AeroFS</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="shortcut icon" href="${request.static_url('aerofs_web.layout:static/favicon.ico')}">

    <!-- stylesheets -->
    <link href='https://fonts.googleapis.com/css?family=Open+Sans:300,400,600' rel='stylesheet' type='text/css'>
    <link href="${request.static_url('aerofs_web.layout:static/css/bootstrap.css')}" rel="stylesheet">
    <link href="${request.static_url('aerofs_web.layout:static/css/responsive.css')}" rel="stylesheet">
    <link href="${request.static_url('aerofs_web.layout:static/css/main.css')}" rel="stylesheet">
    <%block name="css"/>

    <!-- Le HTML5 shim, for IE6-8 support of HTML5 elements -->
    <!--[if lt IE 9]>
    <script src="https://html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->


    <!-- fav and touch icons -->
    <link rel="shortcut icon" href="../assets/ico/favicon.ico">
</head>

<body>

## this wrapper is used to keep the footer at the bottom, even if the content height is less than the window height ("sticky" footer)
<div id="wrapper">

    <div class="container">
        <div id="empty_message_bar" class="offset5 message_container">View Message</div>
    </div>
    <div class="container">
        <div id="message_bar" class="span12 message_container">
            % if request.session.peek_flash(queue='error_queue'):
                % for message in request.session.pop_flash(queue='error_queue'):
                    <div class="flash_message error_message">
                        ${message}
                    </div>
                % endfor
            % endif
            % if request.session.peek_flash(queue='success_queue'):
                % for message in request.session.pop_flash(queue='success_queue'):
                    <div class="flash_message success_message">
                        ${message}
                    </div>
                % endfor
            % endif
        </div>
    </div>

    <div class="navbar">
        <div class="navbar-inner">
            <div class="container">
                <a class="brand" href="/">
                    <img src="${request.static_url('aerofs_web.layout:static/img/aerofs-logo-navbar.png')}" width="151" height="44" alt="AeroFS" />
                </a>

                %if 'username' in request.session:
                   <div class="btn-group pull-right">
                        <a class="dropdown-toggle" data-toggle="dropdown" href="#">
                            <i class="icon-user"></i> ${request.session['username']}
                            <span class="caret"></span>
                        </a>
                        <ul class="dropdown-menu">
                            <!-- <li><a href="#">Profile</a></li>
                            <li class="divider"></li> -->
                            <li><a href="${request.route_path('logout')}">Sign Out</a></li>
                        </ul>
                    </div>
                %endif

            </div>
        </div>
    </div>

    <div class="container">
        <div class="row">
            ${next.body()}
        </div>
    </div>

    ## this element is the same height as the footer, and it ensures that the footer never overlaps the content
    <div id="footer-push"></div>
</div>

<footer>
    <div class="container">
        <div class="row">
            <div class="span12">
                <hr>
                <p>&copy; Air Computing Inc. ${year}</p>
            </div>
        </div>
    </div>
</footer>


<!-- javascript
================================================== -->
<!-- Placed at the end of the document so the pages load faster -->
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js"></script>
<script src="${request.static_url('aerofs_web.layout:static/js/jquery.easing.1.3.js')}"></script>
<script src="${request.static_url('aerofs_web.layout:static/js/message_bar.js')}"></script>
<script src="${request.static_url('aerofs_web.layout:static/js/bootstrap.js')}"></script>
<%block name="scripts"/>

</body>
</html>

## sets a global variable with the current year, for copyright notices
<%!
    import datetime
    year = datetime.datetime.now().year
%>

## Outputs the sidebar navigation
## param links: list of tuples (route_name, text_to_display)
## example: sidebar_links([('route1', "Link Name"), ('route2', "Another Link")])
<%def name="sidebar_links(links)">
    % if links:
        <ul class="nav">
            % for link in links:
                <li ${'class="active"' if request.matched_route.name == link[0] else '' | n }>
                    <a href="${request.route_path(link[0])}">${link[1]}</a></li>
            % endfor
        </ul>
    % endif
</%def>
