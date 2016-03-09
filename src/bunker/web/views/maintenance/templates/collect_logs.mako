<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Collect Logs" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="modal" file="modal.mako"/>
<%namespace name="common" file="logs_common.mako"/>

<%
    support_url = 'https://support.aerofs.com/hc/en-us/articles/204593134'
%>

<div class="page-block">
    <h2>Collect Logs</h2>
    <p>
        Logs may be helpful when things go wrong, and AeroFS Support often
        requires logs to diagnose any issues that may arise.
    </p>
    <hr/>
</div>

<form id="appliance-logs-form" role="form" autocomplete="off">
    ${csrf.token_input()}
    <div class="page-block">
        <a name="appliance"></a><h4>Appliance logs</h4>
        <p>
            Submit appliance logs directly to AeroFS Support servers with a description of the
            problem, and we will follow up with you at your appliance's support email address.
        </p>
    </div>
    <div class="form-group">
        <label for="appliance-subject">Subject:</label>
        <input id="appliance-subject" name="subject" class="form-control" type="text"
                placeholder="Subject"
                value="${subject}"
                required/>
        <p></p>
        <label for="appliance-message">Description:</label>
        <textarea id="appliance-message" name="message" class="form-control"
                  placeholder="Message (please describe the problem)"
                  required>${message}</textarea>
    </div>
    <div class="form-group">
        <button type="submit" class="btn btn-primary">Submit appliance logs</button>
    </div>
    <div class="page-block">
        <p>
            Alternatively, you can
            <a onclick="archiveAndDownloadLogs(); return false;">download appliance logs</a>
            to your desktop and submit them manually. ${common.submit_logs_text()}</p>
        </p>
    </div>
    <hr/>
</form>

<div class="page-block">
    <a name="client"></a><h4>Client logs</h4>
    <p>
        You can instruct the AeroFS appliance to automatically collect client
        logs from computers running AeroFS.
        <a href="${support_url}">Read more</a> about collecting client logs.
    </p>
</div>

<form id="client-logs-form" role="form" autocomplete="off">
    ${csrf.token_input()}
    <input type="hidden" id="defect_id" name="defect_id" value="${defect_id}">

    <%modal:modal>
        <%def name="id()">modal</%def>
        <%def name="title()">Select users</%def>

        <p id="message-loading">
            Please wait while we retrieve the list of AeroFS users.
        </p>

        <p id="message-error" hidden>
            Sorry, we have encountered an error while retrieving the list of
            AeroFS users from the appliance. Please refresh the page and try
            again later.
        </p>

        <div id="message-loaded" hidden>
            <div class="form-group">
                <label for="search" hidden>Search:</label>
                <input type="search" id="search" class="form-control"
                        placeholder="Start typing the user's e-mail address">
            </div>

            <div id="users-table" class="form-group"
                 style="max-height: 400px; overflow: auto">
                ## the following script is not executed nor rendered. The
                ## purpose of the script is to serve as templates and we will
                ## render the templates later on with javascript.
                <script id="user-row-template" type="text/template">
                    <div class="checkbox">
                        <label for="user-{{ index }}" data-index="{{ label }}"
                                class="searchable">
                            <input type="checkbox" id="user-{{ index }}"
                                   name="users" value="{{ user }}"> {{ label }}
                        </label>
                    </div>
                </script>
            </div>
        </div>

        <button type="submit" data-dismiss="modal"
                class="btn btn-default">Close</button>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">confirm-modal</%def>
        <%def name="title()">Warning</%def>

        <p>
            You are about to instruct AeroFS to send logs to a server hosted
            by AeroFS.
        </p>

        <p>
            These logs contain sensitive information such as the user's
            activities, folder names, and file names.
            <a href="${support_url}">Learn more</a>
        </p>

        <p>
            Are you sure you want to upload logs to AeroFS's servers?
        </p>

        <hr>
        <button type="button" data-dismiss="modal"
                class="btn btn-default">Cancel</button>
        <button type="submit" data-dismiss="modal" class="btn btn-danger"
                onclick="postForm()">Send Logs to AeroFS Servers</button>
    </%modal:modal>

    <%modal:modal>
        <%def name="id()">success-modal</%def>
        <%def name="title()">Success</%def>

        <p>
            You have issued a request to collect AeroFS logs from these users.
        </p>

        <p>
            AeroFS client logs will be collected from the users' computers over
            the next seven days.
        </p>

        <hr>
        <button type="submit" data-dismiss="modal"
                class="btn btn-default">Close</button>
    </%modal:modal>

    <div class="form-group">
        <label for="select-users">Collect client logs from these users:</label>
        <br />
        <button type="button" id="select-users"
                class="btn btn-default">Select Users</button>
    </div>

    <div class="form-group">
        <label for="option">Send the logs to:</label>
        <select id="option" name="option" class="form-control" required>
            <option value="">
                --Select the destination--</option>
            <option id="option-on-site" value="on-site"
                    %if option == 'on-site':
                        selected
                    %endif
                    >
                Your on-site server</option>
            <option id="option-aerofs" value="aerofs"
                    %if option == 'aerofs':
                        selected
                    %endif
                    >
                AeroFS Support servers</option>
        </select>
    </div>

    <div id="aerofs-options" class="form-group"
         %if option != 'aerofs':
             hidden
         %endif
            >
        <label for="email">Contact Email:</label>
        <input type="email" id="email" name="email" value="${email}"
               %if option == 'aerofs':
                   required
               %endif
               class="form-control aerofs-option">
    </div>

    <div id="on-site-options" class="form-group row"
         %if option != 'on-site':
             hidden
         %endif
            >
        <div class="col-md-6">
            <label for="host">Hostname:</label>
            <input type="text" id="host" name="host" value="${host}"
                   %if option == 'on-site':
                       required
                   %endif
                   class="form-control on-site-option">
        </div>
        <div class="col-md-2">
            <label for="port">Port:</label>
            <input type="number" min="0" step="1" id="port" name="port"
                   %if option == 'on-site':
                       required
                   %endif
                   value="${port}" class="form-control on-site-option">
        </div>
        <div class="col-md-4">
            <label for="cert-selector">
                Certificate:
            </label>
            <input type="file" id="cert-selector"
                   class="form-control on-site-option"
                   %if option == 'on-site' and cert == '':
                       required
                   %endif
                    >
            <input type="hidden" id="cert" name="cert" value="${cert}">
        </div>
    </div>

    <div class="form-group">
        <label for="subject">Subject:</label>
        <input id="subject" name="subject" class="form-control" type="text"
                placeholder="Subject"
                value="${subject}"
                required/>
        <p></p>
        <label for="message">Description:</label>
        <textarea id="message" name="message" class="form-control"
                  placeholder="Message (please describe the problem)"
                  required>${message}</textarea>
    </div>
    <button type="submit" class="btn btn-primary">Collect client logs</button>
