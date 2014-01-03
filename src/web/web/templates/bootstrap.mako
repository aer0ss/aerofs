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
            enqueueBootstrapTaskImpl(task, onComplete, onFailure, false);
        }

        function enqueueBootstrapTaskImpl(task, onComplete, onFailure, retry) {
            $.post('${request.route_path('json_enqueue_bootstrap_task')}', {
                task: task
            }).done(function(resp) {
                var eid = resp['execution_id'];
                console.log("bootstrap task " + eid + " enqueued: " + task);
                if (onComplete) onComplete(eid);
            }).fail(function(xhr, textStatus, errorThrown) {
                if (!retry && xhr.readyState == 0 && xhr.status == 0) {
                    ## See comments in pollBootstrapTask() for detail
                    console.log("enqueue bootstrap task readyState == 0. retry.");
                    ## Wait a bit to avoid the same error on the retry.
                    ## Self note: without the delay Chrome's JS console shows
                    ## an error along the line of ""can't load resources".
                    setTimeout(function () {
                        enqueueBootstrapTaskImpl(task, onComplete, onFailure, true);
                    }, 3000);
                } else {
                    console.log("enqueue bootstrap task failed: " +
                        xhr.status + " " + textStatus + " " + errorThrown);
                    showErrorMessageFromResponse(xhr);
                    if (onFailure) onFailure();
                }
            });
        }

        ## Wait until the bootstrap task specified by eid complete. Show error
        ## messages on failures.
        function pollBootstrapTask(eid, onComplete, onFailure) {
            var interval = window.setInterval(function() {
                $.get('${request.route_path('json_get_bootstrap_task_status')}', {
                    execution_id: eid
                }).done(function(resp) {
                    var status = resp['status'];
                    if (status == 'success') {
                        console.log("bootstrap task " + eid + " complete");
                        window.clearInterval(interval);
                        if (onComplete) onComplete();
                    } else {
                        console.log("bootstrap task " + eid + " in progress. status: " +
                            status);
                        ## TODO (WW) add timeout?
                    }
                }).fail(function(xhr, textStatus, errorThrown) {
                    ## According to stack overflow, readyState == 0 can occur when you cancel your
                    ## ajax request before it completes, when you navigate to another page or when
                    ## you refresh the page.
                    ##
                    ## In this case, we do not clear the interval, and retry the request.
                    ##
                    ## TODO (WW) need to figure out exactly why we're getting a ready state here
                    ##
                    ## Reference:
                    ## http://stackoverflow.com/questions/13892529/ajax-request-fails-cant-see-why
                    if (xhr.readyState == 0 && xhr.status == 0) {
                        console.log("get bootstrap task status readyState == 0. retry.");
                    } else {
                        console.log("get bootstrap task status failed: " +
                                xhr.status + " " + textStatus + " " + errorThrown);
                        window.clearInterval(interval);
                        showErrorMessageFromResponse(xhr);
                        if (onFailure) onFailure();
                    }
                });
            }, 1000);
        }
    </script>
</%def>
