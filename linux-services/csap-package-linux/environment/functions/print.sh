#!/bin/bash


LINE_WIDTH=${LINEWIDTH:-120}
LINE="$(printf -- '_%.0s' $(seq 1 $LINE_WIDTH))\n" ;
#
#   prints
#
function print_with_big_head() { 
	print_line "\n\n\n\n" ;
	print_with_head $*
}

function print_with_head() { 
	echo -e "\n$LINE \n$*\n$LINE"; 
}


function print_section() { 
	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
}

function print_with_head2() { 
	>&2 echo -e "\n$LINE \n$* \n$LINE"; 
}

function print_command() {

	local description="$1" ;
	shift 1 ;
	local commandOutput="$*" ;
	
	echo -e "\n\n$description:\n$LINE \n$commandOutput\n$LINE\n"; 
}

function print_error() { 
	>&2 echo -e "\n$LINE \n ERROR:    $* \n$LINE"; 
}

function delay_with_message() {
	
	local seconds=${1:-10} ;
	local message=${2:-continuing in};
	local dots;
	local iteration;
	
	print_line "\n\n $message \n"
	for (( iteration=$seconds; iteration > 0; iteration--)) ; do 
	
		dots=$(printf "%0.s-" $( seq 1 1 $iteration ));
		print_line "$(printf "%3s" $iteration) seconds  $dots"  ; 
		sleep 2; 
	done
	
}


function print_with_date() { 
		print_if_debug "System millis: $(date '+%N ms')"
		printf "\n\n %-20s %-30s %s\n\n" "$(date '+%x %H:%M:%S')"  "$(whoami)@$(hostname --long)" "$*"; 

	#echo -e "$LINE \n $(date '+%x %H:%M:%S') host: '$HOSTNAME' user: '$USER' \n $* \n$LINE"; 
}

function print_line() { 
	echo -e "   $*" ;
}


function print_separator() { 
	# echo -e "\n\n---------------   $*  ------------------" ;
	#printf "\n\n---------------   %-40s  ------------------\n" $*;
	
	local theMessage="   $*   " ;
	local dashesWidth=$(($LINE_WIDTH - ${#theMessage} )) ;
	dashesWidth=$(($dashesWidth/ 2)) ;
	if (( $dashesWidth < 5 )) ; then
		dashesWidth=5;
	fi ;
	
	#echo "theMessage width: ${#theMessage} dashesWidth: $dashesWidth" ;
	
	#local lineCharacters="$(printf '%0.1s' -{1..100})" ;
	local lineCharacters="$(printf -- '-%.0s' $(seq 1 $dashesWidth))" ;
	
	printf '\n\n%*.*s %s %*.*s\n' 0 "$dashesWidth" "$lineCharacters" "$theMessage"  0 "$dashesWidth" "$lineCharacters"
}

function print_separator2() { 
	>&2 echo -e "\n\n---------------   $*  ------------------" ;
}

function print_line2 { 
	>&2 echo -e "   $*" ;
}

# function test() { >&2 echo hi ; } 
function print_columns() { 
	printf "%15s: %-20s %15s: %-20s %15s: %-20s \n" "$@" ; 
}

function print_two_columns() { 
	printf "%25s: %-20s\n" "$@"; 
}

function print_two_columns2() { 
	>&2 printf "%25s: %-20s\n" "$@"; 
}

function print_info() { 
	local leadWidth=${3:-30s};
	printf "%-$leadWidth: %s\n" "$1" "$2"; 
}

function print_if_debug() {
	
	if [ "$debug"  != "" ] ; then
		printf "%-22s %s\n\n" "DEBUG: $(date '+%Nms')" "$*"; 
		#echo `date "+%Nms"`;echo -e "$*" ; echo
	fi ;
}

function print_debug_command() {
	
	if [ "$debug"  != "" ] ; then
	
		local description="$1" ;
		shift 1 ;
		local commandOutput="$*" ;
		
		echo -e "\n\nDEBUG: $description:\n$LINE \n$commandOutput\n$LINE\n"; 
	fi 

}

function print_with_prompt() {
	
	print_section $*
	#print_separator $*
	
	if $isPrompt ; then
		print_line "enter to continue or ctrl-c to exit"
		read progress
	fi ;
}

function prompt_to_continue() {
	
	print_with_head $*
	#print_separator $*
	
	print_separator "enter y to continue, or anything else to abort"
	read -n 1 -r userResponse
	
	if [[ "$userResponse" != "y" ]] ; then
		print_line "Exiting '$progress'" ;
		exit 99 ;
	fi ;
}


function print_if_verbose() {
	
	local verbose="$1" ; 
	local heading="$2" ;
	local message="$3" ;
	
	if $verbose ; then
		print_two_columns "$heading" "$message"
	fi ;

}
