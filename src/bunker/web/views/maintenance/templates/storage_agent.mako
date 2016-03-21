<%inherit file="maintenance_layout.mako"/>
<%! page_title = "Storage Options" %>

<%namespace name="csrf" file="csrf.mako"/>
<%namespace name="modal" file="modal.mako"/>
<%namespace name="loader" file="loader.mako"/>
<%namespace name="spinner" file="spinner.mako"/>
<%namespace name="progress_modal" file="progress_modal.mako"/>

<div class="page-block">
    <h2>Storage Servers</h2>
    <p>
        Storage Servers are a reliable, always-online client to sync all of your organization's files. 
        Storage servers can be run as either an external storage server or an onboard storage server within your AeroFS Application Server.
        We recommend that AeroFS deployments use an external Storage Server, as both the Application Server and the Storage Server can beresource intensive.
    </p>

    %if is_maintenance_mode:
        <p><strong>You cannot set up a storage server or the onboard storage module while in maintenance mode</strong></p>
    %else:
        ${default_page()}
    %endif
</div>


<%def name="default_page()">
    <div class="row">
        <div class="col-sm-12">
            <h4>Onboard Storage Server</h4>
            <p>Runs on the same machine as the Application Server for a simpler deployment.</p>
        </div>
    </div>
    <form id="onboard-form" method="POST" onsubmit="setOnboardStorage(); return false;">
        ${csrf.token_input()}
        <div id="onboard-storage">
            <p>Changing the storage options for an onboard storage module can make previously synced data unavailable.</p>
            ${storage_options(prefix="onboard", prefilled=onboard_settings)}
        </div>

        <div class="row">
            <div class="col-sm-12">
                <button class="btn btn-primary" type="submit">
                    Save Configuration
                </button>
            </div>
        </div>
    </form>
    <hr/>
    <div class="row">
        <div class="col-sm-12">
            <h4>External Storage Servers</h4>
            <p>Runs on a separate machine for greater redundancy and scalability.</p>
            <p>
                <a class="nojump" id="show-external-setup" href="#"
                    onclick="showExternalSetup(); return false;">
                    Configure external storage server &#x25BE;</a>
                <a class="nojump" id="hide-external-setup" href="#"
                    onclick="hideExternalSetup(); return true;" style="display: none;">
                    Hide configuration form &#x25B4;</a>
            </p>
        </div>
    </div>
    <div id="external-setup" style="display: none;">
        <div class="row">
            <div class="col-sm-12">
                <p>Fill out this form to download a configuration bundle, then upload it to an external Storage Server.</p>
            </div>
        </div>
        <form id="external-form" method="POST" onsubmit="makeAndDownloadConfigBundle(); return false;">
            ${csrf.token_input()}
            ${storage_options(prefix="external")}

            <div class="row">
                <div class="col-sm-12">
                    <button class="btn btn-primary" type="submit">
                        Download Storage Server Configuration
                    </button>
                </div>
            </div>
        </form>
    </div>
    <hr/>
    <div class="row">
        <div class="col-sm-12">
            <h4>Clearing Onboard Data</h4>
            <p>
                After migrating stored files from an onboard Storage Server to an external one, clearing synced files from the local hard drive saves space and creates smaller backups.
                This <strong>can</strong> cause permanent data loss if you haven't completely migrated your onboard Storage Server's data.
            </p>
            <p>
                <a class="nojump" id="show-clear-storage" href="#"
                    onclick="showClearStorage(); return false;">
                    Clear locally stored files &#x25BE;</a>
                <a class="nojump" id="hide-clear-storage" href="#"
                    onclick="hideClearStorage(); return true;" style="display: none;">
                    Hide &#x25B4;</a>
            </p>
        </div>
    </div>
    <div id="clear-storage" style="display: none;">
        <form id="clear-storage-form" method="POST" onsubmit="clearOnboardStorage(); return false;">
            ${csrf.token_input()}
            <button class="btn btn-danger" type="submit">
                Clear Onboard Data
            </button>
        </form>
    </div>

    <div id="warning-modal" class="modal" tabindex="-1" role="dialog">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal">&times;</button>
                    <h4>Disk Usage Warning</h4>
                </div>

                <div class="modal-body">
                    <p>Running the onboard Storage Server and saving files locally will quickly fill up the hard drive, potentially causing problems for the Application Server.</p>
                    <p>Please only use these settings if you are running AeroFS as a proof of concept.</p>

                </div>
                <div class="modal-footer">
                    <a href="#" class="btn btn-default" data-dismiss="modal">Close</a>
                    <a href="#" class="btn btn-danger" data-dismiss="modal" 
                    onclick="setOnboardStorageNoWarn(); return false;">Got It</a>
                </div>
            </div>
        </div>
    </div>
