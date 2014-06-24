<%inherit file="dashboard_layout.mako"/>
<%! page_title = "Devices" %>

<h2>${page_heading}</h2>

<table class="table table-hover">
    <thead>
      <tr>
        <th>Name</th>
        <th></th>
      </tr>
    </thead>
    <tbody>

    %for d in devices:
        <% device_id = d.device_id.encode('hex') %>
        <tr id="tr_${device_id}">
            <td>
                ## The default margin is too big. Use min-width to avoid the form fields to be wrapped
                <form class="form-inline name_form" method="post" style="margin-bottom: 0px; min-width: 160px;">

                    ## The OS icon
                    <label>${device_icon(d.os_family, d.os_name)}</label>

                    ## The device name
                    <label id="name_label_${device_id}">
                        <a id="name_link_${device_id}" href="#" onclick="startEditingName('${device_id}'); return false;">
                            %if d.device_name:
                                ${d.device_name | h}
                            %else:
                                (no name)
                            %endif
                        </a>
                    </label>

                    ## The device name editing form
                    <span class="hide" id="name_inputs_${device_id}">
                        <input type="text" class="input-small name_input"
                               id="name_input_${device_id}"
                               value="${d.device_name | h}"
                               ## for the key up event handler. We can't inline JS calls as other
                               ## links does as inlining doesn't allow passing the event object.
                               data-device-id="${device_id}">

                        <a href="#" onclick="setAndStopEditingName('${device_id}'); return false;">
                            <span class="glyphicon glyphicon-ok"></span>
                        </a>

                        <a href="#" onclick="stopEditingName('${device_id}'); return false;">
                            <span class="glyphicon glyphicon-remove"></span>
                        </a>
                    </span>
                </form>
            </td>
            <td>
                <a href="#"
                   ## We don't support unlink or erase mobile devices yet
                   %if d.os_family != 'Android' and d.os_family != 'iOS':
                        onclick='confirmUnlinkOrErase("unlink", "${device_id}", "${d.device_name | h}"); return false;'
                        style="margin-right: 15px;"
                   %else:
                        class="invisible"
                   %endif
                        >
                    Unlink
                </a>
                <a href="#"
                    ## We don't support unlink or erase mobile devices yet
                    %if d.os_family != 'Android' and d.os_family != 'iOS':
                           onclick='confirmUnlinkOrErase("erase", "${device_id}", "${d.device_name | h}"); return false;'
                           style="margin-right: 15px;"
                    %else:
                           class="invisible"
                    %endif
                        >
                    Erase
                </a>
            </td>
        </tr>
    %endfor

    %for d in mobile_devices:
        <%
            token = d['token']
            os_name = 'Android' if d['client_id'] == 'aerofs-android' else 'iOS'
        %>
        <tr id="${token}">
          <td>
            <label style="display: inline-block;">${device_icon(os_name, os_name)}</label>
            <label style="display: inline-block;">AeroFS ${os_name} Device</label>
          </td>
          <td class="text-muted">N/A</td>
          <td></td>
          <td><a href="#" onclick='confirmUnlinkMobile("${token}", "${os_name}"); return false;'>Unlink</a></td>
        </tr>
    %endfor

    </tbody>
</table>

<div class="text-muted">
    %if are_team_servers:
        <a href="${request.route_path('download_team_server')}">Install the Team Server app</a>
    %else:
        Add new devices by installing <a href="${request.route_path('download')}">desktop</a> or
        <a href="${request.route_path('add_mobile_device')}">mobile</a> apps.
    %endif
</div>

<%def name="device_icon(os_family, os_name)">
    <%
        tooltip = os_name

        # See OSUtil.OSFamily for these string definitions
        if os_family == 'Windows':
            icon = "aerofs-icon-windows"
        elif os_family == 'Mac OS X':
            icon = "aerofs-icon-osx"
        elif os_family == 'Linux':
            icon = "aerofs-icon-linux"
        elif os_family == 'Android':
            icon = "aerofs-icon-android"
        elif os_family == 'iOS':
            icon = "aerofs-icon-osx"
        else:
            icon = "glyphicon glyphicon-question-sign"
            tooltip = "Unknown operating system"
    %>

    <span data-toggle="tooltip" class="${icon} os_name_tooltip" title="${tooltip | h}"></span>
