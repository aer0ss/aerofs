def includeme(config):
    # The URL string must be consistent with BaseParam.java
    config.add_route('download', 'download')
    config.add_route('download_team_server', 'download_team_server')

    config.add_route('downloading', 'downloading')
    config.add_route('downloading_team_server', 'downloading_team_server')

    config.add_route('download_sccm', 'download_sccm')