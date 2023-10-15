#!/bin/bash

# Wrapper function to allow sftp connections which choke on terminal output
function _echo ()
{

	if [ "$TERM" == "dumb" ]; then
		return ;
	else
		echo ___ $*
	fi ;

}


source /etc/bashrc
source "$HOME/.csapEnvironment"
source "$CSAP_FOLDER/bin/csap-environment.sh"

#
#  DISPLAY setting
#
function get_xserver ()
{
    case $TERM in
	xterm )
            XSERVER=$(who am i | awk '{print $NF}' | tr -d ')''(' ) 
            XSERVER=${XSERVER%%:*}
	    ;;
	aterm | rxvt)
 	# find some code that works here.....
	    ;;
    esac  
}

if [ -z ${DISPLAY:=""} ]; then
    get_xserver
    if [[ -z ${XSERVER}  || ${XSERVER} == $(hostname) || ${XSERVER} == "unix" ]]; then 
	DISPLAY=":0.0"		# Display on local host
    else		
	DISPLAY=${XSERVER}:0.0	# Display on remote host
    fi
fi

export DISPLAY

#
#  limits
#
ulimit -S -c 0		# Don't want any coredumps

# https://www.gnu.org/software/bash/manual/html_node/The-Set-Builtin.html#The-Set-Builtin
set -o notify    # Cause the status of terminated background jobs to be reported immediately, rather than before printing the next primary prompt.
set -o noclobber  # Prevent output redirection using ‘>’, ‘>&’, and ‘<>’ from overwriting existing files

#set -o ignoreeof  
export IGNOREEOF=1 # EOF ctrl-d causes logout only if done twice
set +o nounset   #ignore unset vars
# set -o nounset    # Treat unset variables and parameters other than the special parameters ‘@’ or ‘*’ as an error when performing parameter expansion
#set -o xtrace          # useful for debuging

# Enable options:
shopt -s cdspell
shopt -s cdable_vars
shopt -s checkhash
shopt -s checkwinsize
shopt -s mailwarn
shopt -s sourcepath
shopt -s no_empty_cmd_completion  # bash>=2.04 only
shopt -s cmdhist
shopt -s histappend histreedit histverify
shopt -s extglob	# necessary for programmable completion

# Disable options:
shopt -u mailwarn
unset MAILCHECK		# I don't want my shell to warn me of incoming mail


#
# SHELL customizations
#

red='\e[0;31m'
RED='\e[1;31m'
blue='\e[0;34m'
BLUE='\e[1;34m'
cyan='\e[0;36m'
CYAN='\e[1;36m'
DARKYELLOW='\e[1;33m'
GREEN='\e[0;32m'
LIGHTGREEN='\e[1;32m'
GREY='\e[1;30m'
BOLDBLACK='\e[1;29m'
NC='\e[0m'              # No Color




#
#  function helpers
#

# function to run upon exit of shell
#function _exit() {
#    echo -e "${RED}Logging out from csap host${NC}"
#}
#trap _exit EXIT


#
# prompt
#
export HOST=$(hostname --long)

if [[ "${DISPLAY#$HOST}" != ":0.0" &&  "${DISPLAY}" != ":0" ]]; then  
    HILIT=${GREEN}   # remote machine: prompt will be partly red
else
    HILIT=${GREEN}  # local machine: prompt will be partly cyan
fi


function prompt_command {
     #   How many characters of the $PWD should be kept
     local pwd_length=32
     if (( $(echo -n $PWD | wc -c | tr -d " ") > $pwd_length )) ; then 
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
	PS1="$(hostname --short):$newPWD> "
}

export PROMPT_COMMAND=prompt_command

#
#  alias - command shortcuts
#

alias st="cd $CSAP_FOLDER"
alias pl="cd $CSAP_FOLDER"
alias pr="cd $csapPlatformWorking"
alias wo="cd $csapPlatformWorking"
alias conf="cd $CSAP_FOLDER/definition"
alias def="cd $CSAP_FOLDER/definition"

alias term="export TERM=ansi; shopt -s checkwinsize"
alias de='docker exec -e COLUMNS="`tput cols`" -e LINES="`tput lines`" -it '
alias ke='kubectl exec -it '

alias psj="ps -ef | grep java | grep -v grep"

