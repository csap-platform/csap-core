

// Note: circular dependency with reports, so we use eventListeners
define( [], function () {

    console.log( "Module loaded: model" ) ;

    var _applicationSummaryArray = null ;
    var _modelPackages = null ;
    var _serviceAttributes = null ;

    var _modelDeffered = new $.Deferred() ;

    var _eventListeners = {
        applicationsUpdated: null,
        projectModelUpdated: null,
        lifecycleUpdated: null
    } ;

    var _clusterServices = null ;

    return {
        getModelLoadedDeffered: function () {
            return _modelDeffered ;
        },
        // event binding to ui
        registerListeners: function () {
            return _eventListeners ;
        },
        // 
        updateApplications: function ( ) {

            getApplications(  ) ;
        },
        // 
        updateProjectLifeCycles: function ( ) {
            getProjectLifeCycles(  ) ;
        },
        // 
        updateLifecycleModel: function ( ) {
            
            // $.Deferred
            return getLifecycleModel(  ) ;
        },
        // 
        getAppSummayArray: function ( ) {
            return _applicationSummaryArray ;
        },
        //
        getPackages: function () {
            return _modelPackages ;
        },
        //
        getServiceLabels: function ( service, item ) {

            var label = splitUpperCase( item ) ;

            if ( _metricLabels[item] ) {
                label = _metricLabels[item] ;
                ;
            }

            if ( _serviceAttributes
                    && _serviceAttributes[service]
                    && _serviceAttributes[service][item] ) {
                label = _serviceAttributes[service][item] ;
            }

            return label ;
        },
        getPackageDetails: function ( packageName ) {
            return getPackageDetails( packageName ) ;
        },
        isServiceInSelectedCluster: function ( serviceName ) {
            return isServiceInSelectedCluster( serviceName ) ;
        },
        //
        getSelectedProject: function () {
            return getSelectedProject() ;
        },
        //
        isHostInSelectedCluster: function ( hostName ) {
            return isHostInSelectedCluster( hostName ) ;
        },
        //
        updateClusterServices: function () {
            updateClusterServices() ;
        }
    }

    function getApplications () {

        // console.log( "getProjects():  uiSettings: " + jsonToString( uiSettings ) )
        // $('#serviceOps').css("display", "inline-block") ;

        console.groupCollapsed( "Loading Application Model" ) ;
        $.getJSON(
                uiSettings.eventApiUrl
                + "/appIds?numDays=7&category=/csap/reports/host/daily",
                {} )

                .done( function ( projectJson ) {
                    getApplicationSuccess( projectJson ) ;
                    console.groupEnd( "Completed Application Model" ) ;
                } )

                .fail(
                        function ( jqXHR, textStatus, errorThrown ) {

                            handleConnectionError(
                                    "Retrieving service appIds in last 7 days: <br/>"
                                    + errorThrown, errorThrown ) ;
                        } ) ;
    }

    function getSelectedAppId () {
        if ( $( "#projectSelect option" ).length == 0 )
            return "dummy" ;
        return $( "#projectSelect" ).val().split( "," )[0] ;
    }

    function getSelectedProject () {
        // console.log("getSelectedProject: " + $("#projectSelect").val());
        // race condition on startUp
        if ( $( "#projectSelect" ).val() == null )
            return uiSettings.projectParam ;

        return $( "#projectSelect" ).val().split( "," )[1] ;
    }

    function getApplicationSuccess ( jsonResponse ) {

        _applicationSummaryArray = jsonResponse ;

        //console.log( "applicationSummaryArray: " + jsonToString( _applicationSummaryArray )) ;

        if ( _applicationSummaryArray.error ) {
            alertify.alert( "Error response from server: " + _applicationSummaryArray.error ) ;
            return ;
        }

        if ( _eventListeners.applicationsUpdated != null ) {
            _eventListeners.applicationsUpdated() ;
        }

        if ( $( "#projectSelect option" ).length > 0 ) {
            console.log( "project: " + $( "#projectSelect" ).val() )
            getProjectLifeCycles() ;
        }

    }

    function getProjectLifeCycles () {

        var appIdSelected = getSelectedAppId() ;
        $( "#lifeCycleDiv" ).hide() ;

        console.log( "Getting lifecycles available for: " + appIdSelected
                + " project: " + $( "#projectSelect" ).val() ) ;

        // $('#serviceOps').css("display", "inline-block") ;

        $.getJSON(
                uiSettings.eventApiUrl
                + "/lifecycles?numDays=7&category=/csap/reports/host/daily",
                {
                    appId: appIdSelected,
                } )

                .done( function ( responseJson ) {
                    projectLifecycleSuccess( responseJson, appIdSelected ) ;
                } )

                .fail(
                        function ( jqXHR, textStatus, errorThrown ) {

                            handleConnectionError(
                                    "getProjectLifeCycles() failed: " + uiSettings.eventApiUrl, errorThrown ) ;
                        } ) ;
    }

    function projectLifecycleSuccess ( responseJson, appIdSelected ) {

        console.log( "projectLifecycleSuccess() " + jsonToString( responseJson ) ) ;

        if ( responseJson.error ) {
            alertify.alert( "Error response from server: " + responseJson.error ) ;
            return ;
        }


        if ( _eventListeners.lifecycleUpdated != null ) {
            _eventListeners.lifecycleUpdated( responseJson.lifecycles ) ;
        }


        getLifecycleModel() ;

    }

    function getLifecycleModel ( isUseProjectParam ) {

        var appId = getSelectedAppId(), life = $( "#lifeSelect" ).val() ;
        if ( appId === "dummy" ) {
            appId = uiSettings.appIdParam, life = uiSettings.lifeParam ;
        }
        console.log( `getLifecycleModel() appId ${ appId },  life: ${ life}` ) ;
        // console.trace() ;

        if ( life == "" ) {
            life = $( "#lifeSelect" ).val()
        }

        var appIdSelected = appId ;
        var lifeCycleSelected = life ;

        // console.log("getLifecycleModel(): " + appIdSelected + " env: " + lifeCycleSelected ) ;

        var requestParams = {
            appId: appIdSelected,
            life: lifeCycleSelected,
            category: "/csap/system/model/summary"
        } ;

        if ( isUseProjectParam ) {
            $.extend( requestParams, {
                project: getSelectedProject()
            } ) ;
        }
        let $contentLoaded = new $.Deferred() ;
        // latestCached or latest
        $.getJSON( uiSettings.eventApiUrl + "/latest", requestParams )

                .done( function ( responseJson ) {
                    lifeCycleModelSuccess( responseJson, appIdSelected, lifeCycleSelected, isUseProjectParam ) ;
                     $contentLoaded.resolve() ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "getLifecycleModel()  " + uiSettings.eventApiUrl + " error "
                            + errorThrown, errorThrown ) ;
                } ) ;
                
         return $contentLoaded ;

    }

    function lifeCycleModelSuccess ( responseJson, appIdSelected, lifeCycleSelected, isUseProjectParam ) {

        $( "#filterSection" ).show() ;
        if ( responseJson == null ) {
            var message = "Failed to retrieve model data:  Validate data exists for: "
                    + appIdSelected + " lifecycle: " + lifeCycleSelected ;
            console.log( message ) ;
            if ( $( "#serviceStats" ).length == 0 ) {
                alertify.alert( message ) ;
            }
            return ;
        }

        if ( responseJson.error ) {
            
            if ( responseJson.stub ) {
                console.log( "lifeCycleModelSuccess() : Foud stub - error response from server: " + responseJson.error ) ;
                
            } else {
                alertify.alert( "Error response from server: " + responseJson.error ) ;
            }
            return ;
        }

        if ( responseJson.data.packages == undefined ) {
            _modelPackages = responseJson.data.packageSummary
        } else {
            _modelPackages = responseJson.data.packages ;
            _serviceAttributes = responseJson.data.serviceAttributes ;
        }

        console.log( "modelSuccess() for project: " + getSelectedProject()
                + " life: " + lifeCycleSelected ) ;

        $( "#clusterSelect" ).empty() ;
        
        for ( var i = 0 ; i < _modelPackages.length ; i++ ) {
            if ( _modelPackages[i].package == getSelectedProject() ) {

                var projectClusters = _modelPackages[i].clusters ;

                for ( var clusterName in projectClusters ) {

                    var clusterOption = jQuery( '<option/>', {text: clusterName} ) ;
                    $( "#clusterSelect" ).append( clusterOption ) ;
                }
            }
        }
        _clusterHosts = null ;

        /**
         * Appid have been reused - so sometimes need to query to include project
         */
        if ( getPackageDetails( getSelectedProject() ) == undefined ) {

            if ( !isUseProjectParam ) {
                console.info( "No matching project  found in default model - adding project to request", getSelectedProject() ) ;
                getLifecycleModel( true ) ;
                return ;
            } else {
                console.error( "No information found for project: '" + getSelectedProject() + "'. Select Another"  ) ;
            }
        }


        if ( _eventListeners.projectModelUpdated != null ) {
            _eventListeners.projectModelUpdated() ;
        }


        _modelDeffered.resolve() ;
    }

    function getPackageDetails ( selectedPackage ) {

        // console.log("getPackageDetails() modelPackages: " + JSON.stringify(_modelPackages, null,"\t")  ) ;

        if ( _modelPackages == null )
            return null ;

        // temporary - support old style
        if ( _modelPackages.length == undefined )
            return _modelPackages[selectedPackage] ;

        // latest release uses arrays
        for ( var i = 0 ; i < _modelPackages.length ; i++ ) {
            if ( _modelPackages[i].package == selectedPackage )
                return _modelPackages[i] ;
        }

        return null ;
    }

    function isServiceInSelectedCluster ( serviceName ) {

        // temporary until all clusters are in summary
        var selectedCluster = $( "#clusterSelect" ).val().split( "-" )[0] ;
//	console.log("isServiceInSelectedCluster() " + selectedCluster + " service " + serviceName 
//			+ "  _clusterServices: " + JSON.stringify(_clusterServices, null,"\t")  )

        if ( _clusterHosts == null || selectedCluster == "all" )
            return true ;


        if ( _clusterServices[serviceName].indexOf( selectedCluster ) != -1 ) {
            return true
        }
        return false ;

    }

    function updateClusterServices () {

        for ( var i = 0 ; i < _modelPackages.length ; i++ ) {
            if ( _modelPackages[i].package === getSelectedProject() ) {

                var projectClusters = _modelPackages[i].clusters ;
                var projectServices = _modelPackages[i].instances.instanceCount ;
                _clusterServices = new Object() ;

                for ( var clusterName in projectClusters ) {
                    if ( $( "#clusterSelect" ).val() == clusterName ) {
                        _clusterHosts = projectClusters [clusterName] ;

                        for ( var j = 0 ; j < projectServices.length ; j++ ) {
                            var serviceInstance = projectServices[j] ;
                            _clusterServices[ serviceInstance.serviceName ] = serviceInstance.cluster ;
                        }
                    }
                }
            }
        }
    }


    var _clusterHosts = null ;
    function isHostInSelectedCluster ( hostName ) {

        if ( _clusterHosts == null || $( "#clusterSelect" ).val() == "all" )
            return true ;
        if ( $.inArray( hostName, _clusterHosts ) != -1 ) {
            return true
        }
        return false ;

    }


} )

