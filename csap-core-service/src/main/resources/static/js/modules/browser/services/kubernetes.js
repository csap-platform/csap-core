// define( [ "browser/utils", "ace/ace", "ace/ext-modelist" ], function ( utils, aceEditor, aceModeListLoader ) {




import _dom from "../../utils/dom-utils.js";



import utils from "../utils.js"


const svcKubernetes = service_kubernetes();

export default svcKubernetes


function service_kubernetes() {

    _dom.logHead( "Module loaded" );


    const $podPanel = $( "#services-tab-kpods" );
    const $summaryPanel = $( "#pods-summary", $podPanel );
    const $podOptions = $( ".options", $podPanel );
    const ALL_NAMESPACES = "all-namespaces";

    let podRequested;


    const $hideEmpty = $( "input#hide-empty-namespaces", $podOptions );
    let $podIncludeFilter = $( "#pod-filter", $podOptions );
    let $podSortCpu = $( "#pod-sort-cpu", $podOptions );
    let $podClearFilter = $( "#clear-filter", $podOptions );
    let _podFilterTimer;
    const $namespaceSelection = $( "#namespace-selection", $podOptions );
    const $namespaceSelectContainer = $namespaceSelection.parent();


    const $panelLayoutSelect = $( "#panel-layout" );

    let longestNameSize = 0;
    let initialized = false;

    const $podInstancePanel = $( "#services-tab-kpod-instances" );
    const $podOwnerSelection = $( "#pod-owner-selection", $podInstancePanel );
    const $podNamespace = $( "#pod-browser-namespace", $podInstancePanel );


    const $servicesTab = $( "#services-tab" );
    const $podInstanceMenu = $( "#instances-for-pod", $servicesTab );
    const $podNameItem = $( ".pod-selected.level3", $servicesTab );
    const $podMenuItems = $( ".pod-selected.level4", $servicesTab );


    const $podInstances = $( "#pod-instances", $podInstancePanel );
    const $podTable = $( "table", $podInstances );
    const $podInstanceBody = $( "tbody", $podTable );
    const $podTableHeader = $( "thead", $podTable );
    const $containerInstances = $( "#container-instances", $podInstancePanel );
    const $containerTable = $( "table", $containerInstances );
    const $containerInstanceBody = $( "tbody", $containerTable );
    const $containerTableHeader = $( "thead", $containerTable );



    const $nodePanel = $( "#services-tab-knodes" );
    const $nodeFilter = $( "#node-filter", $nodePanel );
    const $nodeMenuItems = $( ".node-selected", $servicesTab );
    const $nodeInstances = $( "#node-instances", $nodePanel );
    const $nodeTable = $( "table", $nodeInstances );
    const $nodeInstanceBody = $( "tbody", $nodeTable );
    const $nodeTableHeader = $( "thead", $nodeTable );


    const $volumeMenuItems = $( ".volume-selected", $servicesTab );
    const $volumePanel = $( "#services-tab-kvolumes" );
    const $volumeFilter = $( "#volume-filter", $volumePanel );
    const $volumeInstances = $( "#volume-instances", $volumePanel );
    const $volumeTable = $( "table", $volumeInstances );
    const $volumeInstanceBody = $( "tbody", $volumeTable );
    const $volumeTableHeader = $( "thead", $volumeTable );

    let _filterTimer;

    let latestOwnerReports;



    return {

        browseToPod: function ( pod, namespace ) {

            check_for_initialize();
            browseToPod( pod, namespace );
        },

        browsePodNamespace: function ( namespace ) {
            check_for_initialize();
            browsePodNamespace( namespace );
        },

        showVolumes: function ( $menuContent, forceHostRefresh, menuPath ) {
            check_for_initialize();

            return showVolumes( forceHostRefresh );

        },
        selectedVolume: function () {
            return $( "tr.selected", $volumeInstanceBody );
        },

        showNodes: function ( $menuContent, forceHostRefresh, menuPath ) {
            check_for_initialize();

            return showNodes( forceHostRefresh );

        },
        selectedNode: function () {
            return $( "tr.selected", $nodeInstanceBody );
        },

        showPodSummary: function ( $displayContainer, forceHostRefresh ) {

            check_for_initialize();

            if ( namespaceSelected() == "" ) {
                return showPodsByNamespace( forceHostRefresh );
            } else {
                return showPodsByOwner();
            }


        },

        showPodInstances: function ( $displayContainer, forceHostRefresh ) {

            if ( forceHostRefresh ) {
                let $loading = showPodsByOwner( forceHostRefresh );
                $.when( $loading ).done( function () {
                    return showPodInstances( forceHostRefresh );
                } );
                return $loading;

            } else {
                return showPodInstances( forceHostRefresh );
            }

        },

        namespaceSelected: function () {
            return namespaceSelected();
        },

        selectPod: function ( namespace, name ) {

            check_for_initialize();
            namespaceSelected( namespace );
            return selectPodOwner( name );

        },

        hidePodOptions: function () {
            $podMenuItems.hide();
        },

        selectedPod: function () {
            return $( "tr.selected", $podInstanceBody );
        },

        selectedContainer: function () {
            return $( "tr.selected", $containerInstanceBody );
        },

        podRows: function () {
            return $( "tr", $podInstanceBody );
        },

        reDraw: function () {
            reDraw();
        }
    };


    function check_for_initialize() {

        if ( initialized ) {
            return;
        }
        initialized = true;

        $hideEmpty.change( function () {
            showPodsByNamespace( false );
        } )

        console.log( `Hiding node items` );
        $nodeMenuItems.hide();
        $volumeMenuItems.hide();
        $podMenuItems.hide();
        $containerInstances.hide();


        $podOwnerSelection.click( function () {
            $podMenuItems.hide();
            $podNameItem.hide();
            $podOwnerSelection.data( "name", "" );
            utils.launchMenu( "services-tab,kpods" );
            return false;
        } );

        $podIncludeFilter.off().keyup( function () {
            console.log( "Applying template filter" );
            clearTimeout( _podFilterTimer );
            _podFilterTimer = setTimeout( function () {
                applyPodFilter();
            }, 500 );
        } );

        $podClearFilter.hide();
        $podClearFilter.click( function () {
            $podClearFilter.hide();
            $podIncludeFilter.val( "" );
            applyPodFilter();
        } );

        $podSortCpu.change( function () {
            if ( $namespaceSelection.is( ":visible" ) ) {
                showPodsByOwner( false );
            } else {
                showPodsByNamespace( false );
            }

        } );


        let applyVolFilterFunction = utils.addTableFilter( $volumeFilter, $volumeInstanceBody.parent() );
        let applyNodeFilterFunction = utils.addTableFilter( $nodeFilter, $nodeInstanceBody.parent() );

        $namespaceSelection.click( function () {
            $namespaceSelectContainer.hide();
            $namespaceSelection.data( "name", "" );
            showPodsByNamespace( false );
            return false;
        } )


        $namespaceSelectContainer.hide();

    }

    function applyPodFilter() {

        let $podPanels = $( 'div.summary', $summaryPanel );

        let includeFilter = $podIncludeFilter.val();

        console.log( ` includeFilter: ${ includeFilter }` );


        if ( includeFilter.length > 0 ) {
            $podPanels.hide();
            $podClearFilter.show();
            let filterArray = includeFilter.split( "," );
            for ( let i = 0; i < filterArray.length; i++ ) {
                $( `>div:icontains("${ filterArray[ i ] }")`, $podPanels ).parent().show();
            }
        } else {
            $podPanels.show();
        }

    }

    function selectedPod() {
        return $( "tr.selected", $podInstanceBody );
    }

    function showVolumes( forceHostRefresh ) {
        let nodeReportUrl = APP_BROWSER_URL + "/kubernetes/volumes";

        console.log( `resourceReportUrl(): ${ nodeReportUrl }` );
        // getActivityCount() ;

        utils.loading( "Collecting node report..." );
        let parameters = {
            project: utils.getActiveProject(),
        };


        let $contentLoaded = new $.Deferred();
        //        $podIncludeFilter.val("") ;
        //        $podClearFilter.hide() ;

        $.getJSON(
            nodeReportUrl,
            parameters )

            .done( function ( volumeReports ) {

                $contentLoaded.resolve();
                utils.loadingComplete();


                buildVolumeTable( volumeReports, forceHostRefresh );



            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Retrieving volume summary", errorThrown );
            } );

        return $contentLoaded;
    }

    function buildVolumeTable( volumeReports, forceHostRefresh ) {

        console.log( `buildVolumeTable(): volume reports: `, volumeReports );



        let isNewTable = $( "tr", $volumeInstanceBody ).length === 0;

        if ( forceHostRefresh ) {
            $volumeInstanceBody.empty();
        }


        $( "tr", $volumeInstanceBody ).addClass( "purge" );

        let sortedVolumeReports = ( volumeReports ).sort( ( a, b ) => ( a.name > b.name ) ? 1 : -1 );
        for ( let volumeReport of sortedVolumeReports ) {

            let volumeName = volumeReport.name;
            let rowId = "volume-" + utils.buildValidDomId( volumeName );

            console.debug( `building ${ rowId }` );


            let $row = $( `#${ rowId }`, $volumeInstanceBody );
            $row.removeClass( "purge" );

            let need_to_add_row = $row.length == 0;
            if ( need_to_add_row ) {


                console.debug( `Adding new row: ${ volumeName }` );

                $row = jQuery( '<tr/>', {
                    id: rowId,
                    "data-name": volumeName,
                    "data-host": volumeName,
                    "data-self": volumeReport.apiPath
                } );

                $( "th", $volumeTableHeader ).each( function () {
                    let fieldPath = $( this ).data( "path" );
                    jQuery( '<td/>', {
                        "data-path": fieldPath,
                        "data-show-object": $( this ).data( "show-object" )
                    } ).appendTo( $row );
                } );

                $row.appendTo( $volumeInstanceBody );

                if ( volumeSelected().length == 0 ) {
                    volumeSelected( rowId );
                }

            }



            $( "td", $row ).each( function () {

                let $column = $( this );
                let fieldPath = $column.data( "path" );
                let fieldValue = utils.json( fieldPath, volumeReport );
                let refName = utils.json( "ref-name", volumeReport );
                let nfsServer = utils.json( "nfs-server", volumeReport );


                $column.data( "text", fieldValue );
                if ( fieldPath === "path" ) {
                    let $fileManager = utils.buildAgentLink( "", "/file/FileManager", fieldValue );
                    //                    let url = $fileManager.attr( "href" ) ;
                    //                    url += `?quickview=${ refName }&nfs=${ nfsServer }&fromFolder=${fieldValue}` ;
                    $fileManager.attr( "href", "#open-file-browser" );

                    $fileManager.click( function () {


                        let folderHost = utils.getMasterHost();
                        console.log( `master host:  ${ utils.getMasterHost() }` );
                        if ( volumeReport.affinity ) {
                            folderHost = volumeReport.affinity;
                        }
                        if ( folderHost.includes( "." ) ) {
                            console.log( `host includes . - splitting and taking the first entry` )
                            folderHost = folderHost.split( "." )[ 0 ];
                        }
                        let parameterMap = {
                            quickview: refName,
                            nfs: nfsServer,
                            locationAndClear: refName, // forces context refresh
                            fromFolder: fieldValue
                        };
                        if ( utils.isAgent() ) {
                            utils.launchFiles( parameterMap );
                        } else {
                            utils.openAgentWindow( folderHost, "/app-browser#agent-tab,file-browser", parameterMap );
                        }
                        return false;
                    } );
                    $column.empty();
                    $column.append( $fileManager );

                } else {
                    buildColumn( $( this ),
                        volumeReport,
                        need_to_add_row );
                }
            } );


        }

        $( "tr.purge", $volumeInstanceBody ).remove();

        if ( isNewTable ) {

            console.log( `\n\n registering events for new table` );
            // themes: csap , csapSummary
            $volumeTable.tablesorter( {
                sortList: [ [ 3, 0 ] ],
                theme: 'csapSummary',
                //                textExtraction: {
                //                    0: function ( node, table, cellIndex ) {
                //                        return $( "span", node ).text() ;
                //                    },
                //                }
            } );
        }


        $volumeTable.trigger( "update" );


    }
    function volumeSelected( rowId ) {
        console.debug( `volumeSelected() : ${ rowId }` );

        if ( rowId ) {
            //$( "tr.selected", $detailsBody ).removeClass( "selected" ) ;
            $( `tr#${ rowId }`, $volumeInstanceBody ).toggleClass( "selected" );
        }
        //updateItemsSelectedMessage() ;
        return $( "tr.selected", $volumeInstanceBody );
    }

    function showNodes( forceHostRefresh ) {
        let nodeReportUrl = APP_BROWSER_URL + "/kubernetes/nodes";

        console.log( `resourceReportUrl(): ${ nodeReportUrl }` );
        // getActivityCount() ;

        utils.loading( "Collecting node report..." );
        let parameters = {
            project: utils.getActiveProject(),
        };


        let $contentLoaded = new $.Deferred();
        //        $podIncludeFilter.val("") ;
        //        $podClearFilter.hide() ;

        $.getJSON(
            nodeReportUrl,
            parameters )

            .done( function ( summaryReport ) {

                $contentLoaded.resolve();
                utils.loadingComplete();
                //                    $nodeMenuItems.show() ;

                buildNodeTable( summaryReport, forceHostRefresh );



            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Retrieving node summary", errorThrown );
            } );

        return $contentLoaded;
    }

    function buildNodeTable( nodeReports, forceHostRefresh ) {

        console.log( `buildNodeSummarys(): node reports: `, nodeReports );



        let isNewTable = $( "tr", $nodeInstanceBody ).length === 0;


        if ( forceHostRefresh ) {
            $nodeInstanceBody.empty();
        }


        $( "tr", $nodeInstanceBody ).addClass( "purge" );

        let sortedNodeReports = ( nodeReports ).sort( ( a, b ) => ( a.name > b.name ) ? 1 : -1 );
        let $maxPodWarning = $( "#max-pod-alerts", $nodePanel );
        let $maxPodCount = $( "#node-max-pod-running", $nodePanel );
        $maxPodWarning.addClass( "status-green" );
        for ( let nodeReport of sortedNodeReports ) {

            let maxPods = utils.json( "capacity.pods", nodeReport );
            let nodePods = utils.json( "active.pods", nodeReport );
            if ( maxPods ) {
                let uiMax = $maxPodCount.val();
                if ( uiMax === ""
                    || maxPods > uiMax ) {
                    $maxPodCount.val( maxPods );
                }

                if ( nodePods
                    && ( nodePods / maxPods * 100 ) > 90 ) {
                    $maxPodWarning.attr( "title", "One ore more nodes is over 90% capacity" ).addClass( "status-red" );
                } else if ( nodePods
                    && !$maxPodCount.hasClass( "status-red" )
                    && ( nodePods / maxPods * 100 ) > 80 ) {
                    $maxPodWarning.attr( "title", "One ore more nodes is over 80% capacity" ).addClass( "status-yellow" );
                }
            }

            let nodeName = nodeReport.name;
            let rowId = "node-" + utils.buildValidDomId( nodeName );

            console.log( `building ${ rowId }` );


            let $row = $( `#${ rowId }`, $nodeInstanceBody );
            $row.removeClass( "purge" );

            let need_to_add_row = $row.length == 0;
            if ( need_to_add_row ) {
                console.log( `Adding new row: ${ nodeName }` );


                $row = jQuery( '<tr/>', {
                    id: rowId,
                    "data-name": nodeName,
                    "data-host": nodeName,
                    "data-self": nodeReport.apiPath
                } );

                $( "tr.path-specifier th", $nodeTableHeader ).each( function () {
                    let fieldPath = $( this ).data( "path" );
                    jQuery( '<td/>', {
                        "data-path": fieldPath,
                        "data-show-object": $( this ).data( "show-object" )
                    } ).appendTo( $row );
                } );

                $row.appendTo( $nodeInstanceBody );

                if ( nodeSelected().length === 0 ) {
                    nodeSelected( rowId );
                }

            }



            $( "td", $row ).each( function () {
                buildColumn( $( this ),
                    nodeReport,
                    need_to_add_row );
            } );


        }

        $( "tr.purge", $nodeInstanceBody ).remove();

        if ( isNewTable ) {

            console.log( `\n\n registering events for new table` );
            // themes: csap , csapSummary
            $nodeTable.tablesorter( {
                sortList: [ [ 0, 0 ] ],
                theme: 'csapSummary',
                textExtraction: {
                    0: function ( node, table, cellIndex ) {
                        return $( "span", node ).text();
                    },
                }
            } );
        }


        $nodeTable.trigger( "update" );


    }


    function nodeSelected( rowId ) {
        console.log( `instanceSelected() : ${ rowId }` );

        if ( rowId ) {
            //$( "tr.selected", $detailsBody ).removeClass( "selected" ) ;
            $( `tr#${ rowId }`, $nodeInstanceBody ).toggleClass( "selected" );
        }
        //updateItemsSelectedMessage() ;
        return $( "tr.selected", $nodeInstanceBody );
    }


    function showPodsByOwner( forceHostRefresh, podName ) {
        let resourceReportUrl = APP_BROWSER_URL + "/kubernetes/pods";

        console.log( `resourceReportUrl(): ${ resourceReportUrl }` );
        // getActivityCount() ;

        utils.loading( "Collecting pod report..." );
        let parameters = {
            namespace: namespaceSelected(),
            project: utils.getActiveProject(),
            podName: podName
        };

        if ( namespaceSelected() == ALL_NAMESPACES ) {
            parameters = { project: utils.getActiveProject() };
        }

        let $contentLoaded = new $.Deferred();
        $podIncludeFilter.val( "" );
        $podClearFilter.hide();

        $.getJSON(
            resourceReportUrl,
            parameters )

            .done( function ( summaryReport ) {

                if ( podName ) {
                    buildPodContainerTable( summaryReport );
                } else {
                    buildPodGroups( summaryReport );
                    latestOwnerReports = summaryReport;
                }
                $contentLoaded.resolve();
                utils.loadingComplete();





            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Retrieving service summary", errorThrown );
            } );

        return $contentLoaded;
    }


    function sortByOwnerCores( a, b ) {
        // ( a, b ) => ( a.metrics.cores > b.metrics.cores ) ? 1 : -1
        if ( a.cores && b.cores ) {
            return ( a.cores > b.cores ) ? 1 : -1;
        } else if ( a.cores ) {
            return 1;
        } else {
            return -1;
        }
    }

    function buildPodGroups( podOwnerReports ) {

        $hideEmpty.parent().css( "visibility", "hidden" );

        let $latest = jQuery( '<div/>', {
            class: "",
        } );

        let sortedOwnerReports;
        if ( $podSortCpu.is( ":checked" ) ) {
            sortedOwnerReports = ( podOwnerReports ).sort( sortByOwnerCores ).reverse();
        } else {
            sortedOwnerReports = ( podOwnerReports ).sort( ( a, b ) => ( a.owner > b.owner ) ? 1 : -1 );
        }

        let podOwnerRequested;
        for ( let podOwnerReport of sortedOwnerReports ) {

            let podOwnerName = podOwnerReport.owner;
            if ( podOwnerName.length > longestNameSize ) {
                longestNameSize = podOwnerName.length;
            }

            let $summary = jQuery( '<div/>', {
                class: "summary pod-name",
                title: podOwnerReport.ownerName,
                "data-name": podOwnerName
            } );
            $latest.append( $summary );



            let $imageDiv = jQuery( '<div/>', {} );
            $summary.append( $imageDiv );
            let imagePath = `${ IMAGES_URL }/${ utils.getClusterImage( podOwnerName, null ) }`;

            jQuery( '<img/>', { src: imagePath } ).appendTo( $imageDiv );

            let $infoDiv = jQuery( '<div/>', {} );
            $summary.append( $infoDiv );
            let $summaryLine = jQuery( '<div/>', {} ).appendTo( $infoDiv );
            jQuery( '<span/>', {
                class: "pod-owner-name",
                text: podOwnerName
            } ).appendTo( $summaryLine );

            //ownerKind

            //
            //  host and kind
            //
            //            let $deployInfo = jQuery( '<div/>', {
            //                title: "pod host",
            //                class: "deploy-info"
            //            } ).appendTo( $infoDiv ) ;
            jQuery( '<span/>', {
                class: `deploy-info flex-right-info`,
                text: podOwnerReport.ownerKind
            } ).appendTo( $summaryLine );



            let $status = jQuery( '<div/>', { class: "status" } );
            $status.appendTo( $infoDiv );


            let totalRunning = 0;
            let totalPending = 0;
            let totalSucceeded = 0;
            let totalStopped = 0;
            let totalRestarts = 0;
            let totalAlerts = 0;
            let totalContainers = 0;
            for ( let podReport of podOwnerReport.pods ) {

                if ( podRequested
                    && podRequested === podReport.name ) {
                    podOwnerRequested = podOwnerName;
                }

                if ( podReport.status == "running" ) {
                    totalRunning++;
                }

                if ( podReport.status == "succeeded" ) {
                    totalSucceeded++;
                }
                if ( podReport.status == "pending" ) {
                    totalPending++;
                }
                if ( podReport.status == "stopped" ) {
                    totalStopped++;
                }

                totalContainers += podReport[ "container-count" ];
                totalRestarts += podReport[ "container-restarts" ];
                totalAlerts += podReport[ "conditions-failed" ] + podReport[ "containers-not-ready" ];

            }
            if ( totalRunning > 0 ) {
                jQuery( '<span/>', {
                    class: "status-green",
                    title: "pods running",
                    text: totalRunning
                } ).appendTo( $status );
            }

            if ( totalSucceeded > 0 ) {
                jQuery( '<span/>', {
                    class: "status-success",
                    title: "pods completed successfully",
                    text: totalSucceeded
                } ).appendTo( $status );
            }

            if ( totalStopped > 0 ) {
                jQuery( '<span/>', {
                    class: "status-red",
                    title: "pods stopped",
                    text: totalStopped
                } ).appendTo( $status );
            }

            if ( totalRestarts > 0 ) {
                jQuery( '<span/>', {
                    class: "status-restarted",
                    title: "pod restarts",
                    text: totalRestarts
                } ).appendTo( $status );
            }

            if ( totalPending > 0 ) {
                jQuery( '<span/>', {
                    class: "status-yellow",
                    title: "pods pending",
                    text: totalPending
                } ).appendTo( $status );
            }

            if ( totalAlerts > 0 ) {
                jQuery( '<span/>', {
                    class: "status-warning",
                    title: "pod failed conditions and not ready",
                    text: totalAlerts
                } ).appendTo( $status );
            }

            if ( ( podOwnerName == ALL_NAMESPACES ) && servicesTotal[ UNREGISTERED_SERVICE ] ) {

                jQuery( '<span/>', {
                    class: "status-unregistered",
                    title: "Unregistered Services",
                    text: servicesTotal[ UNREGISTERED_SERVICE ]
                } ).appendTo( $status );
            }

            if ( totalRunning > 0
                && totalRunning !== totalContainers ) {
                jQuery( '<span/>', {
                    class: "status-run",
                    title: "containers",
                    text: totalContainers
                } ).appendTo( $status );
            }
            jQuery( '<span/>', {
                class: `deploy-info flex-right-info`,
                text: `${ podOwnerReport.cores } cores`
            } ).appendTo( $status );
        }


        $summaryPanel.empty();
        $summaryPanel.removeClass();

        let displayType = $( "#panel-layout" ).val();
        $summaryPanel.addClass( `deployment-panel ${ $panelLayoutSelect.val() }` );

        $summaryPanel.append( $latest.html() );
        reDraw();

        //        $( ".status-run", $summaryPanel ).click( function ( event ) {
        //            alertify.csapInfo(" containers: ") ;
        //            event.stopPropagation() ;
        //        } ) ;

        $( ".summary", $summaryPanel ).click( function () {
            let name = $( this ).data( "name" );
            selectPodOwner( name );
        } );


        if ( podOwnerRequested ) {

            setTimeout( function () {
                selectPodOwner( podOwnerRequested );
            }, 500 );

        }


    }


    function selectPodOwner( name ) {

        if ( !name ) {
            return $podOwnerSelection.text();
        }

        console.log( `selectPod: ${ name }` );
        $containerInstances.hide();
        $containerInstanceBody.empty();
        $podOwnerSelection.text( name );
        $podInstanceMenu.data( "name", name );
        $( "#pod-name", $podInstanceMenu ).text( name );
        $podInstanceBody.empty();
        if ( !latestOwnerReports ) {

            $.when( showPodsByOwner() ).done( function () {
                utils.launchMenu( "services-tab,kpod-instances" )
            } )

        } else {
            utils.launchMenu( "services-tab,kpod-instances" );
        }

    }

    function showPodInstances( blockingUpdate ) {

        console.log( `showPodInstances` );
        $podNameItem.show();

        if ( blockingUpdate ) {
            $podInstanceBody.empty();
            $containerInstances.hide();
            $containerInstanceBody.empty();
        }

        let isNewTable = $( "tr", $podInstanceBody ).length === 0;
        let autoShowContainer = false;

        //$podMenuItems.css( "display", "flex" ) ;





        let selectedPodOwner = $podOwnerSelection.text();

        let selectedPodReport;
        for ( let podOwnerReport of latestOwnerReports ) {

            let podOwnerName = podOwnerReport.owner;
            if ( podOwnerName === selectedPodOwner ) {
                selectedPodReport = podOwnerReport;
            }

        }

        if ( !selectedPodReport ) {
            console.error( `Failed to find ${ selectedPodOwner }`, latestOwnerReports );
            return;
        }
        $( "tr", $podInstanceBody ).addClass( "purge" );

        for ( let podReport of selectedPodReport.pods ) {

            let podName = podReport.name;
            let rowId = "pod-" + utils.buildValidDomId( podName );

            console.debug( `building ${ rowId }` );

            $podNamespace.text( `Namespace: ${ podReport.namespace }` );


            let $row = $( `#${ rowId }`, $podInstanceBody );
            $row.removeClass( "purge" );

            let need_to_add_row = $row.length == 0;
            if ( need_to_add_row ) {

                console.debug( `Adding new row: ${ podName }` );

                let containerNames = podReport[ "container-names" ];

                if ( Array.isArray( containerNames )
                    && containerNames.length > 1
                    && selectedPodReport.pods.length === 1 ) {
                    autoShowContainer = true;
                }

                $row = jQuery( '<tr/>', {
                    id: rowId,
                    "data-name": podName,
                    "data-namespace": podReport.namespace,
                    "data-host": podReport.host,
                    "data-containers": containerNames,
                    "data-self": podReport.apiPath
                } );

                $( "th", $podTableHeader ).each( function () {
                    let fieldPath = $( this ).data( "path" );
                    let columnClass = "";
                    if ( $( this ).hasClass( "image" ) ) {
                        columnClass = "image";
                    }
                    jQuery( '<td/>', {
                        class: columnClass,
                        "data-path": fieldPath,
                        "data-show-object": $( this ).data( "show-object" )
                    } ).appendTo( $row );
                } );

                $row.appendTo( $podInstanceBody );

                console.debug( `podRequested: ${ podRequested }, podName: ${ podName } ` );
                if ( !podRequested
                    && podInstanceSelected().length == 0 ) {
                    podInstanceSelected( rowId );
                } else if ( podRequested == podName ) {
                    podInstanceSelected( rowId );

                }

            }



            $( "td", $row ).each( function () {
                buildColumn( $( this ),
                    podReport,
                    need_to_add_row );
            } );
        }
        podRequested = null;

        $( "tr.purge", $podInstanceBody ).remove();

        if ( isNewTable ) {

            //$podTable.trigger("destroy");
            console.log( `\n\n registering events for new table, parent id: ${ $podTable.parent().attr( "id" ) }` );
            // themes: csap , csapSummary
            $podTable.tablesorter( {
                sortList: [ [ 1, 0 ] ],
                theme: 'csapSummary'
            } );
        }


        $podTable.trigger( "updateAll" );

        if ( autoShowContainer ) {
            showPodContainers( selectedPodReport.pods[ 0 ].name );
        }
    }


    function podInstanceSelected( rowId ) {
        console.debug( `instanceSelected() : ${ rowId }` );

        if ( rowId ) {
            //$( "tr.selected", $detailsBody ).removeClass( "selected" ) ;
            $( `tr#${ rowId }`, $podInstanceBody ).toggleClass( "selected" );
        }
        //updateItemsSelectedMessage() ;
        return $( "tr.selected", $podInstanceBody );
    }

    function showPodContainers( podName ) {
        showPodsByOwner( false, podName );
    }

    function buildPodContainerTable( podReport ) {
        console.log( `buildPodContainerTable: `, podReport );

        $containerInstances.show();
        let label = `${ podReport.metadata.namespace }: ${ podReport.metadata.name }`;
        $( ".options", $containerInstances ).text( label );
        $containerInstanceBody.empty();
        let isNewTable = true;

        // should only be a single pod 
        //        for ( let container of podReport.spec.containers ) {
        //
        //        }

        $( "tr", $containerInstanceBody ).addClass( "purge" );

        let initAndRunContainers = new Array();
        if ( podReport.spec.containers ) {
            initAndRunContainers.push( ...podReport.spec.containers )
        }
        if ( podReport.spec.initContainers ) {
            initAndRunContainers.push( ...podReport.spec.initContainers )
        }

        for ( let container of initAndRunContainers ) {

            let containerName = container.name;
            // merge status into container object for use in table column auto detect
            if ( podReport.status.containerStatuses ) {
                for ( let containerStatus of podReport.status.containerStatuses ) {
                    if ( containerStatus.name === containerName ) {
                        Object.assign( container, containerStatus );
                        break;
                    }
                }
            }
            if ( podReport.status.initContainerStatuses ) {
                for ( let containerStatus of podReport.status.initContainerStatuses ) {
                    if ( containerStatus.name === containerName ) {
                        Object.assign( container, containerStatus );
                        break;
                    }
                }
            }

            // merge metrics into container object for use in table column auto detect
            for ( let containerMetrics of podReport.containerMetrics ) {
                if ( containerMetrics.name === containerName ) {
                    Object.assign( container, containerMetrics );
                    break;
                }
            }


            let rowId = "container-" + utils.buildValidDomId( containerName );

            console.debug( `building ${ rowId }` );


            let $row = $( `#${ rowId }`, $containerInstanceBody );
            $row.removeClass( "purge" );

            let need_to_add_row = $row.length == 0;
            if ( need_to_add_row ) {

                console.debug( `Adding new row: ${ containerName }` );

                $row = jQuery( '<tr/>', {
                    id: rowId,
                    "data-name": containerName,
                    "data-namespace": podReport.namespace,
                    "data-host": podReport.host,
                    "data-self": podReport.apiPath
                } );

                $( "th", $containerTableHeader ).each( function () {
                    let fieldPath = $( this ).data( "path" );
                    let columnClass = "";
                    if ( $( this ).hasClass( "image" ) ) {
                        columnClass = "image";
                    }
                    jQuery( '<td/>', {
                        class: columnClass,
                        "data-path": fieldPath,
                        "data-show-object": $( this ).data( "show-object" )
                    } ).appendTo( $row );
                } );

                $row.appendTo( $containerInstanceBody );

                console.debug( `podRequested: ${ podRequested }, podName: ${ containerName } ` );


            }



            $( "td", $row ).each( function () {
                buildColumn( $( this ),
                    container,
                    need_to_add_row );
            } );
        }
        podRequested = null;

        $( "tr.purge", $containerInstanceBody ).remove();

        if ( isNewTable ) {

            console.log( `\n\n registering events for new table` );
            // themes: csap , csapSummary
            $containerTable.tablesorter( {
                sortList: [ [ 2, 1 ] ],
                theme: 'csapSummary'
            } );
        }


        $containerTable.trigger( "update" );

    }


    function buildColumn( $column, attributeReport, need_to_add_row ) {

        let fieldPath = $column.data( "path" );
        let fieldValue = utils.json( fieldPath, attributeReport );


        $column.data( "text", fieldValue );


        if ( fieldPath === "name" ) {

            buildNameColumn( fieldValue, $column, attributeReport, need_to_add_row );

        } else if ( fieldPath === "conditions"
            || fieldPath.endsWith( "coresPercent" )
            || fieldPath.endsWith( "memoryPercent" ) ) {

            $column.empty();

            let status = "status-yellow";
            let details = "";
            if ( !fieldValue ) {
                status = "status-green";
            }

            if ( fieldPath.endsWith( "coresPercent" )
                || fieldPath.endsWith( "memoryPercent" ) ) {
                details = fieldValue;
                if ( fieldValue < 80 ) {
                    status = "status-green";
                } else if ( fieldValue > 90 ) {
                    status = "status-red";
                }
            }

            jQuery( '<span/>', {
                class: status,
                text: details
            } ).appendTo( $column );

        } else if ( fieldPath === "host" ) {
            $column.html( utils.buildAgentLink( fieldValue ) );

        } else if ( fieldPath === "container-count" ) {

            let text = fieldValue;
            let $showContainersButton = jQuery( '<button/>', {
                title: "Show Containers",
                class: "csap-icon csap-search"
            } ).click( function () {

                let $row = $( this ).closest( "tr" );

                showPodContainers( $row.data( "name" ) );

            } );

            $column.html( $showContainersButton );
            $column.append( text );

        } else {
            if ( Number.isInteger( fieldValue ) ) {
                $column.addClass( "numeric" );
            }


            $column.html( fieldValue );

        }
    }

    function buildNameColumn( fieldValue, $column, attributeReport, need_to_add_row ) {
        if ( need_to_add_row ) {

            let $instanceContainer = jQuery( '<div/>', { class: "instance-container" } )
                .appendTo( $column );


            let $nameLabel = jQuery( '<label/>', {
                class: "csap"
            } ).appendTo( $instanceContainer );

            let $hostText = jQuery( '<span/>', {
                class: "host-status",
                html: "&nbsp;"
            } ).appendTo( $nameLabel );

            console.debug( `fieldValue: ${ fieldValue } attributeReport`, attributeReport );
            let isNodeReport = attributeReport[ "master" ] != undefined;
            let isVolumeReport = attributeReport[ "ref-name" ] != undefined;
            let isPodReport = attributeReport[ "first-image" ] != undefined;
            let isContainerReport = attributeReport[ "image" ] != undefined;

            // placedholder for css
            let helpMessage = `View kubernetes describe`;

            let $kubectlDescribeButton = jQuery( '<button/>', {
                class: "csap-button-icon csap-info",
                title: helpMessage,
                text: ` ${ fieldValue }`
            } ).appendTo( $nameLabel );

            let $handleClickFunction = function () {

                let isLogsButton = $( this ).hasClass( "csap-logs" );

                let $tableBody = $column.closest( "tbody" );

                // remove previous selections
                $( "tr.selected", $tableBody ).removeClass( "selected" );

                let $row = $( this ).closest( "tr" );
                $row.addClass( "selected" );

                let menuNavigation = "services-tab,node-describe";
                let $menuNav = $nodeMenuItems;
                if ( isVolumeReport ) {
                    menuNavigation = "services-tab,volume-describe";
                    $menuNav = $volumeMenuItems;
                } else if ( isPodReport || isContainerReport ) {
                    menuNavigation = "services-tab,pod-describe";
                    if ( isLogsButton ) {
                        menuNavigation = "services-tab,pod-logs";
                    }
                    $menuNav = $podMenuItems;

                    // mark selected for logs
                    let $row = $( this ).closest( "tr" );

                    $row.addClass( "selected" );


                }
                utils.launchMenu( menuNavigation );
                $menuNav.show();
            }

            $kubectlDescribeButton.click( $handleClickFunction );



            if ( isPodReport || isContainerReport ) {
                let $podLogButton = jQuery( '<button/>', {
                    class: "csap-icon csap-logs",
                    title: "View pod logs",
                } ).appendTo( $nameLabel );

                $podLogButton.click( $handleClickFunction );
            }


        }

        let instanceStatusClass = `status-red`;
        let succeedStatus = utils.json( "state.terminated.exitCode", attributeReport );
        if ( succeedStatus === 0 ) {
            instanceStatusClass = `status-success`;
        } else if ( attributeReport.ready
            || ( attributeReport.status == "running" ) ) {
            instanceStatusClass = `status-green`;
        } else if ( attributeReport.status == "succeeded" ) {
            instanceStatusClass = `status-success`;
        } else if ( attributeReport.status == "pending" ) {
            instanceStatusClass = `status-yellow`;
        }


        // update running status
        let $hostData = $( "span.host-status", $column );
        $hostData.removeClass();
        $hostData.addClass( `host ${ instanceStatusClass }` );

    }




    function showPodsByNamespace( forceHostRefresh ) {
        let resourceReportUrl = APP_BROWSER_URL + "/kubernetes/namespace/pods";

        $hideEmpty.parent().css( "visibility", "visible" );


        console.log( `resourceReportUrl(): ${ resourceReportUrl }` );
        // getActivityCount() ;

        utils.loading( "Collecting pod report..." );
        let parameters = {
            blocking: forceHostRefresh,
            project: utils.getActiveProject()
        };

        let $contentLoaded = new $.Deferred();
        $podIncludeFilter.val( "" );
        $podClearFilter.hide();

        $.getJSON(
            resourceReportUrl,
            parameters )

            .done( function ( summaryReport ) {

                $contentLoaded.resolve();
                utils.loadingComplete();

                buildPodSummary( summaryReport );

            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Retrieving service summary", errorThrown );
            } );

        return $contentLoaded;
    }

    function sortByCores( a, b ) {
        // ( a, b ) => ( a.metrics.cores > b.metrics.cores ) ? 1 : -1
        if ( a.metrics && b.metrics ) {
            return ( a.metrics.cores > b.metrics.cores ) ? 1 : -1;
        } else if ( a.metrics ) {
            return 1;
        } else {
            return -1;
        }
    }

    function buildPodSummary( podNamespaces ) {

        if ( podNamespaces.length == 0 ) {
            $summaryPanel.empty();
            jQuery( '<div/>', {
                class: "csap-white",
                text: "Kubernetes not available"
            } ).appendTo( $summaryPanel );
            return;
        }
        let $latest = jQuery( '<div/>', {
            class: "",
        } );

        let sortedPodNamespaces;
        if ( $podSortCpu.is( ":checked" ) ) {
            sortedPodNamespaces = ( podNamespaces ).sort( sortByCores ).reverse();
        } else {
            sortedPodNamespaces = ( podNamespaces ).sort( ( a, b ) => ( a.name > b.name ) ? 1 : -1 );
        }
        for ( let podNameSpace of sortedPodNamespaces ) {


            if ( $hideEmpty.is( ':checked' )
                && ( podNameSpace.total === 0 ) ) {
                continue;
            }

            let podNamespaceName = podNameSpace.name;
            if ( podNamespaceName.length > longestNameSize ) {
                longestNameSize = podNamespaceName.length;
            }

            let $summary = jQuery( '<div/>', {
                class: "summary",
                "data-name": podNamespaceName,
                title: podNamespaceName
            } );
            $latest.append( $summary );

            let $imageDiv = jQuery( '<div/>', {} );
            $summary.append( $imageDiv );
            //let imagePath = `${ IMAGES_URL }/32x32/network-workgroup.png` ;
            let imagePath = `${ IMAGES_URL }/${ utils.getClusterImage( podNamespaceName, "namespace" ) }`;
            jQuery( '<img/>', { src: imagePath } ).appendTo( $imageDiv );

            let $infoDiv = jQuery( '<div/>', {} );
            $summary.append( $infoDiv );
            jQuery( '<div/>', { text: podNamespaceName } ).appendTo( $infoDiv );

            let $status = jQuery( '<div/>', { class: "status" } );
            $status.appendTo( $infoDiv );

            let totalRunning = podNameSpace.running;
            let totalPending = podNameSpace.pending;
            let totalStopped = podNameSpace.stopped;
            let totalSuccess = podNameSpace.succeeded;
            let totalRestarts = podNameSpace.restarts;
            let totalAlerts = podNameSpace[ "conditions-failed" ] + podNameSpace[ "containers-not-ready" ];

            if ( totalRunning > 0 ) {
                jQuery( '<span/>', {
                    class: "status-green",
                    title: "pods running",
                    text: totalRunning
                } ).appendTo( $status );
            }

            if ( totalSuccess > 0 ) {
                jQuery( '<span/>', {
                    class: "status-success",
                    title: "pods completed successfully",
                    text: totalSuccess
                } ).appendTo( $status );
            }

            if ( totalStopped > 0 ) {
                jQuery( '<span/>', {
                    class: "status-red",
                    title: "pods stopped",
                    text: totalStopped
                } ).appendTo( $status );
            }

            if ( totalRestarts > 0 ) {
                jQuery( '<span/>', {
                    class: "status-os",
                    title: "pod restarts",
                    text: totalRestarts
                } ).appendTo( $status );
            }

            if ( totalPending > 0 ) {
                jQuery( '<span/>', {
                    class: "status-yellow",
                    title: "pods pending",
                    text: totalPending
                } ).appendTo( $status );
            }

            if ( totalAlerts > 0 ) {
                jQuery( '<span/>', {
                    class: "status-warning",
                    title: "pod failed conditions and not ready",
                    text: totalAlerts
                } ).appendTo( $status );
            }

            if ( podNameSpace.metrics ) {
                let memoryGb = ( podNameSpace.metrics.memoryInMb / 1024 ).toFixed( 2 );
                let metricNotes = `Metrics:`
                    + `\n CPU (Cores): \t ${ podNameSpace.metrics.cores }`
                    + `\n Memory (Gb): \t ${ memoryGb }`
                    + `\n Containers: \t\t ${ podNameSpace.metrics.containers }`;
                jQuery( '<span/>', {
                    class: "metrics flex-right-info",
                    title: metricNotes,
                    text: `${ podNameSpace.metrics.cores } cores`
                } ).appendTo( $status );
            }

            //            if ( ( podNamespaceName == ALL_NAMESPACES ) ) {
            //
            //                jQuery( '<span/>', {
            //                    class: "status-unregistered",
            //                    title: "total pods",
            //                    text: podNameSpace.total
            //                } ).appendTo( $status ) ;
            //            }

        }


        $summaryPanel.empty();
        $summaryPanel.removeClass();

        $summaryPanel.addClass( `deployment-panel ${ $panelLayoutSelect.val() }` );

        $summaryPanel.append( $latest.html() );
        reDraw();

        $( ".summary", $summaryPanel ).click( function () {
            let name = $( this ).data( "name" );
            namespaceSelected( name );
            showPodsByOwner();
        } );

    }

    function browseToPod( pod, namespace ) {
        podRequested = pod;
        //browsePodNamespace( namespace ) ;
        console.log( `browseToPod() pod: ${ pod }, namespace: ${ namespace }` );
        namespaceSelected( namespace );
        showPodsByOwner();
    }

    function browsePodNamespace( namespace ) {
        utils.launchMenu( "services-tab,kpods" );
        setTimeout( function () {
            namespaceSelected( namespace );
            showPodsByOwner();
        }, 500 );
    }

    function isNamespaceSelectionEmpty() {
        return namespaceSelected() == "";
    }

    function namespaceSelected( name ) {

        if ( !name ) {
            return $namespaceSelection.data( "name" );

        } else {
            $namespaceSelection.data( "name", name );
            $namespaceSelection.text( name );
            $namespaceSelectContainer.show();
        }
    }


    function reDraw() {

        let numPanelItems = $( "div.summary", $summaryPanel ).length;
        console.debug( `numPanelItems: ${ numPanelItems }` );

        let singleColumnMaximum = 12;  // 10
        let multiColumnMinimum = 16;  // 16

        if ( ( $panelLayoutSelect.val() === "columns" )
            && numPanelItems > singleColumnMaximum ) {

            let panelWidth = $summaryPanel.outerWidth();
            let columnsEstimate = 2;
            if ( panelWidth < 600 ) {
                columnsEstimate = 1;
            }

            if ( numPanelItems > multiColumnMinimum ) {

                let numChars = longestNameSize;
                if ( numChars > 20 ) {
                    numChars = 20;
                }
                let serviceWidth = numChars * 10 + 50;
                if ( serviceWidth < 400 ) {
                    serviceWidth = 300;
                }
                columnsEstimate = Math.floor( panelWidth / serviceWidth );
                console.debug( `numPanelItems: ${ numPanelItems } columnsEstimate: ${ columnsEstimate } panelWidth: ${ panelWidth }, serviceWidth: ${ serviceWidth } ` );
                if ( columnsEstimate < 2 ) {
                    console.log( "Forcing minimum column size" );
                    //columnsEstimate = 2 ;
                }
            } else {
                console.debug( `numPanelItems: ${ numPanelItems } columnsEstimate: ${ columnsEstimate } ` );
            }
            $summaryPanel.css( "columns", `20em ${ columnsEstimate.toString() }` );

            //            $summaryPanel.css( "columns", `20em auto` ) ;
            $summaryPanel.css( "column-gap", `3em` );

        } else {
            $summaryPanel.css( "columns", "" );
            $summaryPanel.css( "column-gap", `` );
        }
    }


}