alias csl="tail -F  $csapPlatformWorking/$csapAgentId/logs/console.log | jq --raw-output '\"\n\n\", .friendlyDate, .message'"
alias csll="tail -F  $csapPlatformWorking/$csapAgentId/logs/console.log | jq"

alias csv="print_separator 'converting csap-agent logs json to jq'; \
	\rm -rf $csapPlatformWorking/$csapAgentId/logs/console-jq.log ; \
	cat $csapPlatformWorking/$csapAgentId/logs/console.log | jq --raw-output '\"\n\n\", .friendlyDate, .message' > $csapPlatformWorking/$csapAgentId/logs/console-jq.log; \
	ls -al $csapPlatformWorking/$csapAgentId/logs/console-jq.log ; \
	vi  $csapPlatformWorking/$csapAgentId/logs/console-jq.log" 
	
alias web="cd $csapPlatformWorking/httpd_8080;ls"

alias scp="scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "
alias ssh="ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "

alias rm='rm -i'
alias s='source ~/.bashrc'
alias cp='cp -i'
alias mv='mv -i'
# -> Prevents accidentally clobbering files.
alias mkdir='mkdir -p'

alias h='history'
alias j='jobs -l'
alias r='rlogin'
alias which='type -all'
alias ..='cd ..'
alias path='echo -e ${PATH//:/\\n}'

alias duu='du -hs * | sort -h'
alias dff='df -kTh'

# The 'ls' family (this assumes you use the GNU ls)
alias la='ls -Al'               # show hidden files
alias ls='ls -hF --color'	# add colors for filetype recognition
alias lx='ls -lXB'              # sort by extension
alias lk='ls -lSr'              # sort by size
alias lc='ls -lcr'		# sort by change time  
alias lu='ls -lur'		# sort by access time   
alias lr='ls -lR'               # recursive ls
alias lt='ls -ltr'              # sort by date
alias lm='ls -al |more'         # pipe through 'more'
alias tree='tree -Csu'		# nice alternative to 'ls'

# tailoring 'less'
alias more='less'
export PAGER=less

function help() {

	print_separator "CSAP help"
	
	print_two_columns "admin-restart.sh" "<hostname> will wget the definition from the specified host, then restart csap agent"

	print_line "\n"		
	print_two_columns "csap" "print help for cli"
	print_two_columns "agent status" "agent agent/runtime" 
	print_two_columns "clusters" "csap model/clusters"
	print_two_columns "hosts" 'csap model/hosts/base-os --jpath /hosts'
	print_two_columns "serviceids" 'csap model/services/name?reverse=true --parse'
	print_two_columns "info" "more samples are in csap shell templates"
	
	print_line "\n"
	print_two_columns "def" "alias for: cd to csap definition folder"
	print_two_columns "pl" "alias for: cd to csap platform folder"
	print_two_columns "wo" "alias for: cd to csap working folder"
	print_two_columns "csl" "alias for: tail agent logs"
	print_two_columns "csv" "alias for: vi agent logs"
	print_line "\n"
	print_two_columns "hint" "yum -y install epel-release; yum -y install htop"
	print_line "\n\n"
}

function xtitle () {
    case "$TERM" in
        *term | rxvt)
            echo -n -e "\033]0;$*\007" ;;
        *)  
	    ;;
    esac
}


function cleanTags() {

	# yum -y install git
	# git config credential.helper store

	local prefix=${1:-201904}
	local start=${2:-1} ;
	local end=${3:-31} ;
	
	print_line "start=10;end=30"
	print_line 'for i in $(seq $start $end); do git push origin :refs/tags/201905"$i"-SNAPSHOT ; done'
	for item in $(seq $start $end); do 
		suffix=$item ;
		if (( $suffix < 10 )) ; then 
			suffix="0$suffix" ;
		fi
		
		git push origin :refs/tags/$prefix"$suffix"-SNAPSHOT ; 
	done
}





if [ -e "$HOME/.csapEnvironmentOverRide" ];  then
	_echo ======= $HOME/.csapEnvironmentOverRide Detected, sourcing
	source "$HOME/.csapEnvironmentOverRide"
fi ;
# hook for duplicate path. Long paths can kill VM
PATH=$(echo -n $PATH | awk -v RS=: '{ if (!arr[$0]++) {printf("%s%s",!ln++?"":":",$0)}}')