</%def>

<%def name="storage_options(prefix, prefilled={})">
    <div class="row">
        <div class="col-sm-12">
            %if prefix=="onboard":
                <div class="radio">
                    <label>
                        <input type='radio' id="${prefix}-disabled" name='sa_storage_type' required value='disabled' onchange="disableStorageSelected(this);">
                        Disable onboard storage
                    </label>
                </div>
            %endif
            <div class="radio">
                <label>
                    <input type='radio' id="${prefix}-local" name='sa_storage_type' required value='local' onchange="localStorageSelected(this);">
                    Store files on local hard drive
                </label>
            </div>
            <div class="radio">
                <label>
                    <input type='radio' id="${prefix}-s3" name='sa_storage_type' required value='s3' onchange="s3StorageSelected(this);">
                    Store files in S3 
                </label>

                <div id="${prefix}-s3-options" style="display: none;">
                    ${s3_options(prefix, prefilled)}
                </div>
            </div>
            <div class="radio">
                <label>
                    <input type='radio' id="${prefix}-swift" name='sa_storage_type' required value='swift' onchange="swiftStorageSelected(this);">
                    Store files in Swift 
                </label>

                <div id="${prefix}-swift-options" style="display: none;">
                    ${swift_options(prefix, prefilled)}
                </div>
            </div>
        </div>
    </div>
    <% onboard_enabled = onboard_settings.get("onboard.storage.enabled", "") %>
    <div class="row" id="${prefix}-nonstorage-options"
        %if not onboard_enabled:
            style="display:none;"
        %endif
    >
        <div class="col-sm-6">
            <label for="${prefix}-device-name" class="control-label">Device Name</label>
            <input class="form-control ${prefix}-required" id="${prefix}-device-name"
                   name="sa_device_name" type="text"
                   value="${prefilled.get('onboard.sa.devicename', '')}">
            <div class="help-block">To differentiate Storage Servers</div>
        </div>
    </div>
</%def>

<%def name="s3_options(prefix, prefilled={})">
    <div class="row">
        <div class="col-sm-12">
            <label for="${prefix}-s3-bucker-id" class="control-label">S3 bucket</label>
            <input class="form-control ${prefix}-s3-required" id="${prefix}-s3-bucket-id"
                    name="sa_s3_bucket_id" type="text"
                    value="${prefilled.get('onboard.sa.s3.bucketid', '')}">
            <div class="help-block">e.g. arn:aws:s3:::examplebucket</div>
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <label for="${prefix}-s3-access-key" class="control-label">S3 Access Key</label>
            <input class="form-control ${prefix}-s3-required" id="${prefix}-s3-access-key"
                   name="sa_s3_access_key" type="text"
                   value="${prefilled.get('onboard.sa.s3.accesskey', '')}">
        </div>
        <div class="col-sm-6">
            <label for="${prefix}-s3-secret-key" class="control-label">S3 Secret Key</label>
            <input class="form-control ${prefix}-s3-required" id="${prefix}-s3-secret-key"
                   name="sa_s3_secret_key" type="text"
                   value="${prefilled.get('onboard.sa.s3.secretkey', '')}">
        </div>
    </div>
    <div class="row">
        <div class="col-sm-6">
            <label for="${prefix}-s3-encryption-password" class="control-label">S3 Encryption Password</label>
            <input class="form-control" id="${prefix}-swift-encryption-password"
                   name="sa_s3_encryption_password" type="password"
                   value="${prefilled.get('onboard.sa.s3.encryptionpassword', '')}">
            <div class="help-block">Remote data encryption is optional</div>
        </div>
    </div>
</%def>

