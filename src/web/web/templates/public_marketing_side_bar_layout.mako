## This template is used for non-dashboard pages with the left side bar for
## public deployment.

<%inherit file="public_marketing_layout.mako"/>

<div class="row page-block">
    <div class="col-sm-12">
        <div class="row">
            <div class="col-sm-3" style="margin-top: 20px;">
                <%block name="side_bar"/>
            </div>
            <div class="col-sm-9">
                ${next.body()}
            </div>
        </div>
    </div>
</div>
