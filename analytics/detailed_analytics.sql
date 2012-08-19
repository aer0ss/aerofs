
--
--
--
-- progressive sign up count for a cohort
--
--
--
drop table if exists cohort_signups;
create temporary table cohort_signups as
select
COUNT(sign_up_ts) as signup_count,
YEARWEEK(sign_up_ts) as cohort
from analytics
group by cohort;

-- drop table if exists cohort_cumulative_signups;
-- create temporary table cohort_cumulative_signups as
-- select cohort, sign_up_date, num_users, cumulative_users
-- FROM (
--     SELECT
--         cohort,
--         sign_up_date,
--         num_users,
--         @cs := IF(@prev_cohort = cohort, @cs+num_users, num_users) AS cumulative_users,
--         @prev_cohort := cohort AS prev_cohort
--     FROM cohort_signups_by_date, (SELECT @prev_cohort := 0, @cs := 0) AS vars
--     ORDER BY cohort,sign_up_date
-- ) AS tmp;


--
--
-- taskbar clicked
--
--
--

drop table if exists user_time_to_first_click;
create temporary table user_time_to_first_click as
select user,
first_taskbar_click_ts,
sign_up_ts,
DATEDIFF(first_taskbar_click_ts,DATE_SUB(sign_up_ts, INTERVAL DAYOFWEEK(sign_up_ts)-1 DAY)) as time,
YEARWEEK(sign_up_ts) as cohort
from analytics
where first_taskbar_click_ts > '0000-00-00 00:00:00' and YEARWEEK(sign_up_ts)
group by user
order by sign_up_ts desc;

drop table if exists cohort_time_to_first_click;
create table cohort_time_to_first_click as
select
count(user) as num_users,
time as time_to_first_click,
cohort
from user_time_to_first_click
group by cohort, time_to_first_click;

insert ignore into cohort_time_to_first_click(cohort,num_users,time_to_first_click)
    select
    distinct
    cohort,
    0,
    6
    from
    cohort_time_to_first_click;

drop table if exists cohort_cumulative_days_to_first_click;
create temporary table cohort_cumulative_days_to_first_click as
SELECT cohort, time_to_first_click, num_users, cumulative_users
FROM (
    SELECT
        cohort,
        time_to_first_click,
        num_users,
        @cs := IF(@prev_cohort = cohort, @cs+num_users, num_users) AS cumulative_users,
        @prev_cohort := cohort AS prev_cohort
    FROM cohort_time_to_first_click, (SELECT @prev_cohort := 0, @cs := 0) AS vars
    ORDER BY cohort,time_to_first_CLICK
) AS tmp;

drop table if exists cohort_percentage_clicked;
create table cohort_percentage_clicked
select
cohort_cumulative_days_to_first_click.cohort as cohort,
time_to_first_click as time_to_first_click,
MAX(cumulative_users/signup_count*100) as percent
from cohort_cumulative_days_to_first_click left join cohort_signups on cohort_cumulative_days_to_first_click.cohort = cohort_signups.cohort
group by cohort, time_to_first_click;

-- --
--
--
-- shared invite
--
--
--
drop table if exists user_time_to_first_share;
create temporary table user_time_to_first_share as
select user,
DATEDIFF(first_share_invite_sent_ts,DATE_SUB(sign_up_ts, INTERVAL DAYOFWEEK(sign_up_ts)-1 DAY)) as time,
YEARWEEK(sign_up_ts) as cohort
from analytics
where first_share_invite_sent_ts > '0000-00-00 00:00:00'
group by user
order by sign_up_ts desc;

drop table if exists user_time_to_invite_sent;
create temporary table user_time_to_invite_sent as
select user,
DATEDIFF(first_aerofs_invite_sent_ts,DATE_SUB(sign_up_ts, INTERVAL DAYOFWEEK(sign_up_ts)-1 DAY)) as time,
YEARWEEK(sign_up_ts) as cohort
from analytics
where first_aerofs_invite_sent_ts > '0000-00-00 00:00:00'
group by user
order by sign_up_ts desc;

drop table if exists user_time_to_first_share_or_invite;
create temporary table user_time_to_first_share_or_invite as
select user,
cohort,
MIN(time) as time
FROM (
SELECT user, cohort, time from user_time_to_first_share
UNION
SELECT user, cohort, time from user_time_to_invite_sent) as t
group by user;

drop table if exists cohort_time_to_first_share;
create  table cohort_time_to_first_share as
select
count(user) as num_users,
time as time_to_first_share,
cohort
from user_time_to_first_share_or_invite
group by cohort, time_to_first_share;

insert ignore into cohort_time_to_first_share(cohort,num_users,time_to_first_share)
    select
    distinct
    cohort,
    0,
    6
    from
    cohort_time_to_first_share;

drop table if exists cohort_cumulative_days_to_first_share;
create temporary table cohort_cumulative_days_to_first_share as
SELECT cohort, time_to_first_share, num_users, cumulative_users
FROM (
    SELECT
        cohort,
        time_to_first_share,
        num_users,
        @cs := IF(@prev_cohort = cohort, @cs+num_users, num_users) AS cumulative_users,
        @prev_cohort := cohort AS prev_cohort
    FROM cohort_time_to_first_share, (SELECT @prev_cohort := 0, @cs := 0) AS vars
    ORDER BY cohort,time_to_first_share
) AS tmp;

