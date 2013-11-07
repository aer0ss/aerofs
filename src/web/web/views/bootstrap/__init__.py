from web.util import add_routes

routes = {
    'json_enqueue_bootstrap_task',
    'json_get_bootstrap_task_status'
}

def includeme(config):
    add_routes(config, routes)
