<%namespace name="csrf" file="csrf.mako" import="token_input, token_param"
        inheritable="True"/>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="P2P file sharing and file sync lets you create your own private cloud. Secure, unlimited data transfer for large or sensitive files on Windows, Mac, Linux, and Android.">

    <title>
        ## This title is recommended by Beth to maximize keyword matching for SEO
        Secure File Sharing and File Sync | AeroFS
        ## The following code requires _every_ page to define a page title.
        ##
        ## Place the title definition to the top of the file:
        ## <%! page_title = "About Us" %>
        ## And remember to capitalize initials.
        %if len(self.attr.page_title) != 0:
            | ${self.attr.page_title}
        %endif
    </title>

    <link rel="shortcut icon" href="${request.static_path('web:static/img/favicon.ico')}">

    ## stylesheets
    ##
    ## N.B. to support private deployment, all static assets must be hosted
    ## locally as opposed to on 3rd-party servers.
    <link href="${request.static_path('web:static/css/google-open-sans.css')}" rel='stylesheet' type='text/css'>
    <link href="${request.static_path('web:static/css/bootstrap.css')}" rel="stylesheet">
    <link href="${request.static_path('web:static/css/responsive.css')}" rel="stylesheet">
    <link href="${request.static_path('web:static/css/aerofs.css')}" rel="stylesheet">

    <%block name="css"/>

    ## Le HTML5 shim, for IE6-8 support of HTML5 elements
    <!--[if lt IE 9]>
    <script src="${request.static_path('web:static/js/html5.js')}"></script>
    <![endif]-->

    ## fav and touch icons
    <link rel="shortcut icon" href="${request.static_path('web:static/img/favicon.ico')}">

    ## Google Analytics. Put it to header rather than footer: http://stackoverflow.com/questions/10712908/google-analytics-in-header-or-footer
    ## TODO (WW) use different API keys for prod and dev as Mixpanel does?
    <script type="text/javascript">
        var _gaq = _gaq || [];
        _gaq.push(['_setAccount', 'UA-24554389-1']);
        _gaq.push(['_trackPageview']);
        _gaq.push(['_trackPageLoadTime']);

        (function() {
            var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
            ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
            var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
        })();
    </script>

    ## Mixpanel. Put this to header rather than footer as required by Mixpanel.
    <script type="text/javascript">(function(e,b){if(!b.__SV){var a,f,i,g;window.mixpanel=b;a=e.createElement("script");a.type="text/javascript";a.async=!0;a.src=("https:"===e.location.protocol?"https:":"http:")+'//cdn.mxpnl.com/libs/mixpanel-2.2.min.js';f=e.getElementsByTagName("script")[0];f.parentNode.insertBefore(a,f);b._i=[];b.init=function(a,e,d){function f(b,h){var a=h.split(".");2==a.length&&(b=b[a[0]],h=a[1]);b[h]=function(){b.push([h].concat(Array.prototype.slice.call(arguments,0)))}}var c=b;"undefined"!==
        typeof d?c=b[d]=[]:d="mixpanel";c.people=c.people||[];c.toString=function(b){var a="mixpanel";"mixpanel"!==d&&(a+="."+d);b||(a+=" (stub)");return a};c.people.toString=function(){return c.toString(1)+".people (stub)"};i="disable track track_pageview track_links track_forms register register_once alias unregister identify name_tag set_config people.set people.increment people.append people.track_charge people.clear_charges people.delete_user".split(" ");for(g=0;g<i.length;g++)f(c,i[g]);b._i.push([a,
        e,d])};b.__SV=1.2}})(document,window.mixpanel||[]);
        mixpanel.init("${request.registry.settings['mixpanel.api_key']}");

        %if 'team_id' in request.session:
            mixpanel.identify("${request.session['team_id']}");
        %endif

        <%block name="page_view_tracker"/>
    </script>
</head>

<body>
    ## this wrapper is used to keep the footer at the bottom, even if the content
    ## height is less than the window height ("sticky" footer)
    <div id="wrapper">
        ## Horizontal navigation bar
        <div class="container" style="margin-bottom: 20px;">
            <div class="row">
                <div class="span10 offset1" id="top-nav-span">
                    <ul class="nav nav-pills top-nav">
                        <li><a href="<%block name="home_url"/>">
                            <img src="${request.static_path('web:static/img/logo_small.png')}" width="144" height="40" alt="AeroFS"/>
                        </a></li>
                        <%block name="top_navigation_bar"/>
                    </ul>
                </div>
            </div>
        </div>

        ## Message bar
        <div class="container">
            <div id="message_bar" class="span6 offset3 message_container"></div>
        </div>

        ## Main body
        <div class="container">
            ${next.body()}
        </div>

        ## this element has the same or larger height as the footer, and it
        ## ensures that the footer never overlaps the content
        <div id="footer-push"></div>
    </div>

    <%block name="footer"/>

    ## javascript
    ##==================================================
    ##Placed at the end of the document so the pages load faster

    ## N.B. to support private deployment, all static assets must be hosted
    ## locally as opposed to on 3rd-party servers.
    <script src="${request.static_path('web:static/js/jquery.min.js')}"></script>
    <script src="${request.static_path('web:static/js/jquery.easing.1.3.js')}"></script>
    <script src="${request.static_path('web:static/js/bootstrap.min.js')}"></script>
    <script src="${request.static_path('web:static/js/aerofs.js')}"></script>
    <script type="text/javascript">
                    $('#jobsCarousel').carousel({
                        interval: 5000
                    })
    </script> 
    <%
        from web.util import is_admin, get_last_flash_message_and_empty_queue
        ret = get_last_flash_message_and_empty_queue(request)
    %>

    %if ret:
        <script type="text/javascript">
            $(document).ready(function() {
                ## No need to encode message texts here. jQuery will do the job for
                ## us in show*Message() methods.
                %if ret[1]:
                    showSuccessMessage("${ret[0] | n}");
                %else:
                    showErrorMessage("${ret[0] | n}");
                %endif
            });
        </script>
    %endif

    <%block name="scripts"/>
</body>
</html>