</form>

<%common:html/>

<%block name="scripts">
    <%common:scripts/>

    <script type="text/javascript">
        $(document).ready(function() {
            var $modal = $('#modal');

            $('#select-users').on('click', function() {
                $modal.modal('show');
            });

            $modal.on('shown.bs.modal', function() {
                $('#search').focus();
            });

            $modal.on('hidden.bs.modal', function() {
                $('#search').val('');
                filterUsersList();
            });

            $('#search').on('input', filterUsersList);

            $('#option').change(onSelectedOptionChanged);

            linkFileSelectorToField('#cert-selector', '#cert');

            $('form#client-logs-form').on('submit', onSubmitClientLogs);
            $('form#appliance-logs-form').on('submit', onSubmitApplianceLogs);

            getUsersList();
        });


        ## Functions for Uploading Appliance logs
        function validateApplianceForm() {
            throwIfMissing('#appliance-subject', 'Please provide a subject.');
            throwIfMissing('#appliance-message', 'Please provide a message that describes the ' +
                            'problem.');
        }

        function onSubmitApplianceLogs() {
            try {
                    validateApplianceForm();
                } catch (e) {
                    showErrorMessage(e.message);
                    return false;
                }

            $('#logs-modal').modal('show');
            $.post('${request.route_path("json-upload-container-logs")}',
                    $('form#appliance-logs-form').serialize())
                .done(function (response) {
                     hideProgressModal();
                     ## clear out the form fields
                     $('form#appliance-logs-form').find("input[type=text], textarea").val("");
                     showSuccessMessage('Your appliance logs are being sent to AeroFS Support ' +
                        'servers.');
                     return false;
                 }).fail(function(xhr) {
                    showErrorMessageFromResponse(xhr);
                    hideProgressModal();
                    return false;
                });
            return false;
        }


        ## Functions for submitting client logs
        function filterUsersList() {
            var query = $('#search').val().trim().toLocaleLowerCase();

            ## enforce the following rules in order of priority:
            ## - checked items must be shown
            ## - empty query means hide all items
            ## - only show items whose label starts with the query
            ##   (case-insensitive)
            ##
            ## N.B. JQuery is able to do this filter, possibly faster and
            ##   better. However, doing so will open up an attack vector for
            ##   JQuery injection attacks.
            $('.searchable').has(':not(:checked)').each(function() {
                var index = this.getAttribute('data-index').toLocaleLowerCase();
                var show = query.length > 0 && startsWith(index, query);

                this.parentNode.style.display = show ? 'block' : 'none';
            });
        }

        ## returns true iff _str_ starts with _prefix_
        function startsWith(str, prefix) {
            return str.substr(0, prefix.length) == prefix;
        }

        function getUsersList() {
            $.get("${request.route_path('json_get_users')}")
                    .done(function(data) {
                        populateUsersList(data);
                        populateSelectedUsers();
                        filterUsersList();

                        $('#message-loading').hide();
                        $('#message-loaded').show();
                    }).fail(function() {
                        $('#message-loading').hide();
                        $('#message-error').show();
                    });
        }

        function populateUsersList(data) {
            var $table = $('#users-table');
            var template = $('#user-row-template').html();

            $.each(data['users'], function(index, user) {
                var map = {
                    'index':    index,
                    'user':     user,
                    ## team server id starts with ':'
                    'label':    user[0] == ':' ? 'Team Server' : user
                };

                ## this is vulnerable to MITM attacks where a rogue server can
                ## return crafted data to inject arbitrary HTML and JavaScript.
                ## Given that bunker as a whole is vulnerable to MITM attacks,
                ## I'm not going to worry about this.
                $table.append(render(template, map));
            });
        }

        function populateSelectedUsers() {
            ## hack alert: using associative array to true to simulate a set
            var users = {};
            % for user in users:
                users['${user}'] = true;
            % endfor

            $('[name=users]').each(function() {
                if (users[this.value]) {
                    this.checked = true;
                }
            });
        }

        ## given a template string and a map of key -> value, replace all
        ## occurrences of '{{ key }}' (white-space sensitive) in the template
        ## with corresponding values for all keys in the map.
        function render(template, map) {
            var rendered = template;
            for (var key in map) {
                rendered = rendered.split('{{ ' + key + ' }}').join(map[key]);
            }

            return rendered;
        }

        function getSelectedOption() {
            return $('#option').val();
        }

        function onSelectedOptionChanged() {
            var selectedOption = getSelectedOption();

            $('#aerofs-options').attr('hidden', selectedOption != 'aerofs');
            $('.aerofs-option').attr('required', selectedOption == 'aerofs');
            $('#on-site-options').attr('hidden', selectedOption != 'on-site');
            $('.on-site-option').attr('required', selectedOption == 'on-site');
            $('#cert-selector').attr('required',
                    selectedOption == 'on-site' && $('#cert').val() == '');
       }

        function onSubmitClientLogs() {
            try {
                validateForm();
            } catch (e) {
                showErrorMessage(e.message);
                return false;
            }

            if (getSelectedOption() == 'aerofs') {
                $('#confirm-modal').modal('show');
            } else {
                postForm();
            }

            return false;
        }

        function postForm() {
            $.post("${request.route_path('json_collect_logs')}",
                    $('form#client-logs-form').serialize())
                    .done(onSuccess)
                    .fail(showErrorMessageFromResponse);
        }

        function onSuccess() {
            var $modal = $('#success-modal');
            $modal.on('hidden.bs.modal', function() {
                window.location.assign("${request.route_path('collect_logs')}");
            });
            $modal.modal('show');
        }

        ## this is mostly to support Safari because Safari doesn't support
        ## required attribute
        function validateForm() {
            throwIfMissing('#option', 'Please select a destination to upload ' +
                    'the client logs to.');
            throwIfMissing('#subject', 'Please provide a subject.');
            throwIfMissing('#message', 'Please provide a message that describes ' +
                    'the problem.');

            var selectedOption = getSelectedOption();
            switch (selectedOption) {
                case 'on-site':
                    throwIfMissing('#host', 'Please enter the hostname of ' +
                            'your on-site server.');
                    throwIfMissing('#port', 'Please enter the port of your ' +
                            'on-site server.');
                    throwIfMissing('#cert', 'Please provide the certificate ' +
                            'of your on-site server.');
                    break;
                case 'aerofs':
                    throwIfMissing('#email', 'Please enter a contact e-mail.');
                    break;
                default:
                    throw new Error('Please select a valid destination to ' +
                            'upload the client logs to.');
            }
        }

        ## Shared functions - Appliance and Client logs
        function throwIfMissing(element, message) {
            var v = $(element).val();
            if (v == null || v == '') {
                throw new Error(message);
            }
        }
    </script>
</%block>