<%def name="swift_options(prefix, prefilled={})">
    <div class="row">
        <div class="col-sm-12">
            <div class="radio">
                <label>
                    <input type="radio" id="${prefix}-swift-auth-basic" name="sa_swift_auth_mode" 
                    class="${prefix}-swift-required" value="basic" onchange="basicAuthSelected(this);">
                    Basic Authorization
                </label>
            </div>
            <div class="radio">
                <label>
                    <input type="radio" id="${prefix}-swift-auth-keystone" name="sa_swift_auth_mode" 
                    class="${prefix}-swift-required" value="keystone" onchange="keystoneAuthSelected(this);">
                    Keystone Authorization
                </label>

                <div class="row" id="${prefix}-keystone-options" style="display: none;">
                    <div class="col-sm-6">
                        <label for="${prefix}-swift-tenant-id" class="control-label">Keystone Tenant ID</label>
                        <input class="form-control ${prefix}-keystone-required" id="${prefix}-swift-tenant-id"
                               name="sa_swift_tenant_id" type="text"
                               value="${prefilled.get('onboard.sa.swift.tenantid', '')}">
                    </div>
                    <div class="col-sm-6">
                        <label for="${prefix}-swift-tenant-name" class="control-label">Keystone Tenant Name</label>
                        <input class="form-control ${prefix}-keystone-required" id="${prefix}-swift-tenant-name"
                               name="sa_swift_tenant_name" type="text"
                               value="${prefilled.get('onboard.sa.swift.tenantname', '')}">
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-sm-6">
            <label for="${prefix}-swift-url" class="control-label">Swift URL</label>
            <input class="form-control ${prefix}-swift-required" id="${prefix}-swift-url"
                    name="sa_swift_url" type="text"
                    value="${prefilled.get('onboard.sa.swift.url', '')}">
            <div class="help-block">e.g. http://swift.hostname:8080/auth/v1.0</div>
        </div>
        <div class="col-sm-6">
            <label for="${prefix}-swift-container" class="control-label">Swift Container</label>
            <input class="form-control ${prefix}-swift-required" id="${prefix}-swift-container"
                    name="sa_swift_container" type="text"
                    value="${prefilled.get('onboard.sa.swift.container', '')}">
        </div>
    </div>

    <div class="row">
        <div class="col-sm-6">
            <label for="${prefix}-swift-username" class="control-label">Swift Username</label>
            <input class="form-control ${prefix}-swift-required" id="${prefix}-swift-username"
                   name="sa_swift_username" type="text"
                   value="${prefilled.get('onboard.sa.swift.username', '')}">
        </div>
        <div class="col-sm-6">
            <label for="${prefix}-swift-password" class="control-label">Swift Password</label>
            <input class="form-control ${prefix}-swift-required" id="${prefix}-swift-password"
                   name="sa_swift_password" type="password"
                   value="${prefilled.get('onboard.sa.swift.password', '')}">
        </div>
    </div>
    <div class="row">
        <div class="col-sm-6">
            <label for="${prefix}-swift-encryption-password" class="control-label">Swift Encryption Password</label>
            <input class="form-control" id="${prefix}-swift-encryption-password"
                   name="sa_swift_encryption_password" type="password"
                   value="${prefilled.get('onboard.sa.swift.encryptionpassword', '')}">
            <div class="help-block">Remote data encryption is optional</div>
        </div>
    </div>
</%def>

<%progress_modal:progress_modal>
    <%def name="id()">progress-modal</%def>
    Please wait while we apply changes...
</%progress_modal:progress_modal>

