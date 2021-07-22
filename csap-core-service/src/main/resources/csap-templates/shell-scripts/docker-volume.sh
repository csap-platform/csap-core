# dockerVolume, Used by CSAP docker integration to add/update/delete volumes
# Notes:
# 1. Extend time for command to complete if Host is busy or a long running command
# 2. Select additional hosts as needed
# 3. Refer to https://docs.docker.com/storage/volumes/

print_with_head "Containers: `docker ps --format '{{.Names}}\t' | tr -d '\n'`"

print_with_head "Volume(s): Dangling"
docker volume ls -qf dangling=true

print_with_head "Volumes by container"
volumes=$(docker volume ls --quiet)
for volume in $volumes ; do
  print_line "\n\t Volume: $volume"
  print_line  "\t\t"	$(docker ps --format '{{.Names}}\t' --filter "volume=$volume")
done ;


print_with_head "docker disk utilization"
docker system df -v

exit ;

#
#  Samples
#
print_with_head "Prune dangling volumes"
docker volume rm $(docker volume ls -qf dangling=true)

print_with_head "list of volumes"
docker volume ls

print_with_head "Volume(s):"
docker volume ls



print_with_head "Creating Volume"
docker volume create your_volume_name

print_with_head "Removing Volume"
docker volume rm your_volume_name

print_with_head "Inspecting Volume"
docker volume inspect your_volume_name



print_with_head container names and image
docker ps --format '{{.Names}}\t{{.Image}}'
