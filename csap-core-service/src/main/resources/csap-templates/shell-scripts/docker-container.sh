# dockerContainer, docker command templates
# Notes:
# 1. Extend time for command to complete if Host is busy or a long running command
# 2. Select additional hosts as needed


containerName='_serviceName_' ;


exit_if_not_installed docker
exit_if_not_installed wget


print_with_head "Running containers"
docker ps


print_with_head "Files in container:"
filesInContainer=`docker exec $containerName ls -l`
echo "$filesInContainer"

exit ;

print_with_head "Cleaning up unused containers, all volumes not used by a container, all dangling images"
docker system prune --force

print_with_head "Container limits"
docker exec $containerName ulimit -a

print_with_head "Container details"
docker inspect $containerName

print_with_head stopping docker container  $containerName
docker stop $containerName


print_with_head stopping docker container  $containerName
docker start $containerName


print_with_head Deleting container
docker rm -f $containerName

# 
print_with_head "starting  nginx docker with containerName webServer99 on port 80 as a daemon"
docker run -d -p 80:80 --name webServer99 nginx


print_with_head stopping all containers
docker stop $(docker ps -a -q) ;

print_with_head removing all containers
docker rm $(docker ps -a -q) ;


sleep 1;

testUrl="http://localhost" ;
output=$(wget -qO- $testUrl 2>&1)

print_with_head "Hitting $testUrl using wget, stripping off html tags"
echo $output | sed -e 's/<[^>]*>//g'



