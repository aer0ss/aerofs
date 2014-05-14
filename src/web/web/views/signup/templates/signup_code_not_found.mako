<%inherit file="marketing_layout.mako"/>
<%! page_title = "Invalid Signup Code" %>

<div class="row">
    <div class="col-sm-12 text-center">

        <div class="page-block">
            <h1>Invalid Signup Code</h1>
        </div>
        <div class="page-block">
            <p>Your signup code may have expired.</p>
            <p>Please contact <a href="mailto:${support_email}">${support_email}</a>
                for more information.</p>
        </div>
    </div>
</div>
