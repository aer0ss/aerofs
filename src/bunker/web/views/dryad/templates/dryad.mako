<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Report a Problem" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="modal" file="modal.mako"/>

## TODO: hacks were used to do button styling, replace them with proper styling
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
                Please select the users to collect AeroFS client logs from:
            </p>

            <p id="message-error" hidden>
                Sorry, we have encountered an error while retrieving the list of
                AeroFS users. Please refresh the page and try again later.
            </p>

            <div id="users-table">
                <label for="search">Search:</label>
                <input type="search" id="search" name="search" placeholder="Start typing an user's email">

                ## the following script is not executed nor rendered. The
                ## purpose of the script is to serve as templates and we will
                ## render the templates later on with javascript.
                <script id="user-row-template" type="text/template">
                    <label for="user-{{ index }}" class="searchable" data-index="{{ label }}">
                        ## TODO: a space was inserted here before the text,
                        ## replace it with proper styling!
                        <input id="user-{{ index }}" name="user" value="{{ user }}" type="checkbox"> {{ label }}
                    </label>
                </script>
            </div>

            <button type="submit" class="btn btn-primary" data-dismiss="modal">Done</button>
        </%modal:modal>

        <div>
            <label for="email">Contact Email: *</label>
            <input id="email" name="email" type="email" required>
        </div>
        <div>
            <label for="desc">Description: *</label>
            <textarea id="desc" name="desc" required></textarea>
        </div>
        <div>
            <label>Client Logs:</label>
            <div>
                <div>
                    ## TODO: replace the margin styling with proper styling
                    <button type="button" class="btn btn-default" id="select-users" style="margin-bottom: 8px">Select Users</button>
                </div>
                <p>
                    AeroFS will collect the AeroFS logs from these users'
                    computers and send them to AeroFS Support. These logs
                    contains data on how the user uses AeroFS and may contain
                    the file name of the files on the users' computers.
                    <a href="">Read more</a>
                </p>
            </div>
        </div>
        <div>
            <button type="submit" class="btn btn-primary">Submit</button>
        </div>
    </form>
</div>

## TODO: replace this with proper styling
<style>
    button {
        margin-top: 5px;
    }
</style>

<%block name="scripts">
    <script type="text/javascript">
        ## given a template string and a map of key -> value, replace all
        ## occurrences of '{{ key }}' in template with corresponding the value
        ## for all keys in the map.
        function render(template, map) {
            var rendered = template;
            for (var key in map) {
                rendered = rendered.split('{{ ' + key + ' }}').join(map[key]);
            }

            return rendered;
        }

        ## returns true iff _str_ starts with _prefix_
        function startsWith(str, prefix) {
            return str.substr(0, prefix.length) == prefix;
        }

        function getUsersList() {
            $.get("${request.route_path('json-get-users')}")
                    .done(function(data) {
                        var $table = $('#users-table');
                        var template = $('#user-row-template').html();

                        $.each(data['users'], function(index, user) {
                            var map = {
                                'index':    index,
                                'user':     user,
                                ## team server id starts with ':'
                                'label':    user[0] == ':' ? 'Team Server' : user
                            };

                            $table.append(render(template, map));
                        });

                        filterUsers();

                        $('#message-loading').hide();
                        $('#message-loaded').show();
                    })
                    .fail(function() {
                        $('#message-loading').hide();
                        $('#message-error').show();
                    })
            ;
        }

        ## the following method of filtering is not sufficiently fast.
        ##
        ## this was added to deal with the early release and is fast enough when
        ## there are only 500 users, but it's visibly laggy when there are 1277
        ## users.
        function filterUsers() {
            var query = $('#search').val().trim().toLowerCase();

            ## enforce the following rules in order of priority:
            ## * checked items must be shown
            ## * empty query means hide all items
            ## * only show items whose label starts with the query (case-insensitive)
            $('.searchable').has(':not(:checked)').each(function() {
                var show = query.length > 0
                        && startsWith(this.getAttribute('data-index').toLowerCase(), query);
                this.style.display = show ? 'block' : 'none';
            });
        }

        $(document).ready(function() {
            $('#select-users').click(function() {
                $('#modal').modal('show');
                $('#search').focus();
            });

            $('#search').on('input', filterUsers);

            getUsersList();
        });
    </script>
</%block>
