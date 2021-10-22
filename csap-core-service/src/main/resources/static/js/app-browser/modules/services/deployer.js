define( [ "browser/utils", "ace/ace", ], function ( utils, aceEditor ) {

    console.log( "Module loaded" ) ;
    const $dialog = $( ".alertify-content" ) ;

    const $servicesContent = utils.findContent( "#services-tab-content" ) ;

    const $serviceParameters = $( "#serviceParameters", $servicesContent ) ;
    const $resultsPre = $( "#results" ) ;

    const $instancePanel = $( "#services-tab-instances" ) ;
    const $tableOperations = utils.findContent( "#table-operations", $instancePanel ) ;
    const $instanceMenu = $( "#instance-actions-menu", $instancePanel ) ;
    const $optionsPanel = $( "div.options", $instancePanel ) ;

    const $killOptions = $( "#killOptions" ) ;
    const $killButton = $( '#killButton', $killOptions ) ;
    const $stopButton = $( '#stopButton', $killOptions ) ;

    const $deployOptions = $( "#deployOptions" ) ;
    const $osDeploy = $( "#osDeployOptions", $deployOptions ) ;


    const $kubernetesMastersSelect = $( "#master-deploy-hosts", $tableOperations ) ;

    let _postBacklogRefreshes = 0 ;

    let _kubernetesEditor = null ;
    let latest_instanceReport = null ;
    let serverType = null ;
    let primaryHost = null ;
    let selectedHosts = null ;
    let primaryInstance = null ;
    let serviceName = null ;

    let isBuild = false ;

    let instancesRefreshFunction ;
    let refreshTimer ;


    let _deployDialog = null ;
    let fileOffset = "-1" ;
    const LOG_CHECK_INTERVAL = 2 * 1000 ;

    let _dockerStartParamsEditor ;


    let servicePerformanceId, serviceShortName ;

    return {

        initialize: function ( getInstances ) {
            initialize() ;
            instancesRefreshFunction = getInstances ;
        },

        update_view_for_service: function ( instanceReport ) {
            update_view_for_service( instanceReport ) ;
        },

        showStartDialog: function ( hosts ) {
            selectedHosts = hosts ;
            primaryHost = hosts[0] ;
            showStartDialog() ;
        },

        showStopDialog: function ( hosts ) {
            selectedHosts = hosts ;
            primaryHost = hosts[0] ;
            showStopDialog() ;
        },

        showDeployDialog: function ( hosts ) {
            selectedHosts = hosts ;
            primaryHost = hosts[0] ;
            showDeployDialog() ;
        },

        executeOnHosts: function ( hosts, command, paramObject ) {
            selectedHosts = hosts ;
            executeOnSelectedHosts( command, paramObject ) ;
        }




    } ;

    function initialize() {

        $( "#dockerImageVersion" ).change( function () {
            $serviceParameters.val( "{}" ) ;
            if ( latest_instanceReport.dockerSettings ) {
                latest_instanceReport.dockerSettings.image = $( this ).val() ;
                if ( _dockerStartParamsEditor != null ) {
                    _dockerStartParamsEditor.setValue( JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" ) ) ;
                }
            }
        } ) ;

        $( "#isClean" ).change( function () {
            if ( latest_instanceReport.kubernetes ) {
                if ( $( "#isClean" ).is( ':checked' ) ) {
                    alertify.csapWarning( "Caution: deleting volumes will permanently delete service data." ) ;
                }
            }
        } ) ;

        $( "#clean-docker-volumes" ).change( function () {
            if ( $( "#clean-docker-volumes" ).is( ':checked' ) ) {
                $( '#isClean' )
                        .prop( 'checked', true )
                        .prop( 'disabled', true ) ;
                alertify.csapWarning( "Caution: deleting volumes will permanently delete service data." ) ;
            } else {
                $( '#isClean' )
                        .prop( 'disabled', false ) ;
            }
        } ) ;
    }


    function update_view_for_service( instanceReport ) {

        latest_instanceReport = instanceReport ;
        primaryInstance = instanceReport.instances[0]
        serviceName = primaryInstance.serviceName ;
        serverType = primaryInstance.serverType ;

        console.log( `update_view_for_service: ${ serviceName }, ${ serverType }, primary:`, primaryInstance ) ;


        let noteText = instanceReport.deploymentNotes ;
        let sentenceInNotesLocation = ( instanceReport.deploymentNotes ).indexOf( "." ) ;
        if ( sentenceInNotesLocation > 0 ) {
            noteText = instanceReport.deploymentNotes.substring( 0, sentenceInNotesLocation ) ;
        }
        let $notes = jQuery( '<div/>' ) ;
        jQuery( '<span/>', {
            text: noteText
        } ).appendTo( $notes ) ;
        $( ".deployment-notes" ).html( $notes.html() ) ;
        if ( sentenceInNotesLocation > 0 ) {
            $notes.append( instanceReport.deploymentNotes.substring( sentenceInNotesLocation ) ) ;
        }



        $( ".service-live", $instanceMenu ).hide() ;
        if ( primaryInstance.serviceHealth && ( primaryInstance.serviceHealth != "" ) ) {
            $( ".service-live", $instanceMenu ).show() ;
        }

        $( ".instance-tools", $instanceMenu ).hide() ;
        if ( latest_instanceReport.javaJmx ) {
            $( ".instance-tools", $instanceMenu ).show() ;
        }



        $( "#service-description .text" ).text( instanceReport.description ) ;
        if ( utils.isUnregistered( serverType ) ) {
            $( "#service-description .text" ).text( `To collect and trend os resources, add containers to the application definition` ) ;
        }
        if ( instanceReport.docUrl !== "" ) {

            jQuery( '<a/>', {
                href: "#" + instanceReport.docUrl,
                class: "csap-link-icon csap-help",
                target: "_blank",
                text: "learn more"
            } )
                    .click( function () {
                        let urlArray = instanceReport.docUrl.split( ',' ) ;
                        for ( let targetUrl of  urlArray ) {
                            console.log( `launch url: targetUrl` ) ;
                            utils.launch( targetUrl ) ;
                        }
                        return false ;
                    } )
                    .appendTo( $( "#service-description .text" ) ) ;
        }

        let profile = serverType ;
        if ( profile === "script" ) {
            profile = "OS Files" ;
        } else if ( profile === "os" ) {
            profile = "Process Monitor" ;
        } else if ( ( profile === "docker" )
                && ( latest_instanceReport.kubernetes ) ) {
            profile = "kubernetes" ;
        }
        $( "#profile", $instancePanel ).text( `[${ profile }]` ) ;


        $( ".is-java-server" ).hide() ;
        if ( instanceReport.parameters ) {
            $serviceParameters.val( instanceReport.parameters ) ;
            $( ".is-java-server" ).show() ;
        }
        //$( "#serviceParameters" ).text( instanceReport.parameters ) ;
        $( "#mavenArtifact" ).val( instanceReport.mavenId ) ;
        $( "#scmLocation" ).text( instanceReport.scmLocation ) ;
        $( "#scmLocation" ).attr( "href", instanceReport.scmLocation ) ;
        $( "#scmFolder" ).text( instanceReport.scmFolder ) ;
        $( "#scmBranch" ).val( instanceReport.scmBranch ) ;


        //
        // Main buttons
        //
        $( "#deploy-buttons", $optionsPanel ).show() ;
        $( "button.start, button.stop", $optionsPanel ).show() ;

        if ( latest_instanceReport.kubernetes ) {
            $( "button.start, button.stop", $optionsPanel ).hide() ;
            $( " button.remove", $optionsPanel ).show() ;
        } else {
            $( " button.remove", $optionsPanel ).hide() ;
        }
        $( ".hquote", $optionsPanel ).remove() ;
        if ( serverType === "os" ) {

            let message = "<span>OS Process Monitor</span> - deployment operations not available"
            jQuery( '<div/>', {
                class: "hquote",
                html: message
            } ).css( "margin", "10px" ).css( "font-size", "10pt" ).css( "padding", "0em" ).appendTo( $optionsPanel ) ;

            $( "#deploy-buttons", $optionsPanel ).hide() ;
            $( "#meters" ).hide() ;
        }


        //
        //  dialog buttons
        //
        $stopButton.show() ;
        $( "#clean-docker-volumes-container" ).hide() ;
        let killText = "Stop and Remove" ;
        if ( latest_instanceReport.filesOnly || latest_instanceReport.kubernetes ) {
            $stopButton.hide() ;
        }
        if ( serviceName == AGENT_NAME ) {
            killText = "Restart CSAP Agent" ;

        } else if ( latest_instanceReport.filesOnly ) {
            killText = "Stop and Remove: CSAP package" ;

        } else if ( latest_instanceReport.kubernetes ) {
            killText = "Delete Kubernetes Resource(s)" ;

        } else if ( serverType == "docker" ) {
            killText = "Stop and Remove: container" ;
            $( "#clean-docker-volumes-container" ).show() ;
        }
        $( "span", $killButton ).text( killText ) ;

        //
        //  Menu Customization
        //
        $( "button.java", $instanceMenu ).hide() ;
        if ( latest_instanceReport.javaCollection ) {
            $( "button.java", $instanceMenu ).show() ;
        }

        $( "button.application", $instanceMenu ).hide() ;
        if ( latest_instanceReport.performanceConfiguration ) {
            $( "button.application", $instanceMenu ).show() ;
        }
        $( "button.launch", $instanceMenu ).show() ;
        if ( serverType == "unregistered" ) {
            $( "button.launch", $instanceMenu ).hide() ;
        }


        //
        //  Deployment dialog
        //
        $( ">div", $deployOptions ).hide() ;
        if ( serverType == "docker" ) {
            if ( latest_instanceReport.kubernetes ) {
                $( "#kubernetesDeployOptions" ).show() ;
            } else {

                $( "#dockerDeployOptions" ).show() ;
                $( "#dockerImageVersion" ).val( "" ) ;
                if ( latest_instanceReport.dockerSettings ) {
                    if ( latest_instanceReport.dockerSettings.image ) {
                        $( "#dockerImageVersion" ).val( latest_instanceReport.dockerSettings.image ) ;
                    }

                }
            }
        } else {
            $osDeploy.show() ;
        }


        if ( latest_instanceReport.filesOnly ) {
            $( "#service-cpu" ).hide() ;
            $( "#osChart" ).hide() ;
            $( "#osLearnMore" ).show() ;
        } else {
            $( "#service-cpu" ).show() ;
            $( "#osChart" ).show() ;
            $( "#osLearnMore" ).hide() ;
        }




        $( "#deployStart" ).prop( "checked", true ) ;
        $( "#deployStart" ).show() ;
        if ( serviceName == AGENT_NAME ) {
            $( "#deployStart" ).prop( 'checked', false ) ;
            $stopButton.hide() ;
        }

        servicePerformanceId = serviceName ;
        serviceShortName = serviceName ;
        if ( latest_instanceReport.kubernetes ) {
            let instanceK8sHosts = primaryInstance["kubernetes-masters"] ;

            if ( Array.isArray( instanceK8sHosts ) ) {

                $kubernetesMastersSelect.empty() ;

                instanceK8sHosts.forEach( k8Master => {
                    console.log( `\n\n\n master : ${k8Master} ` ) ;
                    let $optionItem = jQuery( '<option/>', {
                        text: k8Master
                    } ) ;
                    $kubernetesMastersSelect.append( $optionItem ) ;

                } )
            }

//            _is_clear_instance_on_refresh = true ;
            servicePerformanceId += "-" + ( primaryInstance.containerIndex + 1 ) ;
            serviceShortName = serviceName + "-" + ( primaryInstance.containerIndex + 1 )
            console.log( `cluster is kubernetes, performance id updated: '${ servicePerformanceId }'`
                    + ` \t serviceShortName is: '${serviceShortName}'` ) ;

            console.log( `Hiding startButton` ) ;
            $( "#killOptionsButton span" ).text( "Remove..." ) ;

            $( "#docker-clean-description" ).text( "Clean will remove associated persistent volumes" ) ;
            if ( latest_instanceReport.dockerSettings["deployment-files-use"] == "true" ) {
                $( "#docker-clean-description" ).text( "Clean will include any specs named '*deploy-only*.yaml'" ) ;
            }
            $( "#service-runtime" ).html( 'Kubernetes<img src="images/k8s.png">' ) ;
            $killButton.attr( "title", "Service delete will be done by running kubectl delete -f spec_file(s)" ) ;

            $( "#deployStart" ).prop( "checked", false ) ;
            $( "#deployStart" ).hide() ;
            $( "#admin-deploy-note" ).text( "Kubernetes deployments will auto select host, pull docker image(s), and start the container(s)" ) ;

        }

        $( ".is-tomcat" ).hide() ;
        if ( instanceReport.tomcat ) {
            $( ".is-tomcat" ).show() ;
        }

        $( ".is-java-jmx" ).hide() ;
        if ( instanceReport.javaJmx ) {
            $( ".is-java-jmx" ).show() ;
        }




        $( ".is-datastore" ).hide() ;
        if ( instanceReport.datastore ) {
            $( ".is-datastore" ).show() ;
        }


        $( ".is-kubernetes" ).hide() ;
        $( ".is-not-kubernetes" ).show() ;
        if ( latest_instanceReport.kubernetes ) {
            $( ".is-kubernetes" ).show() ;
            $( ".is-not-kubernetes" ).hide() ;
        }



        //
        //
        //

        $( ".is-docker" ).hide() ;
        $( ".is-not-docker" ).show() ;
        $( '#isClean' ).prop( 'checked', true ) ;
        //alert(`utils.getActiveEnvironment() ${utils.getActiveEnvironment()}`) ;


        $( '#isSaveLogs' ).prop( 'checked', true ) ;
        if ( utils.getActiveEnvironment() == ( `dev` ) ) {
            $( '#isSaveLogs' ).prop( 'checked', false ) ;
        }
        if ( serverType == "docker" ) {
            $( ".is-docker" ).show() ;
            $( ".is-not-docker" ).hide() ;
            $( '#isClean' ).prop( 'checked', false ) ;
        }

        $( ".is-csap-api" ).hide() ;
        $( ".is-not-csap-api" ).show() ;
        if ( latest_instanceReport.csapApi ) {
            $( ".is-csap-api" ).show() ;
            $( ".is-not-csap-api" ).hide() ;
        }


        $( ".is-files-only" ).hide() ;
        $( ".is-note-files-only" ).show() ;
        if ( latest_instanceReport.filesOnly ) {
            $( ".is-files-only" ).show() ;
            $( ".is-not-files-only" ).hide() ;
        }




    }

    function showStartDialog() {

        // Lazy create
        if ( !alertify.start ) {
            let startDialogFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( $( "#startOptions" ).show()[0] ) ;
                        this.setting( {
                            'onok': startService,
                            'oncancel': function () {
                                console.log( "Cancelled Request" ) ;
                            }
                        } ) ;
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Start Service", className: alertify.defaults.theme.ok, key: 0 },
                                { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                            ],
                            options: buildAlertifyOptions( "Start Service Dialog" )
                        } ;
                    }

                } ;
            } ;
            alertify.dialog( 'start', startDialogFactory, false, 'confirm' ) ;
        }

        let startDialog = alertify.start().show() ;

        if ( serverType == "docker" ) {
            maximizeDialog( startDialog ) ;
            if ( _dockerStartParamsEditor == null ) {

                let params = "{}" ;
                if ( latest_instanceReport.dockerSettings ) {
                    params = JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" ) ;
                }

                $( "#docker-params-definition" ).text( params ) ;
                resize_alertify_element( $( "#docker-params-definition" ), 200 ) ;


                setTimeout( function () {
                    _dockerStartParamsEditor = aceEditor.edit( "docker-params-definition" ) ;
                    //editor.setTheme("ace/theme/twilight");
                    //editor.session.setMode("ace/mode/yaml");

                    _dockerStartParamsEditor.setOptions( utils.getAceDefaults( "ace/mode/json" ) ) ;
                }, 200 ) ;

            }

        } else {
            resizeDialog( startDialog, $( "#startOptions" ) ) ;
        }

        setAlertifyTitle( "Starting", startDialog ) ;

        if ( serviceName.indexOf( AGENT_NAME ) != -1 ) {
            let message = "Warning - csap agent should be killed which will trigger auto restart."
                    + "<br> Do not issue start on csap agent unless you have confirmed in non production environment." ;

            alertify.csapWarning( message ) ;
        }


    }

    function maximizeDialog( dialog ) {

        let targetWidth = $( window ).outerWidth( true ) - 50 ;
        let targetHeight = $( window ).outerHeight( true ) - 50 ;

        dialog.resizeTo( targetWidth, targetHeight ) ;
    }


    function resizeDialog( theAlertifyDialog, $displaySection ) {

        if ( theAlertifyDialog == null ) {
            console.log( "resizeDialog() theAlertifyDialog is null- skipping" ) ;
            return ;
        }
        let targetWidth = $( window ).outerWidth( true ) - 100 ;
        let targetHeight = $( window ).outerHeight( true ) - 100 ;

        let customHeight = targetHeight / 2 ;

        if ( $displaySection ) {
            customHeight = Math.round( $displaySection[0].scrollHeight ) + 120 ;
        }
        //let customHeight = Math.round( $warningsList.height()  ) ;

        console.log( "customHeight: ", customHeight )

        if ( customHeight > targetHeight )
            customHeight = targetHeight ;

        theAlertifyDialog.resizeTo( targetWidth, customHeight )
    }

    function resize_alertify_element( $element, additionalHeight, alertifyExtra = 300 ) {
        let alertifyHeight = Math.round( $( ".alertify" ).parent().outerHeight( true ) - alertifyExtra ) ;
        let targetHeight = alertifyHeight ;
        let currentHeight = $element[0].scrollHeight + additionalHeight ;

        if ( currentHeight < alertifyHeight ) {
            targetHeight = currentHeight ;
        }
        $element.height( targetHeight ) ;
        //$element.height( Math.round( $( ".alertify" ).parent().outerHeight( true ) - 400 ) ) ;
        $element.width( Math.round( $( ".alertify" ).parent().outerWidth( true ) - 200 ) ) ;
    }

    function buildAlertifyOptions( title ) {
        let options = {
            title: title,
            movable: true,
            maximizable: true,
            resizable: true,
            autoReset: false
        }

        return options ;
    }


    /**
     * 
     * This sends an ajax http get to server to start the service
     * 
     */

    /** @memberOf ServiceAdmin */
    function startService() {


        let paramObject = new Object() ;

        let startParams = "" ;
        if ( serverType == "docker" ) {

            if ( _dockerStartParamsEditor != null ) {
                startParams = _dockerStartParamsEditor.getValue() ;
            } else {
                startParams = JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" ) ;
            }
        } else {
            if ( $( "#noDeploy" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    noDeploy: "noDeploy"
                } ) ;
            }

            if ( $( "#isDebug" ).is( ':checked' )
                    && $serviceParameters.val().indexOf( "agentlib" ) == -1 ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;

                $serviceParameters.val( $serviceParameters.val()
                        + " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + debugPort ) ;

            }

            if ( $( "#isJmc" ).is( ':checked' )
                    && $serviceParameters.val().indexOf( "FlightRecorder" ) == -1 ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;

                $serviceParameters.val( $serviceParameters.val()
                        + " -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints" ) ;

            }


            if ( $( "#isGarbageLogs" ).is( ':checked' )
                    && $serviceParameters.val().indexOf( "PrintGCDetails" ) == -1 ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;

                $serviceParameters.val( $serviceParameters.val()
                        + gcParams ) ;

            }
            if ( $( "#isInlineGarbageLogs" ).is( ':checked' )
                    && $serviceParameters.val().indexOf( "PrintGCDetails" ) == -1 ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;

                $serviceParameters.val( $serviceParameters.val()
                        + gcInlineParams ) ;

            }
            startParams = $serviceParameters.val() ;
        }

        $.extend( paramObject, {
            commandArguments: startParams
        } ) ;

        if ( $serviceParameters.length && ( $serviceParameters.val().indexOf( "agentlib" ) != -1 ) ) {
            alertify.alert( 'Service started in debug mode, configure your ide with host: <div class="note">' + hostName
                    + '</div> debug port: <div class="note">' + debugPort
                    + '</div><br><br> Jvm started using options: <br><div class="note">' + $serviceParameters.val() + '</div>' ) ;
            $( ".alertify-inner" ).css( "text-align", "left" ) ;
            $( ".alertify" ).css( "width", "800px" ) ;
            $( ".alertify" ).css( "margin-left", "-400px" ) ;
        }


        executeOnSelectedHosts( "startServer", paramObject ) ;

    }


    function executeOnSelectedHosts( command, paramObject ) {

        console.log( `Running '${command}':`, paramObject ) ;
        //$busyPanel.show() ;

        let message = `running ${command}` ;
        if ( command == 'killServer' ) {
            message = "Scheduling removal of service" ;
        } else if ( command == 'runServiceJob' ) {
            message = ` Running service job ....` ;
        }
        utils.loading( message ) ;

        // alert("numSelected: " + numHosts) ;
        // nothing to do on a single node build
        if ( !isBuild ) {
            $resultsPre.html( "" ) ;
        }

        $resultsPre.append( "\n\nStarting Request: " + command + "\n" ) ;
        $( 'body' ).css( 'cursor', 'wait' ) ;

        let numResults = 0 ;

        // Now run through the additional hosts selected
        let postCommandToServerFunction = function (
                serviceInstance,
                serviceHost,
                totalCommandsToRun ) {

            let hostParamObject = new Object() ;

            if ( $( "#isHotDeploy" ).is( ':checked' ) ) {
                $.extend( paramObject, {
                    hotDeploy: "hotDeploy"
                } ) ;
            }


            $.extend( hostParamObject, paramObject, {
                hostName: serviceHost,
                serviceName: serviceInstance
            } ) ;

            let commandUrl = SERVICE_URL + "/" + command ;
            console.log( `Posting to: '${commandUrl}'` ) ;

            $.post( commandUrl, hostParamObject, totalCommandsToRun )
                    .done(
                            function ( results ) {
                                // displayResults(results);
                                numResults++ ;


                                displayHostResults(
                                        serviceHost,
                                        serviceInstance,
                                        command, results,
                                        numResults,
                                        totalCommandsToRun ) ;


                                isBuild = false ;

                                if ( numResults >= totalCommandsToRun ) {
                                    $( 'body' ).css( 'cursor', 'default' ) ;
                                    utils.loadingComplete() ;
                                    //refresh_service_instances() ;
                                }
                            } )

                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        //console.log( JSON.stringify( jqXHR, null, "\t" ));
                        //console.log( JSON.stringify( errorThrown, null, "\t" ));

                        utils.loadingComplete() ;
                        handleConnectionError( serviceHost + ":" + command, errorThrown ) ;
                    } ) ;


        }


        if ( latest_instanceReport.kubernetes ) {
            postCommandToServerFunction( serviceName, $kubernetesMastersSelect.val(), 1 ) ;

        } else {

            let totalCommandsToRun = selectedHosts.length ;
            for ( let host of selectedHosts ) {
                postCommandToServerFunction( serviceName, host, totalCommandsToRun ) ;
            }
        }

        // show logs
        if ( command != "runServiceJob" ) {

            if ( utils.isLaunchServiceLogs() ) {
                utils.launchServiceLogs( command ) ;
            } else {
                alertify.notify( ` ${command} has been requested, monitor via backlog queue or switch to log view` ) ;
            }
        }

    }

    function displayHostResults( commandHost, serviceInstance, command, resultsJson, currentCount, totalCount ) {
        //console.log( `displayHostResults() ${ serviceInstance } `, resultsJson ) ;

        let hostPath = `clusteredResults.${ commandHost }` ;
        let hostResponse = utils.json( hostPath, resultsJson ) ;
        let directResponse = resultsJson.results ;
//        console.log( `${ commandHost }: ${hostResponse}` ) ;
        console.log( `displayHostResults() host: ${ commandHost } : ${command }` ) ;

        //

        if ( command == "runServiceJob" ) {
            let serviceResponse = utils.json( serviceInstance, hostResponse ) ;
            if ( serviceResponse && serviceResponse.includes( utils.getErrorIndicator() ) ) {
                alertify.csapWarning( `Error in Output: \n\n ${ serviceResponse }` ) ;
            } else if ( serviceResponse ) {
                alertify.csapInfo( serviceResponse ) ;
            } else {
                let serviceDirectResponse = utils.json( serviceInstance, resultsJson ) ;
                if ( serviceDirectResponse ) {
                    alertify.csapInfo( serviceDirectResponse ) ;
                } else {
                    alertify.csapWarning( JSON.stringify( resultsJson, "\n", "\t" ) ) ;
                }
            }

        } else {

            let response = utils.json( "results", hostResponse ) ;
            if ( ( response == "Request queued" )
                    || ( directResponse == "Request queued" )
                    || ( command == "stopServer" ) ) {
                // we switched to log view to view output via tail
                if ( !utils.isLaunchServiceLogs() ) {
                    alertify.csapInfo( response ) ;
                }

            } else if ( directResponse ) {
                alertify.csapInfo( `${commandHost}:  ${directResponse}` ) ;

            } else {
                // JSON.stringify( resultsJson, "\n", "\t" )
                //let hostResponse = utils.json( `clusteredResults.${ commandHost }`, resultsJson ) ;

                console.log( `processing hostReponse: `, hostResponse ) ;

                if ( hostResponse && hostResponse.results ) {
                    hostResponse = hostResponse.results ;
                }

                if ( !hostResponse ) {
                    // direct commands will not have clusterResutls
                    hostResponse = utils.json( `${ commandHost }`, resultsJson ) ;
                }
                alertify.csapInfo( `${commandHost}:  ${hostResponse}` ) ;
            }
        }
        _postBacklogRefreshes = 0 ;
        update_status_until_backlog_empty() ;


    }

    function update_status_until_backlog_empty() {

        console.log( `update_status_until_backlog_empty: backlog: ${ $( "#backlog-count" ).text() } postRefreshes: ${ _postBacklogRefreshes}` ) ;
        clearTimeout( refreshTimer ) ;
        refreshTimer = setTimeout( function () {

            $.when( utils.refreshStatus( true ) ).done( function () {
                instancesRefreshFunction( ) ;
                let backlog = $( "#backlog-count" ).text() ;
                //console.log( `update_status_until_backlog_empty() backlog: ${ backlog }` ) ;

                if ( backlog > 0 ) {
                    refreshTimer = setTimeout( update_status_until_backlog_empty, 1 * 5000 ) ;
                    _postBacklogRefreshes = 0 ;
                } else {
                    // add a couple of lagging
                    if ( _postBacklogRefreshes++ < 3 ) {
                        refreshTimer = setTimeout( update_status_until_backlog_empty, 1 * 5000 ) ;
                    }
                }
            } ) ;


        }, 1000 ) ;

    }

    function setAlertifyTitle( operation, dialog ) {
        let target = selectedHosts.length ;

        if ( primaryInstance.clusterType == "kubernetes" ) {
            target = " kubernetes master: " + $kubernetesMastersSelect.val() ;
        } else if ( target === 1 ) {
            target = primaryHost ;
        } else {
            target += " hosts" ;
        }
        _lastOperation = operation + " service: " + serviceName + " on " + target ;

        console.log( "setAlertifyTitle", _lastOperation ) ;
        dialog.setting( {
            title: _lastOperation
        } ) ;
    }


    function showStopDialog() {
        // Lazy create
        if ( !alertify.kill ) {
            let killDialogFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( $killOptions.show()[0] ) ;
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ } ],
                            options: buildAlertifyOptions( "Stop Service Dialog" )
                        } ;
                    }

                } ;
            } ;

            alertify.dialog( 'kill', killDialogFactory, false, 'alert' ) ;

            $killButton.click( function () {
                alertify.closeAll() ;
                killService() ;
            } ) ;

            $stopButton.click( function () {
                alertify.closeAll() ;
                if ( serverType != "SpringBoot" ) {
                    stopService() ;
                    return ;
                }
                let message = "Warning: service stops can take a while, and may never terminate the OS process."
                        + "<br><br>Use the CSAP Host Dashboard  and log viewer to monitor progress; use kill if needed."
                        + ' Unless specifically requested by service owner: <br>'
                        + '<div class="news"><span class="stopWarn">kill option is preferred as it is an immediate termination</span></div>' ;

                let applyDialog = alertify.confirm( buildHtmlMessage( message, "stop-confirm warning" ) ) ;
                applyDialog.setting( {
                    title: "Caution Advised",

                    resizable: true,
                    autoReset: false,
                    'labels': {
                        ok: 'Proceed Anyway',
                        cancel: 'Cancel request'
                    },
                    'onok': function () {
                        stopService() ;
                    },
                    'oncancel': function () {
                        alertify.warning( "Operation Cancelled" ) ;
                    }

                } ) ;
                resizeDialog( applyDialog, $( ".stop-confirm" ).parent() ) ;


            } ) ;

        }

        let stopDialog = alertify.kill().show() ;

        let dialogTitle = "Stopping" ;

        console.log( "latest_instanceReport", latest_instanceReport )
        if ( latest_instanceReport.kubernetes ) {
            dialogTitle = "Removing kubernetes"
            //serviceHost = $kubernetesMastersSelect.val() ;
        }
        setAlertifyTitle( dialogTitle, stopDialog ) ;

        resizeDialog( stopDialog, $killOptions.parent() ) ;

    }

    function stopService() {


        let paramObject = {
            serviceName: serviceName
        } ;

        executeOnSelectedHosts( "stopServer", paramObject ) ;

    }

    function killService() {

        let paramObject = new Object() ;

        if ( $( "#isSuperClean" ).is( ':checked' ) ) {
            // paramObject.push({noDeploy: "noDeploy"}) ;
            $.extend( paramObject, {
                clean: "super"
            } ) ;
        } else {


            if ( $( "#clean-docker-volumes" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    clean: "cleanVolumes"
                } ) ;
            } else if ( $( "#isClean" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    clean: "clean"
                } ) ;
            }
            if ( $( "#isSaveLogs" ).is( ':checked' ) ) {
                // paramObject.push({noDeploy: "noDeploy"}) ;
                $.extend( paramObject, {
                    keepLogs: "keepLogs"
                } ) ;
            }




        }


        let message = serviceName + " is configured with warnings to prevent data loss." ;
        message += "<br><br>Ensure procedures outlined by service owner have been followed to avoid data loss." ;
        message += "<br><br> Click OK to proceed anyway, or cancel to use the stop button." ;

        if ( latest_instanceReport.killWarnings ) {

            let $content = buildHtmlMessage( message, "warning" ) ;
            let applyDialog = alertify.confirm( $content ) ;
            applyDialog.setting( {
                title: "Caution Advised",
                resizable: true,
                autoReset: false,
                'labels': {
                    ok: 'Proceed with operation',
                    cancel: 'Cancel request'
                },
                'onok': function () {
                    executeOnSelectedHosts( "killServer", paramObject ) ;
                },
                'oncancel': function () {
                    alertify.warning( "Operation Cancelled" ) ;
                }

            } ) ;

            resizeDialog( applyDialog ) ;


        } else {
            executeOnSelectedHosts( "killServer", paramObject ) ;
        }


    }

    function buildHtmlMessage( message, theStyle ) {

        let $warning = jQuery( '<div/>', { } ) ;

        jQuery( '<div/>', { class: theStyle, html: message } )
                .css( "font-size", "12pt" )
                .css( "margin-top", "2em" )
                .css( "font-weight", "bold" )
                .appendTo( $warning ) ;

        return $warning.html() ;
    }

    function showDeployDialog() {

        // Lazy create
        if ( !alertify.deploy ) {

            createDeployDialog() ;
        }

        _deployDialog = alertify.deploy().show() ;
        console.log( `showDeployDialog() clusterType: ${primaryInstance.clusterType}` ) ;
        if ( latest_instanceReport.kubernetes ) {

            console.log( "Updated text", latest_instanceReport.dockerSettings ) ;
            maximizeDialog( _deployDialog ) ;

            let dockerServiceSetting = JSON.stringify( latest_instanceReport.dockerSettings, "\n", "\t" ) ;
            if ( _kubernetesEditor == null ) {
                $( "#kubernetes-definition-text" ).text( dockerServiceSetting ) ;
                //$( "#dockerImageVersion" ).parent().parent().append( $serviceParameters ) ;
                resize_alertify_element( $( "#kubernetes-definition-text" ), 100, 300 ) ;


                setTimeout( function () {
                    _kubernetesEditor = aceEditor.edit( "kubernetes-definition-text" ) ;
                    //editor.setTheme("ace/theme/twilight");
                    //editor.session.setMode("ace/mode/yaml");

                    _kubernetesEditor.setOptions( utils.getAceDefaults( "ace/mode/json" ) ) ;
                }, 200 ) ;
            } else {
                _kubernetesEditor.getSession().setValue( dockerServiceSetting ) ;
                resize_alertify_element( $( "#kubernetes-definition-text" ), 100, 300 ) ;
            }

        } else {
            resizeDialog( _deployDialog, $( "#deployDialog" ) ) ;
        }
        setAlertifyTitle( "Deploying", _deployDialog ) ;
        // $("#sourceOptions").fadeTo( "slow" , 0.5) ;

        if ( serviceName.indexOf( AGENT_NAME ) != -1 ) {
            let message = "CSAP Agent update: ensure latest csap linux package is installed." ;

            let agentConfirm = alertify.confirm(
                    buildHtmlMessage( message, "settings" ),
                    function () {
                        //alertify.notify( "csap agent will be updated" );
                    },
                    function () {
                        alertify.closeAll()
                    }
            ) ;
            agentConfirm.setting( {
                title: "CSAP Upgrade Confirmation",
                resizable: true,
                autoReset: false,
                'labels': {
                    ok: 'Proceed With Update',
                    cancel: 'Abort update'
                }
            } ) ;
        }
    }




    function createDeployDialog() {

        console.log( "createDeployDialog() " ) ;
        $( "#scmUserid", $osDeploy ).val( utils.getScmUser() ) ;

        let deployTitle = 'Service Deploy: <span title="After build/maven deploy on build host, artifact is deployed to other selected instances">'
                + primaryHost + "</span>"

        let okFunction = function () {
            let deployChoice = $( 'input[name=deployRadio]:checked' ).val() ;
            // alertify.success("Deployment using: "
            // +
            // deployChoice);
            alertify.closeAll() ;



            switch ( deployChoice ) {

                case "maven":
                    if ( $( "#deployServerServices input:checked" ).length == 0 ) {
                        deployService( true, serviceName ) ;
                    } else {
                        $( "#deployServerServices input:checked" ).each( function () {
                            let curName = $( this ).attr( "name" ) ;
                            deployService( true, curName ) ;
                        } ) ;
                    }
                    break ;

                case "source":
                    deployService( false, serviceName ) ;
                    break ;

                case "upload":
                    uploadArtifact(  ) ;
                    break ;

            }

        }

        let deployDialogFactory = function factory() {
            return{
                build: function () {
                    // Move content from template
                    this.setContent( $( "#deployDialog" ).show()[0] ) ;
                    this.setting( {
                        'onok': okFunction,
                        'oncancel': function () {
                            alertify.warning( "Cancelled Request" ) ;
                        }
                    } ) ;
                },
                setup: function () {
                    return {
                        buttons: [ { text: "Deploy Service", className: alertify.defaults.theme.ok, key: 0 },
                            { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                        ],
                        options: buildAlertifyOptions( deployTitle )
                    } ;
                }

            } ;
        } ;

        alertify.dialog( 'deploy', deployDialogFactory, false, 'confirm' ) ;



        $( 'input[name=deployRadio]' ).change( function () {

            $( "#osDeployOptions >div" ).hide() ;

            let $selectedDiv = $( "#" + $( this ).val() + "Options" ) ;
            $selectedDiv.show() ;

            resizeDialog( _deployDialog, $( "#deployDialog" ) ) ;

        } ) ;

        $( 'input[name=deployRadio]:checked' ).trigger( "change" ) ;



        $( '#scmPass' ).keypress( function ( e ) {
            if ( e.which == 13 ) {
                $( '.ajs-buttons button:first',
                        $( "#deployDialog" ).parent().parent().parent() )
                        .trigger( "click" ) ;
            }
        } ) ;





        $( '#cleanServiceBuild' ).click( function () {
            cleanServiceBuild( false ) ;
            return false ; // prevents link
        } ) ;

        $( '#cleanGlobalBuild' ).click( function () {
            cleanServiceBuild( true ) ;
            return false ; // prevents link
        } ) ;


    }

    function uploadArtifact() {

        let uploadMessage = `Uploading artifact for ${ serviceShortName }: ${ $( "#uploadOptions :file" ).val()  }`
                + `<div class="csap-red percent-upload"> </div>` ;
        utils.loading( uploadMessage ) ;
//        showResultsDialog( "Uploading artifact: " +  ;
//
//
//        displayResults( "" ) ;
        $resultsPre.append( '<div class="progress"><div class="bar"></div ><div class="percent">0%</div ></div>' ) ;

        $( "#upService" ).val( serviceName ) ;
        // <input type="hidden " name="hostName" value="" />
        $( "#upHosts" ).empty() ;
        for ( let host of selectedHosts ) {
            jQuery( '<input/>', {
                type: "hidden",
                value: host,
                name: "hostName"
            } ).appendTo( $( "#upHosts" ) ) ;
        }
//        $( "#instanceTable *.selected" ).each( function () {
//
//            let reqHost = $( this ).data( "host" ) ; // (this).data("host")
//            $( "#upHosts" ).append( '<input type="hidden" name="hostName" value="' + reqHost + '" />' ) ;
//
//        } ) ;

        let $percent = $( '.percent-upload' ).css( "width", "4em" ) ;
        let status = $( '#status' ) ;

        let formOptions = {
            beforeSend: function () {
                $( 'body' ).css( 'cursor', 'wait' ) ;

                status.empty() ;
                let percentVal = '0%' ;
                $percent.html( percentVal ) ;

            },
            uploadProgress: function ( event, position, total, percentComplete ) {
                let percentVal = percentComplete + '%' ;
                $percent.html( percentVal ) ;
            },
            success: function () {
                let percentVal = '100%' ;
                $percent.html( percentVal ) ;

            },
            complete: function ( xhr ) {
                //console.log( `xhr response: `, xhr )
                utils.loadingComplete() ;
                $( 'body' ).css( 'cursor', 'default' ) ;
                let percentVal = '100%' ;
                $percent.html( percentVal ) ;
                // status.html(xhr.responseText);
                // $("#resultPre").html( xhr.responseText ) ;
                displayResults( xhr.responseJSON ) ;
            }
        } ;

        $( '#uploadOptions form' ).ajaxSubmit( formOptions ) ;



    }



    function deployService( isMavenDeploy, deployServiceName ) {

        let copyToHosts = Array.from( selectedHosts ) ;
        copyToHosts.splice( 0, 1 ) ;

        console.log( `deployService: selectedHosts ${selectedHosts} ,  copyToHosts ${ copyToHosts } ` )

        let deployHost = primaryHost ;
        if ( latest_instanceReport.kubernetes ) {
            console.log( `Cluster type is kubernetes, deployment is only on ${ $kubernetesMastersSelect.val() }` ) ;
            deployHost = $kubernetesMastersSelect.val() ;
            copyToHosts = new Array() ;
        }

        let autoStart = $( "#deployStart" ).is( ":checked" ) ;

        let paramObject = {
            scmUserid: $( "#scmUserid" ).val(),
            scmPass: $( "#scmPass" ).val(),
            scmBranch: $( "#scmBranch" ).val(),
            commandArguments: $serviceParameters.val(),
            targetScpHosts: copyToHosts,
            serviceName: deployServiceName,
            hostName: deployHost
        } ;

        if ( serverType == "docker" ) {
            if ( latest_instanceReport.kubernetes ) {
                $.extend( paramObject, {
                    //dockerImage: $( "#dockerImageVersion" ).val(),
                    // mavenDeployArtifact: $( "#kubernetes-definition-text" ).val()
                    mavenDeployArtifact: _kubernetesEditor.getValue()
                } ) ;

            } else {
                $.extend( paramObject, {
                    //dockerImage: $( "#dockerImageVersion" ).val(),
                    mavenDeployArtifact: $( "#dockerImageVersion" ).val()
                } ) ;
            }
        } else if ( isMavenDeploy ) {
            // paramObject.push({noDeploy: "noDeploy"}) ;
            let artifact = $( "#mavenArtifact" ).val() ;
            $.extend( paramObject, {
                mavenDeployArtifact: artifact
            } ) ;
            console.log( "Number of ':' in artifact", artifact.split( ":" ).length ) ;
            if ( artifact.split( ":" ).length != 4 ) {
                $( "#mavenArtifact" ).css( "background-color", "#f5bfbf" ) ;
                alertify.csapWarning( "Unexpected format of artifact. Typical is a:b:c:d  eg. org.csap:BootEnterprise:1.0.27:jar" ) ;
                //return ;
            } else {
                $( "#mavenArtifact" ).css( "background-color", "#CCFFE0" ) ;
            }
        } else {

            let scmCommand = $( "#scmCommand" ).val() ;

            if ( scmCommand.indexOf( "deploy" ) == -1
                    && $( "#isScmUpload" ).is( ':checked' ) ) {
                scmCommand += " deploy" ;
            }

            $.extend( paramObject, {
                scmCommand: scmCommand
            } ) ;
        }


        if ( $( "#deployServerServices input:checked" ).length > 0 ) {
            $( "#deployStart" ).prop( 'checked', false ) ;
            // default params are used when multistarts

            delete paramObject.commandArguments ;
            delete paramObject.runtime ;
            delete paramObject.scmCommand ;
            delete paramObject.mavenDeployArtifact ;
            $.extend( paramObject, {
                mavenDeployArtifact: "default"
            } ) ;

        }


        if ( $( "#isHotDeploy" ).is( ':checked' ) ) {
            $.extend( paramObject, {
                hotDeploy: "hotDeploy"
            } ) ;
        }

        let buildUrl = SERVICE_URL + "/rebuildServer" ;
        // buildUrl = "http://yourlb.yourcompany.com/admin/services" +
        // "/rebuildServer" ;

        utils.loading( `Adding ${ deployServiceName } to the deployment queue ` ) ;
        $.post( buildUrl, paramObject )
                .done( function ( results ) {

                    utils.loadingComplete() ;
                    displayHostResults( deployHost, deployServiceName, "Build Started", results, 0, 0 ) ;

                    // $("#resultPre div").first().show() ;
                    $( "#resultPre div" ).first().css( "display", "block" ) ;

                    fileOffset = "-1" ;

                    _deploySuccess = false ;


                    // show logs
                    utils.launchServiceLogs( "deploy" ) ;

                    getDeployLogs( deployServiceName ) ;

                } )
                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    if ( deployServiceName.indexOf( AGENT_NAME ) != -1 ) {
                        alert( "csap agent can get into race conditions...." ) ;
                        let numHosts = selectedHosts.length ;

                        if ( numHosts > 1 && results.indexOf( "BUILD__SUCCESS" ) != -1 ) {
                            isBuild = true ; // rebuild autostarts
                            startService() ;
                        } else {
                            $( 'body' ).css( 'cursor', 'default' ) ;
                        }
                    } else {
                        handleConnectionError( hostName + ":" + rebuild, errorThrown ) ;
                    }
                } ) ;

    }

    function getDeployLogs( nameOfService ) {

        // $('#serviceOps').css("display", "inline-block") ;

        let serviceHost = primaryHost ;
        if ( latest_instanceReport.kubernetes ) {
            serviceHost = $kubernetesMastersSelect.val() ;
        }

        // console.log("Hitting Offset: " + fileOffset) ;
        let requestParms = {
            serviceName: nameOfService,
            hostName: serviceHost,
            logFileOffset: fileOffset
        } ;

        $.getJSON(
                SERVICE_URL + "/query/deployProgress",
                requestParms )

                .done( function ( hostJson ) {
                    wait_for_build_complete( hostJson, nameOfService ) ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown ) ;
                } ) ;
    }


    function  wait_for_build_complete( changesJson, nameOfService ) {

        if ( changesJson.error || changesJson.contents == undefined ) {
            console.log( "No results found, rescheduling" ) ;
            setTimeout( function () {
                getDeployLogs( nameOfService ) ;
            }, LOG_CHECK_INTERVAL ) ;
            return ;
        }
        // $("#"+ hostName + "Result").append("<br>peter") ;
        // console.log( JSON.stringify( changesJson ) ) ;
        // console.log("Number of changes :" + changesJson.contents.length);

        let previousBlock = "" ;
        for ( let currentChangeBlock of changesJson.contents ) {

            console.debug( `logs: ${ currentChangeBlock }` ) ;
            // TOKEN may get split in lines, so check text results for success and complete tokens
            let check_for_tokens_block = previousBlock + currentChangeBlock ;
            previousBlock = currentChangeBlock ;


            if ( check_for_tokens_block.contains( "BUILD__SUCCESS" ) ) {
                _deploySuccess = true ;
            }

            if ( check_for_tokens_block.contains( "__COMPLETED__" ) ) {

                if ( _deploySuccess ) {
                    isBuild = true ;

                    if ( $( "#deployStart" ).is( ':checked' ) ) {
                        // delay to allow deploy queues to clear
                        setTimeout( () => startService(), 1000 ) ;
                    }

                } else {

                    alertify.csapWarning( "BUILD__SUCCESS not found in output - review logs" ) ;
                }

                return ;
            }
        }



        fileOffset = changesJson.newOffset ;


        setTimeout( function () {
            getDeployLogs( nameOfService ) ;
        }, LOG_CHECK_INTERVAL ) ;


    }


    function cleanServiceBuild( isGlobal ) {

        let paramObject = {
            serviceName: serviceName,
            hostName: primaryHost
        } ;

        if ( isGlobal ) {
            $.extend( paramObject, {
                global: "GLOBAL"
            } ) ;
        }

        $.post( SERVICE_URL + "/purgeDeployCache", paramObject,
                function ( results ) {

                    displayResults( results, false ) ;

                } ) ;

    }

    function displayResults( resultReport, append ) {

        console.log( `displayResults() : `, resultReport ) ;
        if ( resultReport.plainText ) {

            alertify.csapInfo( resultReport.plainText ) ;

        } else {
            let results = JSON.stringify( resultReport, null, "  " ) ;

            if ( results.includes( utils.getErrorIndicator() )
                    || results.includes( utils.getWarningIndicator() ) ) {

                alertify.csapWarning( results ) ;
            } else {
                alertify.csapInfo( results ) ;
            }
        }



    }



} ) ;