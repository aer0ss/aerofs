-- tt_sign_up
drop table if exists tt_sign_up;
create table tt_sign_up(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    PRIMARY KEY(`ev_user`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into tt_sign_up(ev_user,ev_ts)
    select
    hdr_user as ev_user,
    FROM_UNIXTIME(MIN(hdr_ts)/1000) as ev_ts
    from
    aerofs_sv_beta.sv_event
    where ev_type='1000'
    group by ev_user;

-- tt_file_saved
drop table if exists tt_file_saved;
create table tt_file_saved(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    INDEX(`ev_user`),
    INDEX(`ev_ts`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into tt_file_saved(ev_user,ev_ts)
    select
    hdr_user as ev_user,
    FROM_UNIXTIME(hdr_ts/1000) as ev_ts
    from
    aerofs_sv_beta.sv_event
    where ev_type='0';

-- tt_clicked_taskbar
drop table if exists tt_clicked_taskbar;
create table tt_clicked_taskbar(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    INDEX(`ev_user`),
    INDEX(`ev_ts`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into tt_clicked_taskbar
    select hdr_user as ev_user,
    FROM_UNIXTIME(hdr_ts/1000) as ev_ts
    from aerofs_sv_beta.sv_event
    where ev_type='100';

--
drop table if exists tt_invite_sent;
create table tt_invite_sent(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    INDEX(`ev_user`),
    INDEX(`ev_ts`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into tt_invite_sent
    select hdr_user as ev_user,
    FROM_UNIXTIME(hdr_ts/1000) as ev_ts
    from aerofs_sv_beta.sv_event
    where ev_type='300';

drop table if exists tt_folderless_invite_sent;
create table tt_folderless_invite_sent(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    INDEX(`ev_user`),
    INDEX(`ev_ts`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into tt_folderless_invite_sent
    select hdr_user as ev_user,
    FROM_UNIXTIME(hdr_ts/1000) as ev_ts
    from aerofs_sv_beta.sv_event
    where ev_type='301';

--
--
--
drop table if exists analytics;
create table analytics (
    `user` varchar(254) not null,
    `sign_up_ts` timestamp,
    `first_taskbar_click_ts` timestamp,
    `first_share_invite_sent_ts` timestamp,
    `first_aerofs_invite_sent_ts` timestamp,
    `first_file_saved_ts` timestamp,
    `last_file_saved_ts` timestamp,
    PRIMARY KEY(`user`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into analytics(user,first_taskbar_click_ts)
    (select
    tt_clicked_taskbar.ev_user as user,
    MIN(tt_clicked_taskbar.ev_ts) as first_taskbar_click_ts
    from
    tt_clicked_taskbar
    group by user)
    on duplicate key update
    first_taskbar_click_ts = (select MIN(tt_clicked_taskbar.ev_ts) from tt_clicked_taskbar where  user = tt_clicked_taskbar.ev_user group by user);

insert into analytics(user,first_share_invite_sent_ts)
    (select
    tt_invite_sent.ev_user as user,
    MIN(tt_invite_sent.ev_ts) as first_share_invite_sent_ts
    from tt_invite_sent
    group by user)
    on duplicate key update
    first_share_invite_sent_ts = (select MIN(tt_invite_sent.ev_ts) from tt_invite_sent  where user = tt_invite_sent.ev_user  group by user);

insert into analytics(user, first_aerofs_invite_sent_ts)
    (select
    tt_folderless_invite_sent.ev_user as user,
    MIN(tt_folderless_invite_sent.ev_ts) as first_aerofs_invite_sent_ts
    from tt_folderless_invite_sent
    group by user)
    on duplicate key update
    first_aerofs_invite_sent_ts = (select MIN(tt_folderless_invite_sent.ev_ts) from tt_folderless_invite_sent  where user = tt_folderless_invite_sent.ev_user group by user);

insert into analytics(user,first_file_saved_ts)
    (select
    tt_file_saved.ev_user as user,
    MIN(tt_file_saved.ev_ts) as first_file_saved_ts
    from tt_file_saved
    group by user)
    on duplicate key update
    first_file_saved_ts = (select MIN(tt_file_saved.ev_ts) from tt_file_saved where user = tt_file_saved.ev_user  group by user);

insert into analytics(user,last_file_saved_ts)
    (select
    tt_file_saved.ev_user as user,
    MAX(tt_file_saved.ev_ts) as last_file_saved_ts
    from tt_file_saved
    group by user)
    on duplicate key update
    last_file_saved_ts = (select MAX(tt_file_saved.ev_ts) from tt_file_saved  where user = tt_file_saved.ev_user group by user);

update analytics
    set sign_up_ts =
    (select tt_sign_up.ev_ts from tt_sign_up where tt_sign_up.ev_user = analytics.user);

insert ignore into analytics(user,sign_up_ts)
    select
    tt_sign_up.ev_user as user,
    tt_sign_up.ev_ts as sign_up_ts
    from tt_sign_up;

drop table if exists analytics_file_saved;
create table analytics_file_saved (
    `user` varchar(254) not null,
    `sign_up_ts` timestamp,
    `file_saved_ts` timestamp not null,
    INDEX user_idx (`user`),
    INDEX sign_up_ts_idx (`sign_up_ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into analytics_file_saved(user,file_saved_ts)
    select
    tt_file_saved.ev_user as user,
    DATE(tt_file_saved.ev_ts) as file_saved_ts
    from
    tt_file_saved
    group by user, DATE(tt_file_saved.ev_ts);
update analytics_file_saved
    set analytics_file_saved.sign_up_ts =
    (select tt_sign_up.ev_ts
    from tt_sign_up
    where tt_sign_up.ev_user = analytics_file_saved.user);
insert into analytics_file_saved(user,sign_up_ts)
    select
    tt_sign_up.ev_user as user,
    tt_sign_up.ev_ts as sign_up_ts
    from tt_sign_up;