<%block name="scripts">
    <%progress_modal:scripts/>
    <%spinner:scripts/>
    <%loader:scripts/>

    <% onboard_enabled = onboard_settings.get("onboard.storage.enabled", "") %>
    <% storage_type = onboard_settings.get("onboard.sa.storagetype", "") %>
    <% swift_auth = onboard_settings.get("onboard.sa.swift.authmode", "") %>
    <script>
        var onboardOptionsChanged = false;
        var onboardInitialSetting = '${onboard_enabled}'.toLowerCase() == 'true';

        $(document).ready(function() {
            %if not is_maintenance_mode:
                //make the most inner changes first, so that later changes can remove 'required' attribute off hidden sections
                var swiftAuth = '${swift_auth}';
                if (swiftAuth == 'basic') {
                    document.getElementById('onboard-swift-auth-basic').checked = true;
                } else if (swiftAuth == 'keystone') {
                    document.getElementById('onboard-swift-auth-keystone').checked = true;
                }

                var selectedStorageType = '${storage_type}';
                if (selectedStorageType == 'local') {
                    var checkbox = document.getElementById('onboard-local');
                    checkbox.checked = true;
                    localStorageSelected(checkbox);
                } else if (selectedStorageType == 's3') {
                    var checkbox = document.getElementById('onboard-s3');
                    checkbox.checked = true;
                    s3StorageSelected(checkbox);
                } else if (selectedStorageType == 'swift') {
                    var checkbox = document.getElementById('onboard-swift');
                    checkbox.checked = true;
                    swiftStorageSelected(checkbox);
                }

                if (!onboardInitialSetting) {
                    var checkbox = document.getElementById('onboard-disabled');
                    checkbox.checked = true;
                    disableStorageSelected(checkbox);
                }

                initializeProgressModal();

                $('input[id^="onboard-"]').change(function () {
                    onboardOptionsChanged = true;
                })

                $('a.nojump').click(function(e) {
                    e.preventDefault();
                })
            %endif
        });

        function setOnboardStorage() {
            if (document.getElementById('onboard-local').checked) {
                var $warnModal = $('#warning-modal');
                $warnModal.modal('show');
            } else {
                setOnboardStorageNoWarn();
            }
        }

        function setOnboardStorageNoWarn() {
            var newsetting = !document.getElementById('onboard-disabled').checked;
            var newTarget = newsetting ? 'onboard' : 'default';
            var needsRestart = !(onboardInitialSetting == false && newsetting == false) && onboardInitialSetting !== newsetting || onboardOptionsChanged;
            
            var $progressModal = $('#progress-modal');
            $progressModal.modal('show');
            var always = function() {
                $progressModal.modal('hide');
            };

            var success = function () {
                showSuccessMessage('Updated Onboard Storage Settings.');
                always();
            }

            var failure = function(xhr) {
                showErrorMessageFromResponse(xhr);
                always();
            }

            var rebootIfNecessary = function() {
                $.post('${request.route_path('enable_onboard_sa')}', { enabled: newsetting })
                    .done(function() {
                        if (needsRestart) {
                            reboot(newTarget, success, failure);
                        } else {
                            success();
                        }
                    }).error(failure);
            }

            if (onboardOptionsChanged) {
                configureOnboardStorage(rebootIfNecessary, failure);
            } else {
                rebootIfNecessary();
            }

        }

        function showExternalSetup() {
            $('#external-setup').show();
            $('#show-external-setup').hide();
            $('#hide-external-setup').show();
        }

        function hideExternalSetup() {
            $('#external-setup').hide();
            $('#show-external-setup').show();
            $('#hide-external-setup').hide();
        }

        function showClearStorage() {
            $('#clear-storage').show();
            $('#show-clear-storage').hide();
            $('#hide-clear-storage').show();
        }

        function hideClearStorage() {
            $('#clear-storage').hide();
            $('#show-clear-storage').show();
            $('#hide-clear-storage').hide();
        }

        //TODO form-control required functionality
        function disableStorageSelected(radio) {
            if (radio.checked) {
                var prefix = radio.id.split('-')[0];
                $('#' + prefix + '-s3-options').hide();
                $('#' + prefix + '-swift-options').hide();
                $('#' + prefix + '-nonstorage-options').hide();
            }
        }

        function localStorageSelected(radio) {
            if (radio.checked) {
                var prefix = radio.id.split('-')[0];
                $('#' + prefix + '-s3-options').hide();
                $('#' + prefix + '-swift-options').hide();
                $('#' + prefix + '-nonstorage-options').show();
            }
        }

        function s3StorageSelected(radio) {

            if (radio.checked) {
                var prefix = radio.id.split('-')[0];
                $('#' + prefix + '-s3-options').show();
                $('#' + prefix + '-swift-options').hide();
                $('#' + prefix + '-nonstorage-options').show();
            }
        }

        function swiftStorageSelected(radio) {
            if (radio.checked) {
                var prefix = radio.id.split('-')[0];
                $('#' + prefix + '-s3-options').hide();
                $('#' + prefix + '-swift-options').show();
                $('#' + prefix + '-nonstorage-options').show();
            }
        }

        function basicAuthSelected(radio) {
            if (radio.checked) {
                var prefix = radio.id.split('-')[0];
                $('#' + prefix + '-keystone-options').hide();
            }
        }

        function keystoneAuthSelected(radio) {
            if (radio.checked) {
                var prefix = radio.id.split('-')[0];
                $('#' + prefix + '-keystone-options').show();
            }

        }

        function configureOnboardStorage(onSuccess, onFailure) {
            $.post('${request.route_path('config_onboard_sa')}', $('#onboard-form').serialize())
                .done(onSuccess)
                .error(onFailure);
        }

        function makeAndDownloadConfigBundle() {
            $.post('${request.route_path('make_config_bundle')}', $('#external-form').serialize())
                .done(function() {
                    console.log("bundle created");
                    showSuccessMessage('Created config bundle, download should start shortly...');
                    window.location.assign('${request.route_path('download_config_bundle')}');
                })
                .error(function(xhr) {
                    showErrorMessageFromResponse(xhr);
                });
        }

        function clearOnboardStorage() {
            var $progressModal = $('#progress-modal');
            $progressModal.modal('show');
            var always = function() {
                $progressModal.modal('hide');
            };
            $.post('${request.route_path('clear_stored_data')}')
                .done(function() {
                    showSuccessMessage('Cleared local data')
                    always();
                })
                .error(function(xhr) {
                    showErrorMessageFromResponse(xhr);
                    always();
                });
        }
    </script>
</%block>
