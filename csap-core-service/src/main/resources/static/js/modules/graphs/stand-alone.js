console.log( `loading imports` );

import "../libs/csap-modules.js";

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";



import ResourceGraph from "./ResourceGraph.js"



_dom.onReady( function () {

    _utils.prefixColumnEntryWithNumbers( $( "table" ) )

    let appScope = new standalone_main( globalThis.settings );

    // _dialogs.loading( "start up" );

    appScope.initialize();


} );

function standalone_main() {

    const customViews = {
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
    }

    this.initialize = function () {

        _dom.logSection( `initializing main`, uiSettings );

        const theGraph = new ResourceGraph( uiSettings.containerId,
            uiSettings.metricType,
            uiSettings.life,
            uiSettings.eventUser,
            uiSettings.eventMetricsUrl );

        if ( uiSettings.metricType == METRICS_JAVA ) {
            var serviceName = $.urlParam( "service" );
            if ( serviceName != null ) {
                serviceName = serviceName.substr( 0, serviceName.indexOf( "_" ) );

                document.title = uiSettings.pageTitle ;

                $( "header" ).html( '<div class="noteHighlight" >' + serviceName + " : Java Graphs" + '</div>' );
                //		$("#csapPageLabel").text( serviceName + ":"  );
                //		$("#csapPageVersion").text("").css("margin-right", "3em");
            }

            theGraph.addCustomViews( customViews );
        }

    }

}