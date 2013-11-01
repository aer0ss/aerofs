## This template is used for non-dashboard pages with the left side bar for
## public deployment.

<%inherit file="public_marketing_layout.mako"/>

<div class="row page-block">
    <div class="span10 offset1">
        <div class="row-fluid">
            <div class="span3" style="margin-top: 20px;">
                <%block name="side_bar"/>
            </div>
            <div class="span9">
                ${next.body()}
            </div>
        </div>
    </div>
</div>
