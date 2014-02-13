<%inherit file="../maintenance_layout.mako"/>
<%! page_title = "Apps" %>

##
## Note: this file's structure is very similar to access_tokens.mako
##

<%namespace file="../modal.mako" name="modal"/>

<%block name="css">
    <style>
        .info-label {
            display: inline-block;
            width: 100px;
        }
        .id_string {
            font-family:monospace;
        }
    </style>
</%block>

<h2 style="margin-bottom: 30px">Apps</h2>

<table id='clients-table' class="table table-hover hidden">
    <tbody>
        %for client in clients:
            <tr>
                <td><strong>${client['client_name']}</strong></td>
                <td style="padding-bottom: 30px;">
                    <div class="info-label">Client ID:</div>
                    <span class="id_string">${client['client_id']}</span>
                    <br>
                    <div class="info-label">Client Secret:</div>
                    <span class="id_string">${client['secret']}</span>
                    <br>
                    <div class="info-label">Redirect URI:</div>
                    <%
                        uri = client['redirect_uri']
                        if not uri: uri = '-'
                    %>

                    ${uri}
                </td>
                <td>
                  <div style="padding-bottom:10px; white-space:nowrap">
                  <%
                    from json import dumps as jsonify
                    from urllib import quote as urlencode
                    blob = urlencode(jsonify({
                        'client_id': client['client_id'],
                        'client_secret': client['secret'],
                        'hostname': hostname,
                        'cert': cert.replace('\\n','\n'),
                    }).encode('utf-8'))
                  %>
                    ## N.B. download= is not present before HTML5
                    <a href="data:'text/json;charset=utf-8,${blob | n}" download="appconfig.json">Download JSON</a>
                    <a href="https://www.aerofs.com/developers/publish">
                        <i class="icon-question-sign tooltip_json_blob"></i></a>
                  </div>
                  <div>
                    <a href="#" onclick="confirmDeletion('${client['client_id']}', $(this));
                                         return false;">Delete</a>
                  </div>
                </td>
            </tr>
        %endfor
    </tbody>
</table>

<p id="no-clients-label" class="hidden" style="margin-bottom: 40px;">No apps are registered with this appliance yet.</p>

<p class="page-block"><a class="btn btn-primary" href="${request.route_path('register_app')}">Register App</a></p>
<p class="page-block">To learn more, visit the <a href="https://aerofs.com/developers">AeroFS Developers Website</a>.</p>

<%modal:modal>
    <%def name="id()">delete-modal</%def>
    <%def name="title()">Delete the app?</%def>
    <%def name="footer()">
        <a href="#" class="btn" data-dismiss="modal">Close</a>
        <a href="#" id="confirm-btn" class="btn btn-danger" data-dismiss="modal">Delete App</a>
    </%def>

    Keys associated with the app will be deleted. Once deleted, they can no longer
    be used to make API requests.
</%modal:modal>

<%block name="scripts">
    <script>
        $(document).ready(refreshUI);

        function refreshUI() {
            var $table = $('#clients-table');
            var clients = $table.find('tbody').find('tr').length;
            setVisible($table, clients > 0);
            setVisible($('#no-clients-label'), clients == 0);
            registerTooltips();
        }

        function registerTooltips() {
            $('.tooltip_json_blob').tooltip({
                placement: 'right',
                container: 'body',
                title: 'Download a JSON-formatted document used to configure the app. ' +
                    'Click the icon for more information.'
            });
        }

        function confirmDeletion(clientID, $deleteButton) {
            var $modal = $('#delete-modal');
            $('#confirm-btn').off().on('click', function() {
                $modal.modal('hide');
                deleteApp(clientID, $deleteButton);
            });
            $modal.modal('show');
        }

        function deleteApp(clientID, $deleteButton) {
            $.post('${request.route_path('json_delete_app')}', {
                'client_id': clientID
            }).done(function () {
                showSuccessMessage('The application is deleted.');
                ## Remove the app's row
                $deleteButton.closest('tr').remove();
                refreshUI();
            }).fail(showErrorMessageFromResponse);
        }
    </script>
</%block>
