define( [ "services/kubernetes", "services/deployer", "browser/utils" ], function ( kubernetes, deployer, utils ) {


    console.log( "Module loaded" ) ;
    let osPanel, appPanel ;


    const UNREGISTERED_SERVICE = "unregistered" ;

    const $servicesContent = utils.findContent( "#services-tab-content" ) ;

    const $instancePanel = utils.findContent( "#services-tab-instances" ) ;

    const $tableOperations = utils.findContent( "#table-operations", $instancePanel ) ;
    const $clusterFilter = utils.findContent( "#instance-cluster-filter", $tableOperations ) ;
    let _lastCluster ;

    const $options = $( ".options", $instancePanel ) ;
    const $serviceDescription = $( "#service-description", $options ) ;
    const $lastRefreshed = $( ".last-refreshed", $options ) ;
    const $nameLabel = $( ".service-name", $options ) ;
    const $instancesPanel = $( "#instance-details", $instancePanel ) ;
    const $unregisteredTable = $( "table#unregistered", $instancesPanel ) ;
    const $unregisteredBody = $( "tbody", $unregisteredTable ) ;
    const $unregisteredTableHeader = $( "thead", $unregisteredTable ) ;
    const $detailsTable = $( "table#registered", $instancesPanel ) ;
    const $detailsBody = $( "tbody", $detailsTable ) ;
    const $detailsTableHeader = $( "thead", $detailsTable ) ;


    const $serviceSelection = $( "#service-selection", $instancePanel ) ;

    const $servicesTab = $( "#services-tab" ) ;
    const $serviceInstanceMenu = $( "#instances-for-service", $servicesTab ) ;
    const $serviceMenuItems = $( ".service-selected", $servicesTab ) ;



    const $jobContent = $( "#services-tab-jobs", $servicesContent ) ;

    const $jobListing = $( "#job-listing", $jobContent ) ;
    const $jobContainer = $( "#service-job-summary", $jobContent ) ;
    const $jobConfirm = $( "#service-job-confirm", $jobContent ) ;
    const $jobParameters = $( "#service-job-parameters", $jobConfirm ) ;


    let $contentLoaded = null ;

    let instanceCloseTimer = null ;
    const $instanceMenu = $( "#instance-actions-menu" ) ;

    let latest_instanceReport = null ;

    let isServiceSettingsLoaded = false ;


    let _refreshTimer ;

    return {

        initialize: function () {
            initialize() ;
        },

        selectService: function ( name, description ) {
            isServiceSettingsLoaded = false ;
            selectService( name, description ) ;
        },

        getSelectedService: function (  ) {
            return getSelectedService() ;
        },

        getSelectedHosts: function (  ) {
            return getSelectedHosts() ;
        },

        getAllRows: function () {
            return $( "tbody tr", $instancesPanel ) ;
        },

        getSelectedRows: function () {
            return $( "tbody tr.selected", $instancesPanel ) ;
        },

        getAllHosts: function (  ) {
            return getAllHosts() ;
        },

        isKubernetes: function (  ) {
            return latest_instanceReport.kubernetes ;
        },

        isKubernetesNamespaceMonitor: function (  ) {
            return utils.json( "dockerSettings.namespaceMonitor", latest_instanceReport ) ;
        },

        getLatestReport: function (  ) {
            return latest_instanceReport ;
        },

        getKubernetesMasters: function (  ) {
            let masters = null ;
            if ( latest_instanceReport
                    && latest_instanceReport.kubernetes ) {
                masters = utils.json( "instances.0.kubernetes-masters", latest_instanceReport ) ;
            }
            return masters ;
        },

        getSelectedContainers: function (  ) {
            return getSelectedContainers() ;
        },

        getAllContainers: function (  ) {
            return getAllContainers() ;
        },

        show: function ( $displayContainer, forceHostRefresh ) {
            $contentLoaded = new $.Deferred() ;
            getInstances( forceHostRefresh ) ;
            return $contentLoaded ;
        },

        showAnalytics: function ( $displayContainer, forceHostRefresh ) {

            if ( latest_instanceReport == null ) {
                console.log( `switching to service view to select, assuming page reload on metrics` ) ;
                setTimeout( function () {
                    utils.launchMenu( "services-tab,status" ) ;
                }, 500 )

                return ;
            }


            $contentLoaded = new $.Deferred() ;
            if ( !osPanel ) {
                require( [ "services/os-panel", "services/app-panel" ], function ( XosPanel, XappPanel ) {
                    osPanel = XosPanel ;
                    appPanel = XappPanel ;
                    showAnalytics() ;
                } ) ;
            } else {
                showAnalytics() ;
            }

            return $contentLoaded ;

        },

        showJobs: function () {

            buildJobsPanel( latest_instanceReport ) ;
            return null ; // resolves deferred
        }

    } ;

    function showAnalytics() {

        uiSettings.projectParam = utils.getActiveProject() ;

        console.log( `showAnalytics(): updated uiSettings: `, uiSettings ) ;

        let analyticsHosts = getSelectedHosts() ;
        if ( analyticsHosts.length === 0
                && latest_instanceReport.kubernetes ) {
            analyticsHosts = latest_instanceReport.envHosts.split( "," ) ;
            console.log( `kubernetes service, and no hosts selected: using envHosts to scan:`, analyticsHosts ) ;
        }

        osPanel.show(
                getSelectedService(),
                analyticsHosts,
                latest_instanceReport.serviceLimits ) ;

        appPanel.show(
                getSelectedService(),
                analyticsHosts,
                latest_instanceReport.performanceConfiguration,
                latest_instanceReport.javaLabels ) ;

        $contentLoaded.resolve() ;

    }

    function initialize() {


        $serviceSelection.click( function () {
            $serviceMenuItems.hide() ;
            $serviceSelection.data( "name", "" ) ;
            utils.launchMenu( "services-tab,status" ) ;
            return false ;
        } ) ;

        $( `button.csap-clear`, $instancePanel ).click( function () {
            $( "tr.selected", $detailsBody ).removeClass( "selected" ) ;
            $( "tr input.include-host", $detailsBody ).prop( "checked", false ) ;
            updateItemsSelectedMessage() ;
        } ) ;

        $( `button.csap-check`, $instancePanel ).click( function () {
            $( "tr", $detailsBody ).addClass( "selected" ) ;
            $( "tr input.include-host", $detailsBody ).prop( "checked", true ) ;
            updateItemsSelectedMessage() ;
        } ) ;


//        $( 'button.csap-history', $options ).click( function () {
//            let historyUrl = HISTORY_URL.substring( 0, HISTORY_URL.length - 1 ) + "service/" + getSelectedService() + "*&" ;
//            utils.launch( historyUrl ) ;
//        } ) ;

        $( "#k8-wide-view", $tableOperations ).change( function () {
            manageKubernetesColumnDisplay( latest_instanceReport ) ;
        } ) ;

        utils.addTableFilter( $( "#instance-filter", $tableOperations ), $instancesPanel ) ;

        $clusterFilter.change( function () {
            _lastCluster = $clusterFilter.val() ;
            filterInstances() ;
        } ) ;


        $( `button`, $options ).click( function () {

            showOperationDialog( $( this ) ) ;

        } ) ;

        $( `button.tool-menu`, $tableOperations ).click( showInstanceMenu ) ;
//        $( "button.tool-menu", "#table-operations" ).off().hover(
//                showInstanceMenu,
//                closeInstanceMenu ) ;

        $instanceMenu.hover( function () {
            clearTimeout( instanceCloseTimer ) ;
        }, closeInstanceMenu ) ;

        $( "#close-menu", $instancePanel ).click( function () {
            $instanceMenu.css( "opacity", "0.0" ) ;
            return false ;
        } ) ;

        $( "#instance-actions-menu button", $instancePanel ).click( function () {

            $instanceMenu.css( "opacity", "0.0" ) ;
            let rowId = $instanceMenu.data( "rowId" ) ;

            console.log( `menu select: rowId ${rowId} ` ) ;

            let $targetRows = $( "tr.selected", $detailsBody ) ;

            if ( rowId != "all-selected" ) {
                $targetRows = $( `#${ rowId }`, $detailsBody ) ;
            } else {
                console.log( "doing all selected" ) ;

            }
            toolSelected( $targetRows, $( this ) ) ;

        } ) ;
        $instanceMenu.hide() ;

        deployer.initialize( getInstances ) ;

    }

    function selectService( name, descriptionOrCluster ) {

        console.log( `selectService: ${ name }, ${descriptionOrCluster}` ) ;
        if ( name ) {

            $clusterFilter.parent().hide() ;

            $serviceSelection.text( name ) ;
            $serviceInstanceMenu.data( "name", name ) ;
            $( "#instance-name", $serviceInstanceMenu ).text( name ) ;
            $serviceMenuItems.css( "display", "flex" ) ;
            $( ".registered-only", $tableOperations ).show() ;
            $serviceDescription.show() ;
            $unregisteredTable.hide() ;

            $detailsTable.show() ;

            _lastCluster = "all" ;
            if ( name !== UNREGISTERED_SERVICE ) {
                // page reloads may not include this
                if ( descriptionOrCluster ) {
                    
                    _lastCluster = utils.getActiveEnvironment() + ":" + descriptionOrCluster ;

                }
            } else {

                $unregisteredTable.show() ;
                $serviceDescription.hide() ;
                $detailsTable.hide() ;
                $( ".registered-only", $tableOperations ).hide() ;
                $serviceMenuItems.each( function ( index, element ) {
                    let menuId = $( this ).data( "path" ) ;
                    if ( menuId != "logs"
                            && menuId != "instances" ) {
                        $( this ).hide() ;
                    }
                } )
                $( "[data-path=logs]", $serviceMenuItems ).css( "display", "none" ) ;
                $serviceSelection.text( descriptionOrCluster ) ;
                $( "#instance-name", $serviceInstanceMenu ).text( descriptionOrCluster ) ;
            }
            //$serviceMenuItems.show() ;
            $detailsBody.empty() ;
            $unregisteredBody.empty() ;
            utils.launchMenu( "services-tab,instances" ) ;
        } else {
            $serviceMenuItems.css( "display", "none" ) ;
        }

    }

    function getSelectedService() {
        let current = $serviceInstanceMenu.data( "name" ) ;
        //console.log(`getSelectedService() current: ${current} `) ;
        return current ;
    }


    function getInstances( blockingUpdate ) {

        clearTimeout( _refreshTimer ) ;

        // alert("serviceSummaryGet")  ;
        let parameters = {
            name: getSelectedService(),
            project: utils.getActiveProject(),
            blocking: blockingUpdate
        } ;


//        let servicesUrl = SERVICE_URL + "/summary" ;
        let instancesUrl = APP_BROWSER_URL + "/service/instances" ;

        console.log( `refreshing status(): ${ instancesUrl }` ) ;
        // getActivityCount() ;

        $.getJSON(
                instancesUrl,
                parameters )

                .done( function ( instanceReport ) {

                    $contentLoaded.resolve() ;
                    $lastRefreshed.text( instanceReport["host-time"] ) ;

                    if ( getSelectedService() == UNREGISTERED_SERVICE ) {
                        loadUnregisteredContainers( instanceReport, blockingUpdate ) ;
                    } else {
                        loadInstances( instanceReport, blockingUpdate ) ;
                    }

                    let jobCount = 0 ;
                    if ( instanceReport.jobs ) {

                        for ( let type of Object.keys( instanceReport.jobs ) ) {
                            jobCount += instanceReport.jobs[ type ].length ;
                        }

                    }
                    $( "#job-count" ).text( jobCount ) ;
                    $( "#resource-count" ).text( instanceReport.resourceCount ) ;



                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving service summary", errorThrown ) ;
                } ) ;
        ;


    }


    function loadUnregisteredContainers( instanceReport, blockingUpdate ) {
        console.log( `loadUnregisteredContainers()` ) ;

        let isNewTable = $( "tr", $unregisteredBody ).length === 0 ;

        if ( blockingUpdate ) {
            $unregisteredBody.empty() ;
        }


        let sortedInstances = ( instanceReport.instances ).sort( ( a, b ) => ( a.host > b.host ) ? 1 : -1 ) ;
        latest_instanceReport = instanceReport ;


        deployer.update_view_for_service( instanceReport ) ;


        $( "tr", $unregisteredBody ).addClass( "purge" ) ;

        for ( let instance of sortedInstances ) {

            let host = instance.host ;
            let rowId = `instance-${ host }-${instance.containerName.substring( 1 )}` ;

            console.log( `rowId: ${ rowId }`, instance ) ;

            let $row = $( `#${ rowId }`, $unregisteredBody ) ;
            $row.removeClass( "purge" ) ;
            let need_to_add_row = $row.length === 0 ;

            if ( need_to_add_row ) {
                console.debug( `Adding new row: ${ host }` ) ;

                let theContainer = instance.containerName ;
                if ( instance.podName ) {
                    // use pod container verses docker container
                    theContainer = instance.containerLabel ;
                }

                $row = jQuery( '<tr/>', {
                    id: rowId,
                    "data-host": host,
                    "data-service": instance.serviceName,
                    "data-container": theContainer,
                    "data-server-type": instance.serverType,
                    "data-pod": instance.podName,
                    "data-namespace": instance.podNamespace
                } ) ;

//                $( "th", $unregisteredTableHeader ).each( function () {
//                    let $headerColumn = $( this ) ;
//                    let fieldPath = $headerColumn.data( "path" ) ;
//                    let $column = jQuery( '<td/>', {
//                        "data-path": fieldPath,
//                        "data-show-object": $( this ).data( "show-object" )
//                    } ).appendTo( $row ) ;
//                } ) ;

                $row.appendTo( $unregisteredBody ) ;

                if ( instanceSelected().length === 0 ) {
                    instanceSelected( $row, true ) ;
                }

                let containerLabel = instance.serviceName ;
                if ( instance.containerLabel ) {
                    containerLabel = instance.containerLabel ;
                }
                let $containerColumn = jQuery( '<td/>', { text: containerLabel } ) ;
                $row.append( $containerColumn ) ;

                let containerIcon = "csap-icon csap-logs" ;
                let containerTitle = `launch container logs: ${instance.containerName} `
                if ( instance.podNamespace ) {
                    containerIcon = "csap-icon csap-go" ;
                    containerTitle = "navigate to pod browser"
                }
                jQuery( '<button/>', {
                    title: containerTitle,
                    "data-host": host,
                    "data-command": "logs",
                    class: containerIcon
                } ).click( function () {
                    //toolSelected( $column.parent(), $( this ) ) ;
                    if ( instance.podNamespace !== "null" ) {
                        kubernetes.browseToPod( instance.serviceName, instance.podNamespace ) ;
                    } else {
                        toolSelected( $row, $( this ) ) ;
                    }
                } ).prependTo( $containerColumn ) ;




                let $hostColumn = jQuery( '<td/>', { text: host } ).appendTo( $row ) ;

                jQuery( '<button/>', {
                    html: "",
                    title: "launch Agent Dashboard to explore host",
                    "data-host": host,
                    "data-command": "host-dash",
                    class: "csap-icon csap-window"
                } ).click( function () {
                    utils.openAgentWindow( host ) ;
                } ).prependTo( $hostColumn ) ;


                let $podColumn = jQuery( '<td/>', { text: instance.podName } ) ;
                $podColumn.appendTo( $row ) ;


                let $namespaceColumn = jQuery( '<td/>', {
                    text: instance.podNamespace
//                    text: instance.containerName
                } ) ;

                let namespaceBrowse = "csap-icon" ;
                if ( instance.podNamespace ) {
                    namespaceBrowse = "csap-icon csap-go" ;
                    jQuery( '<button/>', {
                        title: "Navigate to namespace",
                        "data-host": host,
                        "data-command": "logs",
                        class: namespaceBrowse
                    } ).click( function () {
                        //toolSelected( $column.parent(), $( this ) ) ;

                        kubernetes.browsePodNamespace( instance.podNamespace ) ;

                    } ).prependTo( $namespaceColumn ) ;

                }
                $row.append( $namespaceColumn ) ;
            } else {
                console.log( `loadUnregisteredContainers: row present ${rowId} - skipping add` ) ;
            }


        }

        $( "tr.purge", $unregisteredTable ).remove() ;

        if ( isNewTable ) {
            console.log( `\n\n registering events for new table` ) ;
            // themes: csap , csapSummary
            $unregisteredTable.tablesorter( {
                sortList: [ [ 3, 0 ] ],
                theme: 'csapSummary'
            } ) ;
        }
        $unregisteredTable.trigger( "update" ) ;

        _refreshTimer = setTimeout( () => {
            if ( $unregisteredTable.is( ":visible" ) ) {
                getInstances( false ) ;
            } else {
                console.log( `status not visible - refreshing cancelled` ) ;
            }
        }, utils.getRefreshInterval() ) ;

    }

    function loadInstances( instanceReport, blockingUpdate ) {

        let isNewTable = $( "tr", $detailsBody ).length === 0 ;

        if ( blockingUpdate ) {
            $detailsBody.empty() ;
        }

        let sortedInstances = ( instanceReport.instances ).sort( ( a, b ) => ( a.host > b.host ) ? 1 : -1 ) ;
        latest_instanceReport = instanceReport ;

        if ( !isServiceSettingsLoaded || blockingUpdate ) {
            deployer.update_view_for_service( instanceReport ) ;
            isServiceSettingsLoaded = true ;
        } else {
            console.log( `deployer settings already loaded - skipping` ) ;
        }

        let desc = " instances" ;
        if ( instanceReport.kubernetes ) {
            desc = " containers" ;
        }
        $( "#instance-count" ).text( `${ sortedInstances.length } ${ desc }` ) ;

        $( "tr", $detailsBody ).addClass( "purge" ) ;

        let clusterNames = new Set() ;
        clusterNames.add( "all" ) ;
        let clusterType = null ;

        for ( let instance of sortedInstances ) {

            clusterType = instance.clusterType ;
            let host = instance.host ;
            let rowId = utils.buildValidDomId( `instance-${ host }-${ instance.lc }` ) ;

            clusterNames.add( instance.lc ) ;

            if ( instanceReport.kubernetes ) {
                rowId += `-${ instance.containerIndex + 1 }` ;

                // skip over masters with no services
                if ( instance["kubernetes-master"] ) {
                    if ( !instance.running ) {
                        console.log( `skipping master insert` ) ;
                        continue ;
                    }

                }
            }

            // in case rowid is ip or fqdn
            rowId = utils.buildValidDomId( rowId ) ;


            let $row = $( `#${ rowId }`, $detailsBody ) ;
            $row.removeClass( "purge" ) ;
            let need_to_add_row = $row.length == 0 ;


            if ( need_to_add_row ) {

                //
                console.log( `Adding new row: ${ host } namespace ${instance.podNamespace}` ) ;

                let theContainer = instance.containerName ;
                if ( instance.podName ) {
                    // use pod container verses docker container
                    theContainer = instance.containerLabel ;
                }

                $row = jQuery( '<tr/>', {
                    id: rowId,
                    "data-host": host,
                    "data-service": instance.serviceName,
                    "data-jmx": instance.jmxPort,
                    "data-container": theContainer,
                    "data-cluster": instance.lc,
                    "data-pod": instance.podName,
                    "data-namespace": instance.podNamespace
                } ) ;

                $( "th", $detailsTableHeader ).each( function () {
                    let $headerColumn = $( this ) ;
                    let fieldPath = $headerColumn.data( "path" ) ;
                    let $column = jQuery( '<td/>', {
                        "data-path": fieldPath,
                        "data-show-object": $( this ).data( "show-object" )
                    } ).appendTo( $row ) ;
                    if ( $headerColumn.hasClass( "kubernetes-only" ) ) {
                        $column.addClass( "kubernetes-only" ) ;
                    }
                } ) ;

                $row.appendTo( $detailsBody ) ;

                if ( instanceSelected().length === 0 ) {
                    instanceSelected( $row, true ) ;
                }

            }



            $( "td", $row ).each( function () {

                let $column = $( this ) ;
                let fieldPath = $column.data( "path" ) ;

                if ( fieldPath === "host" ) {

                    buildHostColumn( $column, host, instance,
                            instanceReport, need_to_add_row ) ;

                } else {

                    buildColumn( fieldPath, $column, instance, ) ;

                }
            } ) ;


        }

        manageKubernetesColumnDisplay( instanceReport ) ;

        $( "tr.purge", $detailsBody ).remove() ;

        let numRows = $( "tr", $detailsBody ).length ;
        if ( numRows === 0 ) {
            // clusterType may be used in metrics to default to pod hosts
            let $row = jQuery( '<tr/>', {
                "data-service": getSelectedService(),
                "data-container-index": 0
            } ).appendTo( $detailsBody ) ;

            // WARNING: clusterType is mixed case - very bad - adding via declaration will not match
            $row.data( "clusterType", clusterType ) ;

            jQuery( '<td/>', {
                colSpan: 99,
                text: "No deployments found"

            } ).appendTo( $row ) ;

        }

        console.log( `_lastCluster: ${ _lastCluster } clusterNames`, clusterNames ) ;

        if ( clusterNames.size > 2 ) {
            $clusterFilter.parent().show() ;
            $( ".cluster-label", $detailsBody ).show() ;
            $clusterFilter.empty() ;
            for ( let clusterName of clusterNames ) {

                let label = clusterShortName( clusterName ) ;
                jQuery( '<option/>', {
                    text: label,
                    value: clusterName
                } ).appendTo( $clusterFilter ) ;
            }
        } else {
            $clusterFilter.parent().hide() ;
            $( "div.cluster-label", $detailsBody ).hide() ;
        }
        $clusterFilter.val( _lastCluster ) ;

        if ( isNewTable ) {

            console.log( `\n\n registering events for new table` ) ;
            // note utils does text extraction registration
            $detailsTable.tablesorter( {
                sortList: [ [ 0, 0 ] ],
                theme: 'csapSummary'
            } ) ;

            $detailsTable
                    .tablesorter()
                    // bind to sort events

                    .bind( 'updateComplete', function ( e, table ) {
                        filterInstances() ;
                    } ) ;
        }

        $detailsTable.trigger( "update", "resort" ) ;

        _refreshTimer = setTimeout( () => {
            if ( $detailsBody.is( ":visible" ) ) {
                getInstances( false ) ;
            } else {
                console.log( `status not visible - refreshing cancelled` ) ;
            }
        }, utils.getRefreshInterval() ) ;


    }


    function filterInstances() {

        if ( $clusterFilter.is( ":visible" ) ) {
            let selectedCluster = $clusterFilter.val() ;
            console.log( `filtering: ${selectedCluster}` ) ;
            $( "tr", $detailsBody ).each( function () {

                let $instanceRow = $( this ) ;
                let cluster = $instanceRow.data( "cluster" ) ;

                if ( cluster === selectedCluster
                        || selectedCluster === "all" ) {

                    $instanceRow.show() ;

                    // console.debug( `cluster: ${ cluster } is not matched filter: ${ $clusterFilter.val() }` ) ;

                } else {
                    $instanceRow.hide() ;
                }
            } ) ;
        }
    }

    function manageKubernetesColumnDisplay( instanceReport ) {
        let $wideView = $( "#k8-wide-view", $tableOperations ) ;
        let $masterHosts = $( "#master-deploy-hosts", $tableOperations ) ;
        if ( instanceReport.kubernetes ) {

            $wideView.parent().show() ;
            $masterHosts.parent().show() ;

            if ( $wideView.is( ":checked" ) ) {
                $( ".kubernetes-only", $detailsTable ).show() ;
                $( "div.kubernetes-only", $detailsTable ).hide() ;
            } else {
                $( ".kubernetes-only", $detailsTable ).hide() ;
                $( "div.kubernetes-only", $detailsTable ).show() ;
            }

        } else {
            $wideView.parent().hide() ;
            $masterHosts.parent().hide() ;
            $( ".kubernetes-only", $detailsTable ).hide() ;
        }
    }


    function buildColumn( fieldPath, $column, instance ) {

        let fieldValue = utils.json( fieldPath, instance ) ;

        if ( fieldPath === "deployedArtifacts" ) {
            $column.attr( "title", instance.scmVersion ) ;
        } else if ( Number.isInteger( fieldValue ) ) {
            $column.addClass( "numeric" ) ;
        }
        if ( instance.running
                || ( fieldPath == "diskUtil" )
                || ( fieldPath == "deployedArtifacts" ) ) {

            if ( fieldPath === "diskUtil"
                    || fieldPath === "rssMemory"
                    || fieldPath === "diskWriteKb" ) {

                let memoryInBytes = 0 ;
                if ( !isNaN( memoryInBytes ) ) {
                    memoryInBytes = fieldValue * 1024 ;
                    if ( fieldPath !== "diskWriteKb" ) {
                        // rss and disk are stored in mb
                        memoryInBytes = memoryInBytes * 1024 ;
                    }
                }
                $column.data( "sortvalue", memoryInBytes ) ;
                fieldValue = utils.bytesFriendlyDisplay( memoryInBytes ) ;
                //fieldValue = memoryInBytes ;

            } else if ( fieldPath === "podNamespace" ) {
                let namespace = fieldValue ;
                fieldValue = jQuery( '<span/>', { } ) ;
                jQuery( '<button/>', {
                    title: "open namespace browser",
                    class: "csap-icon csap-go"
                } ).click( function () {
                    kubernetes.browsePodNamespace( namespace ) ;
                } ).appendTo( fieldValue ) ;
                fieldValue.append( namespace ) ;
            } else if ( fieldPath === "containerLabel" ) {

                let containerLabel = fieldValue ;

//                    let podFields = instance.podName.split("-") ;
//                    podSuffix = podFields[ podFields.length - 1 ] ;
//                    containerLabel = `${ containerLabel } (${ podSuffix})`
                fieldValue = jQuery( '<span/>', { } ) ;
                jQuery( '<button/>', {
                    title: `open pod browser: ${instance.podName}`,
                    class: "csap-icon csap-go"
                } ).click( function () {
                    kubernetes.browseToPod( instance.podName, instance.podNamespace ) ;
                } ).appendTo( fieldValue ) ;

                fieldValue.append( containerLabel ) ;
            } else if ( fieldPath === "topCpu" ) {

                fieldValue = utils.toDecimals( fieldValue, 1 ) ;
            }
            $column.empty() ;
            $column.append( fieldValue ) ;
        } else {
            // instance is not running - filter from column sorts
            $column.attr( "data-text", -1 ) ;
            $column.html( "-" ) ;
        }

    }

    function clusterShortName( envColonName ) {
        if ( envColonName.startsWith( utils.getEnvironment( ) ) ) {
            return envColonName.substr( utils.getEnvironment( ).length + 1 ) ;
        }
        return envColonName ;
    }

    function buildHostColumn( $column, host, instance, instanceReport, need_to_add_row ) {
        let instanceStatusClass = `status-red` ;


        if ( need_to_add_row ) {

            $column.data( "text", host ) ;

            let $instanceContainer = jQuery( '<div/>', { class: "instance-container" } )
                    .appendTo( $column ) ;


            let $hostLabel = jQuery( '<label/>', {
            } ).appendTo( $instanceContainer ) ;

            let $hostInfo = jQuery( '<span/>', {
                class: `host-status`,
                html: "&nbsp;"
            } ).appendTo( $hostLabel ) ;

            let $hostCheckbox = jQuery( '<input/>', {
                type: `checkbox`,
                class: "include-host"
            } ).appendTo( $hostLabel ) ;

            if ( $column.closest( "tr" ).hasClass( "selected" ) ) {
                $hostCheckbox.prop( "checked", true ) ;
            }

            let $hostDescription = jQuery( '<span/>', {
                text: ` ${ utils.getHostShortName( host ) }`,
                title: host
            } ).appendTo( $hostLabel ) ;



            jQuery( '<button/>', {
                html: "&nbsp;",
                title: "Launch Service",
                "data-host": host,
                "data-command": "launch",
                class: "csap-icon launch-window"
            } ).off().click( function () {
                toolSelected( $column.parent(), $( this ) ) ;
            } ).appendTo( $instanceContainer ) ;

            jQuery( '<button/>', {
                html: "&nbsp;",
                title: "Open logs in Agent Dashboard",
                "data-host": host,
                "data-command": "logs",
                class: "csap-icon csap-logs"
            } ).off().click( function () {
                toolSelected( $column.parent(), $( this ) ) ;
            } ).appendTo( $instanceContainer ) ;

            jQuery( '<button/>', {
                html: "&nbsp;",
                title: "Show all available actions",
                "data-host": host,
                class: "csap-icon tool-menu csap-menu"
            } )
                    .off().click( showInstanceMenu )
                    .appendTo( $instanceContainer ) ;



            if ( instanceReport.kubernetes ) {
                let hostIndex = `${ instance.containerIndex + 1 }` ;
                jQuery( '<span/>', { title: "csap service container instance", class: "host-index", text: hostIndex } ).appendTo( $instanceContainer ) ;
            }



            $hostCheckbox.change( function () {
                let isChecked = $( this ).is( ":checked" ) ;
                instanceSelected( $( this ).closest( "tr" ), isChecked ) ;
            } ) ;

        }


        if ( instance.running ) {
            instanceStatusClass = `status-green` ;
        }

        if ( instanceReport.filesOnly ) {
            instanceStatusClass = `status-os` ;
        }

        if ( instanceReport.kubernetes
                && instance["kubernetes-master"]
                && !instance.running ) {
            instanceStatusClass = `status-os` ;
        }

        let containerCount = utils.json( "dockerSettings.container-count", instanceReport ) ;
        console.debug( `containerCount: ${ containerCount } ` ) ;
        if ( containerCount && ( parseInt( containerCount ) == 0 ) ) {
            instanceStatusClass = `status-os` ;
        }


        $column.data( "sortvalue", instance.host ) ;
        if ( instance.podName ) {
            $( ".kubernetes-only", $column ).remove() ;
            console.log( `adding k8s info row` ) ;
            let $kubernetesInfo = jQuery( '<div/>', { class: "kubernetes-only" } )
                    .appendTo( $column ) ;

            $column.data( "sortvalue", instance.host + "-" + instance.containerLabel ) ;

            jQuery( '<label/>', {
                title: "Kubernetes container name",
                class: "code-block",
                text: instance.containerLabel
            } ).appendTo( $kubernetesInfo ) ;

            jQuery( '<label/>', {
                title: "Kubernetes namespace",
                text: instance.podNamespace
            } ).appendTo( $kubernetesInfo ) ;

            jQuery( '<label/>', {
                title: "Kubernetes pod name",
                text: instance.podName
            } ).appendTo( $kubernetesInfo ) ;
        }

        //cluster only display if multiple clusters viewed
        $( "div.cluster-label", $column ).remove() ;
        jQuery( '<div/>', {
            title: "csap service container instance",
            class: "cluster-label",
            text: clusterShortName( instance.lc )
        } )
                .appendTo( $column ) ;



        // update running status
        let $hostData = $( "span.host-status", $column ) ;
        $hostData.removeClass() ;
        $hostData.addClass( `host-status ${ instanceStatusClass }` ) ;
        $hostData.closest( "tr" ).data( "clusterType", instance.clusterType ) ;
        $hostData.closest( "tr" ).data( "server-type", instance.serverType ) ;
        $hostData.closest( "tr" ).data( "pod", instance.podName ) ;
        $hostData.closest( "tr" ).data( "namespace", instance.podNamespace ) ;
        let theContainer = instance.containerName ;
        if ( instance.podName ) {
            // use pod container verses docker container
            theContainer = instance.containerLabel ;
        }
        $hostData.closest( "tr" ).data( "container", theContainer ) ;
        $hostData.closest( "tr" ).data( "container-index", instance.containerIndex ) ;
        $hostData.closest( "tr" ).data( "launch-url", instance.launchUrl ) ;
        $hostData.closest( "tr" ).data( "health-url", instance.serviceHealth ) ;
        $hostData.closest( "tr" ).data( "service", instance.serviceName ) ;
    }

    function updateItemsSelectedMessage() {
        let count = getSelectedHosts().length ;
        $( "#table-operations span.selection-count", $instancePanel ).text( `${ count } item(s) selected ` ) ;

        let $selectButtons = $( "button", $options ) ;
        let $multiButton = $( "#table-operations button.tool-menu", $instancePanel ) ;

        if ( count === 0
                && !latest_instanceReport.kubernetes ) {
            utils.disableButtons( $selectButtons, $multiButton ) ;
        } else {
            utils.enableButtons( $selectButtons, $multiButton ) ;
        }
        utils.enableButtons( $( "button.csap-history,button.csap-edit", $options ) ) ;
    }


    function instanceSelected( $row, isChecked ) {
        console.debug( `instanceSelected() : ${ isChecked }` ) ;

        if ( $row ) {
            //$( "tr.selected", $detailsBody ).removeClass( "selected" ) ;
            if ( isChecked ) {
                $row.addClass( "selected" ) ;
            } else {
                $row.removeClass( "selected" ) ;
            }
        }
        updateItemsSelectedMessage() ;
        return $( "tr.selected", $detailsBody ) ;
    }

    function closeInstanceMenu() {

        clearTimeout( instanceCloseTimer ) ;
        instanceCloseTimer = setTimeout( function () {
            $instanceMenu.hide() ;
            $instanceMenu.css( "opacity", "0.0" ) ;

        }, 1000 ) ;
    }

    function showInstanceMenu() {

//        clearTimeout( instanceCloseTimer ) ;
        let $showButton = $( this ) ;

        let rowIdTarget ;

        if ( $showButton.parent().hasClass( "instance-container" ) ) {
            let $row = $showButton.closest( "tr" ) ;
            if ( $row ) {
                rowIdTarget = $row.attr( "id" ) ;
            }
        } else {
            rowIdTarget = "all-selected" ;
        }

//        instanceCloseTimer = setTimeout( function () {
        console.log( `showInstanceMenu() rowIdTarget: ${ rowIdTarget }` ) ;

        $instanceMenu.data( "rowId", rowIdTarget ) ;
        $instanceMenu.show() ;

        let windowHeight = $( window ).outerHeight( true ) ;
        let menuHeight = $instanceMenu.outerHeight( true ) ;
        let buttonTop = $showButton.offset().top ;
        let buttonLeft = $showButton.offset().left + $showButton.outerWidth( false ) ;

        console.log( `windowHeight: ${windowHeight}, buttonTop: ${buttonTop}, menuHeight: ${menuHeight}` ) ;
        if ( ( buttonTop + menuHeight ) > windowHeight ) {
            buttonTop = windowHeight - menuHeight ;
        }
        //console.log( "panelTop: " + panelTop + " panelLeft: " + panelLeft )

        $instanceMenu.offset(
                {
                    top: Math.round( buttonTop ) - 10,
                    left: Math.round( buttonLeft ) + 10
                } ) ;
        $instanceMenu.css( "opacity", "1.0" ) ;

//        }, 200 ) ;
    }

    function toolSelected( $rows, $buttonClicked ) {

        if ( $rows.length === 0
                && latest_instanceReport.kubernetes ) {
            let $tempTable = jQuery( '<table/>', { } ) ;
            let hosts = latest_instanceReport.envHosts.split( "," ) ;
            let serviceName = utils.json( "instances.0.serviceName", latest_instanceReport ) ;

            for ( let host of hosts ) {

                let attributes = {
                    "data-host": host,
                    "data-service": serviceName,
                    "data-container-index": 0,
                    "data-cluster": "kubernetes"
                }

                jQuery( '<tr/>', attributes ).appendTo( $tempTable ) ;
            }
            $rows = $( "tr", $tempTable ) ;

            console.log( `toolSelected, building: `, $rows ) ;
        }



        let menuItemName = $buttonClicked.data( "command" ) ;
        if ( menuItemName ) {
            if ( !confirmMaxItem( $rows ) ) {
                return ;
            }
//            console.log( ` command not bound ${ $buttonClicked.attr("id") } ` ) ;
//            return ;
        }

        $rows.each( function () {

            let $row = $( this ) ;

            // uses first row data
            let hostName = $row.data( "host" ) ;
            let serviceName = $row.data( "service" ) ;
            let clusterType = $row.data( "clusterType" ) ;
            let serverType = $row.data( "server-type" ) ;
            let containerName = $row.data( "container" ) ;
            let isKubernetes = ( clusterType === "kubernetes"
                    || $row.data( "cluster" ) === "kubernetes" ) ;
            let isContainer = ( containerName !== "default" ) ;
            let containerIndex = $row.data( "container-index" ) ;
            let podSuffix = `-${ containerIndex + 1 }` ;
            let targetUrl = utils.agentUrl( hostName, menuItemName ) ;

            let isUnregistered = utils.isUnregistered( serverType ) ;

            console.log( `loadInstances() isKubernetes: ${isKubernetes} toolSelected: ${ menuItemName }, host: ${ hostName }, containerName: ${ containerName }, targetUrl: ${targetUrl} ` ) ;

            switch ( menuItemName ) {

                case "files" :

                    let parameterMap ;
                    if ( isUnregistered ) {
                        //targetUrl += `?fromFolder=/&containerName=${ containerName }&` ;
                        parameterMap = { fromFolder: "/", containerName: containerName } ;
                    } else {
//                        targetUrl += `?serviceName=${ serviceName }` ;

                        parameterMap = { defaultService: serviceName } ;
                        if ( isContainer ) {
//                            targetUrl += `&containerName=${containerName}&` ;
                            parameterMap = { defaultService: serviceName, containerName: containerName } ;
                        }
                    }
                    utils.openAgentWindow( hostName, "/app-browser#agent-tab,file-browser", parameterMap ) ;
                    //utils.launch( targetUrl ) ;
                    break ;

                case "host-dash" :
                    let osService = serviceName ;
                    if ( isKubernetes ) {
                        osService += podSuffix ;
                    }

                    let osParams = { defaultService: osService } ;
                    utils.openAgentWindow( hostName, "/app-browser#agent-tab,service", osParams ) ;
                    break ;

                case "logs" :
                    if ( isUnregistered ) {
                        targetUrl += `?fileName=__docker__${ containerName }` ;
                        console.log( `logs url: ${ targetUrl } ` )
                        utils.launch( targetUrl ) ;
                    } else {

                        let fullLogName = serviceName ;
                        if ( isKubernetes ) {
                            fullLogName += podSuffix ;
                        }

                        let appParams = { defaultService: fullLogName } ;
//                        if ( isContainer ) {
//                            appParams.container = containerName ;
//                        }
                        utils.openAgentWindow( hostName, "/app-browser#agent-tab,logs", appParams ) ;

//                        targetUrl += `?serviceName=${ serviceName }` ;
//                        if ( isContainer ) {
//                            targetUrl += "&containerName=" + containerName ;
//                        }
//                        utils.launch( targetUrl ) ;
                    }
                    break ;

                case "launch" :
                    let launchUrl = $( this ).data( "launch-url" ) ;

                    if ( launchUrl.trim().length == 0 ) {
                        alertify.csapWarning( "No launch url configured for service - url may be specified using the application editor." ) ;
                        //launchHostDash( jqueryRows ) ;
                    } else if ( launchUrl.startsWith( "launcher:" ) ) {

                        let serviceSlashPath = launchUrl.substring( "launcher:".length ) ;
                        let serviceNameAndParams = utils.splitWithTail( serviceSlashPath, ",", 1 ) ;
                        console.log( `launchService: `, serviceNameAndParams ) ;
                        utils.launchService( serviceNameAndParams[0], serviceNameAndParams[1] ) ;

                    } else {

                        // hook for multiple urls used for a few services
                        let urlArray = launchUrl.split( ',' ) ;
                        for ( let targetUrl of  urlArray ) {
                            console.log( `launch url: targetUrl` ) ;
//                            let frameName = `launchserviceName + hostName + launch + i++` ;
                            utils.launch( targetUrl.replaceAll( "__comma__", "," ), ) ;
                        }
                    }

                    break ;

                case "health" :
//                    let healthUrl = $( this ).data( "health-url" ) ;
//
//                    if ( healthUrl.trim().length == 0 ) {
//                        alertify.csapWarning( "No health url configured for service - url may be specified using the application editor." ) ;
//                    } else {
//                        utils.launch( healthUrl ) ;
//                    }

                    let healthService = serviceName ;
                    if ( isKubernetes ) {
                        healthService += podSuffix ;
                    }
                    let liveParams = { defaultService: healthService } ;
                    utils.openAgentWindow( hostName, "/app-browser#agent-tab,live", liveParams ) ;

                    break ;

                case "application" :

                    let appService = serviceName ;
                    if ( isKubernetes ) {
                        appService += podSuffix ;
                    }

                    let appParams = { defaultService: appService } ;
                    utils.openAgentWindow( hostName, "/app-browser#agent-tab,application", appParams ) ;
                    break ;

                case "java" :
                    let javaService = serviceName ;
                    if ( isKubernetes ) {
                        javaService += podSuffix ;
                    }

                    let javaParams = { defaultService: javaService } ;
                    utils.openAgentWindow( hostName, "/app-browser#agent-tab,java", javaParams ) ;

                    break ;

                default:
                    console.log( ` command not found: ${ menuItemName }` ) ;
                    break ;
            }

        } ) ;


        let report = $buttonClicked.data( "report" ) ;
        if ( report ) {

            let hostsParam = utils.buildHostsParameter( $rows ) ;

            let clusterType = $rows.data( "clusterType" ) ;
            let isKubernetes = ( clusterType === "kubernetes" ) ;
            let containerIndex = $rows.data( "container-index" ) ;
            let podSuffix = `-${ containerIndex + 1 }` ;

            let serviceName = $rows.data( "service" ) ;

            if ( ( report === "graphJava" )
                    && isKubernetes ) {
                serviceName += podSuffix ;
            }

            if ( !serviceName && latest_instanceReport.kubernetes ) {
                serviceName = utils.json( "instances.0.serviceName", latest_instanceReport ) + '-1' ;
                hostsParam = latest_instanceReport.envHosts ;
            }


            // selectedProject may be all packages - we use the graph package
            let urlAction = ANALYTICS_URL + "&report=" + $buttonClicked.data( "report" )
                    + "&project=" + utils.getActiveProject()
                    + "&host=" + hostsParam
                    + "&service=" + serviceName
                    + "&appId=" + APP_ID + "&" ;

            if ( $buttonClicked.hasClass( "application" ) ) {
                urlAction += "appGraph=appGraph&" ;
            }

            console.log( `launching: ${urlAction}` ) ;
            openWindowSafely( urlAction, "_blank" ) ;


        }


        let tool = $buttonClicked.data( "tool" ) ;
        if ( tool ) {
            if ( tool == "jvisualvm" ) {
                launchProfiler( $rows, true )
            } else {
                alertify.csapWarning( `Tool not supported yet: ${ tool }` )
            }
        }

    }

    function confirmMaxItem( $rows ) {

        if ( $rows.length > 4 ) {
            return confirm( "You currently have " + $rows.length + " rows selected. Each will open in a new window if you proceed" ) ;
        }

        return true ;
    }

    function getSelectedHosts() {

        let hosts = new Array() ;

        $( "tr.selected", $detailsBody ).each( function () {
            hosts.push( $( this ).data( "host" ) ) ;
        } )
        //console.log(`getSelectedHosts() `, hosts) ;

        return hosts ;
    }

    function getAllHosts() {
        let hosts = new Array() ;

        $( "tbody tr", $instancesPanel ).each( function () {
            hosts.push( $( this ).data( "host" ) ) ;
        } )
        //console.log(`getSelectedHosts() `, hosts) ;

        return hosts ;
    }



    function getSelectedContainers() {
        let hosts = new Array() ;

        let isContainer = false ;
        $( "tr.selected", $detailsBody ).each( function () {
            hosts.push( $( this ).data( "container" ) ) ;
            isContainer = isContainer || ( $( this ).data( "container" ) !== "default" ) ;
        } )
        //console.log(`getSelectedHosts() `, hosts) ;

        if ( !isContainer ) {
            return null ;
        }

        return hosts ;
    }

    function getAllContainers() {
        let hosts = new Array() ;

        let isContainer = false ;
        $( "tr", $instancesPanel ).each( function () {
            hosts.push( $( this ).data( "container" ) ) ;
            isContainer = isContainer || ( $( this ).data( "container" ) !== "default" ) ;
        } )
        //console.log(`getSelectedHosts() `, hosts) ;

        if ( !isContainer ) {
            return null ;
        }

        return hosts ;
    }


    function showOperationDialog( $button ) {

        let service = getSelectedService() ;
        let hosts = getSelectedHosts() ;


        console.log( `showOperationDialog() ${service} hosts: ${ hosts } ` ) ;

        if ( $button.hasClass( "deploy" ) ) {
            deployer.showDeployDialog( hosts ) ;

        } else if ( $button.hasClass( "start" ) ) {
            deployer.showStartDialog( hosts ) ;

        } else if ( $button.hasClass( "stop" ) ) {
            deployer.showStopDialog( hosts ) ;

        } else if ( $button.hasClass( "remove" ) ) {
            deployer.showStopDialog( hosts ) ;

        } else if ( $button.hasClass( "csap-edit" ) ) {

            //console.log( `latest_instanceReport`, latest_instanceReport)
            let isNamespaceMonitor = utils.json( "dockerSettings.namespaceMonitor", latest_instanceReport ) ;
            if ( isNamespaceMonitor ) {
                //service = "namespace-monitor-template" ;
            }
            utils.launchServiceEditor( service ) ;

        } else if ( $button.hasClass( "csap-history" ) ) {
            utils.launchServiceHistory( service ) ;
        }


    }


    function buildJobsPanel( serviceReport ) {

        $jobContainer.empty() ;
        if ( !serviceReport ) {

            jQuery( '<div/>', {
                class: "quote",
                text: "Use services menu to select job"
            } ).appendTo( $jobContainer ) ;

            return ;
        }
        let serviceJobDefinition = serviceReport.jobs ;
        if ( !serviceJobDefinition ) {

            jQuery( '<div/>', {
                class: "quote",
                text: "No jobs defined: use service editor"
            } ).appendTo( $jobContainer ) ;

            return ;
        }

        for ( let jobType of Object.keys( serviceJobDefinition ) ) {
            console.log( `jobType: ${jobType}`, serviceJobDefinition[jobType] ) ;
            if ( !Array.isArray( serviceJobDefinition[jobType] ) )
                continue ;

            for ( let job of serviceJobDefinition[jobType] ) {

                let notes = "" ;
                if ( job.notes ) {
                    notes = ` <br/><span class="job-note">Note: ${ job.notes }</span>` ;
                }


                let background = "" ;
                if ( job.background ) {
                    background = `<span title="job will be launched in background - monitor service logs to track progress" class="job-background">[ background ]</span> ` ;
                }

                let envFilters = "" ;
                if ( job["environment-filters"] ) {
                    envFilters = `<span title="job will only be run on matching environments" class="job-background">${ JSON.stringify( job["environment-filters"] ) }</span> ` ;
                }
                if ( job["host-filters"] ) {
                    envFilters += `<span title="job will only be run on matching hosts" class="job-background">${ JSON.stringify( job["host-filters"] ) }</span> ` ;
                }

                if ( job.description ) { // scripts
                    jQuery( '<button/>', {
                        class: "csap-button",
                        "data-job": job.description,
                        text: job.frequency
                    } ).appendTo( $jobContainer ) ;
                    jQuery( '<span/>', {
                        class: "description",
                        text: job.description
                    } ).appendTo( $jobContainer ) ;
                    jQuery( '<span/>', {
                        html: background + envFilters + job.script + notes
                    } ).appendTo( $jobContainer ) ;
                } else if ( job.settings ) { // log rotation
                    jQuery( '<button/>', {
                        class: "csap-button",
                        "data-job": "Log Rotation",
                        text: "Log Rotation"
                    } ).appendTo( $jobContainer ) ;
                    jQuery( '<span/>', {
                        class: "description",
                        text: job.path
                    } ).appendTo( $jobContainer ) ;
                    jQuery( '<span/>', {
                        text: job.settings
                    } ).appendTo( $jobContainer ) ;
                } else { // disk cleanup
                    jQuery( '<button/>', {
                        class: "csap-button",
                        "data-job": job.path,
                        text: `Disk Cleanup`
                    } ).appendTo( $jobContainer ) ;
                    jQuery( '<span/>', {
                        class: "description",
                        text: job.path
                    } ).appendTo( $jobContainer ) ;
                    jQuery( '<span/>', {
                        text: `> ${job.olderThenDays} days max depth: ${ job.maxDepth }, prune: ${ job.pruneEmptyFolders }`
                    } ).appendTo( $jobContainer ) ;
                }
            }
        }

        $jobListing.show() ;
        $jobConfirm.hide() ;

        $( '#service-job-summary button' ).off().click( function () {

            let jobToRun = $( this ).data( "job" ) ;

            $jobParameters.empty() ;

            for ( let jobType of Object.keys( serviceJobDefinition ) ) {
                console.log( `jobType: ${jobType}`, serviceJobDefinition[jobType] ) ;
                if ( !Array.isArray( serviceJobDefinition[jobType] ) )
                    continue ;

                for ( let job of serviceJobDefinition[jobType] ) {


                    if ( job.description != jobToRun
                            || !Array.isArray( job.parameters ) ) {
                        continue ;
                    }

                    for ( let parameterCommaValCommaDesc of job.parameters ) {

                        //let def = parameterCommaValCommaDesc.split( ",", 3 ) ;
                        let def = utils.splitWithTail( parameterCommaValCommaDesc, ",", 2 )
                        console.log( def ) ;
                        if ( def.length == 3 ) {
                            jQuery( '<label/>', {
                                class: "csap",
                                text: def[0]
                            } ).appendTo( $jobParameters ) ;
                            let placeholder = def[1] ;
                            if ( placeholder == "selected-hosts-on-ui" ) {
                                let hostsArray = getSelectedHosts( ) ;

                                let $inputField = jQuery( '<textarea/>', {
                                    title: "To modify: select hosts using service instance",
                                    class: "read-only",
                                    placeholder: placeholder,
                                    readonly: "readonly",
                                    spellcheck: "false",
                                    rows: 8
                                } ).appendTo( $jobParameters ) ;

                                $inputField.val( hostsArray.join( " " ) ) ;
                            } else {

                                jQuery( '<input/>', {
                                    placeholder: placeholder
                                } ).appendTo( $jobParameters ) ;

                            }
                            jQuery( '<span/>', {
                                text: def[2]
                            } ).appendTo( $jobParameters ) ;
                        }

                    }
                }
            }

            $jobListing.hide() ;
            $( ".quote span", $jobConfirm ).text( jobToRun ) ;
            $jobConfirm.show() ;

            $( ".csap-remove", $jobConfirm ).off().click( function () {
                $jobConfirm.hide() ;
                $jobListing.show() ;
                return false ;
            } ) ;

            $( "button.csap-play", $jobConfirm ).off().click( function () {

                let jobParams = new Object() ;
                $( "label", $jobParameters ).each( function () {
                    let name = $( this ).text() ;
                    let value = $( this ).next().val() ;
                    if ( value != "" ) {
                        jobParams[ name ] = value ;
                    }
                } )

                let paramObject = {
                    jobToRun: jobToRun,
                    jobParameters: JSON.stringify( jobParams )
                } ;
                deployer.executeOnHosts(
                        getSelectedHosts( ),
                        "runServiceJob",
                        paramObject ) ;
            } ) ;

        } ) ;
        //$jobContainer.text( JSON.stringify( serviceJobDefinition, null, "\t" ) ) ;
    }

    function launchProfiler( $rowsSelected, isJvisualVm ) {

        let firstHost = $rowsSelected.first().data( "host" ) ;
        let firstService = $rowsSelected.first().data( "service" ) ;
        let jmxPort = $rowsSelected.first().data( "jmx" ) ;

        if ( !jmxPort
                || jmxPort == "-1" ) {
            let message = "Current service does not support JMX (port set to -1 in definition).<br><br>" ;
            alertify.csapWarning( message ) ;
            return ;
        }

        let jmxPorts = firstHost + ":" + jmxPort ;
        ;

        let alertMessage = `CSAP optionally configures secure connections via application definition; if prompted,  use userid "csap" and password: "csap"` ;


        let $message = jQuery( '<div/>', { html: "<br><br>" } ) ;

        jQuery( '<div/>', {
            class: "csap-blue",
            html: "<br>Windows Users: Click OK to Save/Launch the bat file.<br><br>Recommended: Install the latest JDK, ensure that JAVA_HOME/bin must be in path<br><br>"
                    + "Non-Windows: open JAVA_HOME/bin/jvisualvm. and use the connection string above.<br><br>"
        } ).appendTo( $message ) ;
        ;

        jQuery( '<a/>', {
            class: "csap-link-icon csap-window",
            href: "https://visualvm.github.io/",
            text: "https://visualvm.github.io/",
            target: "_blank"
        } ).appendTo( $message ) ;



        jQuery( '<div/>', {
            class: "csap-white",
            html: `Connection: ${ jmxPorts } `
        } ).prependTo( $message ) ;
        ;


        let applyDialog = alertify.confirm( $message.html() ) ;
        applyDialog.setting( {

            title: "Java JDK Tools",

            'labels': {
                ok: 'Save JDK Tools Launch File',
                cancel: 'Cancel request'
            },
            'onok': function () {
                let url = "service/profilerLauncher?serviceName="
                        + firstService + "&jmxPorts=" + jmxPorts ;

                if ( isJvisualVm ) {
                    url += "&jvisualvm=jvisualvm" ;
                }


                openWindowSafely( url, "_blank" ) ;
            },

            'oncancel': function () {
                alertify.warning( "Operation Cancelled" ) ;
            }

        } ) ;
    }


} ) ;