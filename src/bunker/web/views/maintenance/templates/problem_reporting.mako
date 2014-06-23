<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Problem Reporting" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="modal" file="modal.mako"/>

<%def name="support_url()">
    https://support.aerofs.com/hc/en-us/articles/202258960
</%def>
<%def name="support_url2()">
    https://support.aerofs.com/hc/en-us/articles/202375834
</%def>

<h2>Problem reporting</h2>

<p>
    AeroFS users can directly report problems by selecting "Help" >
    "Report a Problem" from the tray menu using the desktop client.
    <a href="${support_url()}">Learn more.</a>
</p>
<p>
    <b>Note:</b> AeroFS also collects logs when users report problems.
</p>

<form class="page-block" method="POST">
    ${csrf.token_input()}

    <div class="checkbox">
        <label>
            <input type="radio" name="option" value="disabled">
            Disable reporting problems
        </label>
    </div>
    <div class="checkbox">
        <label>
            <input type="radio" name="option" value="public">
            Report problems to AeroFS Support
        </label>
    </div>
    <div class="checkbox">
        <label>
            <input type="radio" name="option" value="on-site">
            Report problems to an <a href="${support_url2()}">on-site server</a>
            <div id="on-site-options" hidden>
                ## the p tag is used as a hack to increase margin between the
                ## label and the on-site options
                <p> </p>
                <div class="row">
                    <div class="col-sm-6">
                        <label for="hostname">Hostname:</label>
                        <input type="text" id="hostname" name="hostname"
                               class="form-control" value="${hostname}">
                    </div>
                    <div class="col-sm-2">
                        <label for="port">Port:</label>
                        <input type="text" id="port" name="port"
                               class="form-control" value="${port}">
                    </div>
                    <div class="col-sm-4">
                        <label for="certificate-selector">Upload a
                            %if option == 'on-site':
                                new
                            %endif
                            certificate file:</label>
                        <input type="file" id="certificate-selector"
                               class="form-control">
                        <input type="hidden" id="certificate" name="certificate"
                               class="form-control">
                    </div>
                </div>
            </div>
        </label>
    </div>

    <hr />
    <button type="submit" class="btn btn-primary">Save</button>
</form>

<%modal:modal>
    <%def name="id()">success-modal</%def>
    <%def name="title()">Success</%def>

    <p>
        The configuration is saved. However, it will not take effect until
        AeroFS is restarted. Please advise AeroFS users to restart AeroFS on
        their computers.
    </p>

    <%def name="footer()">
        <a href="#" data-dismiss="modal" class="btn btn-primary">OK</a>
    </%def>
</%modal:modal>

<%block name="scripts">
    <script>
        $(document).ready(function() {
            $(':input[name=option]').change(onSelectedOptionChanged);
            $(':input[name=option][value=${option}]').click();

            linkFileSelectorToField('#certificate-selector', '#certificate');

            $('form').on('submit', onSubmit);
        });

        function onSubmit() {
            if (!isDirty()) {
                onSuccess();
            } else if (isFormValid()) {
                $.post("${request.route_path('json_set_problem_reporting_options')}",
                                $('form').serialize())
                        .done(onSuccess)
                        .fail(showErrorMessageFromResponse);
            }
            return false;
        }

        function onSuccess() {
            var $modal = $('#success-modal');

            $modal.on('hidden.bs.modal', function() {
                window.location.assign("${request.route_path('problem_reporting')}");
            });
            $modal.modal('show');
        }

        function isDirty() {
            if (getSelectedOption() != '${option}') {
                return true;
            }

            if ('${option}' == 'on-site') {
                if ($('#hostname').val() != '${hostname}'
                        || $('#port').val() != '${port}'
                        || hasField('#certificate')) {
                    return true;
                }
            }

            return false;
        }

        function isFormValid() {
            ## TODO more client-side validation? or leave it all to server?

            if (getSelectedOption() == 'on-site') {
                if (!hasField('#hostname')) {
                    showErrorMessage('Please specify the hostname of the ' +
                            'on-site support system.');
                    return false;
                }

                if (!hasField('#port')) {
                    showErrorMessage('Please specify the port of the on-site ' +
                            'support system.');
                    return false;
                }

                ## the only time when we don't need certificate is when we have
                ## previously selected on-site and the hostname hasn't changed.
                if ('${option}' == 'on-site'
                        && $('#hostname').val() == '${hostname}') {
                    return true;
                }

                if (!hasField('#certificate')) {
                    showErrorMessage('Please specify the certificate of the ' +
                            'on-site support system.');
                    return false;
                }
            }

            return true;
        }

        function getSelectedOption() {
            return $(':input[name=option]:checked').val();
        }

        function onSelectedOptionChanged() {
            $('#on-site-options').attr('hidden',
                    getSelectedOption() != 'on-site');
        }

        function hasField(element) {
            var v = $(element).val();
            return !(v == null || v == '');
        }
    </script>
</%block>