drop table if exists cohort_percentage_shared;
create table cohort_percentage_shared
select
cohort_cumulative_days_to_first_share.cohort as cohort,
time_to_first_share as time_to_first_share,
MAX(cumulative_users/signup_count*100) as percent
from cohort_cumulative_days_to_first_share left join cohort_signups on cohort_cumulative_days_to_first_share.cohort = cohort_signups.cohort
group by cohort, time_to_first_share;



--
--
--
-- first saved
--
--
--
drop table if exists user_time_to_first_save;
create temporary table user_time_to_first_save as
select user,
DATEDIFF(first_file_saved_ts,DATE_SUB(sign_up_ts, INTERVAL DAYOFWEEK(sign_up_ts)-1 DAY)) as time,
YEARWEEK(sign_up_ts) as cohort
from analytics
where first_file_saved_ts > '0000-00-00 00:00:00'
group by user
order by sign_up_ts desc;


drop table if exists cohort_time_to_first_save;
create table cohort_time_to_first_save as
select
count(user) as num_users,
time as time_to_first_save,
cohort
from user_time_to_first_save
group by cohort, time_to_first_save;

insert ignore into cohort_time_to_first_save(cohort,num_users,time_to_first_save)
    select
    distinct
    cohort,
    0,
    6
    from
    cohort_time_to_first_save;

drop table if exists cohort_cumulative_days_to_first_save;
create temporary table cohort_cumulative_days_to_first_save as
SELECT cohort, time_to_first_save, num_users, cumulative_users
FROM (
    SELECT
        cohort,
        time_to_first_save,
        num_users,
        @cs := IF(@prev_cohort = cohort, @cs+num_users, num_users) AS cumulative_users,
        @prev_cohort := cohort AS prev_cohort
    FROM cohort_time_to_first_save, (SELECT @prev_cohort := 0, @cs := 0) AS vars
    ORDER BY cohort,time_to_first_save
) AS tmp;

drop table if exists cohort_percentage_saved;
create table cohort_percentage_saved
select
cohort_cumulative_days_to_first_save.cohort as cohort,
time_to_first_save as time_to_first_save,
MAX(cumulative_users/signup_count*100) as percent
from cohort_cumulative_days_to_first_save left join cohort_signups on cohort_cumulative_days_to_first_save.cohort = cohort_signups.cohort
group by cohort, time_to_first_save;



--
--
--
-- first saved last 7 days
--
--
--


drop table if exists users_saved_last_seven_days;
create temporary table users_saved_last_seven_days as
select distinct user,
DATE(DATE_SUB(sign_up_ts, INTERVAL DAYOFWEEK(sign_up_ts)-1 DAY)) as cohort_date,
YEARWEEK(sign_up_ts) as cohort
from analytics_file_saved
where file_saved_ts >= DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY) and file_saved_ts <= CURRENT_DATE()
group by user
order by sign_up_ts desc;


drop table if exists cohort_saved_last_seven_days;
create table cohort_saved_last_seven_days as
select
count(user) as num_users,
cohort_date,
cohort
from users_saved_last_seven_days
group by cohort;

drop table if exists cohort_percentage_saved_last_seven_days;
create temporary table cohort_percentage_saved_last_seven_days
select
cohort_saved_last_seven_days.cohort_date as cohort_date,
cohort_saved_last_seven_days.cohort as cohort,
num_users/signup_count*100 as percent
from cohort_saved_last_seven_days left join cohort_signups on cohort_saved_last_seven_days.cohort = cohort_signups.cohort;

-- drop table if exists cohort_percentage_saved_last_seven_days_trailing;
-- create table cohort_percentage_saved_last_seven_days_trailing(
-- `day` int not null,
-- `cohort` int(6) not null,
-- `percent` double not null,
-- PRIMARY KEY (`day`,`cohort`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into cohort_percentage_saved_last_seven_days_trailing(day, cohort, percent)
    select
    DATEDIFF(CURRENT_DATE(),(DATE_ADD(cohort_date, INTERVAL 7 day))) as day,
    cohort,
    percent
    from
    cohort_percentage_saved_last_seven_days
    where DATEDIFF(CURRENT_DATE(),(DATE_ADD(cohort_date, INTERVAL 7 day))) >= 0;

--
--
--






-- create temporary table user_files_saved_last_seven_days as
-- select user,
-- CURRENT_DATE(),
-- file_saved_ts,
-- DATEDIFF(file_saved_ts,DATE_SUB(sign_up_ts, INTERVAL DAYOFWEEK(sign_up_ts)-1 DAY)) as time,
-- YEARWEEK(sign_up_ts) as cohort
-- from analytics_file_saved
-- where file_saved_ts >= SUBDATE(CURRENT_DATE(), interval 7 day)
-- order by cohort desc;


-- create temporary table cohort_time_to_first_save as
-- select
-- count(user) as num_users,
-- time as time_to_first_save,
-- cohort
-- from user_time_to_first_save
-- group by cohort, time_to_first_save;

-- create temporary table cohort_cumulative_days_to_first_save as
-- SELECT cohort, time_to_first_save, num_users, cumulative_users
-- FROM (
--     SELECT
--         cohort,
--         time_to_first_save,
--         num_users,
--         @cs := IF(@prev_cohort = cohort, @cs+num_users, num_users) AS cumulative_users,
--         @prev_cohort := cohort AS prev_cohort
--     FROM cohort_time_to_first_save, (SELECT @prev_cohort := 0, @cs := 0) AS vars
--     ORDER BY cohort,time_to_first_save
-- ) AS tmp;

-- create table cohort_percentage_saved
-- select
-- cohort_cumulative_days_to_first_save.cohort as cohort,
-- time_to_first_save as time_to_first_save,
-- cumulative_users/signup_count*100 as percent
