# kubenetes proxy, exposes kubernetes api server, including proxy support for services

#
# Notes
# - if run using root is checked - port 80 will be used
# - this runs in the foreground - and will block. Set timeout  for longer sessions
#


if [ "$USER" != "root" ]; then

	kubectl proxy --port 8014 --address 0.0.0.0 --accept-hosts .*
	
else

	kubectl proxy --port 80 --address 0.0.0.0 --accept-hosts .*

fi;