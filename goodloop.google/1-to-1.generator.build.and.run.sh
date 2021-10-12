#!/bin/bash

# Overall Goal : Compile and run the 1-to-1 generator
# Intermediate Goal : While performing the compilation:
# -- check for errors and send email alerts if any are encountered
# Intermediate Goal : While running the 1-to-1 generator:
# -- if the process has taken more than 300 seconds to run, then assume that there is a prompt on the screen which requires human interaction (probably an auth) before the routine can be run automatically from then on.

ALERT_LIST=(sysadmin@good-loop.com wing@good-loop.com roscoe@good-loop.com)

PROJECT_ROOT_ON_SERVER='/home/winterwell/open-code/goodloop.google'

# defining email alert function
function send_alert {
    for email_address in ${ALERT_LIST[@]}; do
        message=$1
        time=$(date)
        body="Hi,\nThe 1-to-1 generator on the office's adrecorder encountered a problem at $time:\n\n$message\n"
        title="[$HOSTNAME] $message"
        echo -e $body | mutt -s "$title" $email_address
    done
}

# custom timeout (because 'timeout' does not have access to subshell runtime functions)
function custom_timeout() {

    time=$1

    # start the command in a subshell to avoid problem with pipes
    # (spawn accepts one command)
    command="/bin/sh -c \"$2\""

    expect -c "set echo \"-noecho\"; set timeout $time; spawn -noecho $command; expect timeout { exit 1 } eof { exit 0 }"    

    if [ $? = 1 ] ; then
        echo "Timeout after ${time} seconds. sending alert email and killing this process"
        send_alert "After asking the 1-to-1 generator to generate a calendar of 1-to-1's, the JVM took more than 5 minutes to finish cleanly -- so at this point, a human should intervene and debug the 1-to-1 generation java command and update the open-code repo accordingly. You can/should SSH into the office's adrecorder and debug the process directly."
    fi

}

# Cleaning the build env:
function clean_build_env {
    if [ -f /home/winterwell/bobwarehouse/bobhistory.csv ]; then
        rm /home/winterwell/bobwarehouse/bobhistory.csv
    fi
    if [ -d $PROJECT_ROOT_ON_SERVER/dependencies ]; then
        rm -rf $PROJECT_ROOT_ON_SERVER/dependencies
        mkdir $PROJECT_ROOT_ON_SERVER/dependencies
    fi
    if [ -d $PROJECT_ROOT_ON_SERVER/build-lib ]; then
        rm -rf $PROJECT_ROOT_ON_SERVER/build-lib
        mkdir $PROJECT_ROOT_ON_SERVER/build-lib
    fi
    if [ -f $PROJECT_ROOT_ON_SERVER/bob.log ]; then
        rm $PROJECT_ROOT_ON_SERVER/bob.log
    fi
}

# Building the JARs and getting dependencies
function build_backend {
    cd $PROJECT_ROOT_ON_SERVER && bob
}

# check bob.log output for severe errors
function check_boblog {
    if [[ $(grep -i 'Compile task failed' $PROJECT_ROOT_ON_SERVER/bob.log) = '' ]]; then
        printf "\nNo failures recorded in bob.log on $HOSTNAME in first bob.log sweep.\n"
    else
        printf "\nFailure or failures detected in latest bob.log. Breaking Operation\n"
        printf "\nSending email and terminating routine...\n"
        send_alert "After asking Bob to get dependency JARs and compile all necessary JARs for the 1-to-1 generator routine, a sweep of the bob.log file showed that one or more compilation tasks failed.  SSH into the office's adrecorder and please debug the compilation task in the directory /home/winterwell/open-code/goodloop.google"
        exit 0
    fi
    if [[ $(grep -i 'ERROR EXIT' $PROJECT_ROOT_ON_SERVER/bob.log) = '' ]]; then
        printf "\nBob reported a clean exit from it's process.  Continuing to next task.\n"
    else
        printf "\nFailure or failures detected in latest bob.log. Breaking Operation\n"
        printf "\nSending email and terminating routine...\n"
        send_alert "After asking Bob to get dependency JARs and compile all necessary JARs for the 1-to-1 generator routine, a sweep of the bob.log file showed that one or more compilation tasks failed.  SSH into the office's adrecorder and please debug the compilation task in the directory /home/winterwell/open-code/goodloop.google"
        exit 0
    fi
}

# update the locally held 'logins' repository
function update_logins {
    cd /home/winterwell/logins
    git checkout master
    git pull
    git reset --hard FETCH_HEAD
}




# Run the functions in a logical series
clean_build_env
build_backend
check_boblog
update_logins
timeout 300 /home/winterwell/open-code/goodloop.google/generate.chat.schedule.sh