# dockerImage, Used by CSAP docker integration to add/update/delete images


imageName='_serviceName_' ;


exit_if_not_installed docker
exit_if_not_installed wget



print_command "Images on Host" "$(docker images)"

print_command "docker inspect $imageName" "$(docker inspect $imageName)"



exit ;






#
#  samples
#

# remove all unused images
docker image prune --all --force

# import / export images
docker save --output $destination $imageName
docker load --input $sourceTar
      

print_with_head "Cleaning up unused containers, all volumes not used by a container, all dangling images"
docker system prune --force 

##
##  image commands
##
print_with_head pulling latest image
docker pull $imageName
docker rmi --force $imageName


docker pull docker.io/hello-world:latest
docker pull docker.io/library/nginx:latest
docker pull containers.yourcompany.com/someDeveloper/csap-simple:latest 

print_with_head Deleting nginx image
docker rmi -f docker.io/library/nginx

print_with_head removing all images 
docker rmi --force $(docker images -a -q) ;

print_with_head removing all dangling volumes
docker volume rm $(docker volume ls -f dangling=true -q)

##
##  Creating new container 
##
newContainerName="csapTestContainer"
workingDir=$csapPlatformWorking/$newContainerName
\rm -rf $workingDir ; mkdir -p $workingDir; touch $workingDir/dummy.txt

print_with_head "created $workingDir for use as a volume"

print_with_head "removing $newContainerName"
docker stop $newContainerName
docker rm $newContainerName

print_with_head "creating container named $newContainerName. Port 7080 is public - routed to 8080. Filemounts using read only"
docker run -d -p 7080:8080 \
--cpu-period=50000 --cpu-quota=25000 \
-v $JAVA_HOME:/java:ro -v $workingDir:/$newContainerName:z \
--name $newContainerName $imageName \

print_with_head "Files in container:"
filesInContainer=`docker exec $newContainerName ls -l /java /$newContainerName`
echo "$filesInContainer"

NOW=$(date +"%h-%d-%I-%M-%S")
print_with_head touching a ro filesystem
docker exec $newContainerName touch  /java/test.$NOW

print_with_head touching a rw filesystem
docker exec $newContainerName touch  /$newContainerName/test.$NOW

##
## echo 1000 > /sys/fs/cgroup/cpu/system.slice/docker-069ef55e22e9d6716946077430ee7e7e4e2c61c4e3fb747df541834f8aa70319.scope/cpu.cfs_quota_us
##

##
## Using CSAP stress image and docker cgroups throttle options https://www.kernel.org/doc/Documentation/scheduler/sched-bwc.txt

# use cpu quota to limit to 2x cores. periods are 400ms = 400000
#  docker run --detach  --cpu-period=400000 --cpu-quota=800000 --rm stress --cpu 8

# run on cores 0, 1 and 2
# docker run --detach  --cpuset-cpus "0-2"  stress --cpu 8
