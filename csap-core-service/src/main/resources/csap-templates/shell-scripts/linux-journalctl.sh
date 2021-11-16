# journalctl , linux logs entries



# clear the journal
# \rm --recursive --force /run/log/journal/* /var/log/journal/*

service='_serviceName_' ;

filter="_searchText_" ; 

if [[ $(whoami) != "root" ]] ; then
	print_with_head "change user to root journalctl access"
	exit;
fi ;


print_with_head "Using linux journalctl for service '$service'"


if [[ "$filter" != "no-search-text-param" ]] ; then
	
	journalctl --no-pager \
		--since "1 days ago"  \
		--unit $service \
		| grep \
		--before-context=1 --after-context=3 \
		--fixed-strings "$filter"
	
else
	
	journalctl --no-pager \
		--since "1 days ago"  \
		--unit $service
	
fi;

exit

# Run this for options
man journalctl | col -bx

# --reverse , --lines=100 , 