
import _dom from "../utils/dom-utils.js";

import model from "./model.js";
import reportPackage from "./report/_reportMain.js";
import tabs from "./tabs.js";
import hostSelectForGraphs from "./hostSelectForGraphs.js"


const header = perfomance_header();

export default header


function perfomance_header() {
    
    _dom.logHead( "Module loaded" );



    var _loadCount = 0;

    return {
        // 
        initialize: function () {


            model.registerListeners().applicationsUpdated = applicationsUpdated;
            model.registerListeners().projectModelUpdated = projectModelUpdated;
            model.registerListeners().lifecycleUpdated = lifecycleUpdated;

            // load the model
            model.updateApplications();
        },
    };

    function applicationsUpdated() {
        console.log( "applicationsUpdated()" );

        var appSummary = model.getAppSummayArray();

        // console.log( "applicationSummaryArray: " + jsonToString( appSummary )) ;

        if ( $( "#appIdFilterSelect option" ).length == 0 ) {
            initializeAppIdSelect( appSummary )
        }


        updateProjectSelectionOnUi( appSummary );

        // $("#appIdFilterSelect").sortSelect() ;

        //_modelPackages
        if ( $( "#projectSelect option" ).length > 0 ) {
            $( "#projectSelect" ).sortSelect();
            $( "#loadMessage" ).hide();
            //$( "#reportTabs" ).css("display", "grid") ;
            //$( "#reportTabs ul" ).hide() ;

            $( "#projectSelect" ).selectmenu( {
                //                        width: "25em",
                change: function () {
                    resetView();
                    model.updateProjectLifeCycles();
                    reportPackage.getLastReport();
                    switchToVmTabIfViewingMetrics();
                }
            } );

            $( "#projectSelect" ).selectmenu( "refresh" );
        } else {
            $( "#loadMessage" )
                .text( "Select an application, a project, and a lifecycle" )
            $( "#loadMessage" ).css( "background-image", "url(../images/boot.png)" );

        }
        $( "#reportTabs" ).css( "visibility", "visible" );

    }

    function updateProjectSelectionOnUi( appSummary ) {
        $( "#projectSelect" ).empty();

        for ( var i = 0; i < appSummary.length; i++ ) {

            var appItem = appSummary[ i ];

            if ( $( "#appIdFilterSelect" ).val() != "all"
                && $( "#appIdFilterSelect" ).val() != appItem.appId )
                continue;

            var projectArray = appItem.projects;

            for ( var projectIndex = 0; projectIndex < projectArray.length; projectIndex++ ) {

                var displayName = projectArray[ projectIndex ];

                if ( $( "#appIdFilterSelect" ).val() == "all" )
                    displayName = projectArray[ projectIndex ] + "   - "
                        + appItem.appId;

                // Projects store BOTH appId and project Id to support selection
                // when appId filter is set to all.
                var optionItem = jQuery( '<option/>', {
                    value: appItem.appId + "," + projectArray[ projectIndex ],
                    text: displayName
                } );
                $( "#projectSelect" ).append( optionItem );

                if ( uiSettings.projectParam == projectArray[ projectIndex ]
                    || ( uiSettings.projectParam == "All Packages" && projectIndex == 0 ) ) {
                    console.log( "Selecting: " + uiSettings.projectParam );
                    optionItem.prop( "selected", "selected" );

                    if ( uiSettings.projectParam == "All Packages" )
                        $( "#isAllProjects" ).prop( "checked", "checked" );
                }

            }
        }
    }



    function initializeAppIdSelect( projectJson ) {

        console.log( "initializeAppIdSelect()" );

        $( "#appIdFilterSelect" ).append( '<option value="none">Select</option>' );

        for ( var i = 0; i < projectJson.length; i++ ) {

            var appItem = projectJson[ i ];

            var optionItem = jQuery( '<option/>', {
                value: appItem.appId,
                text: appItem.appId
            } );

            $( "#appIdFilterSelect" ).append( optionItem );

            if ( uiSettings.appIdParam != "null" && uiSettings.appIdParam == appItem.appId ) {
                optionItem.prop( "selected", "selected" );
            }
        }
        $( "#appIdFilterSelect" ).sortSelect();
        $( "#appIdFilterSelect" ).selectmenu( {
            width: "12em",
            change: function () {
                // this will trigger projectModelUpdated
                updateProjectSelectionOnUi( model.getAppSummayArray() );
                model.updateProjectLifeCycles();

            }
        } );
    }


    function lifecycleUpdated( lifecycles ) {

        console.log( "lifecycleUpdated()" );

        var prevLife = $( "#lifeSelect" ).val();
        if ( uiSettings.lifeParam != "" ) {
            prevLife = uiSettings.lifeParam;
            uiSettings.lifeParam = "";
        }
        $( "#lifeSelect" ).empty();

        for ( var i = 0; i < lifecycles.length; i++ ) {

            var lifeCycle = lifecycles[ i ];

            // prescribed order for known lifecycles. There may be many more, but
            // first four will be the following
            var sortClass = lifeCycle;
            if ( lifeCycle == "dev" )
                sortClass = "1" + lifeCycle;
            if ( lifeCycle == "stage" )
                sortClass = "2" + lifeCycle;
            if ( lifeCycle == "lt" )
                sortClass = "3" + lifeCycle;
            if ( lifeCycle == "prod" )
                sortClass = "4" + lifeCycle;

            var optionItem = jQuery( '<option/>', {
                class: sortClass,
                value: lifeCycle,
                text: lifeCycle
            } );

            $( "#lifeSelect" ).append( optionItem );

            if ( prevLife == lifeCycle ) {
                console.log( "Selecting: " + lifeCycle );
                optionItem.prop( "selected", "selected" );

            }

        }

        $( "#lifeSelect" ).sortSelect( "class" );
        $( "#lifeSelect" ).selectmenu( {
            width: "10em",
            change: function () {
                console.log( "life changed" );
                _lifeForMetricsData = $( "#lifeSelect" ).val();
                resetView();
                var selectedAppid = $( "#projectSelect" ).val();

                reportPackage.resetReportResults();
                model.updateLifecycleModel();


                if ( uiSettings.hostParam == "null" ) {

                    reportPackage.getLastReport();
                }
                switchToVmTabIfViewingMetrics();
                reportPackage.runSummaryReports();


            }

        } );

        $( "#lifeSelect" ).selectmenu( "refresh" );
        if ( !$( "#reportTabs" ).is( ":visible" ) ) {
            //tabs.activateTab(uiSettings.reportRequest);
            applicationsUpdated();
        }
        reportPackage.runSummaryReports();
    }

    function resetView() {
        _clusterHosts = null;
        $( "#hostSelection" ).hide();
        $( "#filterSection" ).hide();
        reportPackage.hide();
    }


    function projectModelUpdated() {

        console.log( "projectModelUpdated()" );


        $( "#clusterSelect" ).val( "all" );
        $( "#filterSection" ).show();

        updateMetricCategories();
        setTimeout( function () {
            updateHosts();

            $( "#reportTabs ul" ).show();
        }, 1000 )


        switchToVmTabIfViewingMetrics();
        reportPackage.getLastReport();

    }

    // Host views need to be refreshed completely - except for direct launches
    function switchToVmTabIfViewingMetrics() {
        // need a hook to do this only after initialization
        _loadCount++;
        console.log( "_loadCount: " + _loadCount );
        var active = $( ".ui-tabs-active" );
        if ( _loadCount > 2 && active.data( "metric" ) != undefined ) {
            // graphs need to be reinitialized
            alertify.warning( "Switching to host summary view." );
            header.activateTab( "tableVm" );
        }
    }

    function updateMetricCategories() {

        var selectedPackage = model.getSelectedProject();

        var categorySelected = $( '#catContainer input[name=categoryRadio]:checked' )
            .data( "id" );
        console.log( "updateMetricCategories(): Wiping categories, selected is: " + categorySelected
            + " selectedPackage: " + selectedPackage );

        $( "#catContainer" ).empty();


        var curPackage = model.getPackageDetails( selectedPackage );

        if ( curPackage == null ) {
            alertify.alert( "No data available for: " + selectedPackage
                + ". Select Another" );
            return;
        }

        // for ( var metric in getPackage( selectedPackage ).metrics ) {
        for ( var metric in curPackage.metrics ) {

            var metricDiv = jQuery( '<div/>', {
                class: "category",
                title: "",
            } );

            var graphInput = jQuery( '<input/>', {
                id: metric,
                data: {
                    id: metric
                },
                type: "radio",
                class: "instanceCheck",
                name: "categoryRadio",
                title: "Select Metric Category"
            } );

            if ( metric == categorySelected )
                graphInput.prop( "checked", true )

            var labelText = metric;
            if ( metric == "resource" )
                labelText = "Host Resources";
            if ( metric == "service" )
                labelText = "Service Resources ";
            if ( metric == "jmx" )
                labelText = "Java JMX";
            var metricLabel = jQuery( '<label/>', {
                title: "Category",
                text: labelText
            } ).css( "padding", 0 ).css( "margin", 0 );

            metricDiv.append( graphInput );
            metricDiv.append( metricLabel );

            $( "#catContainer" ).append( metricDiv );
        }

    }


    function updateHosts() {

        var selectedProject = model.getSelectedProject();

        console.log( "updateHosts() for project: " + selectedProject );

        if ( model.getPackageDetails( selectedProject ) == undefined ) {
            console.error( "No data available for: " + selectedProject
                + ". Select Another" );
            return;
        }

        var clusterDetails = model.getPackageDetails( selectedProject ).clusters;

        $( "#hostDisplay" ).empty();

        // alert( clusterObject.all.length ) ;

        $( "#lcSelect" ).empty();
        for ( var cluster in clusterDetails ) {

            $( "#lcSelect" ).append(
                '<option value="' + cluster + '">' + cluster + '</option>' );
        }

        $( '#lcSelect' ).change( hostSelectForGraphs.updateHosts );
        $( "#lcSelect" ).sortSelect();
        $( "#lcSelect" ).val( "all" );
        $( "#lcSelect" ).trigger( "change" );

        //rebindHosts();

        if ( uiSettings.hostParam != "null" ) {

            console.log( "Found param: " + uiSettings.hostParam );
            showFilterSection( false );
            if ( uiSettings.serviceParam == "null" ) {
                tabs.activateTab( "graphHost" );
                // $("#reportTabs").tabs("option", "active", getTabIndex( "resource"
                // ));
            } else {
                tabs.activateTab( uiSettings.reportRequest );
                // $("#reportTabs").tabs("option", "active", 5);
                // $("#reportTabs").tabs("option", "active", getTabIndex( "service"
                // ));
            }
        }

    }

    function showFilterSection( isShow ) {

        if ( isShow ) {
            $( "#filterSection" ).show();
        } else {
            $( "#filterSection" ).hide();
            $( "#showFilter" ).animate( {
                borderColor: "red"
            }, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );
        }
    }

}