</%def>

<%include file="device_modals.html" />

<%block name="scripts">
    <script type="text/javascript">
        $(document).ready(function() {
            $('.os_name_tooltip').tooltip({placement: 'left'});

            $('.last_seen_not_registered_label').tooltip({
                title: "This device hasn't been registered with the system."});

            $('.last_seen_service_down_label').tooltip({
                title: "The service is temporarily unavailable."});

            ## Stop editing on Escape keypress and submit on Enter keypress
            $('.name_input').keyup(function (e) {
                ## jQuery's data() convert the string to integers when possible :S
                ## See http://bugs.jquery.com/ticket/7579
                var device_id = $(this).attr('data-device-id');

                if (e.keyCode == 27) {
                    stopEditingName(device_id);
                } else if (e.keyCode == 13) {
                    setAndStopEditingName(device_id);
                }

                ## Disable default behavior.
                return false;
            });

            ## Disable default sumbmission behavior when Enter is pressed
            $('.name_form').submit(function () { return false; })
        });

        function startEditingName(device_id) {
            ## Stop editing other devices
            stopEditingAllNames();

            $('#name_label_' + device_id).hide();
            $('#name_inputs_' + device_id).show();
            $('#name_input_' + device_id).focus();
        }

        function stopEditingName(device_id) {
            $('#name_inputs_' + device_id).hide();
            $('#name_label_' + device_id).show();
        }

        function stopEditingAllNames(device_id) {
            $('[id^=name_inputs_]').hide();
            $('[id^=name_label_]').show();
        }

        function setAndStopEditingName(device_id) {
            var $nameLink = $('#name_link_' + device_id);
            var oldName = $nameLink.text();
            var newName = $('#name_input_' + device_id).val();

            ## Do nothing if the string is empty.
            if (!newName) return;

            ## Update the UI first to make the progress appear faster
            $nameLink.text(newName);

            stopEditingName(device_id);

            $.post('${request.route_path('json.rename_device')}',
                {
                    '${url_param_user}': '${user}',
                    '${url_param_device_id}': device_id,
                    '${url_param_device_name}': newName
                }
            )
            .fail(function (jqXHR, textStatus, errorThrown) {
                showErrorMessage(errorThrown);
                ## Restore the old name
                $nameLink.text(oldName);
                $('#name_input_' + device_id).val(oldName);
            });
        }

        function confirmUnlinkMobile(token, os) {
            $('#unlink-mobile-modal').modal('show');
            $('.modal-mobile-os-name').text(os);
            var $confirm = $('#unlink-mobile-modal-confirm');
            $confirm.off('click');
            $confirm.click(function() {
                $('#unlink-mobile-modal').modal('hide');
                revokeToken(token);
                return false;
            });
        }

        function revokeToken(token) {
            $.post('${request.route_path('json_delete_access_token')}', {access_token: token})
            .done(function() {
                showSuccessMessage('The mobile device has been unlinked.');
                $('tr#' + token).remove();
            })
            .fail(function() {
                showErrorMessage("Couldn't unlink the device. Please try again.");
            });
        }

        ## @param action Can be either 'unlink' or 'erase'.
        function confirmUnlinkOrErase(action, device_id, device_name) {
            $('#' + action + '-modal-device-name').text(device_name);
            $('#' + action + '-modal').modal('show');
            var $confirm = $('#' + action + '-model-confirm');
            $confirm.off('click');
            $confirm.click(function() {
                unlinkOrErase(device_id, action);
                return false;
            });
        }

        function unlinkOrErase(device_id, action) {
            $('#' + action + '-modal').modal('hide');
            $.post(action == 'unlink' ?
                        '${request.route_path('json.unlink_device')}' :
                        '${request.route_path('json.erase_device')}',
                {
                    '${url_param_device_id}': device_id
                }
            )
            .done(function() {
                var past = action == 'unlink' ? 'unlinked' : 'erased';
                showSuccessMessage('The device has been ' + past + '.');
                ## Remove the device row
                $('#tr_' + device_id).remove();
            })
            .fail(function(jqXHR, textStatus, errorThrown) {
                showErrorMessage("Couldn't " + action + " the device. Please try again.");
                console.log(textStatus + ": " + errorThrown);
            });
        }
    </script>
</%block>
