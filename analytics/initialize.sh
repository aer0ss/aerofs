#!/bin/bash
set -e

mysql analytics -e"
drop table if exists cohort_percentage_saved_last_seven_days_trailing;
create table cohort_percentage_saved_last_seven_days_trailing(
day int(11) not null,
cohort int(6) not null,
percent double not null,
PRIMARY KEY(cohort, day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

drop table if exists analytics_for_email;
create table trailing_cohort_analytics_for_email(
cohort int(6) unique not null,
invites_sent int not null,
signups int not null,
users_shared_afd int not null,  -- users_shared after fourteen days
retention_afd int not null      -- retention after fourteen days
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
"
for i in {1..30}
do
mysql analytics -e"
drop table if exists cohort_signups;
create temporary table cohort_signups as 
select
COUNT(sign_up_ts) as signup_count,
YEARWEEK(sign_up_ts) as cohort
from analytics
group by cohort;

drop table if exists users_saved_last_seven_days;
create temporary table users_saved_last_seven_days as 
select distinct user,
DATE(DATE_SUB(sign_up_ts, INTERVAL DAYOFWEEK(sign_up_ts)-1 DAY)) as cohort_date,
YEARWEEK(sign_up_ts) as cohort
from analytics_file_saved 
where file_saved_ts >= DATE_SUB(CURRENT_DATE(), INTERVAL 7+$i DAY) and file_saved_ts <= DATE_SUB(CURRENT_DATE(), INTERVAL $i DAY)
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

insert into cohort_percentage_saved_last_seven_days_trailing(day, cohort, percent)
    select
    DATEDIFF(DATE_SUB(CURRENT_DATE(), INTERVAL $i DAY),(DATE_ADD(cohort_date, INTERVAL 7 day))) as day,
    cohort,
    percent
    from
    cohort_percentage_saved_last_seven_days
    where DATEDIFF(DATE_SUB(CURRENT_DATE(), INTERVAL $i DAY),(DATE_ADD(cohort_date, INTERVAL 7 day))) >= 0;
"
done
