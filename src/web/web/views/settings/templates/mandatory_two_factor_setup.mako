<%inherit file="marketing_layout.mako"/>
<%namespace name="components" file="two_factor_components.mako"/>
<%! page_title = "Set up Two-Factor Authentication" %>

<div class="row">
    <div class="col-sm-8 col-sm-offset-2">
        <%components:setup/>
    </div>
</div>