#!/bin/bash
set -x -e


# The amount of time a cohort takes to "close" from the first da it opens
COHORT_SIZE=7

# The age of the cohort, in days
#
# Because cohorts require 7 days to fill up and close, data collected
# should only be looked at after the cohort closes, so if we want 14 days of data
# the cohort needs to be at least 21 days old from the first day of the cohort
COHORT_AGE=$(echo "3*$COHORT_SIZE" | bc)

# how many days of data do we want to look at
COHORT_ACTIVITY=$(echo "2*$COHORT_SIZE" | bc)

#######################################
# DO NOT MODIFY BELOW THIS LINE       #
# UNLESS YOU KNOW WHAT YOU ARE DOING! #
#######################################

DATABASE="analytics"
TABLE="trailing_cohort_analytics_for_email"
INVITES_SENT="invites_sent"
SIGNUPS="signups"
USERS_SHARED="users_shared_afd"
RETENTION="retention_afd"


#######################################
# HELPER FUNCTIONS                    #
#######################################

#select function which filters by cohort
function selectSQL() {
    DATABASE="analytics"
    TABLE="trailing_cohort_analytics_for_email"

    mysql $DATABASE -sN -e "SELECT $1 FROM $TABLE WHERE cohort=$2"
}

#select function which returns a table to be plotted
function selectSQLDataForPlot() {
    SQL_FILE=`mktemp -t sqldata.XXXX`
    DATABASE="analytics"
    TABLE="trailing_cohort_analytics_for_email"

    mysql $DATABASE -NBe "SELECT cohort,$1 from $TABLE ORDER by cohort" > $SQL_FILE
    echo $SQL_FILE
}

#calculate a percentage
function percent() {
    echo "scale = 2; $1/$2*100" | bc
}

#calculate the percentage difference between two numbers
function percentDiff() {
    TEMP=$(percent $1 $2)
    TEMP=$(echo "scale = 2; $TEMP-100" | bc)
    if [[ $TEMP =~ ^[\.0-9]+$ ]]; then
        TEMP="+${TEMP}"
    fi
    echo "${TEMP}"
}

function gnuPlot() {
    DATA_FILE="$1"
    TITLE="$2"
    XLABEL="$3"
    YLABEL="$4"

    PLOT_FILE=`mktemp -t --suffix=.png gnuplot.XXXX`

    echo "
    set terminal png nocrop font small size 640,480
    set output '${PLOT_FILE}'
    set style data linespoints
    set title '${TITLE}'
    set xlabel '${XLABEL}'
    set ylabel '${YLABEL}'
    plot '${DATA_FILE}' using 1:2
    " | gnuplot

    echo ${PLOT_FILE}
}

TMP_EMAIL=`mktemp -t email.XXXX`
TMP_TASQL=`mktemp -t trailing_analytics.XXXX`

###########################
# RUN THE ANALYTICS QUERY #
###########################
mysql analytics -e < trailing_analytics.sql


THIS_COHORT=$(mysql -sN -e "SELECT YEARWEEK(DATE_SUB(CURRENT_DATE(), INTERVAL ${COHORT_AGE} DAY))")
LAST_COHORT=$(mysql -sN -e "SELECT YEARWEEK(DATE_SUB(CURRENT_DATE(), INTERVAL ${COHORT_AGE}+${COHORT_SIZE} DAY))")

THIS_COHORT_INVITES_SENT=$(selectSQL ${INVITES_SENT} ${THIS_COHORT})
LAST_COHORT_INVITES_SENT=$(selectSQL ${INVITES_SENT} ${LAST_COHORT})
INVITES_SENT_DIFF=$(percentDiff ${THIS_COHORT_INVITES_SENT} ${LAST_COHORT_INVITES_SENT})

THIS_COHORT_SIGNUPS=$(selectSQL ${SIGNUPS} ${THIS_COHORT})
LAST_COHORT_SIGNUPS=$(selectSQL ${SIGNUPS} ${LAST_COHORT})
SIGNUPS_DIFF=$(percentDiff $THIS_COHORT_SIGNUPS $LAST_COHORT_SIGNUPS)
SIGNUPS_AS_PERCENT_OF_INVITES_SENT=$(percent ${THIS_COHORT_SIGNUPS} ${THIS_COHORT_INVITES_SENT})

THIS_COHORT_USERS_SHARED_AFD=$(selectSQL ${USERS_SHARED} ${THIS_COHORT})
LAST_COHORT_USERS_SHARED_AFD=$(selectSQL ${USERS_SHARED} ${LAST_COHORT})
USERS_SHARED_AFD_DIFF=$(percentDiff ${THIS_COHORT_USERS_SHARED_AFD} ${LAST_COHORT_USERS_SHARED_AFD})

THIS_COHORT_RETENTION_AFD=$(selectSQL ${RETENTION} ${THIS_COHORT})
LAST_COHORT_RETENTION_AFD=$(selectSQL ${RETENTION} ${LAST_COHORT})
RETENTION_AFD_DIFF=$(percentDiff ${THIS_COHORT_RETENTION_AFD} ${LAST_COHORT_RETENTION_AFD})


SHARE_DATA_FILE=$(selectSQLDataForPlot ${USERS_SHARED})
RETENTION_DATA_FILE=$(selectSQLDataForPlot ${RETENTION})

SHARE_DATA_IMG=$(gnuPlot $SHARE_DATA_FILE "Share data by cohort after ${COHORT_ACTIVITY} days" "cohort" "%")
RETENTION_DATA_IMG=$(gnuPlot $RETENTION_DATA_FILE "Retention data by cohort after ${COHORT_ACTIVITY} days" "cohort" "%")

##############
# SEND EMAIL #
##############
eval "echo \"$(<email.template)\"" | mutt -s "Weekly cohort analysis" -a $SHARE_DATA_IMG $RETENTION_DATA_IMG -- yuri@aerofs.com


############
# CLEAN UP #
############
rm $TMP_EMAIL
rm $TMP_TASQL
rm $SHARE_DATA_FILE
rm $RETENTION_DATA_FILE
rm $SHARE_DATA_IMG
rm $RETENTION_DATA_IMG
