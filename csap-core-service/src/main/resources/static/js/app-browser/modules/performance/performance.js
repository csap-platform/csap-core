define( [ "services/kubernetes", "performance/summary-trends", "performance/events", "performance/event-details", "performance/health", "performance/meters", "browser/utils" ], function ( kubernetes, summaryTrends, events, eventDetails, health, meters, utils ) {

    console.log( "Module loaded" ) ;
    let $contentLoaded = null ;

    let kubernetesServiceName = "kubelet" ;


    const $healthServiceNames = utils.findContent( "#health-service-names" ) ;
    const $kubernetesNav = utils.findNavigation( ".kubernetes-active" ) ;



    const $performanceTab = $( "#performance-tab-content" ) ;
    const $trendTime = $( "select.trend-time", $performanceTab ) ;
    const $trendTop = $( "select.trend-top", $performanceTab ) ;


    const $alertsPanel = $( "#performance-tab-alerts", $performanceTab ) ;
    const $alertTrends = $( ".alert-trends", $alertsPanel ) ;
    const $alertOptions = $( ".options", $alertsPanel ) ;
    const $alertWeeks = $( "select", $alertOptions ) ;
    const $alertByHost = $( "input.by-host", $alertOptions ) ;


    const $activityPanel = $( "#performance-tab-activity-trends", $performanceTab ) ;
    const $activityTrends = $( ".activity-trends", $activityPanel ) ;
    const $activityDays = $( "select", $activityPanel ) ;
    const $activityByHost = $( "input.by-host", $activityPanel ) ;



    const $kubernetesRtPanel = $( "#performance-tab-kubernetes-rt", $performanceTab ) ;
    const $kubernetesRtNodeTableBody = $( "#node-table-div table tbody", $kubernetesRtPanel ) ;
    const $kubernetesRtContainerTableBody = $( "#container-table-div table tbody", $kubernetesRtPanel ) ;
    const $kubernetesRtPodTableBody = $( "#pod-table-div table tbody", $kubernetesRtPanel ) ;
    const $kubeRtPodFilter = $( "#k8-top-filter", $kubernetesRtPanel ) ;
    const $kubeRtClearPodFilter = $( "#clear-pod-filter", $kubernetesRtPanel ) ;
    const $kubeRtSummaryCategory = $( "#metric-summary-category", $kubernetesRtPanel ) ;
    const $kubeRtDetailCategory = $( "#metric-detail-category", $kubernetesRtPanel ) ;
    const $kubeNodeScroll = $( "#node-table-height", $kubernetesRtPanel ) ;
    let _latestKubernetesReport ;

    let _kubeRtFilterTimer ;




    const $appPanel = $( "#performance-tab-app", $performanceTab ) ;
    const $appTrends = $( ".app-trends", $appPanel ) ;
    const $appTrendFilter = $( "#app-trend-filter", $appPanel ) ;
    let _appTrendFilterTimer ;
    const $appDays = $( "select.trend-time", $appPanel ) ;
    const $appByHost = $( "input.by-host", $appPanel ) ;
    const $appSlideShow = $( "input.slide-show", $appPanel ) ;

    const $servicePanel = $( "#performance-tab-service", $performanceTab ) ;
    const $serviceTrends = $( ".service-trends", $servicePanel ) ;
    const $serviceDays = $( "select.trend-time", $servicePanel ) ;
    const $serviceByService = $( "input.by-host", $servicePanel ) ;
    const $serviceSlideShow = $( "input.slide-show", $servicePanel ) ;


    const $hostPanel = $( "#performance-tab-host", $performanceTab ) ;
    const $hostTrends = $( ".host-trends", $hostPanel ) ;
    const $hostDays = $( "select.trend-time", $hostPanel ) ;
    const $hostByHost = $( "input.by-host", $hostPanel ) ;
    const $hostSlideShow = $( "input.slide-show", $hostPanel ) ;


    const $infraPanel = $( "#performance-tab-infra", $performanceTab ) ;
    const $infraTrends = $( ".infra-trends", $infraPanel ) ;
    const $infraDays = $( "select.trend-time", $infraPanel ) ;
    const $infraByHost = $( "input.by-host", $infraPanel ) ;
    const $infraSlideShow = $( "input.slide-show", $infraPanel ) ;


    const $dockerPanel = $( "#performance-tab-docker", $performanceTab ) ;
    const $dockerTrends = $( ".docker-trends", $dockerPanel ) ;
    const $dockerDays = $( "select.trend-time", $dockerPanel ) ;
    const $dockerByHost = $( "input.by-host", $dockerPanel ) ;
    const $dockerSlideShow = $( "input.slide-show", $dockerPanel ) ;


    const $kubeletPanel = $( "#performance-tab-kubelet", $performanceTab ) ;
    const $kubeletTrends = $( ".kubelet-trends", $kubeletPanel ) ;
    const $kubeletDays = $( "select.trend-time", $kubeletPanel ) ;
    const $kubeletByHost = $( "input.by-host", $kubeletPanel ) ;
    const $kubeletSlideShow = $( "input.slide-show", $kubeletPanel ) ;


    let csapUser = new CsapUser() ;

    const navigationOperations = {
        "health-reports": health.show,
        "alerts": refreshStatus,
        "events": events.show,
        "event-details": eventDetails.show,
        "infra": showInfraTrends,
        "app": showAppTrends,
        "host": showHostTrends,
        "service": showServiceTrends,
        "docker": showDockerTrends,
        "kubelet": showKubeletTrends,
        "activity-trends": showUserActivityTrends,
        "meters": function ( $menuContainer, forceHostRefresh, menuPath ) {
            return meters.show(
                    forceHostRefresh,
                    utils.getActiveProject(),
                    utils.getActiveProject( false ) ) ;
        },
        "kubernetes-rt": showKubernetesRealTime
    } ;

    return {

        initialize: function () {
            initialize() ;
        },

        refreshStatus: function ( forceHostRefresh ) {
            return refreshStatus( null, forceHostRefresh ) ;
        },

        reDraw: function ( ) {
            $trendTime.filter( ":visible" ).trigger( "change" ) ;
        },

        show: function ( $menuContainer, forceHostRefresh, menuPath ) {

            let activeMenuId = $menuContainer.attr( "id" ) ;

            console.log( `active menuPath: ${ menuPath }` ) ;

            let operation = navigationOperations[ menuPath ] ;

            let $menuLoaded ;

            if ( operation ) {
                $menuLoaded = operation( $menuContainer, forceHostRefresh, menuPath ) ;

            } else {
                console.warn( ` no operation for ${ menuPath }` ) ;
            }

            return $menuLoaded ;

        }

    } ;

    function initialize() {



        $trendTop.change( function () {
            $trendTime.filter( ":visible" ).trigger( "change" ) ;
        } ) ;

        $trendTime.change( function () {
            console.log( ` syncing time selection` ) ;
            let newDayCount = $( this ).val() ;
            let currentServiceDayCount = $serviceDays.val() ;
            $trendTime.val( newDayCount ) ;

            if ( newDayCount > 0 ) {
                $serviceDays.val( newDayCount )
            } else {
                $serviceDays.val( currentServiceDayCount ) ;
            }
        } ) ;

        $appTrendFilter.off().keyup( function () {
            console.log( "Applying trend filter" ) ;
            clearTimeout( _appTrendFilterTimer ) ;
            let trendFilter = $appTrendFilter.val() ;
            _appTrendFilterTimer = setTimeout( function () {
                if ( trendFilter.length > 0 ) {
                    $( "div.the-plot", $appPanel ).hide() ;
                    $( "div.the-plot", $appPanel ).each( function () {
                        let $title = $( ".jqplot-title", $( this ) ) ;
                        if ( $title.text().toLowerCase().includes( trendFilter.toLowerCase() ) ) {
                            $( this ).show() ;
                        }
                    } )
                } else {
                    $( "div.the-plot", $appPanel ).show() ;
                    if ( !$appSlideShow.is( ":checked" ) ) {
                        $( "div#app-master-slide", $appPanel ).hide() ;
                    }
                }
            }, 200 ) ;
        } ) ;

        $appDays.change( showAppTrends ) ;
        $infraDays.change( showInfraTrends ) ;
        $( "input.show-by-env", $performanceTab ).change( function () {
            showInfraTrends() ;
        } ) ;
        $serviceDays.change( showServiceTrends ) ;
        $hostDays.change( showHostTrends ) ;
        $dockerDays.change( showDockerTrends ) ;
        $kubeletDays.change( showKubeletTrends ) ;
        $activityDays.change( showUserActivityTrends ) ;
        $alertWeeks.change( showAlertSummaryGraph ) ;

        meters.initialize() ;

        health.initialize() ;

        events.initialize() ;

        $kubernetesRtNodeTableBody.parent().tablesorter( {
            sortList: [ [ 1, 1 ] ],
            theme: 'csapSummary',
//            widgets: [ 'uitheme', 'default', 'filter', 'scroller' ],
//            widgetOptions: {
//                scroller_height: 100,
//                // scroll tbody to top after sorting
//                scroller_upAfterSort: true,
//                // pop table header into view while scrolling up the page
//                scroller_jumpToHeader: true,
//                // In tablesorter v2.19.0 the scroll bar width is auto-detected
//                // add a value here to override the auto-detected setting
//                scroller_barWidth: null
//                        // scroll_idPrefix was removed in v2.18.0
//                        // scroller_idPrefix : 's_'
//            }
        } ) ;

        $kubernetesRtContainerTableBody.parent().tablesorter( {
            sortList: [ [ 1, 1 ] ],
            theme: 'csapSummary'
        } ) ;

        $kubernetesRtPodTableBody.parent().tablesorter( {
            sortList: [ [ 2, 1 ] ],
            theme: 'csapSummary'
        } ) ;

        let $hostServiceNames = utils.getServiceSelector() ;
        $hostServiceNames.change( function () {
            $( "span", $hostServiceNames.parent() ).text( $( this ).val() ) ;
        } ) ;
    }

    function refreshStatus( $menuContainer, forceHostRefresh ) {
        $contentLoaded = new $.Deferred() ;
        getStatusReport( $menuContainer, forceHostRefresh ) ;
        return $contentLoaded ;
    }


    function getStatusReport( $displayContainer, forceHostRefresh ) {


        let parameters = {
            project: utils.getActiveProject(),
            blocking: forceHostRefresh
        } ;

        if ( utils.isObject( forceHostRefresh ) ) {
            console.trace( "invalid force parameter" ) ;
            parameters = {
                project: utils.getActiveProject(),
                blocking: false
            } ;
        }
        console.debug( `getStatusReport() forceHostRefresh: ${forceHostRefresh}` ) ;


        $.getJSON(
                APP_BROWSER_URL + "/status-report",
                parameters )

                .done( function ( statusReport ) {

                    utils.setStatusReport( statusReport ) ;

                    kubernetesServiceName = statusReport["kubernetes-service"] ;

                    if ( statusReport.serviceIdMapping ) {
                        addServiceNames( statusReport.serviceIdMapping ) ;
                    }

                    if ( statusReport.servicesWithHealth ) {
                        addServiceHealthNames( statusReport.servicesWithHealth ) ;
                    }

                    if ( statusReport.kubernetesNodes === 0 ) {
                        $kubernetesNav.hide() ;
                    } else {
                        $kubernetesNav.show() ;
                        utils.navigationCount( "#kevent-count", statusReport.kubernetesEvents, 500 ) ;
                        utils.navigationCount( "#pod-count", statusReport.podCount, 5000 ) ;
                        utils.navigationCount( "#node-count", statusReport.kubernetesNodes, 5000 ) ;
                        utils.navigationCount( "#volume-count", statusReport.volumeCount, 5000 ) ;

                    }

                    utils.navigationCount( "#host-count", statusReport.hosts ) ;
                    if ( statusReport.cpuCount ) {
                        // Agent dashboard - update settings
                        let $loadCountIndicator = utils.navigationCount( "#cpu-load-count", statusReport.cpuLoad, statusReport.cpuCount - 1 ) ;
                        $loadCountIndicator.attr( "title",
                                `  cpu load: ${ statusReport.cpuLoad }`
                                + `\ncpu count: ${ statusReport.cpuCount }` ) ;
                        utils.findNavigation( "#hosts-tab .tab-primary span:first-child()" ).text( "Host" ) ;
                        utils.navigationCount( "#host-count", statusReport.cpu, 60, "%" ) ;
                    }
                    utils.navigationCount( "#service-count", statusReport.services ) ;

                    $( "#bar-backlog" ).text( statusReport.deploymentBacklog ) ;
                    $( "#backlog-count" ).text( statusReport.deploymentBacklog )
                    if ( statusReport.deploymentBacklog > 0 ) {
                        $( "#backlog-count" ).show() ;
                        utils.flash( $( "#backlog-count" ), true ) ;
                        $( "#bar-backlog" ).parent().addClass( "bar-high" ) ;
                    } else {
                        $( "#backlog-count" ).hide() ;
                        $( "#bar-backlog" ).parent().removeClass( "bar-high" ) ;
                    }

                    updateUsers( statusReport.users ) ;
                    $( "#last-operation" ).text( statusReport.lastOp ) ;
                    $( "#last-operation" ).attr( "title", statusReport.lastOp ) ;

                    let $alertsList = buildAlertsList( statusReport ) ;
                    if ( $displayContainer ) {
                        // only update ui when tab is active
                        $( ".alerts", $alertsPanel )
                                .empty()
                                .append( $alertsList ) ;
                        showAlertSummaryGraph() ;
                    }
                    console.debug( `getStatusReport() : backlog: ${statusReport.deploymentBacklog}` ) ;
                    $contentLoaded.resolve() ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( `retrieving alerts '${textStatus}'`, errorThrown ) ;
                } ) ;
    }

    function addServiceNames( serviceIdMapping ) {

        //console.log( `checking: `, serviceIdMapping )

        let $hostServiceNames = utils.getServiceSelector() ;

        let currentServiceCount = $( "option", $hostServiceNames ).length ;
        if ( ( currentServiceCount - 1 ) !== Object.keys( serviceIdMapping ).length ) {

            $hostServiceNames.empty() ;

            jQuery( '<option/>', {
                text: utils.agentServiceSelect()
            } ).appendTo( $hostServiceNames ) ;

            // statusReport.serviceNames.sort()
            for ( let name of utils.keysSortedCaseIgnored( serviceIdMapping ) ) {
                let $option = jQuery( '<option/>', {
                    text: name,
                    "data-service": serviceIdMapping[ name ]
                } ).appendTo( $hostServiceNames ) ;
            }


            if ( utils.getDefaultService() ) {
                $hostServiceNames.val( utils.getDefaultService() ) ;
                $( "span", $hostServiceNames.parent() ).text( utils.getDefaultService() ) ;
            }
        }
    }

    function addServiceHealthNames( serviceNames ) {

        let currentServiceCount = $( "option", $healthServiceNames ).length ;
        if ( ( currentServiceCount - 1 ) !== serviceNames.length ) {

            $healthServiceNames.empty() ;

            jQuery( '<option/>', {
                html: "&nbsp;",
                value: 0
            } ).appendTo( $healthServiceNames ) ;

            for ( let name of serviceNames.sort() ) {
                jQuery( '<option/>', {
                    text: name
                } ).appendTo( $healthServiceNames ) ;
            }

            $healthServiceNames.off().change( function () {
                utils.getServiceSelector()
                        .val( $( this ).val() )
                        .trigger( "change" ) ;
                $healthServiceNames.val( 0 ) ;
            } ) ;


        }
    }


    function showAppTrends() {

        summaryTrends.cleanUp() ;

        let numberOfDays = $appDays.val() * 7 ;
        if ( $appDays.val() < 1 ) {
            numberOfDays = $appDays.val() ;
        }

        $appByHost.off().change( showAppTrends ) ;
        $appSlideShow.off().change( showAppTrends ) ;

        summaryTrends.showApplication(
                $appTrends,
                numberOfDays,
                $appByHost.is( ":checked" ),
                $appSlideShow ) ;
    }


    function showInfraTrends() {

        summaryTrends.cleanUp() ;

        let numberOfDays = $infraDays.val() * 7 ;
        if ( $infraDays.val() < 1 ) {
            numberOfDays = $infraDays.val() ;
        }

        $infraByHost.off().change( showInfraTrends ) ;
        $infraSlideShow.off().change( function () {
            if ( $infraSlideShow.is( ":checked" ) ) {
                $( ".comment", $infraPanel ).hide() ;
            } else {
                $( ".comment", $infraPanel ).show() ;
            }

            showInfraTrends() ;
        } ) ;

        summaryTrends.showTrend( $( "#infra-cpu", $infraTrends ),
                "CPU Test",
                $infraByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalCpuTestTime",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#infra-disk", $infraTrends ),
                "Disk Test",
                $infraByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalDiskTestTime",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#agent-ms", $infraTrends ),
                "Host Response (ms)",
                $infraByHost.is( ":checked" ),
                2,
                "application",
                numberOfDays,
                "AdminPingsMeanMs",
                "csap-agent",
                "numberOfSamples" ) ;

    }

    function showHostTrends() {

        summaryTrends.cleanUp() ;

        let numberOfDays = $hostDays.val() * 7 ;
        if ( $hostDays.val() < 1 ) {
            numberOfDays = $hostDays.val() ;
        }
        let $containers = $( ".the-plot", $hostTrends ) ;
        $containers.empty() ;

        $hostByHost.off().change( showHostTrends ) ;
        $hostSlideShow.off().change( showHostTrends ) ;

        summaryTrends.showTrend( $( "#host-alerts", $hostTrends ),
                "Alerts",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "alertsCount",
                null,
                null ) ;

        summaryTrends.showTrend( $( "#host-cores", $hostTrends ),
                "CPU Cores Active",
                $hostByHost.is( ":checked" ),
                4,
                "custom/core",
                numberOfDays,
                "coresUsed",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-load", $hostTrends ),
                "CPU Load",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalLoad",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-threads", $hostTrends ),
                "Threads (1000's)",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "threadsTotal",
                "",
                "numberOfSamples,1000" ) ;

        summaryTrends.showTrend( $( "#host-files", $hostTrends ),
                "Files (1000's)",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalFiles",
                "",
                "numberOfSamples,1000" ) ;

        summaryTrends.showTrend( $( "#host-sockets-active", $hostTrends ),
                "Network Connections: Active",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "socketTotal",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-sockets-wait", $hostTrends ),
                "Network Connections: Wait",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "socketWaitTotal",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-sockets-time-wait", $hostTrends ),
                "Network Connections: Time Wait",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "socketTimeWaitTotal",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-network-received", $hostTrends ),
                "Network: Received",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalNetworkReceived",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-network-transmitted", $hostTrends ),
                "Network: Transmitted",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalNetworkTransmitted",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-io-reads", $hostTrends ),
                "IO: Reads",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalIoReads",
                "",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#host-io-writes", $hostTrends ),
                "IO: Writes",
                $hostByHost.is( ":checked" ),
                4,
                "host",
                numberOfDays,
                "totalIoWrites",
                "",
                "numberOfSamples" ) ;
    }

    function showServiceTrends() {

        summaryTrends.cleanUp() ;

        let numberOfDays = $serviceDays.val() * 7 ;
        if ( $serviceDays.val() < 1 ) {
            numberOfDays = $serviceDays.val() ;
        }
        $serviceByService.off().change( showServiceTrends ) ;
        let $containers = $( ".the-plot", $serviceTrends ) ;
        $containers.empty() ;

        $serviceSlideShow.off().change( showServiceTrends ) ;

        let byService = $serviceByService.is( ":checked" ) ;

        let units = ",1000" ;
        let desc = "(1000's)"
        if ( byService ) {
            units = "" ;
            desc = "" ;
        }

        summaryTrends.showTrend( $( "#service-cpu", $serviceTrends ),
                `CPU ${desc}`,
                byService,
                3,
                "os-process",
                numberOfDays,
                "topCpu",
                null,
                `numberOfSamples${units}` ) ;

        summaryTrends.showTrend( $( "#service-threads", $serviceTrends ),
                `Threads ${desc}`,
                byService,
                3,
                "os-process",
                numberOfDays,
                "threadCount",
                null,
                `numberOfSamples${units}` ) ;

        summaryTrends.showTrend( $( "#service-sockets", $serviceTrends ),
                "Connections",
                byService,
                3,
                "os-process",
                numberOfDays,
                "socketCount",
                null,
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#service-memory", $serviceTrends ),
                "Memory (Gb)",
                byService,
                3,
                "os-process",
                numberOfDays,
                "rssMemory",
                null,
                `numberOfSamples,1000` ) ;

        summaryTrends.showTrend( $( "#service-files", $serviceTrends ),
                `Files ${desc}`,
                byService,
                3,
                "os-process",
                numberOfDays,
                "fileCount",
                null,
                `numberOfSamples${units}` ) ;

        summaryTrends.showTrend( $( "#service-disk", $serviceTrends ),
                "Disk Space (Gb)",
                byService,
                3,
                "os-process",
                numberOfDays,
                "diskUtil",
                null,
                `numberOfSamples,1000` ) ;

        summaryTrends.showTrend( $( "#service-reads", $serviceTrends ),
                "Disk Reads (Kb/s)",
                byService,
                3,
                "os-process",
                numberOfDays,
                "diskReadKb",
                null,
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#service-writes", $serviceTrends ),
                "Disk Writes (Kb/s)",
                byService,
                3,
                "os-process",
                numberOfDays,
                "diskWriteKb",
                null,
                "numberOfSamples" ) ;

    }

    function showDockerTrends() {

        summaryTrends.cleanUp() ;

        let numberOfDays = $dockerDays.val() * 7 ;
        if ( $dockerDays.val() < 1 ) {
            numberOfDays = $dockerDays.val() ;
        }
        let $containers = $( ".the-plot", $dockerTrends ) ;
        $containers.empty() ;

        $dockerByHost.off().change( showDockerTrends ) ;
        $dockerSlideShow.off().change( showDockerTrends ) ;

        summaryTrends.showTrend( $( "#is-healthy", $dockerTrends ),
                "Docker Healthy Hosts",
                $dockerByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "isHealthy",
                "docker",
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#running-containers", $dockerTrends ),
                "Running Containers",
                $dockerByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "containerRunning",
                "docker",
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#all-containers", $dockerTrends ),
                "All Containers",
                $dockerByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "containerCount",
                "docker",
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#volumes", $dockerTrends ),
                "Volumes",
                $dockerByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "volumeCount",
                "docker",
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#images", $dockerTrends ),
                "Images",
                $dockerByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "imageCount",
                "docker",
                "numberOfSamples" ) ;
    }

    function showKubeletTrends() {

        summaryTrends.cleanUp() ;

        let numberOfDays = $kubeletDays.val() * 7 ;
        if ( $kubeletDays.val() < 1 ) {
            numberOfDays = $kubeletDays.val() ;
        }
        let $containers = $( ".the-plot", $kubeletTrends ) ;
        $containers.empty() ;

        $kubeletByHost.off().change( showKubeletTrends ) ;
        $kubeletSlideShow.off().change( showKubeletTrends ) ;

        summaryTrends.showTrend( $( "#is-healthy", $kubeletTrends ),
                "Kubernetes Healthy Hosts",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "isHealthy",
                kubernetesServiceName,
                "numberOfSamples" ) ;

        summaryTrends.showTrend( $( "#event-count", $kubeletTrends ),
                "Event Count",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "eventCount",
                kubernetesServiceName,
                "numberOfSamples" ) ;




        summaryTrends.showTrend(
                $( "#node-cores", $kubeletTrends ),
                "Node Cores",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "nodeCores",
                kubernetesServiceName,
                "numberOfSamples" )

        summaryTrends.showTrend(
                $( "#node-memory", $kubeletTrends ),
                "Node Memory (GB)",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "nodeMemory",
                kubernetesServiceName,
                "numberOfSamples" ) ; // 1024 or 1

        summaryTrends.showTrend( $( "#running-pods", $kubeletTrends ),
                "Pods Running",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "podRunningCount",
                kubernetesServiceName,
                "numberOfSamples" ) ;



        summaryTrends.showTrend(
                $( "#resources-cores-available", $kubeletTrends ),
                "Resources: Cores Capacity",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "capacityCores",
                kubernetesServiceName,
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#resources-cores-requested", $kubeletTrends ),
                "Resources: Requested Cpu Cores",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "requestedCores",
                kubernetesServiceName,
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#resources-coresPercent-requested", $kubeletTrends ),
                "Resources: Requested Cores %",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "requestedCores",
                kubernetesServiceName,
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#resources-cores-limits", $kubeletTrends ),
                "Resources: Limits Cores",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "limitsCores",
                kubernetesServiceName,
                "numberOfSamples" ) ;



        summaryTrends.showTrend(
                $( "#resources-memory-available", $kubeletTrends ),
                "Resources: Capacity Memory Gb",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "capacityMemory",
                kubernetesServiceName,
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#resources-memory-requested", $kubeletTrends ),
                "Resources: Requested Memory Gb",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "requestedMemory",
                kubernetesServiceName,
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#resources-memoryPercent-requested", $kubeletTrends ),
                "Resources: Requested Memory %",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "requestedMemoryPercent",
                kubernetesServiceName,
                "numberOfSamples" ) ;

        summaryTrends.showTrend(
                $( "#resources-memory-limits", $kubeletTrends ),
                "Resources: Limits Memory GB",
                $kubeletByHost.is( ":checked" ),
                4,
                "application",
                numberOfDays,
                "limitsMemory",
                kubernetesServiceName,
                "numberOfSamples" ) ;


    }

    function showUserActivityTrends() {

        let numberOfDays = $activityDays.val() * 7 ;
        let $container = $( ".the-plot", $activityTrends ) ;
        ;

        $activityByHost.off().change( showUserActivityTrends ) ;

        summaryTrends.showUserActivity( $container, numberOfDays, $activityByHost.is( ":checked" ), 1 ) ;
    }

    function showAlertSummaryGraph() {

        let numberOfDays = $alertWeeks.val() * 7 ;
        let $container = $( ".the-plot", $alertTrends ) ;


        $alertByHost.off().change( showAlertSummaryGraph ) ;

        summaryTrends.showHealth( $container, numberOfDays, $alertByHost.is( ":checked" ) ) ;
    }



    function showKubernetesRealTime( $menuContainer, forceHostRefresh, menuPath ) {
        let parameters = {
            project: utils.getActiveProject(),
            blocking: forceHostRefresh
        } ;
        $contentLoaded = new $.Deferred() ;

        utils.loading( "Running Kubernetes Real Time Report" ) ;

        $.getJSON(
                APP_BROWSER_URL + "/kubernetes/realtime",
                parameters )

                .done( function ( kubernetesReport ) {

                    console.log( `kubernetesReport`, kubernetesReport ) ;
                    _latestKubernetesReport = kubernetesReport ;
                    buildKubernetesRealTimeReport( kubernetesReport ) ;
                    $contentLoaded.resolve() ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( `retrieving alerts '${textStatus}'`, errorThrown ) ;
                } ) ;

        return $contentLoaded ;

    }

    function buildFilterCell( item, style ) {

        let $cell = jQuery( '<td/>', {
            class: style
        } ) ;


        let $container = jQuery( '<div/>', {
            text: item,
            class: "flex-container"
        } ).appendTo( $cell ) ;

        jQuery( '<button/>', {
            title: "click to apply as filter",
            class: "csap-icon csap-filter"
        } )
                .prependTo( $container )
                .click( function () {
                    $kubeRtPodFilter.val( item ) ;
                    if ( style === `namespace` ) {
                        $kubeRtSummaryCategory.val( "Namespace" ) ;
                    }
                    $kubeRtSummaryCategory.trigger( "change" ) ;
                    // $kubeRtPodFilter.trigger( "keyup" ) ;
                    return false ;
                } ) ;


        return  $cell ;
    }



    function buildCoresCell( cores ) {

        let coresDesc = cores ;
        if ( cores != 0 ) {
            coresDesc = cores.toFixed( 2 )
        }

        let $cell = jQuery( '<td/>', {
            class: "numeric",
            text: coresDesc
        } ) ;

        return $cell ;
    }

    function buildSimpleCell( item, style ) {

        let $cell = jQuery( '<td/>', {
            class: style,
            text: item
        } ) ;

//        jQuery( '<div/>', {
//            text: item,
//            class: "flex-container"
//        } ).appendTo( $cell ) ;

        return $cell ;
    }

    function buildFlexCell( item, style ) {

        let $cell = jQuery( '<td/>', {
            class: style
        } ) ;

        jQuery( '<div/>', {
            text: item,
            class: "flex-container"
        } ).appendTo( $cell ) ;

        return $cell ;
    }

    function applyK8TopFilter( $tableBody ) {

        let $rows = $( 'tr', $tableBody ) ;

        let filterSpaceSeparated = $kubeRtPodFilter.val() ;

        console.log( "filter", filterSpaceSeparated ) ;

        if ( filterSpaceSeparated.length > 0 ) {


            $kubeRtClearPodFilter.css( "visibility", "visible" ) ;
            $rows.hide() ;
            for ( let filter of filterSpaceSeparated.split( " " ) ) {
                $( 'td:contains("' + filter + '")', $rows ).parent().show() ;
            }
        } else {
            $rows.show() ;
            $kubeRtClearPodFilter.css( "visibility", "hidden" ) ;
        }
    }

    function buildKubernetesRealTimeReport( kubernetesReport ) {


        $kubernetesRtNodeTableBody.empty() ;

        if ( kubernetesReport.error ) {
            let $row = jQuery( '<tr/>', { } )
                    .appendTo( $kubernetesRtNodeTableBody ) ;
            jQuery( '<td/>', {
                text: `Kubernetes metrics not available`,
                colspan: 99
            } ).appendTo( $row ) ;

            $kubernetesRtNodeTableBody.trigger( "update", "resort" ) ;
            return ;
        }



        let sortedNodeNames = utils.keysSortedCaseIgnored( kubernetesReport.nodes ) ;
        console.log( `buildKubernetesRealTimeReport() ${sortedNodeNames}` ) ;
        for ( let nodeName of sortedNodeNames ) {

            let nodeDef = kubernetesReport.nodes[nodeName] ;
            let $row = jQuery( '<tr/>', { } )
                    .appendTo( $kubernetesRtNodeTableBody ) ;

            let $nodeCell = buildSimpleCell( nodeName, "" ) ;
            $row.append( $nodeCell ) ;
            $row.append( buildCoresCell( nodeDef.cores ) ) ;

            let memoryInMb = Math.round( parseFloat( nodeDef.memoryGb ) * 1024 ) ;
            $row.append( utils.buildMemoryCell( memoryInMb * 1024 * 1024 ) ) ;
            //$row.append( nodeDef.memoryGb ) ;

            $row.append( buildSimpleCell( nodeDef.podsRunning, "numeric" ) ) ;
            $row.append( buildSimpleCell( nodeDef.podsNotRunning, "numeric" ) ) ;

            jQuery( '<button/>', {
                title: "Open Host Dashboard",
                class: "csap-icon csap-window"
            } )
                    .prependTo( $( "div", $nodeCell ) )
                    .click( function () {
                        //kubernetes.browseToPod( podName, podDef.namespace ) ;
                        utils.openAgentWindow( nodeName, "/app-browser#agent-tab,system" ) ;
                    } ) ;

        }


        //
        //  Container or namespace
        //
        $kubernetesRtNodeTableBody.trigger( "update", "resort" ) ;
        $kubernetesRtContainerTableBody.empty() ;

        let targetCategory = kubernetesReport.containers ;

        let isNamespaceMode = ( $kubeRtSummaryCategory.val() === "Namespace" ) ;
        if ( isNamespaceMode ) {
            targetCategory = kubernetesReport.namespaces ;
        }

        for ( let categoryName in targetCategory ) {

            let containerDef = targetCategory[categoryName] ;

            let $row = jQuery( '<tr/>', { } ) ;
            $kubernetesRtContainerTableBody.append( $row ) ;

            let $containerCell = buildFilterCell( categoryName, "" ) ;
            $row.append( $containerCell ) ;
            $row.append( buildCoresCell( containerDef.cores ) ) ;

            $row.append( utils.buildMemoryCell( containerDef.memoryInMb * 1024 * 1024 ) ) ;
            // $row.append( buildSimpleCell( containerDef.memoryInMb, "num" ) ) ;

            let containerCount = containerDef.containerCount ;
            let containerDesc = containerDef.containerCount ;

            if ( isNamespaceMode
                    && containerDef.containerCount !== containerDef.pods ) {

                containerDesc = `${containerDef.containerCount}, ${containerDef.pods} pods` ;

            }

            if ( containerCount && containerCount > 1 ) {
                jQuery( '<span/>', {
                    title: "number of containers and pods",
                    class: "flex-right-info",
                    text: containerDesc
                } )
                        .appendTo( $( "div", $containerCell ) ) ;
            }


        }

        $kubernetesRtContainerTableBody.trigger( "update", "resort" ) ;

        applyK8TopFilter( $kubernetesRtContainerTableBody ) ;


        $kubernetesRtPodTableBody.empty() ;

        let isPodMode = ( $kubeRtDetailCategory.val() === "Pod" ) ;

        let detailItems = kubernetesReport.containerNamespace ;
        if ( isPodMode ) {
            detailItems = new Array() ;
            for ( let podName in kubernetesReport.pods ) {
                let detailItem = kubernetesReport.pods[podName] ;
                detailItem.name = podName ;
                detailItems.push( detailItem ) ;
            }
        }

        //for ( let podName in kubernetesReport.pods ) {
        for ( let detailItem of detailItems ) {
            //let detailItem = kubernetesReport.pods[podName] ;

            let $row = jQuery( '<tr/>', { } ) ;
            $kubernetesRtPodTableBody.append( $row ) ;

            let $nameCell = buildFlexCell( detailItem.name, "" ) ;
            $row.append( $nameCell ) ;

            let $namespaceCell = buildFilterCell( detailItem.namespace, "namespace" ) ;
            if ( !isPodMode
                    && detailItem.containerCount
                    && detailItem.containerCount > 1 ) {
                jQuery( '<span/>', {
                    title: "containers",
                    class: "flex-right-info",
                    text: detailItem.containerCount
                } ).appendTo( $( ".flex-container", $namespaceCell ) ) ;
            }


            jQuery( '<button/>', {
                title: "view in deployment/pod browser (navigate back using browser back button).",
                class: "csap-icon csap-go  right-2"
            } )
                    .prependTo( $namespaceCell )
                    .click( function () {
                        kubernetes.browsePodNamespace( detailItem.namespace ) ;
                    } ) ;

            $row.append( $namespaceCell ) ;
            $row.append( buildCoresCell( detailItem.cores ) ) ;
            $row.append( utils.buildMemoryCell( detailItem.memoryInMb * 1024 * 1024 ) ) ;

            let containerNames = detailItem.name ;
            if ( Array.isArray( detailItem.containers ) ) {
                containerNames = detailItem.containers.join( " " ) ;
            }

            jQuery( '<span/>', {
                title: "container names in pod",
                class: "container-name",
                text: containerNames
            } )
                    .appendTo( $( "div", $nameCell ) ) ;

            jQuery( '<button/>', {
                title: `filter using pod container names: ${ containerNames }`,
                class: "csap-icon csap-filter"
            } )
                    .prependTo( $( "div", $nameCell ) )
                    .click( function () {
                        // kubernetes.browseToPod( podName, podDef.namespace ) ;
                        $kubeRtPodFilter.val( containerNames ) ;
                        $kubeRtSummaryCategory.val( "Container" ) ;
                        $kubeRtSummaryCategory.trigger( "change" ) ;
                    } ) ;
            if ( isPodMode ) {
                jQuery( '<button/>', {
                    title: "view in deployment/pod browser (navigate back using browser back button).",
                    class: "csap-icon csap-go right-2"
                } )
                        .appendTo( $( "div", $nameCell ) )
                        .click( function () {
                            kubernetes.browseToPod( detailItem.name, detailItem.namespace ) ;
                        } ) ;
            }

        }

        $kubernetesRtPodTableBody.trigger( "update", "resort" ) ;

        applyK8TopFilter( $kubernetesRtPodTableBody ) ;

        $kubeRtPodFilter.off().keyup( function () {
            console.log( "Applying cluster filter" ) ;
            clearTimeout( _kubeRtFilterTimer ) ;
            _kubeRtFilterTimer = setTimeout( function () {
                applyK8TopFilter( $kubernetesRtContainerTableBody ) ;
                applyK8TopFilter( $kubernetesRtPodTableBody ) ;
            }, 200 ) ;
        } ) ;

        $kubeRtClearPodFilter.off().click( function () {
            $kubeRtPodFilter.val( "" ) ;
            $kubeRtPodFilter.trigger( "keyup" ) ;
        } ) ;

        $kubeNodeScroll.off().change( function () {
            $kubeNodeScroll.parent().css( "max-height", $( this ).val() ) ;
        } ) ;

        $kubeRtSummaryCategory.off().change( function ( ) {
            buildKubernetesRealTimeReport( _latestKubernetesReport ) ;
        } ) ;
        $kubeRtDetailCategory.off().change( function ( ) {
            buildKubernetesRealTimeReport( _latestKubernetesReport ) ;
        } ) ;
    }



    function updateUsers( users ) {
        let $users = $( '#users' ) ;
        $users.empty() ;
        let allUsers = "" ;
        for ( let user of users ) {
            for ( let i = 0 ; i < 1 ; i++ ) {
                allUsers += ` ${ user }` ;

                jQuery( '<span/>', {
                    id: 'user_' + user,
                    class: 'userids',
                    text: user
                } ).appendTo( $users ) ;

            }
        }

        csapUser.onHover( $( ".userids" ), 500 ) ;

        jQuery( '<span/>', {
            title: allUsers,
            text: "users: "
        } ).prependTo( $users ) ;
    }


    function buildAlertsList( statusReport ) {


        let errors = statusReport.alerts ;
        let hostSessionReport = statusReport["host-sessions"] ;

        //console.log(`Processing errors`, errors) ;


        let $errorList = jQuery( '<div/>', { class: "alert-listing" } ) ;
        let errorItemNumber = 1 ;

        let errorGroupContainers = new Object() ;
        let errorGroupHostNames = new Object() ;

        let errorCount = 0 ;
        if ( errors.length !== 0 ) {

            for ( let errorRawMessage of errors ) {

                //console.debug( "error: ", errorRawMessage ) ;
                errorCount++ ;

                let firstWordInErrorMessage = errorRawMessage.split( " " )[0] ;
                if ( firstWordInErrorMessage.contains( ":" ) ) {
                    try {
                        let hostName = firstWordInErrorMessage.substring( 0, firstWordInErrorMessage.length - 1 ) ;
                        let targetMessage = errorRawMessage.substring( errorRawMessage.indexOf( ":" ) + 1 ) ;

                        //console.debug( `${hostName} : targetMessage: ${ targetMessage }` ) ;

                        let healthReportToken = "service live:" ;

                        let $healthLink = null ;
                        if ( targetMessage.contains( healthReportToken ) ) {

                            let reportStartIndex = targetMessage.indexOf( healthReportToken ) ;
                            let healthUrl = targetMessage.substring( reportStartIndex + healthReportToken.length )
                            targetMessage = targetMessage.substring( 0, reportStartIndex ) ;

                            $healthLink = jQuery( '<a/>', {
                                target: "_blank",
                                title: "Open service live dashboard on " + hostName,
                                class: "csap-link healthReport",
                                href: healthUrl,
                                html: " service live."
                            } ) ;
                        }

                        let $hostPortalLink = utils.buildAgentLink( hostName ) ;
                        //
                        // Group errors by type. Eg the same error could be on multiple hosts
                        //
                        let first4words =  getWords( targetMessage, 5 ).trim() ;
                        let first3words =  getWords( targetMessage, 4 ).trim() ;
                        let first2words =  getWords( targetMessage, 2 ).trim() ;
                        let firstWord =  getWords( targetMessage, 2 ).trim() ;
                        let errorGroup =  first4words ;
                        if ( firstWord == "current") {
                            // host stats
                            errorGroup = first2words;
                        } else if ( firstWord.endsWith(":")) {
                            // service name:
                            // trim the colon and the last char in case of kubernetes
                            errorGroup = firstWord.substring(0, firstWord.length - 2)
                                    + first3words.substring(firstWord.length );
                        } 
                        
                        console.debug( `firstWord: '${ firstWord }' errorGroup: ${ errorGroup }`) ;
                        if ( errorGroupContainers[ errorGroup ] !== undefined ) {

                            let $existingItem = errorGroupContainers[ errorGroup ] ;
                            let $subDiv = jQuery( '<div/>', { class: "sub-alert" } ) ;
                            $existingItem.append( $subDiv ) ;
                            //$existingItem.append( $hostPortalLink ) ;
                            
                            
                            $subDiv.append( targetMessage ) ;
                            if ( $healthLink ) {
                                $subDiv.append( $healthLink ) ;
                            }
                            
                            
                            if ( errorGroupHostNames[ errorGroup ] !== hostName ) {
                                $subDiv.append(" (") ;
                                $subDiv.append($hostPortalLink) ;
                                $subDiv.append(") ") ;
                            }

                        } else {

                            $errorList.append( jQuery( '<span/>', {
                                text: errorItemNumber++ + ")"
                            } ) ) ;

                            $errorList.append( $hostPortalLink ) ;

                            let $note = jQuery( '<div/>', {
                                text: targetMessage
                            } ) ;
                            $errorList.append( $note ) ;

                            if ( $healthLink ) {
                                $note.append( $healthLink ) ;
                            }
                            errorGroupContainers[ errorGroup ] = $note ;
                            errorGroupHostNames[ errorGroup ] = hostName ;
                            //$errorList.append( $errorItem ) ;
                        }




                    } catch ( e ) {
                        console.error( `alertProcessing() Failed parsing error:  '${errorRawMessage}'`, e ) ;
                        $errorList.append( jQuery( '<span/>', {
                            text: errorItemNumber++ + ")"
                        } ) ) ;
                        $errorList.append( jQuery( '<span/>', {
                            text: "-"
                        } ) ) ;
                        $errorList.append( jQuery( '<span/>', {
                            text: errorRawMessage
                        } ) ) ;
                    }
                } else {
                        $errorList.append( jQuery( '<span/>', {
                            text: errorItemNumber++ + ")"
                        } ) ) ;
                        $errorList.append( jQuery( '<span/>', {
                            text: "-"
                        } ) ) ;
                        $errorList.append( jQuery( '<span/>', {
                            text: errorRawMessage
                        } ) ) ;
                }
            }
        }


        // vmsession reporting
        for ( let hostReport of hostSessionReport ) {
            let hostName = hostReport.name ;
            let hostLoginSessions = hostReport.sessions ;

            if ( hostLoginSessions ) {
                foundErrors = true ;

                for ( let hostLoginSession of hostLoginSessions ) {

                    errorCount++ ;
                    
                    
                        $errorList.append( jQuery( '<span/>', {
                            text: errorItemNumber++ + ")"
                        } ) ) ;
                        $errorList.append( utils.buildAgentLink( hostName ) ) ;
                        $errorList.append( jQuery( '<span/>', {
                            text:  " login:" + hostLoginSession
                        } ) ) ;
                    
//                    let $errorItem = jQuery( '<li/>', { class: "decimal" } ) ;
//                    $errorList.append( $errorItem ) ;
//
//                    $errorItem.append( utils.buildAgentLink( hostName ) ) ;
//                    $errorItem.append( " login:" + hostLoginSession ) ;

                }
            }
        }

        let $statusContainer = jQuery( '<div/>', {
            class: "csap-white"
        } ) ;

        $( "#bar-alerts" ).text( errorCount ) ;
        if ( errorCount > 0 ) {

            jQuery( '<span/>', {
                class: "status-red",
                text: `${ errorCount } Alerts`
            } ).appendTo( $statusContainer ) ;

            $statusContainer.append( $errorList ) ;

            $( ".alert-count" ).text( `${ errorCount }` ) ;
            utils.flash( $( ".alert-count" ), true ) ;
            $( "#bar-alerts" ).parent().addClass( "bar-high" ) ;
        } else {
            $( ".alert-count" ).empty() ;
            utils.flash( $( ".alert-count" ), false ) ;

            jQuery( '<span/>', {
                class: "status-green",
                text: "No active alerts"
            } ).appendTo( $statusContainer ) ;
        }

        return $statusContainer ;
    }


    function getWords( str, count ) {
        return str.split( /\s+/ ).slice( 0, count ).join( " " ) ;
    }

    function buildAlertsTable( alertsReport ) {

        let $tempDiv = jQuery( '<div/>', { } )
        let $alertTable = jQuery( '<table/>', {
            id: "service-alerts-table",
            class: "simple "
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

} ) ;