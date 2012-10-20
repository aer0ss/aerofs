--
-- This file is used by email.sh
-- to generate each week's analytics update
-- when email.sh is run
--

SET @COHORT_AGE = 21;
SET @COHORT_ACTIVITY = 14;

SET @COHORT = YEARWEEK(DATE_SUB(CURRENT_DATE(), INTERVAL @COHORT_AGE DAY));

SELECT COUNT(*) INTO @invites_sent
FROM aerofs_sv.email_event
WHERE ee_category = 'FOLDERLESS_INVITE'
AND YEARWEEK(FROM_UNIXTIME(ee_ts)) = @COHORT
AND ee_event='delivered';

SELECT COUNT(*) INTO @signups
FROM aerofs_sv.sv_event
WHERE ev_type='1000'
AND YEARWEEK(FROM_UNIXTIME(hdr_ts/1000)) = @COHORT;

SELECT percent INTO @users_shared
FROM cohort_percentage_shared
WHERE time_to_first_share <= @COHORT_ACTIVITY
AND cohort = @COHORT
ORDER BY time_to_first_share DESC
LIMIT 1;

SELECT percent INTO @retention
FROM cohort_percentage_saved_last_seven_days_trailing
WHERE cohort = @COHORT
AND day = @COHORT_ACTIVITY;

INSERT INTO trailing_cohort_analytics_for_email
(cohort, invites_sent, signups, users_shared_afd, retention_afd)
VALUES (@COHORT,@invites_sent,@signups,ROUND(@users_shared),ROUND(@retention));
