--
-- This file is used by email.sh
-- to generate each week's analytics update
-- when email.sh is run
--

--
-- By default we use a cohort age of 21 (e.g cohorts that started on the week
-- 21 days ago
--

SET @COHORT_AGE = 21;

--
-- We measure activity for the 14 days from the day the cohort ended
--
SET @COHORT_ACTIVITY = 14;

--
-- Calculate the cohort we're looking for
--
SET @COHORT = YEARWEEK(DATE_SUB(CURRENT_DATE(), INTERVAL @COHORT_AGE DAY));

--
-- Count the number of invitations that were delivered that week
--
SELECT COUNT(*) INTO @invites_sent
FROM aerofs_sv.email_event
WHERE ee_category = 'FOLDERLESS_INVITE'
AND YEARWEEK(FROM_UNIXTIME(ee_ts)) = @COHORT
AND ee_event='delivered';

--
-- Count the number of signups that happened that week
--

SELECT COUNT(*) INTO @signups
FROM aerofs_sv.sv_event
WHERE ev_type='1000'
AND YEARWEEK(FROM_UNIXTIME(hdr_ts/1000)) = @COHORT;

--
-- Count the percentage of users in that cohort
-- who have shared a folder with someone else in the past COHORT_ACTIVITY days
--

SELECT percent INTO @users_shared
FROM cohort_percentage_shared
WHERE time_to_first_share <= @COHORT_ACTIVITY
AND cohort = @COHORT
ORDER BY time_to_first_share DESC
LIMIT 1;

--
-- Calculate the percentage of users who are still active
-- (e.g. have saved a file) after COHORT_ACTIVITY days
--

SELECT percent INTO @retention
FROM cohort_percentage_saved_last_seven_days_trailing
WHERE cohort = @COHORT
AND day = @COHORT_ACTIVITY;

--
-- Insert all of the above values into our table.
-- Note: Although we calculate cohorts on a 21 day trailing schedule,
--       we insert support data immediately as it is collected (e.g. 21 days ahead of when
--       we will calculate the cohort data). This means that it's quite likely that
--       by the time this script runs, the cohort key will already exist, hence the
--       ON DUPLICATE KEY UPDATE line
--

INSERT INTO trailing_cohort_analytics_for_email
(cohort, invites_sent, signups, users_shared_afd, retention_afd)
VALUES (@COHORT,@invites_sent,@signups,ROUND(@users_shared),ROUND(@retention))
ON DUPLICATE KEY UPDATE
invites_sent=@invites_sent,
signups=@signups,
users_shared_afd=ROUND(@users_shared),
retention_afd=ROUND(@retention);
