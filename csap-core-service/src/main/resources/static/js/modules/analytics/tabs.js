
import _dom from "../utils/dom-utils.js";

import ResourceGraph from "../graphs/ResourceGraph.js";

import reports from "./report/_reportMain.js";


import model from "./model.js";



const tabs = perfomance_tabs();

export default tabs

// define( [ "graphPackage", "reportPackage", "model", "hostSelectForGraphs" ], function ( ResourceGraph, reports, model, hostSelectForGraphs ) {

function perfomance_tabs() {
    
    _dom.logHead( "Module loaded" );

    var serviceGraph = null;
    var resourceGraph = null;
    var jmxGraph = null;
    var customJmxViews = {
        "Java Heap": {
            graphs: [ "Cpu_As_Reported_By_JVM", "heapUsed", "minorGcInMs" ],
            graphMerged: { "heapUsed": "heapMax", "minorGcInMs": "majorGcInMs" },
            graphSize: {
                Cpu_As_Reported_By_JVM: { "width": "100%", "height": "100" },
                heapUsed: { "width": "100%", "height": "35%" },
                minorGcInMs: { "width": "100%", "height": "45%" }
            }
        },
        "Tomcat Http": {
            graphs: [ "Cpu_As_Reported_By_JVM", "sessionsCount", "httpRequestCount", "httpKbytesReceived", "httpProcessingTime" ],
            graphMerged: { "httpKbytesReceived": "httpKbytesSent", "sessionsCount": "sessionsActive", "httpRequestCount": "tomcatConnections" }
        },
        "Java Thread": {
            graphs: [ "Cpu_As_Reported_By_JVM", "jvmThreadCount", "tomcatThreadsBusy" ],
            graphMerged: { "jvmThreadCount": "jvmThreadsMax", "tomcatThreadsBusy": "tomcatThreadCount" }
        }
    }

    return {
        //
        activateTab: function ( tabId ) {
            activateTab( tabId )
        },
        //
        reloadApplicationGraphs: function () {
            reloadApplicationGraphs();
        },
        //
        reInitGraphs: function () {
            reInitGraphs();
        },
        //
        updateHostLabels: function () {
            updateHostLabels();
        },
        //
        reInitMetricGraphs: function () {
            reInitGraphs();
        },
        //
        registerTabChanges: function () {
            registerForTabActivates();
        }


    }

    function registerForTabActivates() {
        $( "#reportTabs" ).tabs( {
            activate: function ( event, ui ) {
                //					 console.log( "Tab: " + ui.newTab.text() + " report: "
                //								+ ui.newTab.data( "report" ) + " metric:"
                //								+ ui.newTab.data( "metric" ) );

                $( ".category input" ).prop( 'checked', false );
                $( "#hostSelection" ).hide();

                $( "#visualizeSelect" ).val( "table" );
                reports.hide();

                if ( ui.newTab.data( "report" ) != undefined ) {

                    let targetLocation = "#" + ui.newTab.data( "report" ) + "Div";
                    console.log( `registerForTabActivates() - moving report options to ${ targetLocation } ` );
                    $( "#reportOptions" ).prependTo( targetLocation );

                    console.log( "\n\n Report Tab Activited: " + ui.newTab.data( "report" ) );
                    reports.resetReportResults();
                    $( "#compareLabel" ).show();
                    if ( ui.newTab.data( "report" ) == "userid" )
                        $( "#compareLabel" ).hide();

                    if ( uiSettings.reportRequest.indexOf( METRICS_JAVA ) == 0
                        || uiSettings.reportRequest.indexOf( "application" ) == 0 ) {


                        $( "#isShowJmx" ).prop( "checked", true );

                        if ( uiSettings.serviceParam == "null" ) {
                            reports.getReport( uiSettings.reportRequest );
                        } else {
                            reports.getReport( uiSettings.reportRequest, uiSettings.serviceParam );
                        }

                        uiSettings.reportRequest = "";
                    } else if ( uiSettings.reportRequest.indexOf( "os-process" ) == 0 ) {

                        if ( uiSettings.serviceParam == "null" ) {
                            reports.getReport( uiSettings.reportRequest );
                        } else {
                            reports.getReport( uiSettings.reportRequest, uiSettings.serviceParam );
                        }

                        uiSettings.reportRequest = "";


                    } else {
                        // Use Tab Data
                        if ( ui.newTab.data( "report" ) == "os-process" && reports.getLastServiceReport() != "" ) {
                            reports.getReport( reports.getLastServiceReport(), reports.getLastService() );

                        } else if ( ui.newTab.data( "report" ) == "compute" ) {


                            reports.runSummaryReports( true );

                            // Options do not apply to compute tab, move them out
                            $( "#reportOptions" ).prependTo( "#hostDiv" );

                        } else {
                            reports.getReport( ui.newTab.data( "report" ) );
                        }
                    }
                } else {

                    console.log( `registerForTabActivates() - showing graph: ${ ui.newTab.data( "metric" ) } ` );
                    // handle graph tabs - need to defer until model is loaded....
                    // graph needs it
                    $.when( model.getModelLoadedDeffered() ).done( function () {
                        showGraphTab( ui.newTab.data( "metric" ) )
                    } );

                }

            }
        } );


    }

    function buildGraphObject( containerId, metricType ) {

        console.log( `buildGraphObject: ${ containerId } type:  ${ metricType }, appid: ${ uiSettings.appId }` );

        var theNewGraph = new ResourceGraph(
            containerId, metricType, $( "#lifeSelect" ).val(),
            uiSettings.appId, uiSettings.metricsDataUrl, model );

        if ( metricType == METRICS_JAVA ) {
            theNewGraph.addCustomViews( customJmxViews );
        }
        // needed to pull model related information from events DB
        //		  console.log( "buildGraphObject() model: " + model.getSelectedProject()) ;
        //		  theNewGraph.setModel( model );

        return theNewGraph;
    }

    // JMX tab is double used - standard or custom. So we need a full init when switching
    function reloadApplicationGraphs() {

        // Moved back into template while new graph is constructed.
        $( "#appMetricsSection" ).appendTo( $( "#appMetricsTemplate" ) );

        console.log( "reloadApplicationGraphs(): rebuilding graphs" );
        if ( jmxGraph != null ) {

            console.log( `reloadApplicationGraphs() - wiping out timers and jobs` );
            jmxGraph.ignoreSettingsChanges();
            jmxGraph.clearRefreshTimers();
            // 
            $( "#javaGraphs" ).empty();
            jmxGraph = buildGraphObject( "#javaGraphs", getJmxType() );
            _currentGraph = jmxGraph;
            $.when( jmxGraph.getGraphLoadedDeferred() ).done( function () {
                $( "#appMetricsSection" ).prependTo( $( "div#javaGraphs div.resourceConfigDialog" ) );
                //                    $( "#appMetricsSection" ).insertAfter( $( "div#javaGraphs div.resourceConfigDialog>section" ) ) ;
            } );
        } else {
            console.log( "reloadApplicationGraphs() no active jmx graphs" );
        }
        alertify.closeAll();
        // reInitGraphs();
    }


    function activateTab( tabId ) {

        var tabIndex = $( 'li[data-tab="' + tabId + '"]' ).index();

        console.log( "activateTab(): " + tabId + " with index: " + tabIndex );

        // $("#jmx" ).prop("checked", true) ;

        $( "#reportTabs" ).tabs( "option", "active", tabIndex );

        return;
    }


    /**
     * 
     * Graphs will remain active even after tab is changed; making switching back and forth efficient
     * - Host selection are for all graphs; if they change on one - then when previous graph is displayed it will 
     *   be refreshed.
     * 
     */
    var _currentGraph = null;
    function showGraphTab( metric ) {
        // Move to

        $( "#graphCustomizeDiv" ).appendTo( "#" + metric + "GraphDiv" );

        console.log( `showGraphTab() -  moving options to ${ "#" + metric + "GraphDiv" } ` );
        $( "#" + metric ).prop( "checked", true );

        // Very hacky - graph.js customJmx  is managed by DOM element
        // We remove and selectively reenable when JMX
        $( "#jmxCustomWhenClassSet" ).removeClass( "triggerJmxCustom" );


        $( ".graphDiv" ).hide();

        $( "#" + metric + "Graphs" ).show();

        if ( $( "#hostDisplay input:checked" ).length == 0 ) {
            alertify.notify( "Defaulting first host" );
            $( "#hostDisplay input:eq(0)" ).prop( "checked", true );
        }

        var isGraphRefreshed = false; // race condition on displaying twice

        $( "#applicationNameLabel" ).hide();
        if ( metric == "os-process" ) {
            if ( serviceGraph == null ) {
                serviceGraph = buildGraphObject( "#os-processGraphs", "service" );
                isGraphRefreshed = true;
            }
            _currentGraph = serviceGraph;
        }
        if ( metric == "host" ) {
            if ( resourceGraph == null ) {
                resourceGraph = buildGraphObject( "#hostGraphs", "host" );
                isGraphRefreshed = true;
            }
            _currentGraph = resourceGraph;
        }

        if ( metric == METRICS_JAVA ) {

            checkForApplication( $( "#appMetricsSection input:checked" ) );

            if ( jmxGraph == null ) {
                // jmxGraph = buildGraphObject( "#javaGraphs"  , getJmxType() );
                add_services_application_metrics();
                //
                // jmxCustom services - require standard JMX attributes loaded first

                isGraphRefreshed = true;

                if ( uiSettings.appGraphParam != "null" ) {
                    //$.when( jmxGraph.getGraphLoadedDeferred() ).done( function () {
                    console.log( "Switching to appgraph display using id: " + "#"
                        + uiSettings.serviceParam + "jmxCustom, size found: "
                        + $( "#" + uiSettings.serviceParam + "jmxCustom" ).length );
                    $( "#" + uiSettings.serviceParam + "jmxCustom" ).trigger( "click" );
                    uiSettings.appGraphParam = "null";
                    jmxGraph = buildGraphObject( "#javaGraphs", getJmxType() );
                    //								serviceRequestParamForGraph = "null";

                    // } );
                } else {
                    jmxGraph = buildGraphObject( "#javaGraphs", getJmxType() );
                }

                $.when( jmxGraph.getGraphLoadedDeferred() ).done( function () {
                    $( "#appMetricsSection" ).prependTo( $( "div#javaGraphs div.resourceConfigDialog" ) );
                    //                    $( "#appMetricsSection" ).insertAfter( $( "div#javaGraphs div.resourceConfigDialog>section" ) ) ;
                } );

            }
            _currentGraph = jmxGraph;
        }

        _currentGraph.dumpInfo();

        if ( $( "#hostCusomizeDialog input:checked" ).length > 0
            && _currentGraph.getHostCount() != $( "#hostCusomizeDialog input:checked" )
                .length ) {
            //if host counts have changed on another graph, trigger re-init
            add_services_application_metrics();

            if ( !isGraphRefreshed ) {
                // why reInit? Because hostSelection may have changed.
                reInitGraphs();
            }

        }
        $( "#reportsSection" ).hide();
        $( "#reportsSection table" ).hide();

        updateHostLabels();

    }

    function updateHostLabels() {

        $( "#hostSelection" ).show();
        $( "#multiHostCustomize" ).show();
        var numHosts = $( "#hostDisplay input:checked" ).length;
        $( "#hostSelectCount" ).text( numHosts );
        if ( numHosts <= 1 )
            $( "#multiHostCustomize" ).hide();


    }
    function reInitGraphs() {

        console.log( "reInitGraphs() - reDrawing" );

        if ( $( "#hostCusomizeDialog input:checked" ).length == 0 )
            return;

        _currentGraph.settingsUpdated();

        console.log( "reInitGraphs(): host count:  " + _currentGraph.getHostCount()
            + " numChecked: " + $( "#hostCusomizeDialog input:checked" ).length );

    }

    function reInitMetricGraphs() {
        if ( serviceGraph != null ) {
            serviceGraph.clearRefreshTimers();
            $( "#serviceGraphs" ).empty();
        }

        serviceGraph = buildGraphObject( "#serviceGraphs", "service" );

    }


    function add_services_application_metrics() {

        var selectedPackage = model.getSelectedProject();

        console.log( "add_services_application_metrics() selectedProject: " + selectedPackage );

        $( ".jmxRadio" ).off();

        $( "#jmxCustomServicesDiv" ).empty();

        let serviceInstances = model.getPackageDetails( selectedPackage ).instances.instanceCount;

        for ( let serviceInstance of serviceInstances ) {


            if ( !serviceInstance.hasCustom )
                continue;
            let numContainers = 1;

            //            if ( serviceInstance.replicaCount ) {
            //                numContainers = serviceInstance.replicaCount ;
            //            }

            for ( let containerCount = 1; containerCount <= numContainers; containerCount++ ) {

                let serviceDiv = jQuery( '<div/>', {
                    class: "svcDiv",
                } );

                let serviceId = serviceInstance.serviceName;

                let jmxCustomInput = jQuery( '<input/>', {
                    id: serviceId + "jmxCustom",
                    "data-servicename": serviceId,
                    type: "radio",
                    class: "jmxRadio",
                    name: "jmxMetricsRadio",
                    title: "Custom attributes"
                } ).css( "margin-right", "0.25em" );

                console.log( `add_services_application_metrics() - parameter: ${ uiSettings.serviceParam }`
                    + ` serviceName: ${ serviceId }, numContainers: ${ numContainers } ` );

                if ( uiSettings.serviceParam == serviceId && serviceInstance.kubernetes ) {
                    //serviceId += "-" + containerCount ;
                    //console.log( `Kubernetes service selected: defaulting merge to all` ) ;
                    //$( "#isStackHosts" ).val( "99" ) ;
                    alertify.notify( "Only pod-1 on each host is shown. Modify Graph Stack selection to Merge All to view all available pods" );
                }

                if ( uiSettings.serviceParam == serviceId
                    && uiSettings.customParam == "jmxCustom" ) {


                    console.log( "add_services_application_metrics() - triggering: " + uiSettings.serviceParam );
                    jmxCustomInput.prop( "checked", true );
                    $( "#jmxCustomWhenClassSet" ).addClass( "triggerJmxCustom" );

                    uiSettings.serviceParam = "";
                }

                let jmxLabel = jQuery( '<label/>', {
                    class: "configLabels",
                    text: serviceId
                } );

                jmxLabel.prepend( jmxCustomInput );
                serviceDiv.append( jmxLabel );

                $( "#jmxCustomServicesDiv" ).append( serviceDiv );
            }
        }

        $( ".jmxRadio" )
            .click(
                function () {
                    // unbind java resource as it is being 
                    checkForApplication( $( this ) );
                    reloadApplicationGraphs();

                } );

    }

    function getJmxType() {
        let applicationServiceName = $( "#jmxCustomWhenClassSet" ).text();

        if ( applicationServiceName == "" ) {
            return METRICS_JAVA;
        } else {
            return METRICS_APPLICATION;
        }
    }


    function checkForApplication( selectedJmx_JQ ) {

        var serviceName = selectedJmx_JQ.data( "servicename" );
        console.log( `checkForApplication() - found: ${ serviceName }, is Application: ${ serviceName != "standard" }` );


        if ( serviceName != "standard" ) {

            $( "#applicationNameLabel" ).text( "Service: " + serviceName ).show();
            $( "#jmxCustomWhenClassSet" )
                .addClass( "triggerJmxCustom" )
                .text( serviceName );


        } else {
            // $( "#applicationNameLabel" ).hide();
            $( "#jmxCustomWhenClassSet" )
                .removeClass( "triggerJmxCustom" );
        }

        // 
    }

}