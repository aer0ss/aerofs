<%inherit file="dashboard_layout.mako"/>
<%! page_title = "My apps" %>

##
## Note: this file's structure is very similar to apps.mako
##

<%! from datetime import datetime %>

<%namespace file="modal.mako" name="modal"/>

<h2 style="margin-bottom: 30px">My apps</h2>

<table id='clients-table' class="table table-hover hidden">
    <thead>
        <tr><th>App name</th><th></th><th>Created</th><th>Expires</th><th></th></tr>
    </thead>
    <tbody>
        %for token in tokens:
            <tr>
                <td>${token['client_name']}</td>
                <td>
                %if token['owner'] != token['effective_user']:
                    <span class="admin_label label {} tooltip_admin">admin</span>
                %endif
                </td>
                <td>
                    ${datetime.strptime(token['creation_date'], "%Y-%m-%dT%H:%M:%SZ")}
                </td>
                <td>
                    <%
                        expires = token['expires']
                        if expires == 0: expires = 'Never'
                        else: expires = datetime.utcfromtimestamp(long(expires) / 1000)
                    %>
                    ${expires}
                </td>
                <td><a href="#" onclick="confirmDeletion('${token['client_name']}',
                        '${token['token']}', $(this));
                        return false;">Remove</a></td>
            </tr>
        %endfor
    </tbody>
</table>

<p id="no-clients-label" class="hidden">You are not using any AeroFS apps.</p>

<%modal:modal>
    <%def name="id()">delete-modal</%def>
    <%def name="title()">Remove <span class="confirm-client-name"></span>?</%def>
    <%def name="footer()">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
        <a href="#" id="confirm-btn" class="btn btn-danger" data-dismiss="modal">Remove App</a>
    </%def>

    Are you sure you want to remove <strong><span class="confirm-client-name"></span></strong>?
    This app will no longer have permissions to access your AeroFS.
</%modal:modal>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            registerTokenRowTooltips();
            refreshUI();
        })

        function refreshUI() {
            var $table = $('#clients-table');
            var clients = $table.find('tbody').find('tr').length;
            setVisible($table, clients > 0);
            setVisible($('#no-clients-label'), clients == 0);
        }

        function registerTokenRowTooltips() {
            $('.tooltip_admin').tooltip({placement: 'top',
                'title' : 'Admin-level apps can act on ' +
                'users and files within your organization.'});
        }

        function confirmDeletion(clientName, token, $deleteButton) {
            $('.confirm-client-name').text(clientName);
            var $modal = $('#delete-modal');
            $('#confirm-btn').off().on('click', function() {
                $modal.modal('hide');
                deleteAccessToken(token, $deleteButton);
            });
            $modal.modal('show');
        }

        function deleteAccessToken(token, $deleteButton) {
            $.post('${request.route_path('json_delete_access_token')}', {
                'access_token': token
            }).done(function () {
                showSuccessMessage('The application is removed.');
                ## Remove the app's row
                $deleteButton.closest('tr').remove();
                refreshUI();
            }).fail(showErrorMessageFromResponse);
        }
    </script>
</%block>
