Bootstrap REST API
====

The bootstrap service is used to configure the system when it first comes up
(hence the name). Underneath, however, it is simply a service that takes as
input a "task set", queues up that set of tasks for execution, executes the
tasks, and returns information about the tasks as they are executed.

Tasks sets are pre-defined and are part of the bootstrap debian package.

This service exists and has not been incorporated into the web code for one
main reason: because the web service is not running as root. This interface
provides a way for the web code to execute a pre-defined set of code as the
priviledged user.

When using the bootstrap REST API, the flow is generally as follows:

1. `POST http://localhost:9070/tasksets/<task_set>/enqueue`

   This will return, in JSON format, an execution ID. The execution ID is
   a unique identifier that can be used to reference your set of tasks as
   they are executed.

2. `GET http://localhost/eids/<eid>/status`

   This will return the status of your task set. In particular, it
   returns, in JSON format, a "status" enumeration which can be one of
   {"queued", "running", "error", "success"}. In the error case, an error
   message is also returned.

Examples:

       {
           "status": "queued"
       }

       {
           "status": "error",
           "error_message": "Unable to restart nginx."
       }

**N.B.** Once we implement the ability to reload configuration in Java, we can add
     more information to these calls. For example, which task is running, or
     which task failed. For now this is all the information we will provide,
     because providing more details is hard using the current implementation.
