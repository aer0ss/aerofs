## Bootstrap related functions

<%namespace name="csrf" file="csrf.mako"/>

<%def name="scripts()">
    <script>
        ## Enqueue a bootstrap task
        ##
        ## @param onFaiure it will be called with xhr as the parameter
        function runBootstrapTask(task, onComplete, onFailure) {
            enqueueBootstrapTask(task, function(eid) {
                pollBootstrapTask(eid, onComplete, onFailure);
            }, onFailure);
        }

        ## Enqueue a bootstrap task, and wait until it's complete. Show error
        ## messages on failures.
        ##
        ## @param onComplete it will be called with execution id as the parameter
        function enqueueBootstrapTask(task, onComplete, onFailure) {
            $.post('${request.route_path('json_enqueue_bootstrap_task')}', {
                ${csrf.token_param()}
                task: task
            }).done(function(resp) {
                var eid = resp['execution_id'];
                console.log("bootstrap task " + eid + " enqueued: " + task);
                onComplete(eid);
            }).fail(function(xhr) {
                console.log("enqueue bootstrap task failed");
                showErrorMessageFromResponse(xhr);
                onFailure();
            });
        }

        ## Wait until the bootstrap task specified by eid complete. Show error
        ## messages on failures.
        function pollBootstrapTask(eid, onComplete, onFailure) {
            var interval = window.setInterval(function() {
                $.get('${request.route_path('json_get_bootstrap_task_status')}', {
                    execution_id: eid
                }).done(function(resp) {
                    if (resp['status'] == 'success') {
                        console.log("bootstrap task " + eid + " complete");
                        window.clearInterval(interval);
                        onComplete();
                    } else {
                        console.log("bootstrap task " + eid + " in progress");
                        ## TODO (WW) add timeout?
                    }
                }).fail(function(xhr) {
                    console.log("get bootstrap task status failed");
                    window.clearInterval(interval);
                    showErrorMessageFromResponse(xhr);
                    onFailure();
                });
            }, 1000);
        }
    </script>
</%def>