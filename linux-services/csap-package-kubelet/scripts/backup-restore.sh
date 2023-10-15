#!/bin/bash

source $STAGING/bin/csap-shell-utilities.sh

masterBackupFolder=${masterBackupFolder:-/root/kubernetes-backups} ;

# handle path references
masterBackupFolder=$(eval echo $masterBackupFolder)
latestBackupFolder="$masterBackupFolder/kubernetes-master"

#
# change to restore
#	
command=${1:-backup}



#
# Test Only - wipe the current master
#
# print_with_head "Running kubeadm reset to wipe out host"
# run_using_root 'echo y | kubeadm --v 8 reset'



kubadmConfig="kubadm-config.yaml";
	
function do_backup() {

	master=$(kubectl get nodes | grep $(hostname) | grep master);
	if [ "$master" == "" ] ; then 	
	    print_with_head "Current host is a worker - no backup required";	
	    exit ;
	fi ;
	
	print_with_head "creating backup, masterBackupFolder: '$masterBackupFolder'"
	print_line "reference: https://elastisys.com/2018/12/10/backup-kubernetes-how-and-why/"
	
	
	# create if needed
	
	if [ ! -d $masterBackupFolder ]; then
		run_using_root mkdir --parents --verbose $masterBackupFolder
		run_using_root chown -R $USER $masterBackupFolder
		run_using_root chgrp -R $USER $masterBackupFolder
	fi ;
	
	touch $masterBackupFolder/load-on-install-no
	
	
	# handle previous backups
	backup_file $latestBackupFolder
	
	# create if needed
	mkdir --parents --verbose $latestBackupFolder
	
	
	print_line "backing up certs: '$latestBackupFolder/pki'"
	run_using_root "cp -r /etc/kubernetes/pki $latestBackupFolder/pki"
	
	print_line "backing up etcd: '$latestBackupFolder/etcd'"
	run_using_root docker run --rm \
		-v $latestBackupFolder:/backup \
	    --network host \
	    -v /etc/kubernetes/pki/etcd:/etc/kubernetes/pki/etcd \
	    --env ETCDCTL_API=3 \
	    k8s.gcr.io/etcd-amd64:3.2.18 \
	    etcdctl --endpoints=https://127.0.0.1:2379 \
	    --cacert=/etc/kubernetes/pki/etcd/ca.crt \
	    --cert=/etc/kubernetes/pki/etcd/healthcheck-client.crt \
	    --key=/etc/kubernetes/pki/etcd/healthcheck-client.key \
	    snapshot save /backup/etcd-snapshot-latest.db
	
	
	print_line "backing up kubeadm settings: '$masterBackupFolder/$kubadmConfig'"
	kubeadm config view > $latestBackupFolder/kubadm-config.yaml
	
	run_using_root chown -R $USER $masterBackupFolder
	run_using_root chgrp -R $USER $masterBackupFolder
}

function do_restore() {


	print_with_head "Restoring master using masterBackupFolder: '$latestBackupFolder'"


	print_line "restoring certs from: '$latestBackupFolder/pki'"
    run_using_root cp --recursive --verbose $latestBackupFolder/pki /etc/kubernetes/
    
	print_line "restoring etcd from: '$latestBackupFolder/etcd-snapshot-latest.db'"
    run_using_root mkdir -p /var/lib/etcd
    run_using_root docker run --rm \
        -v $latestBackupFolder:/backup \
        -v /var/lib/etcd:/var/lib/etcd \
        --env ETCDCTL_API=3 \
        k8s.gcr.io/etcd-amd64:3.2.18 \
        /bin/sh -c "'etcdctl snapshot restore /backup/etcd-snapshot-latest.db ; mv /default.etcd/member/ /var/lib/etcd/'"
        
    # Initialize the master with backup
    if false ; then
    
    	print_line "restoring kubeadm settings from: '$masterBackupFolder/$kubadmConfig'"
		NOW=$(date +"%h-%d-%I-%M-%S")
		mergedFile=$masterBackupFolder/$kubadmConfig.$NOW
		
	    print_line "updating config to ignore kubelet swap warnings: '$masterBackupFolder/$kubadmConfig'"
	    cp $masterBackupFolder/$kubadmConfig $mergedFile
	    echo '---' >> $mergedFile
	    echo 'apiVersion: kubelet.config.k8s.io/v1beta1' >> $mergedFile
	    echo 'kind: KubeletConfiguration' >> $mergedFile
	    echo 'failSwapOn: false' >> $mergedFile
	    
		
		run_using_root rm -rf /etc/kubeadm
	    run_using_root mkdir --parents --verbose  /etc/kubeadm
	    run_using_root cp $mergedFile /etc/kubeadm/$kubadmConfig
    
	    run_using_root kubeadm init \
	    	--ignore-preflight-errors=DirAvailable--var-lib-etcd,Swap \
	        --config /etc/kubeadm/$kubadmConfig
	else 
		print_with_head "Ready to run kubeadm"
	fi
}

if [ $command == "restore" ] ; then
	do_restore ;
else
	do_backup ;
fi
