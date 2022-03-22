

// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
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
import _dom from "../../utils/dom-utils.js";

import utils from "../utils.js"

import ResourceGraph from "../../graphs/ResourceGraph.js"

export const performanceGraphs = performance_graphs();

function performance_graphs() {
    let activeGraphs = new Object();

    let graphBuilderFunctions = {
        system: buildSystemGraphs,
        service: buildOsGraphs,
        java: buildJavaGraphs,
        application: buildAppGraphs,
    }

    const $agentTab = $( "#agent-tab-content" );
    const $hostGraphs = $( "#vmGraphs", $agentTab );
    const $serviceGraphs = $( "#serviceGraphs", $agentTab );

    const $javaGraphs = $( "#java-graphs", $agentTab );
    const $applicationGraphs = $( "#application-graphs", $agentTab );

    const $javaNameSelect = $( "select.java-service-names", $agentTab );
    const $appNameSelect = $( "select.app-service-names", $agentTab );

    let init;



    // Support for custom views
    const javaCustomViews = {
        "Java Heap": {
            graphs: [ "Cpu_As_Reported_By_JVM", "heapUsed", "minorGcInMs" ],
            graphMerged: { "heapUsed": "heapMax", "minorGcInMs": "majorGcInMs" },
            graphSize: {
                Cpu_As_Reported_By_JVM: { "width": "100%", "height": "100" },
                heapUsed: { "width": "100%", "height": "45%" },
                minorGcInMs: { "width": "100%", "height": "45%" }
            }
        },
        "Tomcat Http": {
            graphs:
                [ "Cpu_As_Reported_By_JVM", "sessionsCount", "httpRequestCount", "httpKbytesReceived", "httpProcessingTime" ],
            graphMerged: { "httpKbytesReceived": "httpKbytesSent", "sessionsCount": "sessionsActive", "httpRequestCount": "tomcatConnections" }
        },
        "Java Thread": { graphs: [ "Cpu_As_Reported_By_JVM", "jvmThreadCount", "tomcatThreadsBusy" ], graphMerged: { "jvmThreadCount": "jvmThreadsMax", "tomcatThreadsBusy": "tomcatThreadCount" } }
    };




    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {
            // lazy

            console.log( `menuPath: ${ menuPath } ` );

            let $menuLoaded = null;

            if ( !init ) {
                registerForEvents();
            }

            let selectedGraph = activeGraphs[ menuPath ];

            if ( ( selectedGraph == null ) || forceHostRefresh ) {
                activeGraphs[ menuPath ] = graphBuilderFunctions[ menuPath ]();
                $menuLoaded = activeGraphs[ menuPath ].getGraphLoadedDeferred();

            } else {
                // window may have been resized since last display
                selectedGraph.reDraw();
            }

            return $menuLoaded;
        }

    };

    function registerForEvents() {
        init = true;
        utils.getServiceSelector().change( function () {

            for ( let graph in activeGraphs ) {
                graphBuilderFunctions[ graph ]();
            }

        } );
    }



    function buildSystemGraphs() {
        console.log( `\n\n  ===== Building  ResourceGraph   ======\n\n` );
        $hostGraphs.empty();
        let hostResourceGraph = new ResourceGraph(
            `#` + $hostGraphs.attr( "id" ),
            METRICS_HOST,
            utils.getActiveEnvironment(),
            utils.getAppId(),
            SERVICE_URL + "/metricsApi/" );

        return hostResourceGraph;

    }

    function buildOsGraphs() {

        $serviceGraphs.empty();
        let serviceResourceGraph = new ResourceGraph(
            `#` + $serviceGraphs.attr( "id" ),
            METRICS_OS_PROCESS,
            utils.getActiveEnvironment(),
            utils.getAppId(),
            SERVICE_URL + "/metricsApi/" );


        if ( utils.getPageParameters().has( "osGraph" ) ) {
            $.when( serviceResourceGraph.getGraphLoadedDeferred() ).done( function () {

                if ( utils.getPageParameters().has( "osGraph" ) ) {
                    $( "input.graphs", $serviceGraphs ).prop( "checked", false );
                    $( `#${ utils.getPageParameters().get( "osGraph" ) }CheckBox`, $serviceGraphs ).prop( "checked", true );
                    utils.getPageParameters().delete( "osGraph" );
                }
                serviceResourceGraph.settingsUpdated();


            } );
        }

        return serviceResourceGraph;
    }

    function buildJavaGraphs() {


        $javaGraphs.empty();
        let javaResourceGraph = new ResourceGraph(
            `#` + $javaGraphs.attr( "id" ),
            METRICS_JAVA,
            utils.getActiveEnvironment(),
            utils.getAppId(),
            SERVICE_URL + "/metricsApi/" );
        javaResourceGraph.addCustomViews( javaCustomViews );

        if ( utils.getPageParameters().has( "javaGraph" ) ) {
            $.when( javaResourceGraph.getGraphLoadedDeferred() ).done( function () {

                if ( utils.getPageParameters().has( "javaGraph" ) ) {
                    $( "input.graphs", $javaGraphs ).prop( "checked", false );
                    $( `#${ utils.getPageParameters().get( "javaGraph" ) }CheckBox`, $javaGraphs ).prop( "checked", true );
                    utils.getPageParameters().delete( "javaGraph" );
                }

                javaResourceGraph.settingsUpdated();

            } );
        }

        $javaNameSelect.off().change( function () {
            utils.getServiceSelector().val( $( this ).val() );
            utils.getServiceSelector().trigger( "change" );
            $( this ).val( "available..." );
        } );

        return javaResourceGraph;

    }

    function buildAppGraphs() {

        $appNameSelect.off().change( function () {
            utils.getServiceSelector().val( $( this ).val() );
            utils.getServiceSelector().trigger( "change" );
            $( this ).val( "available..." );
        } );


        $applicationGraphs.empty();

        let applicationResourceGraph = new ResourceGraph(
            `#` + $applicationGraphs.attr( "id" ),
            METRICS_APPLICATION,
            utils.getActiveEnvironment(),
            utils.getCsapUser(),
            SERVICE_URL + "/metricsApi/" );
        applicationResourceGraph.addCustomViews( javaCustomViews );

        if ( utils.getPageParameters().has( "appGraph" ) ) {
            $.when( applicationResourceGraph.getGraphLoadedDeferred() ).done( function () {

                if ( utils.getPageParameters().has( "appGraph" ) ) {
                    $( "input.graphs", $applicationGraphs ).prop( "checked", false );
                    $( `#${ utils.getPageParameters().get( "appGraph" ) }CheckBox`, $applicationGraphs ).prop( "checked", true );
                    utils.getPageParameters().delete( "appGraph" );
                }

                applicationResourceGraph.settingsUpdated();

            } );
        }


        return applicationResourceGraph;
    }


}