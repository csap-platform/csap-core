

// // http://requirejs.org/docs/api.html#packages
// // Packages are not quite as ez as they appear, review the above
// require.config( {
//     paths: {
//         mathjs: "../../csapLibs/mathjs/modules/math.min"
//     },
//     packages: [
//         { name: 'graphPackage',
//             location: '../../graphs/modules/graphPackage', // default 'packagename'
//             main: 'ResourceGraph'                // default 'main' 
//         }
//     ]
// } ) ;
// define( [ "browser/utils", "mathjs", "graphPackage", "../../../performance/modules/model" ], function ( utils, mathjs, ResourceGraph, model ) {


import ResourceGraph from "../../graphs/ResourceGraph.js"


import _dom from "../../utils/dom-utils.js";



import utils from "../utils.js"


import model from "../../performance/model.js"


const osAppGraphs = os_app_graphs();

export default osAppGraphs


function os_app_graphs() {

    const $browserTab = $( "#services-tab-analytics" );
    const $windowPanel = $( "#graphDiv", $browserTab );
    const $osTable = $( "#serviceStats table", $browserTab );

    const $panelInfo = $( "#panelInfo div", $windowPanel );
    const $panelControls = $( "#panelControls", $windowPanel );
    const $graphContainer = $( "#graphDiv", $browserTab );

    window.csapGraphSettings = {
        graph: "updated-on-invoke",
        service: "updated-on-invoke"
    }


    let _lastGraph = null;
    let _needsInit = true;
    // let _renderTimer = null;
    let _graphContainers = new Object();

    let servicePerformanceId, serviceNameOnly;



    return {

        show: function ( $launchButton, serviceName ) {

            setupAndShow( $launchButton, serviceName );


        },
        hide: function ( event ) {
            //console.log("hiding: " + $(this).attr('class') ) ;
            hide( $( this ) );
        },
        updateHosts: function ( defaultHosts ) {
            updateHosts( defaultHosts );
            $graphContainer.hide();
        }

    }

    function setupAndShow( $launchButton, serviceName ) {
        console.log( `setupAndShow() service: ${ serviceName }` );

        //console.log("Showing: " + $(this).attr('class') ) ;
        //$graphContainer.show();
        serviceNameOnly = serviceName;
        servicePerformanceId = serviceName;
        if ( utils.isSelectedKubernetes() ) {
            servicePerformanceId = utils.selectedKubernetesPod();
            let launchedFrom = $launchButton.closest( "tr" ).data( "type" );
            console.log( `setupAndShow() pod: ${ servicePerformanceId } launched using type: ${ launchedFrom }` );
            if ( launchedFrom == "os-process" ) {
                serviceNameOnly = servicePerformanceId;
            }
        }

        let windowHeight = $( window ).outerHeight( true );
        let windowWidth = $( window ).outerWidth( true );
        let navWidth = utils.findNavigation( "#tabs" ).outerWidth( true );
        let firstColumnWidth = $( "td:first", $osTable ).outerWidth( false );

        let widthVar = 100;
        let tableWidthOffset = $osTable.offset().left + firstColumnWidth + ( widthVar - 30 );

        let graphWidth = windowWidth - firstColumnWidth - navWidth - widthVar;

        console.log( `setupAndShow() graphWidth: ${ graphWidth } Width: ${ windowWidth } firstColumnWidth: ${ firstColumnWidth } left offset: ${ Math.round( tableWidthOffset ) }` );
        resize( windowHeight, graphWidth );


        $.when( model.updateLifecycleModel() ).done( function () {
            show( $launchButton.parent() );
            $windowPanel.offset( {
                left: Math.round( tableWidthOffset ) + 10
            } );
        } )
    }

    function getHeight() {
        return $graphContainer.outerHeight( true ) - 250;
    }

    function getWidth() {

        //        logger.info($graphContainer.attr("id")) ;
        return $graphContainer.outerWidth( true ) - 50;

    }

    function resize( height, width ) {

        $graphContainer.css( "height", Math.round( height ) );
        $graphContainer.css( "width", Math.round( width ) );

        window.csapGraphSettings.height = getHeight();
        window.csapGraphSettings.width = getWidth();

        if ( _lastGraph ) {
            _lastGraph.reDraw();
        }
    }

    function updateHosts( defaultHosts ) {



        let $hostSelectionContainer = $( "#hostDisplay", $windowPanel ).empty();
        init();


        //hosts.push("csap-dev20") ;

        let $rows = utils.selectedInstanceRows();

        if ( utils.isSelectedKubernetes()
            && $( "#all-pods" ).is( ':checked' ) ) {
            $rows = utils.instanceRows();
        }

        let hosts = new Set();
        $rows.each( function ( index ) {
            let hostname = $( this ).data( "host" );
            if ( hostname ) {
                hosts.add( hostname );
            }
        } );

        if ( hosts.size === 0 ) {
            hosts = defaultHosts;
        }


        console.log( "updateHosts() Hosts selected", hosts );

        for ( let host of hosts ) {
            let $label = jQuery( '<input/>', {
                "data-host": host,
                class: "instanceCheck",
                checked: "checked",
                type: "checkbox"
            } ).appendTo( $hostSelectionContainer );
        }

        //        
        //        for ( let graphName in _graphContainers ) {
        //            _graphContainers[ graphName ].settingsUpdated();
        //        }

        //        if ( true ) {
        //            alertify.notify("Hosts updated - reopen graph to view.");
        //        }
        _graphContainers = new Object();
        $( ".gpanel", $windowPanel ).empty();

    }

    function init() {

        if ( _needsInit ) {
            _needsInit = false;

            $( "#closePanel" ).click( function () {
                //resize( _defaultHeight, _defaultWidth ) ;
                // $panelControls.hide();
                $windowPanel.hide();
                return false;
            } );
            $( "#maxPanel" ).click( function () {
                $( "#maxPanel" ).hide();
                resize( $( window ).outerHeight( true ) - 100, $( window ).outerWidth( true ) - 100 );
                $windowPanel.offset( {
                    left: 50
                } );
            } );

            $( "#all-pods" ).click( function () {
                updateHosts();
            } )

            $( '#isStackHosts' ).change( function () {

                _lastGraph.reDraw();

            } );

        }
    }

    function hide( $resourceRow ) {

    }

    function show( $resourceRow ) {

        $( "#maxPanel" ).show();
        window.csapGraphSettings.service = servicePerformanceId;
        renderGraph( $resourceRow );

    }

    function renderGraph( $resourceRow ) {
        window.csapGraphSettings.graph = $resourceRow.data( "graph" );

        let servicePanelType = $resourceRow.data( "type" );


        console.log( `renderGraph()  type: ${ servicePanelType } ` );
        //console.log("renderGraph() servicePanelType", servicePanelType) ;

        let graphType = servicePanelType;

        // convert new CSAP types to analytics types for queries
        if ( servicePanelType == "app" ) {
            graphType = "application";
        } else if ( servicePanelType == "java" ) {
            graphType = "java";
        }

        if ( ( servicePanelType === "app" ) && utils.isSelectedKubernetes() ) {
            console.log( `Updating serviceName: ${ serviceNameOnly } ` );
            window.csapGraphSettings.service = serviceNameOnly;
            $( '#isStackHosts' ).show();
        } else {
            //$( '#isStackHosts' ).val( 0 ).hide() ;
        }
        $( '#isStackHosts' ).show();
        window.csapGraphSettings[ "type" ] = graphType;

        let containerId = "#" + graphType + "Container";

        $windowPanel.show();
        $( ".gpanel", $windowPanel ).hide();
        let $graphPanel = $( containerId, $windowPanel ).show();


        $panelInfo.hide();
        let message = $( ".resourceWarning", $resourceRow ).attr( "title" );

        if ( message ) {
            $panelInfo.text( message ).show();
        }

        _lastGraph = _graphContainers[ graphType ];
        if ( _lastGraph != undefined ) {
            _lastGraph.reDraw();
            return;
        }


        //$graphContainer.append( $resourceRow.attr( 'class' ) );

        // $( "#numberOfDays", $graphContainer ).val( 2 );
        $( ".useHistorical", $windowPanel ).prop( 'checked',
            true );
        $( '.numDaysSelect option[value="0"]', $windowPanel ).remove();
        $( ".datepicker", $windowPanel ).attr( "placeholder", "Last 24 Hours" );

        // $( ".triggerJmxCustom" ).html( serviceFilter );
        let graphFlag = graphType;
        if ( graphType == "jmxCustom" ) {
            graphFlag = "jmx";
            $( "#jmxCustomWhenClassSet" )
                .addClass( "triggerJmxCustom" )
                .text( serviceFilter );
        } else {
            $( "#jmxCustomWhenClassSet" )
                .removeClass( "triggerJmxCustom" )
                .text( "" );
        }

        //  resource, service, jmx, jmxCustom
        _lastGraph = buildGraphObject( containerId, graphFlag );
        _graphContainers[ graphType ] = _lastGraph;
        $.when( _graphContainers[ graphType ].getGraphLoadedDeferred() ).done( function () {
            $( "#initialMessage" ).hide();
        } );
    }

    function buildGraphObject( containerId, metricType ) {

        console.log( `buildGraphObject() constructing Resource graph for container: ${ containerId }, type: ${ metricType }`
            + " window.csapGraphSettings", window.csapGraphSettings );

        let theNewGraph = new ResourceGraph(
            containerId, metricType,
            uiSettings.life, uiSettings.appId, uiSettings.metricsDataUrl,
            model );

        return theNewGraph;
    }


}


