-- create temporary table tt_clicked_taskbar_open_aerofs                 as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='101';
-- create temporary table tt_clicked_taskbar_share_folder                as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='102';
-- create temporary table tt_clicked_taskbar_accept_invite               as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='103';
-- create temporary table tt_clicked_taskbar_manager_shared_folder       as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='104';
-- create temporary table tt_clicked_taskbar_transfers                   as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='105';
-- create temporary table tt_clicked_taskbar_preferences                 as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='106';
-- create temporary table tt_clicked_taskbar_invite_folderless           as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='107';
-- create temporary table tt_clicked_taskbar_exit                        as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='108';
-- create temporary table tt_clicked_taskbar_default_selection           as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='109';
-- create temporary table tt_clicked_taskbar_apply_update                as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='110';
-- create temporary table tt_clicked_shellext                            as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='200';
-- create temporary table tt_clicked_shellext_invite_folder              as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='201';
-- create temporary table tt_sign_returning                              as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='1001';
-- create temporary table tt_sign_in                                     as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='1002';
-- create temporary table tt_exit                                        as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='1003';
-- create temporary table tt_create_store                                as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='1004';
-- create temporary table tt_join_store                                  as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='1005';
-- create temporary table tt_join_store_ok                               as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='1006';
-- create temporary table tt_update                                      as select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='1007';

-- tt_sign_up
create temporary table tt_sign_up(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    PRIMARY KEY(`ev_user`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into tt_sign_up(ev_user,ev_ts)
    select
    hdr_user as ev_user,
    FROM_UNIXTIME(MIN(hdr_ts)/1000) as ev_ts
    from
    sv_event
    where ev_type='1000'
    group by ev_user;

-- tt_file_saved
create temporary table tt_file_saved(
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
    sv_event
    where ev_type='0';

-- tt_clicked_taskbar
create temporary table tt_clicked_taskbar(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    INDEX(`ev_user`),
    INDEX(`ev_ts`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
insert into tt_clicked_taskbar select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='100';

--
create temporary table tt_invite_sent(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    INDEX(`ev_user`),
    INDEX(`ev_ts`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
insert into tt_invite_sent select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='300';

create temporary table tt_folderless_invite_sent(
    `ev_user` varchar(254) not null,
    `ev_ts` timestamp not null,
    INDEX(`ev_user`),
    INDEX(`ev_ts`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
insert into tt_folderless_invite_sent select hdr_user as ev_user,FROM_UNIXTIME(hdr_ts/1000) as ev_ts   from sv_event where ev_type='301';

drop table if exists analytics_clicked_taskbar;
create table analytics_clicked_taskbar (
    `user` varchar(254) not null,
    `sign_up_ts` timestamp,
    `taskbar_click_ts` timestamp not null,
    INDEX user_idx (`user`),
    INDEX sign_up_ts_idx (`sign_up_ts`),
    INDEX taskbar_click_ts_idx (`taskbar_click_ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
insert into analytics_clicked_taskbar(user,taskbar_click_ts)
	select
    tt_clicked_taskbar.ev_user as user,
	DATE_ADD(MAKEDATE(YEAR(tt_clicked_taskbar.ev_ts), 1), INTERVAL WEEK(tt_clicked_taskbar.ev_ts) WEEK) as taskbar_click_ts
	from
	tt_clicked_taskbar
    group by user, YEARWEEK(tt_clicked_taskbar.ev_ts);
update analytics_clicked_taskbar
    set analytics_clicked_taskbar.sign_up_ts =
    (select tt_sign_up.ev_ts
    from tt_sign_up
    where tt_sign_up.ev_user = analytics_clicked_taskbar.user);
insert into analytics_clicked_taskbar(user,sign_up_ts)
    select
    tt_sign_up.ev_user as user,
    tt_sign_up.ev_ts as sign_up_ts
    from tt_sign_up;

drop table if exists analytics_invite_sent;
create table analytics_invite_sent (
    `user` varchar(254) not null,
    `sign_up_ts` timestamp,
    `folder_share_sent_ts` timestamp,
    `aerofs_invite_sent_ts` timestamp,
    INDEX user_idx (`user`),
    INDEX sign_up_ts_idx (`sign_up_ts`),
    INDEX folder_share_sent_ts_idx (`folder_share_sent_ts`),
    INDEX aerofs_invite_sent_ts_idx (`aerofs_invite_sent_ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into analytics_invite_sent(user,folder_share_sent_ts)
    select
    tt_invite_sent.ev_user as user,
    DATE_ADD(MAKEDATE(YEAR(tt_invite_sent.ev_ts), 1), INTERVAL WEEK(tt_invite_sent.ev_ts) WEEK) as folder_share_sent_ts
    from
    tt_invite_sent
    group by user, YEARWEEK(tt_invite_sent.ev_ts);
insert into analytics_invite_sent(user,aerofs_invite_sent_ts)
    select
    tt_folderless_invite_sent.ev_user as user,
    DATE_ADD(MAKEDATE(YEAR(tt_folderless_invite_sent.ev_ts), 1), INTERVAL WEEK(tt_folderless_invite_sent.ev_ts) WEEK) as aerofs_invite_sent_ts
    from
    tt_folderless_invite_sent
     group by user, YEARWEEK(tt_folderless_invite_sent.ev_ts);
update analytics_invite_sent
    set analytics_invite_sent.sign_up_ts =
    (select tt_sign_up.ev_ts
    from tt_sign_up
    where tt_sign_up.ev_user = analytics_invite_sent.user);
insert into analytics_invite_sent(user,sign_up_ts)
    select
    tt_sign_up.ev_user as user,
    tt_sign_up.ev_ts as sign_up_ts
    from
    tt_sign_up;

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



-- drop view if exists analytics;
drop table if exists analytics;
create table analytics
select
tt_sign_up.ev_user as user,
tt_sign_up.ev_ts as sign_up_ts,
MIN(tt_clicked_taskbar.ev_ts) as first_taskbar_click_ts,
MIN(tt_invite_sent.ev_ts) as first_share_invite_sent_ts,
MIN(tt_folderless_invite_sent.ev_ts) as first_aerofs_invite_sent_ts,
MIN(tt_file_saved.ev_ts) as first_file_saved_ts,
MAX(tt_file_saved.ev_ts) as last_file_saved_ts
from
tt_sign_up
left join tt_clicked_taskbar on tt_sign_up.ev_user = tt_clicked_taskbar.ev_user
left join tt_invite_sent on tt_sign_up.ev_user = tt_invite_sent.ev_user
left join tt_folderless_invite_sent on tt_sign_up.ev_user = tt_folderless_invite_sent.ev_user
left join tt_file_saved on tt_sign_up.ev_user = tt_file_saved.ev_user
group by tt_sign_up.ev_user;