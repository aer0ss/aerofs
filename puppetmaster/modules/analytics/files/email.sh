#!/bin/bash
set -e

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
    # use printf for precise rounding (bc doesn't round properly)
    echo "scale = 1; $1*100/$2" | bc | xargs printf "%1.0f"
}

#calculate the percentage difference between two numbers
#return an HTML string with appropriate coloring and directional arrow depending on the sign
# of the result.
function percentDiff() {
    TEMP=$(percent $1 $2)
    # use printf for precise rounding (bc doesn't round properly)
    TEMP=$(echo "scale = 1; $TEMP-100" | bc | xargs printf "%1.0f")
    if [[ $TEMP =~ ^[\.0-9]+$ ]]; then
        # Unicode 9650 is a black upward triangle
        TEMP="<font color=\"green\">&#9650; ${TEMP}%</font>"
    else
        TEMP=$(echo $TEMP | sed -e 's/^-//')
        # Unicode 9660 is a black upward triangle
        TEMP="<font color=\"red\">&#9660; ${TEMP}%</font>"

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
    set terminal png nocrop font "Arial" small size 640,200
    set output '${PLOT_FILE}'
    set style data linespoints
    set title '${TITLE}'
    set yrange [0:]
    set xlabel '${XLABEL}'
    set ylabel '${YLABEL}'
    set nokey
    plot '${DATA_FILE}' using 1:2
    " | gnuplot

    echo ${PLOT_FILE}
}

TMP_EMAIL=`mktemp -t email.XXXX`
TMP_TASQL=`mktemp -t trailing_analytics.XXXX`

###########################
# RUN THE ANALYTICS QUERY #
###########################
#mysql $DATABASE < trailing_analytics.sql

THIS_COHORT=$(mysql -sN -e "SELECT YEARWEEK(DATE_SUB(CURRENT_DATE(), INTERVAL ${COHORT_AGE} DAY))")
LAST_COHORT=$(mysql -sN -e "SELECT YEARWEEK(DATE_SUB(CURRENT_DATE(), INTERVAL ${COHORT_AGE}+${COHORT_SIZE} DAY))")

# retrieve the week of year from the Cohort IDs. A Cohort ID is in the format of "201240" where 40 is the week number.
THIS_COHORT_WEEK_OF_YEAR=$(echo ${THIS_COHORT} | cut -c5-6 )

THIS_COHORT_INVITES_SENT=$(selectSQL ${INVITES_SENT} ${THIS_COHORT})
LAST_COHORT_INVITES_SENT=$(selectSQL ${INVITES_SENT} ${LAST_COHORT})
INVITES_SENT_DIFF=$(percentDiff ${THIS_COHORT_INVITES_SENT} ${LAST_COHORT_INVITES_SENT})

THIS_COHORT_SIGNUPS=$(selectSQL ${SIGNUPS} ${THIS_COHORT})
LAST_COHORT_SIGNUPS=$(selectSQL ${SIGNUPS} ${LAST_COHORT})
SIGNUPS_DIFF=$(percentDiff $THIS_COHORT_SIGNUPS $LAST_COHORT_SIGNUPS)
SIGNUPS_AS_PERCENT_OF_INVITES_SENT=$(percent ${THIS_COHORT_SIGNUPS} ${THIS_COHORT_INVITES_SENT})

LAST_SIGNUPS_AS_PERCENT_OF_INVITES_SENT=$(percent ${LAST_COHORT_SIGNUPS} ${LAST_COHORT_INVITES_SENT})
SIGNUPS_AS_PERCENT_OF_INVITES_SENT_DIFF=$(percentDiff ${SIGNUPS_AS_PERCENT_OF_INVITES_SENT} ${LAST_SIGNUPS_AS_PERCENT_OF_INVITES_SENT})

THIS_COHORT_USERS_SHARED_AFD=$(selectSQL ${USERS_SHARED} ${THIS_COHORT})
LAST_COHORT_USERS_SHARED_AFD=$(selectSQL ${USERS_SHARED} ${LAST_COHORT})
USERS_SHARED_AFD_DIFF=$(percentDiff ${THIS_COHORT_USERS_SHARED_AFD} ${LAST_COHORT_USERS_SHARED_AFD})

THIS_COHORT_RETENTION_AFD=$(selectSQL ${RETENTION} ${THIS_COHORT})
LAST_COHORT_RETENTION_AFD=$(selectSQL ${RETENTION} ${LAST_COHORT})
RETENTION_AFD_DIFF=$(percentDiff ${THIS_COHORT_RETENTION_AFD} ${LAST_COHORT_RETENTION_AFD})

SHARE_DATA_PATH=$(selectSQLDataForPlot ${USERS_SHARED})
RETENTION_DATA_PATH=$(selectSQLDataForPlot ${RETENTION})

SHARE_GRAPH_PATH=$(gnuPlot $SHARE_DATA_PATH "History Trends in Approximate Virality" "Cohort ID" "%")
RETENTION_GRAPH_PATH=$(gnuPlot $RETENTION_DATA_PATH "History Trends in Approximate Retention" "Cohort ID" "%")

VIRALITY_GRAPH_BASE64=$(base64 "${SHARE_GRAPH_PATH}")
RETENTION_GRAPH_BASE64=$(base64 "${RETENTION_GRAPH_PATH}")

HTML=$(eval "echo \"$(<email.template.html)\"")

##############
# SEND EMAIL #
##############

# "multipart/related" is required to prevent GMail from showing embedded images as attachments.
# Also required is the "filename" field in the Content-Type of the images (in the file
# email.template.multipart). See http://stackoverflow.com/questions/4040358/how-to-stop-embedded-images-in-email-being-displayed-as-attachments-by-gmail for detail.
eval "echo \"$(<email.template.multipart)\"" | mail \
    -a "From: AeroFS SV <root@sv.aerofs.com>" \
    -a "Content-Type: multipart/related; boundary=\"multipart_boundary\"" \
    -s "Weekly Key Metrics Report, week #$THIS_COHORT_WEEK_OF_YEAR" \
    team@aerofs.com

############
# CLEAN UP #
############
rm $TMP_EMAIL
rm $TMP_TASQL
rm $SHARE_DATA_PATH
rm $RETENTION_DATA_PATH
rm $SHARE_GRAPH_PATH
rm $RETENTION_GRAPH_PATH
