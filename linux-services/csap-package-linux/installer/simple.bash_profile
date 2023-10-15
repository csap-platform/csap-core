# @(#)local.profile 1.8 99/03/26 SMI

source ~/.bashrc


ulimit -n CSAP_FD_PARAM
ulimit -u CSAP_THREADS_PARAM

#
#  STRONGLY favour using system defaults
#


#.................................................................
#set -o vi

#export EDITOR=vi

# set default files to 755 -rwxr-xr-x
#umask 022


# Prompt
#export PS1="[\u@\h] \W [\!] "

### Variables that don't relate to bash

# Set variables for a warm fuzzy environment
#export EDITOR=/usr/local/bin/emacs
#export PAGER=less

# Execute the subshell script


#PS1="\h:\w> "
