<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Report a Problem" %>

<%namespace name="csrf" file="csrf.mako"/>

<h2>Report a Problem</h2>
<div>
    <form action="${request.route_path('json-submit-report')}" method="POST">
        ${csrf.token_input()}
        <div>
            <label for="email">Contact Email:</label>
            <input id="email" name="email" type="email">
        </div>
        <div>
            <label for="desc">Description:</label>
            <textarea id="desc" name="desc"></textarea>
        </div>
        <div>
            <label>Client Logs:</label>
            <div>
                <div>
                    <button>Add Users</button>
                </div>
                <p>
                    Add users whose AeroFS logs will be collected from their computers to AeroFS support system. The
                    logs do not contain user data or metadata including file names. <a href="">Read more.</a>
                </p>
            </div>
        </div>
        <div>
            <button>Submit</button>
            <hr>
            <a href="">You can download and submit appliance logs separately here.</a>
        </div>
    </form>
</div>
