def includeme(config):
    config.add_route('download_team_server', 'download_team_server')
    # The URL string must be consistent with BaseParam.java
    config.add_route('download', 'download')