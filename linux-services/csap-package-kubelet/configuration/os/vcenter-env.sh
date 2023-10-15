#
# Installed from csap-kubelet-package/csap-api.sh
#

if ! type -t find_kubernetes_vcenter_device ; then 

	source $HOME/vcenter/csap-environment.sh ;
	print_with_head "Loaded $HOME/vcenter/csap-shell-utilities.sh" ;
	print_line "enter vhelp for common commands"
	
fi ;

print_line "loading govc variables"
export GOVC_PASSWORD="$GOVC_PASSWORD"
export GOVC_INSECURE="$GOVC_INSECURE"
export GOVC_URL="$GOVC_URL"
export GOVC_DATACENTER="$GOVC_DATACENTER"
export GOVC_DATASTORE="$GOVC_DATASTORE"
export GOVC_USERNAME="$GOVC_USERNAME"

alias govc="$HOME/vcenter/govc"


function vhelp() {
	print_with_head "CSAP govc help"
	
	print_two_columns "find_kubernetes_vcenter_device <searchTarget> <true|false> will found and optionally remove devices"
	print_line "WARNING: if device is still mounted on any host: host may hang"	
	
	print_line "\n"		
	print_two_columns "govc ls" "will list available devices"

	print_line "\n\n"
}