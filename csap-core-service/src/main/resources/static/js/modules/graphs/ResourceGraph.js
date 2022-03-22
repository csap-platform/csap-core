
// define( [ "browser/utils", "./DataManager", "./hostGraphs", "./graphLayout", "./settings" ], function ( utils, DataManager, hostGraphs, graphLayout, settings ) {

import _dom from "../utils/dom-utils.js";


import utils from "../browser/utils.js"
import DataManager from "./DataManager.js"


import hostGraphs from "./hostGraphs.js"
import settings from "./settings.js"


const graphs = graphs_package();

export default graphs


function graphs_package() {

    _dom.logHead( "Module loaded" );



    //console.log( "Module loaded: graphPackage/ResourceGraph" ) ;
    Date.prototype.stdTimezoneOffset = function () {
        let jan = new Date( this.getFullYear(), 0, 1 );
        let jul = new Date( this.getFullYear(), 6, 1 );
        return Math.max( jan.getTimezoneOffset(), jul.getTimezoneOffset() );
    }

    Date.prototype.dst = function () {
        return this.getTimezoneOffset() < this.stdTimezoneOffset();
    }

    class ResourceGraph {

        constructor( containerId, metricType, targetLifecycle, someAppid, dataServiceUrl, optionalModel ) {


            this._currentResourceGraphInstance = this;

            this._hostArray = new Array();


            this.$found_first_host_data_deferred;
            this.firstGoodHostReport;

            this._customViews = null;

            this._performanceModel = null;

            this._dataManager = null;

            this.lifeCycle = targetLifecycle;
            this.targetAppId = someAppid;

            this.targetMetricsUrl = dataServiceUrl;

            this.$GRAPH_INSTANCE = null;

            this.$javaNameSelect = null;


            this._metricType = null;

            this._graphParam = null;

            this._ignoreSettingsRequest = false;

            this._timersArray = new Array();

            this._lastGraphsChecked = null;

            this.HOST_COUNT_SAMPLE_THRESHOLD = 10;

            this._lastSamplesAvailable = "NONE";

            this._graphsInitialized = new $.Deferred();

            this._settingsConfiguredOnce = false;

            this._stackHost = "";

            this.initialize( containerId, metricType, optionalModel );

        }
        ;
        getHostCount() {
            return this._hostArray.length
        }

        getSelectedCustomView() {
            if ( this._customViews == null )
                return null;
            let viewSelection = $( "select.customViews", this.$GRAPH_INSTANCE ).val();

            if ( this._customViews[ viewSelection ] == undefined )
                return null;

            return this._customViews[ viewSelection ];
        }

        addCustomViews( customViews ) {
            this._customViews = customViews;
            settings.addCustomViews( this._currentResourceGraphInstance, this._customViews );
        }

        isCurrentModePerformancePortal() {
            return this._performanceModel != null
        }
        ;
        getDataManager() {
            return this._dataManager;
        }
        ;
        getCurrentGraph() {
            return this.$GRAPH_INSTANCE;
        }
        ;
        dumpInfo() {
            console.log( "== graph.dumpInfo: " + this.$GRAPH_INSTANCE.attr( "id" ) + " this._metricType: " + this._metricType );
        }

        /**
         * 
         *	invoked at bottom of Resource Graph to provide js context
         */
        initialize( containerId, metricType, optionalModel ) {

            this.$javaNameSelect = $( "select.java-service-names" );

            if ( optionalModel != undefined ) {
                this._performanceModel = optionalModel;
            }

            if ( typeof _lifeForMetricsData != 'undefined' ) {
                console.log( "Eliminate use of _lifeForMetricsData with param" );
                this.lifeCycle = _lifeForMetricsData
            }

            if ( typeof appId != 'undefined' ) {
                console.log( "Eliminate use of appId with param" );
                this.targetAppId = appId;
            }
            if ( typeof metricsDataUrl != 'undefined' ) {
                console.log( "Eliminate use of metricsDataUrl with param" );
                this.targetMetricsUrl = metricsDataUrl
            }

            this._metricType = metricType;

            console.log( `\n\n initialize() appId: ${ this.targetAppId }`
                + `\n\t event service data: ${ this.targetMetricsUrl }`
                + `\n\t resourceId: ${ containerId }`
                + `\n\t metricType: ${ metricType } `
                + "\n\t is model null: ", this._performanceModel == null );


            // alert(resourceId) ;
            this.$GRAPH_INSTANCE = $( containerId );

            if ( containerId != "#resourceTemplate" ) {
                let graphClone = $( "#resourceTemplate" ).clone();
                let id = containerId.substring( 1 ) + "Clone";
                graphClone.attr( "id", id );
                graphClone.data( "preference", this._metricType );
                $( containerId ).append( graphClone );
                graphClone.show();

                this.$GRAPH_INSTANCE = graphClone;
            }
            // alert (this.$GRAPH_INSTANCE.attr("id")) ;
            this.$GRAPH_INSTANCE.show();

            // how to get localhost
            let hostsParams = $.urlParam( "hosts" );
            if ( hostsParams != null && hostsParams != 0 ) {
                console.log( "hostsParams", hostsParams );
                this._hostArray = hostsParams.split( "," );

            } else {
                // hook for hostDashboard
                if ( !this.isCurrentModePerformancePortal() ) {
                    let hostShortName = utils.getHostShortName( utils.getHostName() );
                    console.log( `Host dashboard detected: Pushing host '${ hostShortName }'` );
                    this._hostArray.push( hostShortName );
                }
            }

            // if service is passed default to line mode
            if ( !this.isCurrentModePerformancePortal() && $.urlParam( "service" ) != null ) {
                $( '.useLineGraph', this.$GRAPH_INSTANCE ).prop( "checked", true )
            }

            if ( $.urlParam( "graph" ) != null ) {
                this._graphParam = $.urlParam( "graph" );
            }

            if ( "HOST_DASH_GRAPH" in window ) {
                this._graphParam = HOST_DASH_GRAPH;
            }

            this.settingsUpdated();

            // pass call back
            settings.uiComponentsRegistration( this._currentResourceGraphInstance );


        }

        selectOrSetDefaultVariable( arg, def ) {
            return ( typeof arg == 'null undefined' ? def : arg );
        }

        /**
         * params currently controlled by metrics.jsp
         */

        settingsUpdated() {
            $( ".graphOptions .refresh-window", this.$GRAPH_INSTANCE ).css( "background-color", "" );
            console.log( `settingsUpdated() - before this._metricType: ${ this._metricType }` );
            if ( this._ignoreSettingsRequest ) {
                console.log( `Settings update disabled - assuming graph was shutdown` );
                return;
            }
            //console.trace("hi") ;
            this.updateConfigParams();
            this.triggerGraphRefresh();
            console.log( `settingsUpdated() - after this._metricType: ${ this._metricType }` );
        }

        updateConfigParams() {

            if ( $( 'input[name=categoryRadio]:checked' ).length != 0 ) {
                let category = $( 'input[name=categoryRadio]:checked' ).data( "id" );

                console.log( `Updated metricType: ${ category } ` );
                this._metricType = category;
            }

            let $graphFullContainer = this.$GRAPH_INSTANCE.parent().parent().parent();

            let $performancePortal = $( "#hostCusomizeDialog" );
            if ( $performancePortal.length > 0 ) {
                $graphFullContainer = $performancePortal;
            }

            console.log( `setup: checking for #hostDisplay in dom id: ${ $graphFullContainer.attr( "id" ) }` );


            if ( $( "#hostDisplay input:checked", $graphFullContainer ).length > 0 ) {
                this._hostArray = new Array();

                let resourceGraph = this;
                $( "#hostDisplay input.instanceCheck:checked", $graphFullContainer ).each( function ( index ) {
                    let host = $( this ).data( 'host' );
                    let hostPartsArray = host.split( "." );
                    if ( hostPartsArray.length > 1 ) {
                        host = hostPartsArray[ 0 ];
                        console.log( "Desktop: using short host name[0] ", hostPartsArray );
                    }
                    resourceGraph._hostArray.push( host );
                } );
                console.log( "DOM #hostDisplay found, updated hosts: ", this._hostArray );
            } else {
                console.warn( `did not find host to get graphs using DOM id.` );
            }
        }

        ignoreSettingsChanges( host ) {
            this._ignoreSettingsRequest = true;
        }

        clearRefreshTimers( host ) {

            if ( host == undefined ) {
                for ( let hostTimerKey in this._timersArray ) {
                    clearTimeout( this._timersArray[ hostTimerKey ] );
                    this._timersArray[ hostTimerKey ] = null;
                }
            } else {
                // alert("timer deleted: " + host) ;
                clearTimeout( this._timersArray[ host ] );
                this._timersArray[ host ] = null;
            }
        }

        // This looks for global letiables defined in global scope. It enables customization of
        // internals.

        handlePerformanceGlobalConfig() {
            if ( this.isCurrentModePerformancePortal() ) {



                let resourceGraph = this;

                this._lastGraphsChecked = new Object();

                //console.log("handlePerformanceGlobalConfig():  _lastServiceNames: " + _lastServiceNames) ;
                // _lastGraphsChecked=new Object() ;
                $( ".graphCheckboxes input" ).each( function () {
                    resourceGraph._lastGraphsChecked[ $( this ).val() ] = $( this ).prop( "checked" );
                    //console.log("handlePerformanceGlobalConfig() : adding graph: " + $(this).attr("id") ) ;
                } );
            }
        }

        // Create a public method
        triggerGraphRefresh() {

            let displayHeight = $( window ).outerHeight( true )
                - $( "header" ).outerHeight( true ) - 250;

            $( "#maxHeight", this.$GRAPH_INSTANCE ).attr( "value", displayHeight + "px" );


            this.handlePerformanceGlobalConfig();

            // Get rid of previous containers.
            // alert("Clearing container: " + this.$GRAPH_INSTANCE.attr("id") + " this._metricType: " + this._metricType ) ;
            $( ".hostChildren", this.$GRAPH_INSTANCE ).remove();

            this.clearRefreshTimers();
            // _isDataAutoSampled = false;
            this._dataManager = new DataManager( this._hostArray );

            // alert(type + ":" + label) ;

            // let this._hostArray=unescape(getRequestParams("hosts")).split(",") ;
            console.log( `triggerGraphRefresh() - host count ${ this._hostArray.length }, type ${ this._metricType } ` );
            this.$found_first_host_data_deferred = new $.Deferred();
            for ( let currentHost of this._hostArray ) {

                console.log( `triggerGraphRefresh() refreshing host: '${ currentHost }' ` );
                let newHostContainer = this.buildHostContainer( currentHost, this.$GRAPH_INSTANCE );

                this.getMetrics( $( '#numSamples', this.$GRAPH_INSTANCE ).val(), currentHost );

                settings.addContainerEvents( this._currentResourceGraphInstance, newHostContainer );

            }

            settings.addToolsEvents()

        }

        buildHostContainer( host, $GRAPH_INSTANCE ) {
            let $container = $( ".hostTemplate", $GRAPH_INSTANCE )
                .clone();
            $container.removeClass( "hostTemplate" );
            $container.addClass( "hostChildren" );
            $container.addClass( host + "Container" );
            $( ".hostContainer", $GRAPH_INSTANCE ).append( $container );
            $( ".hostName", $container ).html( host );

            $container.attr( "data-host", host );

            $container.show();

            return $container;
        }

        getMetrics( numSamples, host ) {

            let serviceNames = new Array();
            if ( uiSettings.localApplicationService ) {
                console.log( ` using local service name ${ uiSettings.localApplicationService }` );
                serviceNames.push( uiSettings.localApplicationService );

            } else {
                $( '.serviceCheckbox:checked', this.$GRAPH_INSTANCE ).each( function () {
                    serviceNames.push( $( this ).val() );
                } );

                console.log( `utils.getSelectedService(): ${ utils.getSelectedService() } ` );

                if ( utils.getSelectedService() ) {

                    let utilsServiceName = utils.getSelectedService();
                    if ( utilsServiceName != utils.agentServiceSelect() ) {
                        serviceNames = [ utilsServiceName ];
                        let $selectedOption = utils.getServiceSelector().find( ':selected' );
                        let numdays = $( "#numberOfDays", this.$GRAPH_INSTANCE ).val();
                        if ( this._metricType == METRICS_APPLICATION
                            && $selectedOption.data( "service" )
                            && numdays > 0 ) {

                            console.log( "using service mapping as events-service db requires it" );
                            serviceNames = [ $selectedOption.data( "service" ) ];

                        }
                    } else if ( this._metricType == METRICS_APPLICATION ) {
                        serviceNames = [ "csap-agent" ];
                    }
                    console.log( "getMetrics(): adding service from utils.pageParams: ", utilsServiceName )
                } else if ( serviceNames.length == 0 ) {
                    if ( $.urlParam( "service" ) != null ) {
                        serviceNames.push( $.urlParam( "service" ) );
                        console.log( "getMetrics(): adding service from url param: ", serviceNames );
                        //return;
                    }
                }
            }

            console.groupCollapsed( `\n\n getMetrics(): \t ${ host } \n\t type: ${ this._metricType }`
                + `\n\t id: '${ this.$GRAPH_INSTANCE.attr( "id" ) }'`
                + `\n\t Number of Services: ${ serviceNames.length } `
                + `\n\t services: ${ serviceNames } \n ` );




            if ( typeof _lastServiceNames != 'undefined' ) {
                if ( _lastServiceNames.length > 0 )
                    serviceNames = _lastServiceNames;
                console.log( "getMetrics():  Using previous selection: _lastServiceNames: " + serviceNames );
            }

            // hook for customizing JMX
            if ( $( ".triggerJmxCustom" ).length > 0 ) {
                console.log( `getMetrics() application detected: ${ $( ".triggerJmxCustom" ).text() }` );
                if ( !this.isCurrentModePerformancePortal() && $.urlParam( "service" ) != null ) {
                    $( ".triggerJmxCustom" ).html( $.urlParam( "service" ) );
                }
                // se
                serviceNames = new Array();
                // serviceNameArray.push("_custom_");
                serviceNames.push( $( ".triggerJmxCustom" ).html() );
            }
            if ( window.csapGraphSettings != undefined ) {
                let jmxService = window.csapGraphSettings.service;
                $( ".triggerJmxCustom" ).html( jmxService );
                if ( serviceNames.length === 0 ) {
                    serviceNames = new Array();
                    serviceNames.push( jmxService );
                    console.log( "csapGraphSettings(): adding service: " + jmxService );
                }
            }


            // alert($(".useHistorical", this.$GRAPH_INSTANCE).is(':checked')) ;
            let numdays = $( "#numberOfDays", this.$GRAPH_INSTANCE ).val();
            if ( !$( ".useHistorical", this.$GRAPH_INSTANCE ).is( ':checked' ) ) {
                numdays = -1;
            }

            let id = "none";
            let dayOffset = $( "#dayOffset", this.$GRAPH_INSTANCE ).val();


            let serviceUrl = "/os/metricsData";
            let paramObject = {
                "hostName": host,
                "metricChoice": this._metricType,
                "numSamples": numSamples,
                "skipFirstItems": 1,
                "numberOfDays": numdays,
                "id": id,
                "dayOffset": dayOffset,
                "isLastDay": $( "#useOldest", this.$GRAPH_INSTANCE ).is( ':checked' ),
                "serviceName": serviceNames
            };
            // Use JSONP for historical data
            if ( numdays != -1 ) {

                if ( host == "localhost" && uiSettings.testHost ) {
                    host = uiSettings.testHost;
                    console.log( `Found host 'localhost'. Switching to host specified by 'uiSettings.testHost': '${ host }'` );
                }

                // numberOfDays={numberOfDays}&dateOffSet={dateOffSet}&searchFromBegining={searchFromBegining}
                // /AuditService/show/metricsAPI/{hostName}/{id}?

                paramObject = {
                    "appId": this.targetAppId,
                    "life": this.lifeCycle,
                    "numberOfDays": numdays,
                    "dateOffSet": dayOffset,
                    "serviceName": serviceNames,
                    "padLatest": $( '.padLatest', this.$GRAPH_INSTANCE ).prop( "checked" ),
                    "searchFromBegining": $( "#useOldest", this.$GRAPH_INSTANCE )
                        .is( ':checked' )
                };
                serviceUrl = this.targetMetricsUrl;

                serviceUrl += host;

            }
            //		alert("got 1" + serviceUrl) ;
            this.getIntervals( host, this._metricType, serviceUrl, paramObject,
                this.$GRAPH_INSTANCE );

            console.groupEnd();

        }

        // will display 30 second updates for this or fewer
        getSampleInterval( $GRAPH_INSTANCE, collectionIntervals ) {

            let sampleTimeSelection = $(
                'input[name=interval' + $GRAPH_INSTANCE.attr( "id" )
                + ']:checked' ).val();
            let numDays = $( "#numberOfDays", $GRAPH_INSTANCE ).val();

            let useAutoSelect = $( ".useAutoInterval", $GRAPH_INSTANCE ).is(
                ':checked' );

            if ( typeof sampleTimeSelection == 'undefined' ) {
                // By default -use the longest time interval
                // serviceUrl += "/" + this._metricType + "_" +
                // this._lastSamplesAvailable[this._lastSamplesAvailable.length-1]
                if ( typeof getIntervalSamples == 'undefined' ) {
                    // console.log("Intervals are null") ;
                    sampleTimeSelection = this._lastSamplesAvailable[ 0 ];

                    if ( useAutoSelect && this._hostArray.length > this.HOST_COUNT_SAMPLE_THRESHOLD ) {

                        $( '.padLatest', $GRAPH_INSTANCE ).prop( "checked", false );

                        sampleTimeSelection = this._lastSamplesAvailable[ this._lastSamplesAvailable.length - 2 ];
                        if ( this._hostArray.length > this.HOST_COUNT_SAMPLE_THRESHOLD + 10 )
                            sampleTimeSelection = this._lastSamplesAvailable[ this._lastSamplesAvailable.length - 1 ];
                    }

                    // console.log("\n\n  ========  this._lastSamplesAvailable" + sampleTimeSelection)
                } else {
                    // console.log("Using passed vals") ;
                    // This is escape when using metrics browser as initial samples
                    // are not there
                    sampleTimeSelection = collectionIntervals[ 0 ];
                }
            } else if ( useAutoSelect ) {
                // Select based on number of days chosen
                let selectedIndex = 0;
                if ( numDays > 3 ) {
                    selectedIndex = this._lastSamplesAvailable.length - 1;
                }
                if ( numDays > 3 && numDays < 14
                    && this._lastSamplesAvailable.length == 3 ) {
                    selectedIndex = this._lastSamplesAvailable.length - 2;
                }

                if ( this._hostArray.length > this.HOST_COUNT_SAMPLE_THRESHOLD ) {
                    console.log( "\n\n  ========  Updating auto sample time" )
                    selectedIndex = this._lastSamplesAvailable.length - 2;
                    $( '.padLatest', $GRAPH_INSTANCE ).prop( "checked", false );
                }
                if ( this._hostArray.length > this.HOST_COUNT_SAMPLE_THRESHOLD + 10 ) {
                    console.log( "\n\n  ========  Updating auto sample time" )
                    selectedIndex = this._lastSamplesAvailable.length - 1;
                    $( '.padLatest', $GRAPH_INSTANCE ).prop( "checked", false );
                }

                // console.log("Selecting: " + selectedIndex) ;
                sampleTimeSelection = this._lastSamplesAvailable[ selectedIndex ];
                $(
                    'input:radio[name=interval'
                    + $GRAPH_INSTANCE.attr( "id" ) + ']:nth('
                    + selectedIndex + ')', $GRAPH_INSTANCE ).prop(
                        'checked', true );
            }
            // console.log(" sampleTimeSelection: " + sampleTimeSelection) ;
            return sampleTimeSelection;
        }

        // Gets the sample intervals for UI to update
        getIntervals( host, type, serviceUrl, paramObject, $GRAPH_INSTANCE ) {
            console.log( `getIntervals() - type: ${ type }, url: ${ serviceUrl }, performance model:`, this._performanceModel, `\n\t request parameters: `, paramObject );
            $( ".graph-error-messages" ).hide();
            // if (typeof _modelPackages != 'undefined') {
            if ( this.isCurrentModePerformancePortal() ) {

                this.intervalsForPerformance( host, type, serviceUrl, paramObject,
                    $GRAPH_INSTANCE );

            } else {
                this.intervalsForVm( host, type, serviceUrl, paramObject, $GRAPH_INSTANCE );
            }
        }

        intervalsForPerformance( host, type,
            serviceUrl,
            paramObject,
            $GRAPH_INSTANCE ) {

            // Used in performance.js and service-portal
            let selectedPackage = this._performanceModel.getSelectedProject();
            console.log( `intervalsForPerformance() - type: ${ type }, this._metricType: ${ this._metricType } url: ${ serviceUrl },\n\t package: ${ selectedPackage } this.targetMetricsUrl: ${ this.targetMetricsUrl }` );

            let packageDefinition = this._performanceModel.getPackageDetails( selectedPackage );
            if ( !packageDefinition ) {
                alertOrLog( "Unable to retrieve package information. Verify connectivity with CSAP Event Services" );
                console.log( "this._performanceModel", this._performanceModel );
                return;
            }

            console.log( `package metrics:`, packageDefinition.metrics );
            this._lastSamplesAvailable = packageDefinition.metrics[ type ];
            if ( !this._lastSamplesAvailable ) {
                this._lastSamplesAvailable = packageDefinition.metrics[ "application" ];
            }

            let sampleTimeSelection = this.getSampleInterval( $GRAPH_INSTANCE, this._lastSamplesAvailable );

            if ( serviceUrl.indexOf( this.targetMetricsUrl ) != -1 ) {

                let metricUrl = this._metricType;
                //                if ( $( ".triggerJmxCustom" ).length == 1 ) {
                //                    metricUrl += $( ".triggerJmxCustom" ).text() ;
                //                }
                // console.log("Url from config: " + metricUrl) ;
                serviceUrl += "/" + metricUrl + "_" + sampleTimeSelection;
                // CORS+ "?callback=?" ;
                let useBuckets = $( ".useBuckets", $GRAPH_INSTANCE ).is(
                    ':checked' );
                if ( useBuckets ) {

                    $.extend( paramObject,
                        {
                            "bucketSize": $( ".bucketSize",
                                $GRAPH_INSTANCE ).val(),
                            "bucketSpacing": $( ".bucketSpacing",
                                $GRAPH_INSTANCE ).val(),
                        } );
                }

            } else {
                $.extend( paramObject, {
                    "resourceTimer": sampleTimeSelection
                } );
            }

            console.log( `intervalsForPerformance():\n\n  getting service metrics: ${ serviceUrl }`, paramObject );

            // console.log("Getting metrics from: " + serviceUrl + "\n params: " +
            // JSON.stringify(paramObject, null, "\t")
            // + " Sample interval: " + sampleTimeSelection) ;

            let resourceGraph = this;
            $.getJSON( serviceUrl, paramObject )

                .done( function ( metricJson ) {
                    resourceGraph.metricDataSuccess( host, metricJson, true );
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    // handleConnectionError("Performance Intervals", errorThrown);
                    // console.log("Performance Intervals" + JSON.stringify(jqXHR, null, "\t")) ;
                    handleConnectionError( "Performance Metrics", errorThrown );
                } );
        }

        intervalsForVm( host, type, serviceUrl, paramObject,
            $GRAPH_INSTANCE ) {

            console.log( `intervalsForVm() : type: '${ type }' serviceUrl: '${ serviceUrl }'` );

            let resourceGraph = this;

            // invoke on CSAP VMs when displaying metrics
            //            $.getJSON( "metricIntervals/" + type, {
            $.getJSON( "/os/metricIntervals/" + type, {
                dummyParam: "dummyVal"

            } ).done( function ( resultsJson ) {
                // console.log( "resourceGraph._lastSamplesAvailable: " +
                // resourceGraph._lastSamplesAvailable + " intervals: "
                // +JSON.stringify(resultsJson, null, "\t") ) ;

                // Performance ui uses resourceGraph settings
                resourceGraph._lastSamplesAvailable = resultsJson;
                let sampleTimeSelection = resourceGraph.getSampleInterval(
                    $GRAPH_INSTANCE,
                    resourceGraph._lastSamplesAvailable );

                // if we are using historical, tweak the url
                if ( serviceUrl.includes( resourceGraph.targetMetricsUrl ) ) {
                    serviceUrl += "/" + resourceGraph._metricType + "_"
                        //+ sampleTimeSelection + "?callback=?" ;
                        + sampleTimeSelection;
                    let useBuckets = $( ".useBuckets",
                        $GRAPH_INSTANCE ).is( ':checked' );
                    if ( useBuckets ) {

                        $.extend( paramObject, {
                            "bucketSize": $( ".bucketSize",
                                $GRAPH_INSTANCE ).val(),
                            "bucketSpacing": $( ".bucketSpacing",
                                $GRAPH_INSTANCE ).val(),
                        } );
                    }

                } else {
                    $.extend( paramObject, {
                        "resourceTimer": sampleTimeSelection
                    } );
                }

                // console.log("Getting metrics from: " + serviceUrl
                // + "\n params: " + JSON.stringify(paramObject,
                // null, "\t")
                // + " Sample interval: " + sampleTimeSelection) ;
                //                console.log(`metric source: ${ serviceUrl }`, paramObject ) ;

                $.getJSON( serviceUrl, paramObject )

                    .done(
                        function ( metricJson ) {
                            resourceGraph.metricDataSuccess( host, metricJson, true );
                        } )

                    .fail(
                        function ( jqXHR, textStatus, errorThrown ) {
                            handleConnectionError(
                                "Host Metrics",
                                errorThrown );
                        } );

            } ).fail(
                function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError(
                        "Host Intervals"
                        + serviceUrl, errorThrown );
                } );
        }

        alertOrLog( message ) {

            this.getGraphLoadedDeferred().resolve();

            let fullMessage = "No data found - update selection(service, host, interval).<br>" + message;
            //$( ".graph-error-messages", this.$GRAPH_INSTANCE.parent() ).append( message ).show() ;
            let $targetPlot = $( ".plotContainer", this.$GRAPH_INSTANCE );
            $targetPlot.empty();
            //$( ".plotContainer", this.$GRAPH_INSTANCE ).append( fullMessage ) ;
            jQuery( '<div/>', {
                class: "csap-white",
                html: fullMessage
            } ).appendTo( $targetPlot );

            console.log( `\n\n alertOrLog:  ${ message }\n\n` );
        }

        updateServiceNames( hostGraphReport ) {

            console.log( `updateServiceNames: populating service selects: agent service: ${ utils.getSelectedService() }` );

            let requestedId = utils.json( "attributes.id", hostGraphReport );

            let javaServiceNames = utils.json( "attributes.serviceNames.java", hostGraphReport );
            if ( javaServiceNames && requestedId.startsWith( "java_" ) ) {

                if ( ( javaServiceNames.length + 1 ) !== $( "option", this.$javaNameSelect ).length ) {
                    this.$javaNameSelect.empty();
                    jQuery( '<option/>', {
                        text: "available..."
                    } ).appendTo( this.$javaNameSelect );
                    for ( let name of javaServiceNames ) {
                        let $option = jQuery( '<option/>', {
                            text: name
                        } ).appendTo( this.$javaNameSelect );
                    }

                }


            }

            let appServiceNames = utils.json( "attributes.serviceNames.application", hostGraphReport );
            let $appGraphsNameSelect = $( "select.app-service-names" );


            if ( appServiceNames && requestedId.startsWith( "application-" ) && $appGraphsNameSelect.length > 0 ) {

                if ( appServiceNames.length != $( "option", $appGraphsNameSelect ).length ) {
                    $appGraphsNameSelect.empty();
                    jQuery( '<option/>', {
                        text: "available..."
                    } ).appendTo( $appGraphsNameSelect );
                    for ( let name of appServiceNames ) {
                        let $option = jQuery( '<option/>', {
                            text: name
                        } ).appendTo( $appGraphsNameSelect );
                    }
                }
            }

        }

        metricDataSuccess( host, hostGraphReport, isSchedule ) {



            let hostData = hostGraphReport.data;
            let isFailedHost = true;
            if ( utils.isObject( hostData ) ) {
                //                if ( Array.isArray( hostData ) && hostData.length > 0 ) {
                if ( Object.keys( hostData ).length > 0 ) {
                    isFailedHost = false;
                }
            }
            console.log( `metricDataSuccess() isFailedHost: ${ isFailedHost }` );

            if ( this.$javaNameSelect.length > 0 ) {
                this.updateServiceNames( hostGraphReport );
            }



            if ( ( this.getHostCount() == 1 ) || !isFailedHost ) {

                this.renderGraph( hostGraphReport, isSchedule );
                this.firstGoodHostReport = hostGraphReport;

                setTimeout( () => {
                    this.$found_first_host_data_deferred.resolve();
                }, 500 );

            } else {



                let resourceGraph = this;
                alertify.notify( `Warning: did not find data for host: ${ host } - setting data to -1` );

                $.when( this.$found_first_host_data_deferred ).done( function () {

                    let sourceReport = resourceGraph.firstGoodHostReport;

                    let clonedHostResponse = new Object();
                    clonedHostResponse.attributes = JSON.parse( JSON.stringify( sourceReport.attributes ) );
                    clonedHostResponse.attributes[ "hostName" ] = host;

                    let clonedData = new Object();
                    clonedData.timeStamp = JSON.parse( JSON.stringify( sourceReport.data.timeStamp ) );
                    for ( let attributeName of Object.keys( sourceReport.data ) ) {
                        console.log( `attributeName: ${ attributeName }` )
                        if ( attributeName !== "timeStamp" ) {
                            clonedData[ attributeName ] = new Array( clonedData.timeStamp.length ).fill( -1 );
                        }
                    }
                    clonedHostResponse.data = clonedData;
                    console.log( `clonedHostResponse`, clonedHostResponse );

                    resourceGraph.renderGraph( clonedHostResponse, isSchedule );
                } );

            }

        }

        renderGraph( hostGraphData, isSchedule ) {

            console.log( "metricDataSuccess() hostGraphData: ", hostGraphData.data );

            if ( hostGraphData == null ) {
                this.alertOrLog( "No Data" );
                return;
            }
            if ( hostGraphData.error ) {

                if ( hostGraphData.host ) {
                    setTimeout( function () {

                        $( '#instanceTable tbody tr.selected[data-host="' + hostGraphData.host + '"] input' )
                            .each( function () {
                                $( this ).parent().parent().removeClass( "selected" );
                                $( this ).prop( "checked", false ).trigger( "change" );
                            } );
                    }, 500 );
                }
                this.alertOrLog( "Found error in response: " + hostGraphData.error );
                return;
            }
            if ( hostGraphData.errors ) {
                this.alertOrLog( "Found errors in response: " + hostGraphData.errors );
                return;
            }
            if ( hostGraphData.attributes == undefined ) {
                //$("ul[data-group='Companies'] li[data-company='Microsoft']").attr

                this.alertOrLog( "No attributes found" );
                return;
            }
            if ( hostGraphData.attributes.errorMessage ) {
                this.alertOrLog( "Found errorMessage, cannot display graph: "
                    + hostGraphData.attributes.errorMessage );
                return;
            }

            // hook for admin
            if ( uiSettings.isForceHostToLocalhost ) {
                console
                    .log( "isForceHostToLocalhost is set, overriding response host name: "
                        + hostGraphData.attributes[ "hostName" ] );
                hostGraphData.attributes[ "hostName" ] = "localhost";
            }

            let host = hostGraphData.attributes.hostName;


            // support for tabs
            if ( ( $( "div.ui-tabs" ).length > 0 )
                && ( $( "div.visible-container", this.$GRAPH_INSTANCE ).length > 0 ) ) {
                let displayHeight = $( window ).outerHeight( true ) - $( "div.visible-container", this.$GRAPH_INSTANCE ).offset().top;
                let scrollableHeight = Math.floor( displayHeight );
                $( "div.visible-container", this.$GRAPH_INSTANCE ).css( "height", scrollableHeight + "px" );
            }

            // restore scroll point post refresh
            let $scrollableContent = $( ".hostContainer", this.$GRAPH_INSTANCE );
            let scrollPosition = $scrollableContent.scrollTop();

            let $newGraphContainer = $( "." + host + "Container", this.$GRAPH_INSTANCE );
            $( ".plotContainer", $newGraphContainer ).find( "*" ).off();
            let plotContainer = $( ".plotContainer", $newGraphContainer );

            plotContainer.empty(); // get rid of existing content

            if ( hostGraphData.data.timeStamp == null ) {
                if ( this._dataManager.getHostCount() <= 1 ) {
                    alertOrLog( "No timeStamp in data for selected interval." + host );
                } else {
                    console.log( `Empty data for host '${ host }' - skipping processing` );
                    this._dataManager.removeHost( host );
                    $newGraphContainer.remove();
                }
                return;
            }

            let hostAuto = $( ".autoRefresh", $newGraphContainer );

            if ( hostAuto.prop( 'checked' ) == false ) {
                return

            }

            let optionallyAppendedData = this._dataManager.addHostData( hostGraphData );

            this.configureSettings( hostGraphData.attributes.samplesAvailable,
                hostGraphData.attributes.sampleInterval,
                hostGraphData.attributes.graphs,
                hostGraphData.attributes.titles );

            //
            if ( hostGraphData.attributes.servicesAvailable != undefined ) {
                this.updateServiceCheckboxes( hostGraphData.attributes.servicesAvailable, hostGraphData.attributes.servicesRequested );
            } else {
                this.updateGraphsCheckboxes( hostGraphData.attributes.graphs );
            }

            // console.log("Invoking drawGraph") ;
            hostGraphs.draw( this._currentResourceGraphInstance, $newGraphContainer,
                optionallyAppendedData, host, this._graphsInitialized );


            console.log( `Scrolling to: ${ scrollPosition } ` );
            $scrollableContent.scrollTop( scrollPosition );

            utils.loadingComplete();


            if ( isSchedule ) {
                this.scheduleUpdate( host, hostGraphData, $newGraphContainer )
            }

            settings.postDrawEvents( $newGraphContainer, this.$GRAPH_INSTANCE, hostGraphData.attributes.numDays )

            // if graph is provided - maximize the selected graph
            if ( this._graphParam != null
                && $( "." + this._graphParam, this.$GRAPH_INSTANCE ).is( ":visible" ) ) {
                setTimeout( function () {
                    $( "." + this._graphParam + " .plotMinMaxButton" ).trigger( "click" );
                    this._graphParam = null;
                }, 500 )

            }


        }

        getGraphLoadedDeferred() {
            return this._graphsInitialized;
        }

        scheduleUpdate( host, hostGraphData, $newGraphContainer ) {
            let resourceGraph = this;

            let $refreshInterval = $( "#cpuIntervalId" );

            if ( $refreshInterval.length > 0 ) {
                if ( $refreshInterval.val() == 999 ) {
                    $( ".graphOptions .refresh-window", this.$GRAPH_INSTANCE ).css( "background-color", "red" );
                    $( ".graphOptions .refresh-window", this.$GRAPH_INSTANCE ).attr( "title", "refresh disabled via process settings" );
                    return;
                } else {
                    $( ".graphOptions .refresh-window", this.$GRAPH_INSTANCE ).css( "background-color", "transparent" );
                    $( ".graphOptions .refresh-window", this.$GRAPH_INSTANCE ).attr( "title", "autorefresh enabled" );
                }
            }
            let durationMs = hostGraphData.attributes.sampleInterval * 1000;

            $( ".hostInterval", $newGraphContainer ).html(
                ( durationMs / 60000 ).toFixed( 2 ) );
            let numRec = 1;

            if ( !$( ".useHistorical", this.$GRAPH_INSTANCE ).is( ':checked' ) ) {

                this._timersArray[ host ] = setTimeout( function () {
                    // Get the latest points only
                    resourceGraph.getMetrics( numRec, host );
                }, utils.getRefreshInterval() );
            }
        }

        /**
         * Only need to init when graph type is changed
         * 
         * @param samplesAvailable
         */
        configureSettings( samplesAvailable, currentInterval, graphsAvailable, graphTitles ) {

            // alert( $("#sampleIntervals input").length)
            // if ($(".sampleIntervals input", this.$GRAPH_INSTANCE).length != 0)
            // return;
            if ( this._settingsConfiguredOnce )
                return;

            this._settingsConfiguredOnce = true;
            //console.log("configureOptionsForSelectedHosts() - " + servicesAvailable) ;
            $( ".sampleIntervals", this.$GRAPH_INSTANCE ).empty();


            let resourceGraph = this;
            let settingsCallback = function () {
                resourceGraph.settingsUpdated();
            }
            settings.dialogSetup( settingsCallback, this.$GRAPH_INSTANCE );


            $( '.csv', this.$GRAPH_INSTANCE ).change( function () {
                // $('div.legend *').style("left", "30px" ) ;
                // triggerGraphRefresh();
                return; // 
            } );

            $( '.uncheckAll' ).click( function () {

                $( 'input', $( this ).parent().parent() ).prop( "checked", false ).trigger( "change" );
                return false; // prevents link
            } );
            $( '.checkAll' ).click( function () {

                $( 'input', $( this ).parent().parent() ).prop( "checked", true );
                return false; // prevents link
            } );
            // $("#intervals").empty() ;
            // <input class="custom" id="short" type="radio" name="intervalId"
            // value="-1" ><label class="radio" title="Shows Last 5 hours"
            // for="short">30 sec</label>

            let samples = samplesAvailable;
            if ( typeof samplesAvailable == 'undefined' ) {
                samples = this._lastSamplesAvailable;
                // console.log( "samples: " + samples + " this._lastSamplesAvailable: " +
                // this._lastSamplesAvailable ) ;
            } else {
                this._lastSamplesAvailable = samplesAvailable;
            }
            // console.log("Updating Samples list: " + samples) ;
            for ( let i = 0; i < samples.length; i++ ) {
                let id = "interval" + samples[ i ];


                let labelElement = jQuery( '<label/>', {
                    class: "csap",
                    text: samples[ i ] + " Seconds"
                } ).appendTo( $( ".sampleIntervals", this.$GRAPH_INSTANCE ) );


                jQuery( '<input/>', {
                    id: id,
                    class: "custom",
                    type: "radio",
                    checked: "checked",
                    value: samples[ i ],
                    name: "interval" + this.$GRAPH_INSTANCE.attr( "id" )
                } ).prependTo( labelElement );

            }

            let sortedGraphs = new Array();
            for ( let graphName in graphsAvailable ) {
                sortedGraphs.push( graphName );
            }
            sortedGraphs = sortedGraphs.sort( function ( a, b ) {
                return a.toLowerCase().localeCompare( b.toLowerCase() );
            } );
            // for ( let graphName in graphsAvailable ) {
            for ( let i = 0; i < sortedGraphs.length; i++ ) {
                let graphName = sortedGraphs[ i ];
                //let label = splitUpperCase( graphName );
                let label = graphTitles[ graphName ];

                let $graphLabel = jQuery( '<label/>', {} )
                    .appendTo( $( ".graphCheckboxes", this.$GRAPH_INSTANCE ) );

                let $graphCheckbox = jQuery( '<input/>', {
                    id: graphName + "CheckBox",
                    class: "graphs",
                    type: "checkbox",
                    value: graphName
                } ).appendTo( $graphLabel );

                jQuery( '<span/>', { html: label } ).appendTo( $graphLabel );


                // console.log("graph.configureOptionsForSelectedHosts(): previousGraphsSelection: " +  JSON.stringify(previousGraphsSelection, null, "\t") ) ;
                if ( this._lastGraphsChecked != null ) {
                    let graphSelect = this._lastGraphsChecked[ graphName ];
                    console.log( `graphSelect: ${ graphSelect }` );
                    if ( graphSelect == true || graphSelect == undefined ) {
                        $graphCheckbox.prop( "checked", true );
                    }

                } else {
                    $graphCheckbox.prop( "checked", true );
                }

            }

        }

        updateGraphsCheckboxes( graphs ) {

            let curCheckBoxCount = $( ".serviceCheckbox", this.$GRAPH_INSTANCE ).length;
            console.log( "updateGraphsCheckboxes() size:" + curCheckBoxCount + " servicesAvailable size: " + graphs.length );

            if ( curCheckBoxCount > 5 )
                return;

            $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ).empty();


            //let graphNames = Object.keys( graphs ) ;
            //graphNames.forEach( function ( graphName ) {
            console.debug( `graph names:  ${ Object.keys( graphs ) }` )
            for ( let graphName of Object.keys( graphs ) ) {
                let graphDefintion = graphs[ graphName ];
                if ( graphDefintion == undefined ) {
                    console.log( `graph definition undefined: ${ graphName }` );
                    continue;
                }
                let graphLines = Object.keys( graphDefintion );
                console.debug( `graphLines`, graphLines );
                for ( let graphLineId of graphLines ) {
                    let data_service = graphLineId

                    let label;
                    switch ( graphName ) {
                        case "Memory_Remaining":
                            label = "Memory: " + graphDefintion[ graphLineId ];
                            break;
                        case "iostat":
                            label = "IO: " + graphDefintion[ graphLineId ];
                            break;
                        case "VmFiles":
                            label = "Files: " + graphDefintion[ graphLineId ];
                            break;
                        case "InfraTest":
                            label = "Infra: " + graphDefintion[ graphLineId ];
                            break;
                        case "OS_MpStat":
                            label = "mpstat: " + graphDefintion[ graphLineId ];
                            break;
                        case "ioPercentUsed":
                            label = "Device Used: " + graphDefintion[ graphLineId ];
                            break;
                        default:
                            label = graphName + ": " + graphDefintion[ graphLineId ]
                    }


                    let $serviceLabel = jQuery( '<label/>', {
                        html: label
                    } ).appendTo( $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ) );

                    let $serviceInput = jQuery( '<input/>', {
                        id: "serviceCheckbox" + graphLineId,
                        class: "custom serviceCheckbox servicenameCheck",
                        type: "checkbox",
                        value: graphLineId,
                        "data-servicename": data_service,
                        name: "interval" + this.$GRAPH_INSTANCE.attr( "id" )
                    } ).prependTo( $serviceLabel );

                    $serviceInput.prop( "checked", true );

                    // console.log("updateServiceCheckboxes() Adding:" + label + " selected: " + $serviceInput.prop( "checked") ) ;

                }
            }
            $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ).show();
        }

        updateServiceCheckboxes( servicesAvailable, servicesRequested ) {

            console.log( `updateServiceCheckboxes: populating service selects: agent service: ${ utils.getSelectedService() }` );



            let curCheckBoxCount = $( ".serviceCheckbox", this.$GRAPH_INSTANCE ).length;
            console.log( "updateServiceCheckboxes() size:" + curCheckBoxCount + " servicesAvailable size: " + servicesAvailable.length );

            if ( ( servicesAvailable.length + 1 ) == curCheckBoxCount ) {
                console.log( ` Skipping service box refresh` )
                return;
            }

            //            console.log(`servicesAvailable: `, servicesAvailable, `\n servicesRequested`, servicesRequested );

            $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ).empty();

            let servicesToAdd = servicesAvailable;
            let serviceInstances = new Array();
            if ( this._performanceModel !== null ) {
                let selectedPackage = this._performanceModel.getSelectedProject();
                let packageDetails = this._performanceModel.getPackageDetails( selectedPackage );
                serviceInstances = packageDetails.instances.instanceCount;

                //                console.log(`packageDetails: `, packageDetails) ;
                servicesToAdd = new Array();
                for ( let serviceInstance of serviceInstances ) {
                    servicesToAdd.push( serviceInstance.serviceName );
                }
            }


            let $totalLabel = jQuery( '<label/>', {} ).appendTo( $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ) );

            jQuery( '<input/>', {
                id: "serviceCheckboxtotalCpu",
                class: "serviceCheckbox servicenameCheck",
                type: "checkbox",
                value: "totalCpu",
                name: "interval" + this.$GRAPH_INSTANCE.attr( "id" )
            } ).appendTo( $totalLabel );

            jQuery( '<span/>', { text: "Total Vm" } ).appendTo( $totalLabel );


            servicesToAdd.sort( function ( a, b ) {
                return a.toLowerCase().localeCompare( b.toLowerCase() );
            } );

            let showSkipOnce = true;
            for ( let serviceName_port of servicesToAdd ) {

                let data_service = serviceName_port
                let label = serviceName_port;
                // console.log("updateServiceCheckboxes() Adding:" + label) ;
                let foundIndex = serviceName_port.indexOf( "_" );


                if ( foundIndex > 3 ) {
                    let nameOnly = serviceName_port.substring( 0, foundIndex );
                    label = nameOnly + "<span>" + serviceName_port.substring( foundIndex + 1 ) + "</span>";
                    if ( this.isCurrentModePerformancePortal() ) {
                        data_service = nameOnly;
                    }
                }
                if ( utils.getSelectedService() ) {
                    let utilsServiceName = utils.getSelectedService();
                    if ( utilsServiceName !== utils.agentServiceSelect()
                        && utilsServiceName !== data_service ) {
                        //console.debug( `skipping ${ data_service } checkbox: app browser service being used` ) ;
                        if ( showSkipOnce ) {
                            showSkipOnce = false;
                            jQuery( '<div/>', {
                                class: "csap-blue",
                                text: "To view all available services and customize graphs - switch service selector underneath service view to default"
                            } )
                                .appendTo( $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ).parent() );
                        }
                        continue;
                    }

                }

                // kubernetes service hooks
                let numContainers = 1;
                let matchedInstance = null;

                for ( let serviceInstance of serviceInstances ) {
                    //console.log(`${serviceName_port}: checking serviceInstance.serviceName: ${serviceInstance.serviceName}`)
                    if ( serviceName_port.startsWith( serviceInstance.serviceName ) && serviceInstance.replicaCount ) {
                        //$serviceLabel.append( "replica" +serviceInstance.replicaCount ) ;
                        numContainers = serviceInstance.replicaCount;
                        matchedInstance = serviceInstance;
                        break;
                    }
                }

                for ( let containerCount = 1; containerCount <= numContainers; containerCount++ ) {

                    //if ( numContainers > 1 && containerCount > 1 ) {
                    if ( matchedInstance !== null ) {
                        label = matchedInstance.serviceName + "-" + containerCount;
                        serviceName_port = label;
                        data_service = label;
                    }

                    let id = "serviceCheckbox" + serviceName_port;

                    if ( $( "#" + id, $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ) ).length > 0 )
                        continue;

                    let $serviceLabel = jQuery( '<label/>', {} )
                        .appendTo( $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ) );

                    let $serviceInput = jQuery( '<input/>', {
                        id: id,
                        class: "custom serviceCheckbox servicenameCheck",
                        type: "checkbox",
                        value: serviceName_port,
                        "data-servicename": data_service,
                        name: "interval" + this.$GRAPH_INSTANCE.attr( "id" )
                    } ).appendTo( $serviceLabel );

                    jQuery( '<span/>', { text: label } ).appendTo( $serviceLabel );

                    if ( $.inArray( data_service, servicesRequested ) != -1 ||
                        $.inArray( serviceName_port, servicesRequested ) != -1 ) {
                        // Note that multiple instnances on same host match the first on the first, and second on refreshes
                        $serviceInput.prop( "checked", true );
                    }
                }

                // console.log("updateServiceCheckboxes() Adding:" + label + " selected: " + $serviceInput.prop( "checked") ) ;


            }
            $( ".serviceCheckboxes", this.$GRAPH_INSTANCE ).show();
        }

        setStackHostContainer( hostName ) {
            this._stackHost = hostName;
            console.log( " setStackHostContainer: " + this._stackHost )
        }

        reDraw() {
            console.log( "reDraw(): " + this.$GRAPH_INSTANCE.attr( "id" ) );

            utils.loading( "Updating view" );


            let resourceGraph = this;

            let dialog = null;
            if ( window.csapGraphSettings == undefined ) {
                //                dialog = alertify.notify( "Graphs are being redrawn", 0 ) ;
                console.log( `reDraw() refreshing graphs` );
            }
            // plot does not handle axis well: plot.resize().setupGrid().draw()

            setTimeout( function () {
                resourceGraph._dataManager.clearStackedGraphs();
                for ( let i = 0; i < resourceGraph._hostArray.length; i++ ) {
                    let host = resourceGraph._hostArray[ i ];
                    if ( host == resourceGraph._stackHost ) {
                        continue; // always do last
                    }
                    // console.log( "Drawing: " +  host);
                    // race condition - the last graph should be the same.
                    let lastData = resourceGraph._dataManager.clearHostData( host );
                    if ( lastData != null ) {
                        resourceGraph.metricDataSuccess( host, lastData, false );
                    }
                }
                if ( resourceGraph._stackHost != null ) {
                    let lastData = resourceGraph._dataManager.clearHostData( resourceGraph._stackHost );
                    if ( lastData != null ) {
                        resourceGraph.metricDataSuccess( resourceGraph._stackHost, lastData, false );
                    }
                }
                if ( dialog != null ) {
                    dialog.dismiss();
                }
                utils.loadingComplete();

            }, 100 );

        }

        getMetricType = function () {
            return this._metricType;
        }

    }

    return ResourceGraph;

} 