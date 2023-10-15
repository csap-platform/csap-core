#!/bin/bash

# Source global definitions
if [ -f /etc/bashrc ]; then
        . /etc/bashrc
fi

# User specific aliases and functions
LINE="_______________________________________________________________________________________________\n"

function print_with_head() { 
	echo -e "$LINE \n  $* \n$LINE"; 
}

function print_with_date() { 
	echo -e "$LINE \n `date '+%x %H:%M:%S %Nms'` host: '$HOSTNAME' user: '$USER' \n $* \n$LINE"; 
}

function print_line() { 
	echo -e "   $*" ;
}
# User specific aliases and functions
function test_net() {
	host="${1:-redhat.com}" ;
	port="${2:-80}" ;
	maxTime="${3:-1}" ;
	
	numberOfWords=$(timeout $maxTime nc -w 1 $host $port 2>&1 | wc -w)

	result="Pass" ;
	if (( $numberOfWords > 0 )) ; then
		result="Fail" ;
	fi ;
	
	print_with_head "netcat test host: '$host', port: '$port', maxTime: '$maxTime' \t **result: '$result' ($numberOfWords)"
}



function net_help() {
	print_with_head "CSAP network test functions"	
	print_line "test_net <host> <port> <timeout>  \t : uses netcat and timeouts to return pass or fail"
	print_line "dig redhat.com  MX +noall +answer \t : returns mx record"
	print_line "\n\n"
	print_line "telnet, ssh, dig, nslookup, traceroute, ... are available"
	print_line "\n\n"
}

function prompt_command {
     #   How many characters of the $PWD should be kept
     local pwd_length=32
     if [ $(echo -n $PWD | wc -c | tr -d " ") -gt $pwd_length ]; then
        newPWD="$(echo -n $PWD | sed -e "s/.*\(.\{$pwd_length\}\)/\1/")"
        curIndex=`expr index "$newPWD" //`
        if [ $curIndex -gt 0 ]; then
                #echo index is $curIndex
                #newPWD="$NC.../$HILIT${newPWD:$curIndex}"
                newPWD=".../${newPWD:$curIndex}"
        fi
     else
        newPWD="$(echo -n $PWD)"
     fi

     #another choice - just use the last directories
     # PS1='${PWD#${PWD%/[!/]*/*}/} '
        PS1="`hostname`:$newPWD> "
}
export PROMPT_COMMAND=prompt_command

alias s="source ~/.bashrc"
#
export PATH=$PATH:/sbin
export PATH=`echo -n $PATH | awk -v RS=: '{ if (!arr[$0]++) {printf("%s%s",!ln++?"":":",$0)}}'`
