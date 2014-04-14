<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Report a Problem" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="modal" file="modal.mako"/>

<h2>Report a Problem</h2>
<div>
    <form action="${request.route_path('json-submit-report')}" method="POST">
        ${csrf.token_input()}

        <%modal:modal>
            <%def name="id()">modal</%def>
            <%def name="title()">Select Users</%def>

            ## TODO: add a pretty spinner
            <p id="message-loading">
                Please wait while we retrieve the list of AeroFS users.
            </p>

            <p id="message-loaded" hidden>
                Please select users of whom AeroFS client logs will be collected:
            </p>

            <p id="message-error" hidden>
                Sorry, we have encountered an error while retrieving the list of AeroFS users. Please refresh the page
                and try again later.
            </p>

            <div id="users-table">
                <script id="users-row-template" type="text/template">
                    <label id="users-row-template" for="user-{{ index }}">
                        <input id="user-{{ index }}" name="user" value="{{ user }}" type="checkbox">{{ label }}
                    </label>
                </script>
            </div>

            <button type="button" data-dismiss="modal">Close</button>
        </%modal:modal>

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
                    <button type="button" onclick="onAddUsersClick()">Add Users</button>
                </div>
                <p>
                    Add users whose AeroFS logs will be collected from their computers to AeroFS support system. The
                    logs do not contain user data or metadata including file names. <a href="">Read more.</a>
                </p>
            </div>
        </div>
        <div>
            <button type="submit">Submit</button>
            <hr>
            <a href="">You can download and submit appliance logs separately here.</a>
        </div>
    </form>
</div>

<%block name="scripts">
    <script type="text/javascript">
        function onAddUsersClick() {
            $('#modal').modal('show');
        }

        ## given a template string and a map of key -> value
        ## replace all occurrences of '{{ key }}' in template with corresponding the value for all keys in map.
        function render(template, map) {
            var rendered = template;
            for (var key in map) {
                rendered = rendered.split('{{ ' + key + ' }}').join(map[key]);
            }

            return rendered;
        }

        function getUsersList() {
            $.get("${request.route_path('json-get-users')}")
                    .done(function(data) {
                        var $table = $('#users-table');
                        var template = $('#users-row-template').html();

                        $.each(data['users'], function(index, user) {
                            var map = {
                                'index':    index,
                                'user':     user,
                                ## team server id starts with ':'
                                'label':    user[0] == ':' ? 'Team Server' : user
                            };

                            $table.append(render(template, map));
                        });

                        $('#message-loading').hide();
                        $('#message-loaded').show();
                    })
                    .fail(function() {
                        $('#message-loading').hide();
                        $('#message-error').show();
                    })
            ;
        }

        $(document).ready(getUsersList);
    </script>
</%block>
