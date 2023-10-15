# Overview
CSAP can be installed on any linux host (VmWare, Openstack, AWS, physical hardware, etc.). The majority of existing
hosts are using Centos or Redhat enterprise, so some tweaking in the installation scripts and parsers might be required
if other variants are used. New variants are generally very easy to correct - simply open an issue to track if needed. 



### Quick Start

The csap install script will:
- optionally: update kernel settings with tested values
- optionally: (re)create a filesystem for the csap install user (default is csapUser)
- download the csap-6.0.0.zip from the specified toolsserver
- download/install/start any configured csap-packages in the Application.json

To simplify the learning process, the default Application definition (-clone default) can be used with simple access controls:
``` bash
./installer/install.sh -clone default -noPrompt -installCsap 25 -extraDisk /data 25 -skipKernel \
	-pass yourpass -toolsServer csaptools.youcompany.com

```

### New Application
Typical install for creating a new application. New applications typically require more setup time as company wide settings
(such as LDAP, Maven, etc. providers are defined). Typically a toolshost is created (by ommitting -clone), which then enables
all applications in your company to clone the settings:

```bash
./installer/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com \
 	-starterUrl "http://csaptools.yourcompany.com/admin/os/definitionZip?path=YourStarter"
```
 	
### Adding a host
typical install for new host in existing application
```bash 
 ./installer/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com \
 	-clone existingHost.yourcompany.com
```

### Options:
```
# Options:
#   -noPrompt               # skip past all confirmation prompts
#   -installDisk xxx        # volume group device: default is /dev/sdb . To use root partition: use default
#   -installCsap <size>     # agent disk size in GB ; where services are installed/run
#   -clone <option>         # Optional: hostname 
# 	-starterUrl <url>		# Optional: use url to retrieve a base configuration for new applications.
#   -allInOne               # Optional: Uses -full install package from http://csaptools.cisco.com/csap/
#   -skipKernel             # Optional: skips kernel configuration and os package updates
#   -zone "America/Chicago" # Optional: configures timezone
#   -fsType <type>          # Optional: default ext4, xfs
#   -toolsServer <server>   # default: none (uses full installer) Optional: csaptools.cisco.com , rtptools-prd01.cisco.com
#   -extraDisk /data <size> # Optional: creates disk mount location in volume group; useful for db disks
#   -installActiveMq <size> # Optional: create mqUser account with disk size in GB, recommend: 10
#   -installOracle <size>   # Optional: create oracle account with disk size in GB, recommend: 200
#
```

### Examples

#### Getting the Installer

Typically run as root to leverage core configuration automation (kernel settings, file system creation, etc.). 
The installer can be run as a regular user, but then host configuration will need to be applied as part of a separate process.

```
# Optional: centos firewall rules
systemctl mask firewalld.service; systemctl disable firewalld.service ;systemctl stop firewalld.service ; systemctl status firewalld.service
 
# get installer; if needed: yum -y install unzip wget
echo == cleaning up previous installs
cd $HOME; \rm -rf csap*;
wget http://maven.yourcompany.com/artifactory/your-group/org/csap/csap-package-linux/1.0.5/csap-package-linux-1.0.5.zip
unzip csap-package-linux*.zip
```


#### Tools Server or 1st Host

Setting up a tools server is a one time operation - it provides a staging location for future installs
and for all the non-maven hosted binaries, including csap-*.zip.
- it is also a CSAP application, and typically co-located with the csap-events-service

Ideally, this will be released as a docker image. Until then steps below are required.

1. use maven to build the csap-core-service, csap-package-linux, csap-package-java
2. on your tools host, copy the core (jar), linux (zip), java(zip) packages to the home folder
3. Get the latest jdk and maven releases from internet sources - copy them to your home folder
4. Run the csap-user setup script shown below
5. Use the CSAP editor at http://yourhost:8011/CsAgent. user admin password password
- you can add/build/install the httpd package, and then put binaries under $HOME/web/csap (for csap), 
java (jdk releases)
- update the host and other variables
- installation on other hosts can now use this host as the -toolServer entry

``` bash
#csapUser setup: alternately - just copy directly from your desktop

snapRepo="http://maven.yourcompany.com/artifactory/group-snapshots"
releaseRepo="http://maven.yourcompany.com/artifactory/group-release"
toolsRepo="http://csaptools.yourcompany.com"

export localPackages="$HOME/packages"
cd $HOME; \rm -rf $localPackages
mkdir -p $localPackages
cd $localPackages
wget $releaseRepo/org/csap/csap-package-linux/1.0.5/csap-package-linux-1.0.5.zip
unzip -o -d $HOME/packages $HOME/packages/csap-package-linux*.zip

wget $releaseRepo/org/csap/csap-core-service/6.0.0.8/csap-core-service-6.0.0.8.jar

wget $releaseRepo/org/csap/csap-package-java/1.0.1/csap-package-java-1.0.1.zip

wget $toolsRepo/java/jdk-8u144-linux-x64.tar.gz
wget $toolsRepo/csap/apache-maven-3.3.3-bin.zip

mv -v installer $HOME
ls

cd $HOME

rm -rf $HOME/demo; mkdir $HOME/demo; 
installer/install.sh -targetFs $HOME/demo -noPrompt -toolsServer notSpecified


```

### Getting your maven repo configured

When settting up your toolsserver, mavenRepoUrl and mavenSettingsUrl can optionally be configured.
Alternately - just update the settings directly in Application.json using the CSAP Editor

```bash

# Simple install sets up a vanilla server eg. a tool host with default application definition
./installer/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com \
    -mavenRepoUrl http://maven.yourcompany.com/artifactory/your-group \
    -mavenSettingsUrl http://csaptools.yourcompany.com/cisco/settings.xml
 
# Once a basic application definition has been setup using CSAP Editor, additional hosts are added using clone
./installer/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com -clone <YOUR_FIRST_VM>
```















