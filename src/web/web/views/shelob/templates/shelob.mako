<%inherit file="dashboard_layout.mako"/>
<%! page_title = "My Files" %>

<%!
    from web.util import is_linksharing_enabled
    from pyramid.security import authenticated_userid
%>

<div xmlns:ng="http://angularjs.org" id="ngApp" ng-app="shelobApp">
<div ng-view></div>
</div>

## if you modify the list of scripts, make sure to update jstest/shelob/e2e/index.html
<%block name="scripts">
    <!--[if lt IE 9]>
        <script type="text/javascript">
            // have to disable file upload in Internet Explorer manually
            // timeout is because otherwise BS3's JS will wipe out your handiwork >:P
            window.setTimeout(function(){
            $('.disabled').each(function(index){
                $(this).css('cursor', 'not-allowed');
                $(this).attr('disabled', 'true');
            });
            $('.disabled input').each(function(index){
                $(this).css('class', 'disabled');
                $(this).css('cursor', 'not-allowed');
                $(this).attr('disabled', 'true');
            });
        }, 500);
        </script>
    <![endif]-->

    <script type="text/javascript">
        %if is_linksharing_enabled(request.registry.settings):
            enableLinksharing = true;
        %else:
            enableLinksharing = false;
        %endif
        currentUser = "${authenticated_userid(request)}";
    </script>
    <script src="${request.static_path('web:static/js/angular-lib/modernizr.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/datepicker/bootstrap-datepicker.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular/angular-route.min.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/app.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/filters.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/services.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/controllers.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/directives.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-ui/ui-bootstrap-tpls-0.11.0.min.js')}"></script>
    <script src="${request.static_path('web:static/js/angular-lib/angular-tree-control.js')}"></script>
    <script src="${request.static_path('web:static/shelob/js/config/config_private.js')}"></script>
</%block>

<%block name="css">
    <link rel="stylesheet" href="${request.static_path('web:static/css/compiled/my-table.min.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/shelob/css/shelob.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/shelob/css/tree-control.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/shelob/css/tree-control-attribute.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/js/angular-lib/datepicker/datepicker.css')}"/>
    <link rel="stylesheet" href="${request.static_path('web:static/js/angular-lib/datepicker/datepicker3.css')}"/>
</%block>
