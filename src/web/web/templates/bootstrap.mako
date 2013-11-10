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
                    ## According to stack overflow, readyState == 0 can occur when you cancel your
                    ## ajax request before it completes, when you navigate to another page or when
                    ## you refresh the page.
                    ##
                    ## Reference:
                    ## http://stackoverflow.com/questions/13892529/ajax-request-fails-cant-see-why
                    ##
                    ## In this case, we do not clear the interval, and retry the request.
                    ##
                    ## TODO (MP) need to figure out exactly why we're getting a ready state here.
                    ## Maybe is has something to do with our progress modal?
                    if (xhr.readyState == 0 && xhr.status == 0) {
                        console.log("get bootstrap task status readyState == 0. retry.");
                    } else {
                        console.log("get bootstrap task status failed");
                        window.clearInterval(interval);
                        showErrorMessageFromResponse(xhr);
                        onFailure();
                    }
                });
            }, 1000);
        }
    </script>
</%def>