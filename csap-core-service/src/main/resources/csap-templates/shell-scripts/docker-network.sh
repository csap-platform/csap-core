# dockerNetwork, queries used by CSAP docker integration for network resources. add/update/delete
# Notes:
# 1. Extend time for command to complete if Host is busy or a long running command
# 2. Select additional hosts as needed
# 3. Refer to https://docs.docker.com/engine/userguide/networking

print_with_head "Containers: `docker ps --format '{{.Names}}\t' | tr -d '\n'`"

print_with_head "list of networks"
docker network list

print_with_head "bridge network details"
docker network inspect bridge

exit ;

print_with_head "Creating bridge network with dns resolution using container names"
docker network create --driver bridge demo_bridge

print_with_head "Assign container host"
docker network connect demo_bridge your_container_name

print_with_head "Remove container hosts"
docker network disconnect demo_bridge your_container_name


print_with_head "Remove bridge network"
docker network rm  demo_bridge

print_with_head container names and image
docker ps --format '{{.Names}}\t{{.Image}}'
