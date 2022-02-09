
const serviceSources = [
    "browser/utils",
    "services/instances",
    "services/batch",
    "services/backlog",
    "services/resource-browser",
    "services/logs",
    "services/pod-logs",
    "services/kubernetes",
    "services/kubernetes-describe",
    "services/kevents",
    "services/kevent-details",
    "services/helm"
] ;
//
//
//
define( serviceSources, function ( utils, instances, batch, backlog, resourceBrowser, logs, podLogs, kubernetes, kubeDescribe, kevents, keventDetails, helm ) {


    console.log( "Module loaded" ) ;
    const $statusTab = utils.findContent( "#services-tab-status" ) ;
    const $lastRefreshed = $( ".last-refreshed", $statusTab ) ;

    const ALL_SERVICES = "All Services" ;
    const CONTAINERS_DISCOVERED = "Containers Discovered" ;
    const UNREGISTERED_SERVICE = "unregistered" ;

    let $serviceIncludeFilter = $( "#service-filter", $statusTab ) ;
    let $serviceClearFilter = $( "#clear-filter", $statusTab ) ;
    let $clusterDisplayFilter = $( "#cluster-filter", $statusTab ) ;
    let $clusterClearFilter = $( "#clear-cluster-filter", $statusTab ) ;
    let lastFilter ;


    let $clusterSelection = $( "#cluster-selection", $statusTab ) ;
    let $summaryPanel = $( "#summary-panel", $statusTab ) ;
    let $statusContent = $( "#services-tab-status" ) ;


    let _statusFilterTimer ;
    let $panelLayoutSelect = $( "#panel-layout" ) ;
    let longestNameSize = 0 ;


    let $showStartOrder = $( "#show-start-order", $statusTab ) ;
    let lastServiceReport ;

    let _refreshTimer ;



    let $contentLoaded = null ;


    const navigationOperations = {
        "backlog": backlog.showBacklogInPanel,
        "batch": batch.show,
        "status": serviceSummaryGet,
        "analytics": instances.showAnalytics,
        "jobs": instances.showJobs,
        "instances": instances.show,
        "resources": resourceBrowser.show,
        "logs": logs.show,
        "kpods": kubernetes.showPodSummary,
        "pod-logs": launchPodLogs,
        "pod-describe": kubeDescribe.show,
        "kpod-instances": kubernetes.showPodInstances,
        "knodes": kubernetes.showNodes,
        "kevents": kevents.show,
        "kevent-details": keventDetails.show,
        "node-describe": kubeDescribe.show,
        "kvolumes": kubernetes.showVolumes,
        "volume-describe": kubeDescribe.show,
        "helm-readme": helm.readme,
        "helm-values": helm.values
    } ;

    return {

        initialize: function () {
            initialize() ;
            instances.initialize() ;
            batch.initialize() ;
        },

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            return show( $menuContent, forceHostRefresh, menuPath ) ;

        },

        selectService: function ( name ) {
            instances.selectService( name ) ;
        },

        selectPodOwner: function ( namespace, name ) {
            return kubernetes.selectPod( namespace, name ) ;
        },
        namespaceSelected: function ( ) {
            return kubernetes.namespaceSelected( ) ;
        },

        getSelectedService: function (  ) {
            return instances.getSelectedService(  ) ;
        },

        reDraw: function () {
            reDraw() ;
            kubernetes.reDraw() ;
        },

    }

    function show( $menuContent, forceHostRefresh, menuPath ) {

        console.log( ` menu selected: ${ menuPath }` ) ;

        let $menuLoaded ;

        let operation = navigationOperations[ menuPath ] ;

        if ( operation ) {
            $menuLoaded = operation( $menuContent, forceHostRefresh, menuPath ) ;
        } else {
            console.warn( ` no operation for ${ menuPath }` ) ;
        }

        // if parent selected (eg. to refresh / clean view), close children
        if ( menuPath == "status" ) {
            instances.selectService( ) ;
        } else if ( menuPath == "kpods" ) {
            kubernetes.hidePodOptions() ;
        }

        return $menuLoaded ;

    }

    function launchPodLogs( $menuContent, forceHostRefresh, menuPath ) {

        console.log( ` last Location: ${ utils.getLastLocation()} ` ) ;

        if ( utils.getLastLocation() === "services-tab-kpod-instances"
                || utils.getLastLocation() === "services-tab-pod-describe" ) {
            let selectedPod = kubernetes.selectedPod() ;
            let selectedName = selectedPod.data( "name" ) ;
            let relatedPods = new Array() ;
            relatedPods.push( selectedName ) ;
            kubernetes.podRows().each( function () {
                let name = $( this ).data( "name" ) ;
                if ( name !== selectedName ) {
                    relatedPods.push( name ) ;
                }
            } ) ;
            podLogs.configure(
                    selectedName,
                    selectedPod.data( "namespace" ),
                    selectedPod.data( "containers" ).split( "," ),
                    selectedPod.data( "host" ),
                    relatedPods ) ;

        }

        podLogs.show( $menuContent, forceHostRefresh, menuPath ) ;
    }

    function reDraw() {

        let numPanelItems = $( "div.summary", $summaryPanel ).length ;
        console.debug( `numPanelItems: ${numPanelItems}` ) ;

        if ( ( $panelLayoutSelect.val() === "columns" )
                && numPanelItems > 10 ) {

            let panelWidth = $summaryPanel.outerWidth() ;
            let columnsEstimate = 2 ;
            if ( panelWidth < 600 ) {
                columnsEstimate = 1 ;
            }

            if ( numPanelItems > 16 ) {

                let numChars = longestNameSize ;
                if ( numChars > 20 ) {
                    numChars = 20 ;
                }
                let serviceWidth = numChars * 10 + 50 ;
                if ( serviceWidth < 400 ) {
                    serviceWidth = 300 ;
                }
                columnsEstimate = Math.floor( panelWidth / serviceWidth ) ;
                console.log( `numPanelItems: ${numPanelItems} columnsEstimate: ${columnsEstimate} panelWidth: ${panelWidth}, serviceWidth: ${ serviceWidth } ` ) ;
                if ( columnsEstimate < 2 ) {
                    console.log( "Forcing minimum column size" ) ;
                    //columnsEstimate = 2 ;
                }
            } else {
                console.log( `numPanelItems: ${numPanelItems} columnsEstimate: ${columnsEstimate} ` ) ;
            }
            $summaryPanel.css( "columns", `20em ${columnsEstimate.toString()}` ) ;

//            $summaryPanel.css( "columns", `20em auto` ) ;
            $summaryPanel.css( "column-gap", `3em` ) ;

        } else {
            $summaryPanel.css( "columns", "" ) ;
            $summaryPanel.css( "column-gap", `` ) ;
        }
    }

    function initialize() {

        $clusterDisplayFilter.off().keyup( function () {
            console.log( "Applying template filter" ) ;
            clearTimeout( _statusFilterTimer ) ;
            _statusFilterTimer = setTimeout( function () {
                applyClusterFilter() ;
            }, 500 ) ;
        } ) ;

        $clusterClearFilter.hide() ;
        $clusterClearFilter.click( function () {
            $clusterClearFilter.hide() ;
            $clusterDisplayFilter.val( "" ) ;
            applyClusterFilter() ;
        } ) ;



        $serviceIncludeFilter.off().keyup( function () {
            console.log( "Applying template filter" ) ;
            clearTimeout( _statusFilterTimer ) ;
            _statusFilterTimer = setTimeout( function () {
                applyServiceFilter() ;
            }, 500 ) ;
        } ) ;
        $serviceClearFilter.hide() ;
        $serviceClearFilter.click( function () {
            $serviceClearFilter.hide() ;
            $serviceIncludeFilter.val( "" ) ;
            applyServiceFilter() ;
        } ) ;

        $serviceIncludeFilter.parent().hide() ;

        $showStartOrder.change( function () {
            addServices() ;
        } ) ;

        $clusterSelection.click( function () {
            $clusterSelection.parent().hide() ;
            $clusterSelection.data( "name", "" ) ;
            serviceSummaryGet( $statusContent, false ) ;
            return false ;
        } )


        $clusterSelection.parent().hide() ;

    }

    function applyClusterFilter() {

        let $servicePanels = $( 'div.summary', $summaryPanel ) ;

        let  includeFilter = $clusterDisplayFilter.val() ;
        lastFilter = includeFilter ;

        console.log( ` includeFilter: ${includeFilter}` ) ;

        $clusterDisplayFilter.removeClass( "modified" ) ;

        if ( includeFilter.length > 0 ) {

            $clusterDisplayFilter.addClass( "modified" ) ;

            $clusterClearFilter.show() ;
            $servicePanels.hide() ;
            let filters = includeFilter.split( "," ) ;

            for ( let currentFilter of filters ) {

                $( `>div:icontains("${ currentFilter }")`, $servicePanels ).parent().show() ;
            }

        } else {
            $servicePanels.show() ;
        }

    }

    function applyServiceFilter() {

        let $servicePanels = $( 'div.summary', $summaryPanel ) ;

        let  includeFilter = $serviceIncludeFilter.val() ;
        lastFilter = includeFilter ;

        console.log( ` includeFilter: ${includeFilter}` ) ;

        $serviceIncludeFilter.removeClass( "modified" ) ;

        if ( includeFilter.length > 0 ) {

            $serviceIncludeFilter.addClass( "modified" ) ;

            $serviceClearFilter.show() ;
            $servicePanels.hide() ;
            let filters = includeFilter.split( "," ) ;

            for ( let currentFilter of filters ) {

                let filterWithTypes = includeFilter.split( ":" ) ;
                if ( filterWithTypes.length == 2
                        && filterWithTypes[0] == "runtime" ) {

                    $( `div.summary[data-runtime*='${ filterWithTypes[1] }']`, $summaryPanel ).show() ;

                } else if ( filterWithTypes.length == 2
                        && filterWithTypes[0] == "name" ) {

                    $( `div.summary[data-name*='${ filterWithTypes[1] }']`, $summaryPanel ).show() ;

                } else if ( filterWithTypes.length == 2
                        && filterWithTypes[0] == "kind" ) {

                    $( `div.summary[data-kind*='${ filterWithTypes[1] }']`, $summaryPanel ).show() ;

                } else if ( currentFilter.startsWith( ":err" ) ) {

                    $( `button.status-warning`, $servicePanels ).parent().parent().parent().show() ;
                    $( `span.status-warning`, $servicePanels ).parent().parent().parent().show() ;

                } else {

                    $( `>div:icontains("${ currentFilter }")`, $servicePanels ).parent().show() ;

                    $( `div.summary[data-runtime*='${ currentFilter }']`, $summaryPanel ).show() ;

                    $( `div.summary[data-kind*='${ currentFilter }']`, $summaryPanel ).show() ;
                }

                // if ( c)
            }

        } else {
            $servicePanels.show() ;
        }

    }

    function getLowerCase( theString ) {
        if ( theString ) {
            return theString.toLowerCase() ;
        }

        return "discovered" ;
    }


    function serviceSummaryGet( $displayContainer, blockingUpdate, autoRefresh ) {

        clearTimeout( _refreshTimer ) ;
        $contentLoaded = new $.Deferred() ;
        // alert("serviceSummaryGet")  ;
        let parameters = {
            project: utils.getActiveProject(),
            blocking: blockingUpdate
        } ;

        let loadMessage = "Loading CSAP Clusters" ;
        let targetCluster = clusterSelected() ;
        if ( targetCluster === CONTAINERS_DISCOVERED ) {
            $clusterSelection.parent().hide() ;
            $clusterSelection.data( "name", "" ) ;
            instances.selectService( UNREGISTERED_SERVICE, CONTAINERS_DISCOVERED ) ;
            return ;
        }
        if ( !isClusterSelectionEmpty()
                && ( targetCluster != ALL_SERVICES ) ) {
            $.extend( parameters, {
                cluster: targetCluster
            } ) ;
            loadMessage = "Loading CSAP services" ;
        }

        if ( isClusterSelectionEmpty()
                && targetCluster != ALL_SERVICES ) {
            $serviceIncludeFilter.val( "" ) ;
            $serviceClearFilter.hide() ;
        }

        if ( isClusterSelectionEmpty() ) {
            $showStartOrder.parent().hide() ;
        } else {
            $showStartOrder.parent().show() ;
        }

//        let servicesUrl = SERVICE_URL + "/summary" ;
        let servicesUrl = APP_BROWSER_URL + "/service/summary" ;

        console.log( `99servicesUrl(): ${ servicesUrl }` ) ;
        // $serviceIncludeFilter.val( "" ) ;
        // $serviceClearFilter.hide() ;
        // 
        // 
// getActivityCount() ;

        if ( !autoRefresh ) {
            utils.loading( loadMessage ) ;
        }

        $.getJSON(
                servicesUrl,
                parameters )

                .done( function ( summaryReport ) {

                    $contentLoaded.resolve() ;
                    utils.loadingComplete() ;
                    $lastRefreshed.text( summaryReport["host-time"] ) ;
                    serviceSummarySuccess( $displayContainer, summaryReport ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving service summary", errorThrown ) ;
                } ) ;
        ;

        return $contentLoaded ;
    }

    function serviceSummarySuccess( $displayContainer, servicesReport ) {


//        $( "#hostSummary" ).html( serviceSummaryResponse.totalHostsActive + "/" + serviceSummaryResponse.totalHosts ) ;
        //$( "#serviceSummary" ).html( servicesReport.totalServicesActive + "/" + servicesReport.totalServices ) ;


        if ( $displayContainer ) {

            if ( isClusterSelectionEmpty() ) {
                $serviceIncludeFilter.parent().hide() ;
                $clusterDisplayFilter.parent().show() ;
                addClusters( servicesReport ) ;
                applyClusterFilter() ;
            } else {
                $clusterDisplayFilter.parent().hide() ;
                $serviceIncludeFilter.parent().show() ;
                addServices( servicesReport ) ;
                applyServiceFilter() ;
            }



//        setTimeout( post_summary_function, 500 ) ;

        }

        _refreshTimer = setTimeout( () => {
            if ( $statusTab.is( ":visible" ) ) {
                serviceSummaryGet( $displayContainer, false, true ) ;
            } else {
                console.log( `status not visible - refreshing cancelled` ) ;
            }
        }, utils.getRefreshInterval() ) ;

    }

    function addClusters( servicesReport ) {

        let clusterServiceMap = servicesReport.clusters ;
        let servicesActive = servicesReport.servicesActive ;
        let servicesTotal = servicesReport.servicesTotal ;
        let servicesType = servicesReport.servicesType ;
        let serviceErrors = servicesReport.errorsByService ;

        let $latest = jQuery( '<div/>', {
            class: "",
        } ) ;

        let clusterNames = utils.keysSortedCaseIgnored( clusterServiceMap ) ;

        if ( servicesTotal [UNREGISTERED_SERVICE] ) {
            clusterNames.splice( 0, 0, CONTAINERS_DISCOVERED ) ;
        }
        clusterNames.splice( 0, 0, ALL_SERVICES ) ;
        longestNameSize = 0 ;
        for ( let clusterName of clusterNames ) {

            if ( clusterName.length > longestNameSize ) {
                longestNameSize = clusterName.length ;
            }

            // default to all service count
            let totalRunning = 0 ;
            let totalPackages = 0 ;
            let totalStopped = 0 ;
            let totalAlerts = 0 ;
            let firstServiceType = "csap-api" ;

            let clusterServices = clusterServiceMap[ clusterName ] ;
            if ( !clusterServices ) {
                // total all services
                clusterServices = Object.keys( servicesTotal ) ;
            }

            let firstService = clusterServices[0] ;
            firstServiceType = servicesType[firstService] ;

            for ( let serviceName of clusterServices ) {

                let serviceType = servicesType[ serviceName ] ;
                let serviceTotal = servicesTotal[ serviceName ] ;
                let serviceRunning = servicesActive[ serviceName ] ;
                let serviceStopped = serviceTotal - serviceRunning ;
                let numAlerts = serviceErrors[ serviceName ] ;

                console.debug( `serviceTotal: ${serviceTotal}  serviceStopped: ${serviceStopped} alerts ${ numAlerts } ${ serviceName } ${serviceType} ` )

                if ( Number.isInteger( numAlerts ) ) {
                    totalAlerts += numAlerts ;
                }

                if ( serviceType === "package" ) {
                    if ( Number.isInteger( serviceTotal ) ) {
                        totalPackages += serviceTotal ;
                    }
                } else {
                    if ( Number.isInteger( serviceRunning ) ) {
                        totalRunning += serviceRunning ;
                    }
                    if ( Number.isInteger( serviceStopped )
                            && serviceStopped > 0 ) {
                        // need to handle kubernetes where total is NOT specified in definition
                        totalStopped += serviceStopped ;
                    }
                }

                console.debug( `totalRunning ${totalRunning} totalStopped: ${ totalStopped } totalPackages ${ totalPackages } ${ serviceName }` )

            }

            // ignore services on agents for non
            if ( utils.isAgent()
                    && totalRunning === 0
                    && totalStopped === 0
                    && totalPackages === 0 ) {

                continue ;
            }
            let $summary = jQuery( '<div/>', {
                class: "summary",
                "data-name": clusterName,
                title: clusterName
            } ) ;
            $latest.append( $summary ) ;

            let $imageDiv = jQuery( '<div/>', { } ) ;
            $summary.append( $imageDiv ) ;

            let imagePath = `${ IMAGES_URL }/${ utils.getClusterImage( clusterName, firstServiceType ) }` ;

            jQuery( '<img/>', { src: imagePath } ).appendTo( $imageDiv ) ;

            let $infoDiv = jQuery( '<div/>', { } ) ;
            $summary.append( $infoDiv ) ;
            jQuery( '<div/>', { text: clusterName } ).appendTo( $infoDiv ) ;

            let $status = jQuery( '<div/>', { class: "status" } ) ;
            $status.appendTo( $infoDiv ) ;

            if ( ( clusterName == CONTAINERS_DISCOVERED ) && servicesTotal [UNREGISTERED_SERVICE] ) {

                jQuery( '<span/>', {
                    class: "status-green",
                    title: "Unregistered Services",
                    text: servicesTotal [UNREGISTERED_SERVICE]
                } ).appendTo( $status ) ;

            } else {
                if ( totalRunning > 0 ) {
                    jQuery( '<span/>', {
                        class: "status-green",
                        title: "services running",
                        text: totalRunning
                    } ).appendTo( $status ) ;
                } else if ( totalStopped == 0
                        && totalPackages == 0 ) {

                    jQuery( '<span/>', {
                        class: "status-none",
                        title: "cluster services not found",
                        text: "---"
                    } ).appendTo( $status ) ;
                }

                if ( totalStopped > 0 ) {
                    jQuery( '<span/>', {
                        class: "status-red",
                        title: "services stopped",
                        text: totalStopped
                    } ).appendTo( $status ) ;
                }

                if ( totalPackages > 0 ) {
                    jQuery( '<span/>', {
                        class: "status-os",
                        title: "service packages",
                        text: totalPackages
                    } ).appendTo( $status ) ;
                }

                if ( totalAlerts > 0 ) {
                    jQuery( '<span/>', {
                        class: "status-warning",
                        title: "service warnings",
                        text: totalAlerts
                    } ).appendTo( $status ) ;
                }

            }

        }
        $summaryPanel.empty() ;
        $summaryPanel.removeClass() ;

        let displayType = $( "#panel-layout" ).val() ;
        $summaryPanel.addClass( `deployment-panel ${$panelLayoutSelect.val()}` ) ;

        $summaryPanel.append( $latest.html() ) ;
        reDraw() ;


        $( ".summary", $summaryPanel ).click( function () {
            let name = $( this ).data( "name" ) ;
            clusterSelected( name ) ;
            if ( name == "all" ) {
                addServices( servicesReport ) ;
            } else {
                serviceSummaryGet( $statusContent, false ) ;
            }
        } ) ;


    }

    function addServices( servicesReport ) {

        if ( servicesReport ) {
            lastServiceReport = servicesReport ;
        } else {
            servicesReport = lastServiceReport ;
        }


        let servicesActive = servicesReport.servicesActive ;
        let servicesTotal = servicesReport.servicesTotal ;
        let servicesType = servicesReport.servicesType ;
        let servicesRuntime = servicesReport.servicesRuntime ;
        let serviceErrors = servicesReport.errorsByService ;
        let startOrder = servicesReport.startOrder ;
        let clusters = servicesReport.clusters ;

        let serviceToCluster = null ;

        if ( clusterSelected() == ALL_SERVICES ) {
            serviceToCluster = new Object() ;
            for ( let cluster in clusters ) {

                let clusterServices = clusters[ cluster ] ;

                for ( let serviceName of clusterServices ) {
                    serviceToCluster[serviceName] = cluster ;
                }
            }

            console.log( `serviceToCluster: `, serviceToCluster ) ;
        }


        let $latest = jQuery( '<div/>', {
            class: "",
        } ) ;

        let serviceNames = utils.keysSortedCaseIgnored( servicesTotal ) ;
        if ( $showStartOrder.is( ":checked" ) ) {
            //
            //  sorting a javascript map: create an array of the objects
            //
            let startOrderItems = Object.keys( startOrder ).map( function ( key ) {
                let value = startOrder[key] ;
                if ( value <= 0 ) {
                    value = 99999 ;
                }
                return [ key, value ] ;
            } ) ;
            startOrderItems.sort( function ( first, second ) {
                //return second[1] <= first[1] ;
                return first[1] - second[1] ;
            } ) ;
            console.log( `startOrderItems: `, startOrderItems ) ;
            serviceNames = new Array() ;
            for ( let item of startOrderItems ) {
                serviceNames.push( item[0] ) ;
            }
        }
        longestNameSize = 0 ;
        for ( let serviceName of serviceNames ) {

            if ( serviceName === UNREGISTERED_SERVICE
                    && clusterSelected() != ALL_SERVICES ) {
                continue ;
            }

            if ( serviceName.length > longestNameSize ) {
                longestNameSize = serviceName.length ;
            }

            let runtimeFilter = getLowerCase( servicesRuntime[serviceName] ) ;
            let kindFilter = getLowerCase( servicesType[serviceName] ) ;

            let $summary = jQuery( '<div/>', {
                class: "summary",
                "data-name": serviceName,
                "data-runtime": runtimeFilter,
                "data-kind": kindFilter,
                title: serviceName
            } ) ;
            $latest.append( $summary ) ;

            let $imageDiv = jQuery( '<div/>', { } ) ;
            $summary.append( $imageDiv ) ;

            let imagePath = `${ IMAGES_URL }/${ getServiceImage( servicesType[serviceName] ) }` ;

            jQuery( '<img/>', { src: imagePath } ).appendTo( $imageDiv ) ;


            let $infoDiv = jQuery( '<div/>', { } ) ;
            $summary.append( $infoDiv ) ;

            let $name = jQuery( '<div/>', {
                text: serviceName
            } ).appendTo( $infoDiv ) ;

            if ( $showStartOrder.is( ":checked" ) ) {
                let startDesc = startOrder[serviceName] ;
                let title = "Start Order: " + startDesc ;
                if ( startDesc <= 0 ) {
                    startDesc = "--" ;
                    title = "service is not autostarted" ;
                }
                jQuery( '<label/>', {
                    class: "start-order-label flex-right-info",
                    title: title,
                    text: startDesc
                } ).appendTo( $name ) ;
            }

            if ( serviceToCluster ) {
                jQuery( '<div/>', {
                    class: "deploy-info",
                    title: "Cluster Name",
                    text: serviceToCluster[serviceName]
                } ).appendTo( $infoDiv ) ;
            }

            let serviceType = servicesType[ serviceName ] ;
            let serviceTotal = servicesTotal[ serviceName ] ;
            let serviceRunning = servicesActive[ serviceName ] ;
            let servicePackages = 0 ;
            let serviceStopped = 0 ;

            if ( ( serviceType === "package" ) ||
                    ( serviceTotal === 0 ) ) {
                serviceRunning = 0 ;
                if ( serviceTotal === 0 ) {
                    servicePackages = 1 ; // flag for kubernetes schema
                } else if ( Number.isInteger( serviceTotal ) ) {
                    servicePackages = serviceTotal ;
                }
            } else {
                if ( !Number.isInteger( serviceRunning ) ) {
                    serviceRunning = 0 ;
                } else {
                    if ( Number.isInteger( serviceTotal ) ) {
                        serviceStopped = serviceTotal - serviceRunning ;
                    }
                }
            }

            let $status = jQuery( '<div/>', { class: "status" } ) ;
            $status.appendTo( $infoDiv ) ;

            if ( serviceRunning > 0 ) {
                jQuery( '<span/>', {
                    class: "status-green",
                    title: "services running",
                    text: serviceRunning
                } ).appendTo( $status ) ;
            }

            if ( serviceStopped > 0 ) {

                if ( serviceType == "monitor"
                        && serviceName.startsWith( "ns-" ) ) {
                    jQuery( '<span/>', {
                        class: "status-os",
                        title: "no containers deployed",
                        text: `0`
                    } ).appendTo( $status ) ;
                } else {
                    jQuery( '<span/>', {
                        class: "status-red",
                        title: "services stopped",
                        text: serviceStopped
                    } ).appendTo( $status ) ;
                }
            }

            if ( servicePackages > 0 ) {
                jQuery( '<span/>', {
                    class: "status-os",
                    title: "service packages",
                    text: servicePackages
                } ).appendTo( $status ) ;
            }

            let numAlerts = serviceErrors[serviceName] ;
            if ( numAlerts ) {
                $status.append( buildAlertIconForService( serviceName, numAlerts ) ) ;
            } else {
                jQuery( '<span/>', { } ).appendTo( $status ) ;
            }

        }
        $summaryPanel.empty() ;
        $summaryPanel.removeClass() ;

        let displayType = $( "#panel-layout" ).val() ;
        $summaryPanel.addClass( `deployment-panel ${$panelLayoutSelect.val()}` ) ;

        $summaryPanel.append( $latest.html() ) ;
        reDraw() ;

        registerServiceAlerts( $( "button.service-alert-show", $statusContent ) ) ;

        $( ".summary", $summaryPanel ).click( function () {
            let name = $( this ).data( "name" ) ;
            instances.selectService( name, clusterSelected() ) ;
        } ) ;


    }


    function isClusterSelectionEmpty() {
        return clusterSelected() == "" ;
    }

    function clusterSelected( name ) {

        if ( !name ) {
            return $clusterSelection.data( "name" ) ;

        } else {
            $clusterSelection.data( "name", name ) ;
            $clusterSelection.text( name ) ;
            $clusterSelection.parent().show() ;
        }
    }

    function getServiceImage( serviceType ) {
        let nameMatch = {
//            "runtime": '32x32/process.png',
//            "csap-api": '32x32/process.png',
            "SpringBoot": 'boot.png',
            "database": 'database.png',
            "messaging": '32x32/jms.gif',
            "webServer": 'httpd.png',
            UNREGISTERED_SERVICE: '32x32/new.png',
            "docker": '32x32/docker.jpg',
            "kubernetes": 'kubernetes.svg',
            "monitor": '32x32/utilities-system-monitor.png',
            "os": '32x32/utilities-system-monitor.png',
            "script": '32x32/package.png',
            "scripts": '32x32/package.png',
            "package": '32x32/package.png',
        }

        let serviceImage = nameMatch [ serviceType ] ;

        if ( serviceType.includes( "tomcat" ) ) {
            serviceImage = 'tomcatLarge.png' ;
        }

        if ( !serviceImage ) {
            serviceImage = '32x32/process.png' ;
        }

        return serviceImage ;
    }


    function buildAlertIconForService( serviceName, numAlerts ) {

        let $alertButton = jQuery( '<button/>', {
            class: "csap-button-icon status-warning service-alert-show",
            "data-name": serviceName
        } ) ;

        $alertButton.append( `  ` + numAlerts ) ;

        return $alertButton ;
    }

    function registerServiceAlerts( $alertIcons ) {

        $alertIcons.off().click( function () {
            //alertify.notify( "retrieving alerts" );
            let serviceName = $( this ).data( "name" ) ;
            utils.loading( "Getting Service Alerts" )
            let parameters = {
                filter: serviceName,
                project: $( '.releasePackage' ).val()
            }
            $.getJSON(
                    "service/alerts",
                    parameters )

                    .done( function ( alertReport ) {

                        utils.loadingComplete() ;

                        showServiceAlertsDialog(
                                buildServiceAlertsTable( alertReport ),
                                serviceName
                                ) ;

                    } )

                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        $( "#loadingMessage" ).hide() ;
                        console.log( "Error: Retrieving lifeCycleSuccess fpr host " + hostName, errorThrown )
                        // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
                    } ) ;

            return false ;
        } ) ;

    }

    function buildServiceAlertsTable( alertsReport ) {

        let $tempDiv = jQuery( '<div/>', { } )
        let $alertTable = jQuery( '<table/>', {
            id: "service-alerts-table",
            class: "csap"
        } )
        let $head = jQuery( '<thead/>', { } ).appendTo( $alertTable ) ;
        let $trow = jQuery( '<tr/>', { } ).appendTo( $head ) ;

        jQuery( '<th/>', { html: "Host source" } ).appendTo( $trow ) ;
        jQuery( '<th/>', { html: "Category" } ).appendTo( $trow ) ;
        jQuery( '<th/>', { html: "Type" } ).appendTo( $trow ) ;
        jQuery( '<th/>', { html: "Notes" } ).appendTo( $trow ) ;

        $alertTable.appendTo( $tempDiv ) ;

        for ( let alert of alertsReport ) {
            let $row = jQuery( '<tr/>', { } )
                    .appendTo( $alertTable ) ;
            let hostInfo = alert.host ;
            if ( alert.podIp ) {
                hostInfo += ` pod: '${ alert.podIp }'` ;
            } else {
                hostInfo += ` pid: '${ alert.pids[0] }'` ;
            }
            jQuery( '<td/>', {
                html: hostInfo
            } ).appendTo( $row ) ;


            jQuery( '<td/>', {
                html: alert.category
            } ).appendTo( $row ) ;

            jQuery( '<td/>', {
                html: alert.type
            } ).appendTo( $row ) ;

            let notes = "" ;

            if ( alert.id ) {
                notes += ` id: '${ alert.id }'` ;
            }

            if ( alert.description ) {
                notes += ` description: '${ alert.description }'` ;
            }

            if ( alert.current ) {
                notes += ` current: '${ alert.current }'` ;
            }
            if ( alert.max ) {
                notes += ` max: '${ alert.max }'` ;
            }
            jQuery( '<td/>', {
                html: notes
            } ).appendTo( $row ) ;

        }

        return $tempDiv.html() ;
    }


    function showServiceAlertsDialog( errorHtml, title ) {

        let alertHeader = jQuery( '<div/>', {
            html: "Service Alerts for: " + title,
        } ) ;

        let alertTip = jQuery( '<div/>', {
            text: "For help refer to: "
        } )
                .css( "float", "right" )
                .css( "margin-right", "8em" )
                .appendTo( alertHeader ) ;

        jQuery( '<a/>', {
            class: "simple",
            target: "_blank",
            title: "Open Reference Guide",
            href: "https://github.com/csap-platform/csap-core/wiki#updateRefCS-AP+Monitoring",
            text: "CSAP Reference Guide"
        } )
                .css( "display", "inline" )
                .appendTo( alertTip ) ;


        let alertsDialog = alertify.alert( "<br/>" + errorHtml ) ;


        alertsDialog.setting( {
            title: alertHeader.html(),
            resizable: true,
            modal: true,
            movable: true,
            'label': "Close",
            autoReset: false,
            'onok': function () {
            }

        } ) ;


        let windowWidth = $( window ).outerWidth( true ) - 100 ;
        alertsDialog.resizeTo( Math.round( windowWidth ), 300 ) ;

        setTimeout( () => {
            let windowHeight = $( window ).outerHeight( true ) - 100 ;

//            let $warningsList = $( ".alertifyWarnings li" );
//            let $brList = $( "br", $warningsList );
//            console.log( "$brList", $brList.length, "  $warningsList: ", $warningsList.length )
//            //let customHeight = ($warningsList.length * 44) + 100;
//            let warningsHeight = ( $warningsList.length * 26 ) + ( $brList.length * 16 ) + 100;

            let warningsHeight = windowHeight ;
            let numRows = $( "#service-alerts-table tr" ).length ;
            if ( numRows > 0 ) {
                let tableHeight = $( "#service-alerts-table" ).outerHeight( true ) ;
                console.log( `tableHeight: ${tableHeight} ` ) ;
                warningsHeight = tableHeight + 130 ;
            } else {
                let listHeight = $( ".alertify div.alertifyWarnings" ).outerHeight( true ) ;
                console.log( `listHeight: ${listHeight} ` ) ;
                warningsHeight = listHeight + 130 ;
            }

            console.log( `numRows: ${numRows}, windowHeight: ${ windowHeight }, warningsHeight: ${ warningsHeight }` )

            if ( warningsHeight > windowHeight ) {
                warningsHeight = windowHeight ;
            }

            if ( warningsHeight < 300 ) {
                warningsHeight = 300 ;
            }

            alertsDialog.resizeTo( Math.round( windowWidth ), Math.round( warningsHeight ) )
        }, 500 )

        return false ;
    }


} ) ;

jQuery.expr[':'].icontains = function ( a, i, m ) {
    let contents = jQuery( a ).text().toUpperCase() ;

    //console.log(`contents of filter`, contents) ;
    let expression = m[3].toUpperCase() ;

    //console.log(`contents: ${contents}, expression: ${expression} `) ;
    return contents.indexOf( expression ) >= 0 ;
} ;
