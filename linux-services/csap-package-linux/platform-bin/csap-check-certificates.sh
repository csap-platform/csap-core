#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

minimumExpirationDays=${minimumExpirationDays:-30} ;
refreshCerts=${refreshCerts:-false};

version=$(rpm -qf $(which kubeadm));
alpha="" ;

if [[ "$version" == kubeadm-1.19.* ]] ; then
	print_line "Using alpha mode for legacy kubeadm" ;
	alpha="alpha"
fi ;

deleteCsapRootFile="true"; # this script runs frequently
run_using_root kubeadm $alpha certs check-expiration
expirationLines=$(run_using_root kubeadm  $alpha certs check-expiration | grep no);

#echo "$expireInfo"

function showExpirations() {
	print_separator "expiration minimum days: $minimumExpirationDays "
	
	local nowSeconds=$(date +%s) ; 
	
	local smallestCert=99999;
	IFS=$'\n';
	for certificateLine in $expirationLines ; do
	
		certificateLine=$(echo $certificateLine | sed 's/  */ /g') ;
		count=$((count+1)) ;
		
		numberOfWords=$(echo "$certificateLine" | wc -w) ;
		
		if (( $numberOfWords > 0 )) ; then
		
			certName=$(echo $certificateLine | awk '{ print $1}') ;
			expiration=$(echo $certificateLine | awk '{ print $2 " " $3 " " $4}') ;
			
			print_if_debug "expiration" "$expiration"
			expirationSeconds=$(date +%s --date "$expiration") ;
			difference=$(($expirationSeconds-$nowSeconds)) ;
		
			daysBeforeExpiration=$(( $difference / (3600*24) )) ;
			if (( $daysBeforeExpiration < $smallestCert )) ; then
				smallestCert=$daysBeforeExpiration;
			fi ;
			
			message="$daysBeforeExpiration days";
			if (( $daysBeforeExpiration < $minimumExpirationDays )) ; then
				#
				# DO NOT MODIFY: __Warn is used to trigger kubelet health failures
				#
				message="__WARN: $daysBeforeExpiration days";
			fi ;
			
			print_two_columns "$certName" "$message" ;
			
		fi ;
	done
	if (( $smallestCert < 99999 )) ; then
		print_line "__required-action-days__: $smallestCert" ;
	fi
	
}

showExpirations ;

if $refreshCerts ; then
	run_using_root kubeadm certs renew all
fi ;