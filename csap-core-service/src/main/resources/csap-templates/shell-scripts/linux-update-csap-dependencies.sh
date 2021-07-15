# linux update, update core CSAP OS depenencies


print_with_head run as root and extend timeout to 5 minutes then remove the exit
exit

print_with_head "zlib-devel is used used by csap web package for mod_inflate" 
yum -y install zlib-devel

print_with_head current glibc
yum -y update glibc

yum -y  install nc dos2unix  gcc  gcc-c++  yum_utils  sysstat openssl


