// define( [ "browser/utils" ], function ( utils ) {

//     console.log( "Module loaded" ) ;

import _dom from "../../utils/dom-utils.js";



import utils from "../utils.js"


const batchView = service_batch();

export default batchView


function service_batch() {

    _dom.logHead( "Module loaded" );
    const $batchMain = $( "#services-tab-batch" );
    let $batchButtons = $( ".options button", $batchMain );
    let $showBatchButton = $( ".options button.show-batch", $batchMain );

    const $batchSelect = $( ".batch-select", "#services-tab-batch" );
    const $batchResults = $( ".batch-results", "#services-tab-batch" );


    let batchFullTemplate = null;
    let batchResultTemplate = null;

    let _resultRefreshTimer = null;
    let RESULT_REFRESH_INTERVAL = 5000;
    let batchCompeted = true;


    let init = false;

    return {

        initialize: function () {
            initialize();
        },

        show: function ( $displayContainer, forceHostRefresh ) {

            if ( !init || forceHostRefresh ) {
                getTemplate();
            }
            return;
        }

    };


    function initialize() {
        $batchButtons.show();
        $showBatchButton.hide();



        $batchButtons.off().click( function () {

            let command = $( this ).text();
            if ( command == "Show Batch Operations" ) {
                $batchButtons.show();
                $showBatchButton.hide();
                $batchSelect.show();
                $batchResults.empty();
                return;
            }

            if ( !isValidBatchParams() ) {
                return;
            }
            $batchButtons.hide();
            $showBatchButton.show();
            utils.disableButtons( $showBatchButton );
            batchCompeted = false;

            $batchSelect.hide();

            switch ( command ) {
                case "Deploy":
                    issueBatchDeployRequest();
                    break;

                case "Remove":
                    issueBatchKillRequest();
                    break;

                case "Start":
                    issueBatchStartRequest();
                    break;

                default:
                    alertify.alert( `${ command } not implemented yet` );
                    break;
            }

        } );

    }

    function getTemplate() {

        $.get( `batchDialog?project=${ utils.getActiveProject() }&`,
            function ( batchHtml ) {
                show( batchHtml );
            },
            'html' );

    }


    function show( batchHtml ) {

        init = true;

        let $batchTemplateFromServer = $( "<html/>" ).html( batchHtml ).find( '#batchSelect' );
        batchResultTemplate = $( "<html/>" ).html( batchHtml ).find( '#batchResultTemplate' );
        // console.log( "cluster: " + $cluster.html()) ;


        console.log( `rendering in frame` );
        if ( !batchCompeted ) {
            console.log( `job in progress` );
            return;
        }


        $batchSelect.empty();
        $batchSelect.html( $batchTemplateFromServer.html() );
        batchEventRegistration();

    }

    // Need to register events after dialog is loaded
    function batchEventRegistration() {

        let $batchFilter = $( ".batchFilter", $batchMain );
        $batchFilter.hide();

        $( '.showFiltersButton' ).click( function () {
            console.log( "showing" );
            $( '.batchFilter' ).show( 500 );
            $( '.showFiltersButton' ).hide();
        } );



        $( '.cluster-selection .all-kubernetes-button', $batchMain ).click(
            function () {
                $( 'input.kubernetes', $( ".cluster-selection", $batchMain ) )
                    .prop( "checked", true ).trigger( "change" );

                return false; // prevents link
            } );


        $( '.cluster-selection .uncheckAll', $batchMain ).click(
            function () {

                $( 'input', $( ".cluster-checkboxes", $batchMain ) )
                    .prop( "checked", false ).trigger( "change" );
                return false; // prevents link
            } );

        $( '.cluster-selection .checkAll', $batchMain ).click(
            function () {

                $( 'input', $( ".cluster-checkboxes" ) )
                    .prop( "checked", true ).trigger( "change" );
                return false; // prevents link
            } );

        $( '.uncheckAll', $batchFilter ).click(
            function () {

                $( 'input', $( this ).parent().parent() )
                    .prop( "checked", false ).trigger( "change" );

                return false; // prevents link
            } );

        $( '.checkAll', $batchFilter ).click(
            function () {

                console.log( "checking all" );

                $( 'input', $( this ).parent().parent() ).prop(
                    "checked", true ).trigger( "change" );


                return false; // prevents link
            } );

        $( ".batchDialog input:checked" ).parent().animate( {
            "background-color": "yellow"
        }, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );

        $( ".batchDialog input" ).change( function () {
            let highlightColor = "white";

            if ( $( this ).is( ":checked" ) )
                highlightColor = "yellow";

            // $(".hostLabel",
            // $("input.instanceCheck").parent()).css("background-color",
            // $(".ajs-dialog").css("background-color") ) ;
            $( $( this ).parent() ).css( "background-color", highlightColor );
        } );

        // $("#batchResult table tbody").resizable();
        $( ".batchClusterSelect" ).sortSelect();
        $( ".batchClusterSelect" ).change( function () {

            $( '.showFiltersButton' ).hide();
            $batchFilter.show( 500 );

            let clusterSelected = $( this ).data( "name" );

            let isClusterChecked = $( this ).prop( "checked" );

            console.log( `clusterSelected: ${ clusterSelected }, isClusterChecked: ${ isClusterChecked } ` )
            console.log( "rows", $( ".batchClusterSelect" ).css( "grid-template-rows" ) )

            let clusterHostJson = jQuery.parseJSON( $( "#clusterHostJson" ).text() );

            let hosts = clusterHostJson[ clusterSelected ];
            // console.log("Selecting: " + clusterSelected + " vals: " +
            // hosts) ;
            for ( let i = 0; i < hosts.length; i++ ) {
                console.log( "Selecting: " + hosts[ i ] );
                $( ".batchDialog input[value='" + hosts[ i ] + "']" ).prop(
                    'checked', isClusterChecked ).trigger( "change" );
            }

            let clusterServiceJson = jQuery.parseJSON( $(
                "#clusterServiceJson" ).text() );
            let serviceNames = clusterServiceJson[ clusterSelected ];
            // console.log("Selecting: " + clusterSelected + " vals: " +
            // hosts) ;
            for ( let serviceName of serviceNames ) {
                console.log( `Selecting: ${ serviceName }` );
                $( `.batchDialog input[value=${ serviceName }]` )
                    .prop( 'checked', isClusterChecked ).trigger( "change" );
            }

            $( this ).val( "none" );
            return;
        } );

    }


    function isValidBatchParams() {

        if ( $( 'input.hostCheckbox:checked' ).map( function () {
            return this.value;
        } ).get().length == 0 ) {
            alertify.csapWarning( "No hosts Selected" );
            return false;
        }

        if ( $( 'input.serviceCheckbox:checked' ).map( function () {
            return this.value;
        } ).get().length == 0 ) {
            alertify.csapWarning( "No services Selected" );
            return false;
        }

        return true;
    }

    function issueBatchKillRequest( $batchContent ) {
        if ( !isValidBatchParams() ) {
            return false;
        }
        let hostParamObject = {
            'project': utils.getActiveProject(),
            'hostName': $( 'input.hostCheckbox:checked', $batchMain ).map( function () {
                return this.value;
            } ).get(),
            'serviceName': $( 'input.serviceCheckbox:checked', $batchMain ).map( function () {
                return this.value;
            } ).get()
        };

        if ( $( "#batchCleanCheckbox" ).is( ':checked' ) ) {
            $.extend( hostParamObject, {
                clean: "clean"
            } );
        }

        $.post( SERVICE_URL + "/batchKill", hostParamObject )

            .done( function ( batchResults ) {
                showBatchResults( batchResults, $batchContent )
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "batchKill", errorThrown );
            } );

        return true;

    }

    function issueBatchStartRequest() {

        if ( !isValidBatchParams() ) {
            return false;
        }
        let hostParamObject = {
            'project': utils.getActiveProject(),
            'hostName': $( 'input.hostCheckbox:checked', $batchMain ).map( function () {
                return this.value;
            } ).get(),
            'serviceName': $( 'input.serviceCheckbox:checked', $batchMain ).map( function () {
                return this.value;
            } ).get()
        };

        console.log( "posting to:", SERVICE_URL + "/batchStart", " parameters: ", hostParamObject )

        $.post( SERVICE_URL + "/batchStart", hostParamObject )

            .done( function ( batchResults ) {
                showBatchResults( batchResults )
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "batchStart", errorThrown );
            } );

        return true;

    }

    function issueBatchDeployRequest() {

        if ( !isValidBatchParams() ) {
            return false;
        }

        let hostParamObject = {
            'project': utils.getActiveProject(),
            'hostName': $( 'input.hostCheckbox:checked', $batchMain ).map( function () {
                return this.value;
            } ).get(),
            'serviceName': $( 'input.serviceCheckbox:checked', $batchMain ).map( function () {
                return this.value;
            } ).get()
        };

        $.post( SERVICE_URL + "/batchDeploy", hostParamObject )


            .done( function ( batchResults ) {
                showBatchResults( batchResults, )
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "batchStart", errorThrown );
            } );

        return true;
    }


    function showBatchTempResults( results ) {

        console.log( "Refreshing and launching deploy dialog" )

        //    $( '#refreshButton' ).trigger( "click" ) ;
        setTimeout( function () {
            $( '#deployment-backlog a' ).trigger( "click" );
        }, 2000 );

    }

    function showBatchResults( batchRequestResults ) {

        // console.log( "cluster: " + $cluster.html()) ;



        $batchResults.html( batchResultTemplate.html() );
        $( "#open-admin-logs", $batchResults ).off().click( function () {
            let parameterMap = {
                defaultService: "csap-admin"
            };
            utils.openAgentWindow( utils.getHostName(), "/app-browser#agent-tab,logs", parameterMap );

        } )

        let $resultContainer = $( ".batchResult" ); // inside of dialog

        $( "#batchMessage", $resultContainer ).text( batchRequestResults.result );
        $( "#jobsOperations", $resultContainer ).text( batchRequestResults.jobsOperations );
        $( "#jobsCount", $resultContainer ).text( batchRequestResults.jobsCount );
        $( "#jobsRemaining", $resultContainer ).text( batchRequestResults.jobsRemaining );
        $( "#batchParallel", $resultContainer ).text( batchRequestResults.parallelRequests );

        setTimeout( function () {
            addHostBatchTable( batchRequestResults, $batchResults );
        }, 500 )

        $( "#batchProgressBar", $resultContainer ).progressbar( {
            value: 0,
            max: batchRequestResults.jobsRemaining
        } );
        $( "#batchProgressLabel", $resultContainer ).text( "Jobs Remaining: " + batchRequestResults.jobsRemaining )

        $( '#refreshButton' ).trigger( "click" );
        setTimeout( function () {
            upateJobAndTaskCountsUntilDone( $resultContainer );
        }, RESULT_REFRESH_INTERVAL );

    }


    function addHostBatchTable( batchRequestResults, $batchContent ) {
        let $tbody = $( "#hostJobsTable tbody" );
        $tbody.empty();
        for ( let hostName in batchRequestResults.hostInfo ) {
            let $hostRow = jQuery( '<tr/>', {} );


            //            let urlAction = AGENT_URL_PATTERN.replace( /CSAP_HOST/g, hostName ) ;
            //            urlAction += `/file/FileMonitor?u=1&isLogFile=true&serviceName=${AGENT_ID}&hostName=${ hostName }` ;


            let $hostLogs = jQuery( '<a/>', {
                class: "csap-link",
                title: "View host logs",
                target: "_blank",
                href: "#open-agent-logs",
                text: hostName
            } ).click( function () {
                let parameterMap = {
                    defaultService: `${ AGENT_NAME }`
                };
                utils.openAgentWindow( hostName, "/app-browser#agent-tab,logs", parameterMap );
                return false;
            } );

            $hostRow.append( jQuery( '<td/>', {
                class: "col1"
            } ).append( $hostLogs ) );


            let $serviceLogContainer = jQuery( '<div/>', {
                class: "serviceLogs"
            } );

            $hostRow.append( jQuery( '<td/>', {
                class: "col2"
                //            text: results.hostInfo[hostName].info
            } ).append( $serviceLogContainer ) );

            $tbody.append( $hostRow );

            let serviceNames = batchRequestResults.hostInfo[ hostName ].services;
            if ( serviceNames == undefined ) {
                serviceNames = new Array();
            }

            let maxTesting = 1; // 1 is the normal
            for ( let serviceName of serviceNames ) {

                for ( let i = 0; i < maxTesting; i++ ) {

                    if ( serviceName.includes( "_" ) ) {
                        serviceName = serviceName.split( "_" )[ 0 ];
                    }
                    let $serviceLogs = jQuery( '<div/>', {} );
                    $serviceLogContainer.append( $serviceLogs );


                    $serviceLogs.append( buildServiceLogButton( hostName, serviceName, null, serviceName ) );

                    //$serviceLogs.append( jQuery( '<br/>', { } ) ) ;
                    let opLogType = "-deploy";
                    if ( batchRequestResults.result.contains( "kill" ) ) {
                        opLogType = "-kill";
                    } else if ( batchRequestResults.result.contains( "start" ) ) {
                        opLogType = "-start";
                    }

                    $serviceLogs.append( buildServiceLogButton( hostName, serviceName,
                        `${ serviceName + opLogType }.log`, `(${ opLogType }.log)` ) );

                }


            }

        }
    }

    function buildServiceLogButton( host, service, file, description ) {

        let $logButton = jQuery( '<a/>', {
            class: "simple",
            title: "View service logs",
            target: "_blank",
            href: "#open-logs",
            text: description
        } ).click( function () {
            let parameterMap = {
                defaultService: service
            };
            if ( file ) {
                parameterMap.fileName = file;
            }
            utils.openAgentWindow( host, "/app-browser#agent-tab,logs", parameterMap );
            //openWindowSafely( $( this ).attr( "href" ), "_blank" ) ;
            return false;
        } );

        return $logButton;
    }

    function upateJobAndTaskCountsUntilDone( $resultContainer ) {

        console.log( "updateJobsRemaining..." );

        utils.refreshStatus( true );

        $.getJSON( "service/batchJobs" ).done( function ( responseJson ) {
            // console.log("jobsRemaining: " + responseJson.jobsRemaining )
            $( "#loadingMessage" ).hide();
            $( "#jobsRemaining", $resultContainer ).text( responseJson.jobsRemaining ).animate( {
                "background-color": "yellow"
            }, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );

            if ( responseJson.jobsRemaining > 0
                || responseJson.tasksRemaining == 0 ) {

                $( "#batchProgressLabel", $resultContainer ).text(
                    "Jobs Remaining: " + responseJson.jobsRemaining );

                let progressMax = $( "#batchProgressBar" ).progressbar( "option", "max" );
                console.log( "progressMax: ", progressMax, "jobsRemaining: ", responseJson.jobsRemaining )

                $( "#batchProgressBar", $resultContainer ).progressbar(
                    "value",
                    progressMax - responseJson.jobsRemaining );
            } else {
                let tasksRemaining = responseJson.tasksRemaining;
                $( "#batchProgressLabel", $resultContainer ).text(
                    "Tasks Remaining: " + tasksRemaining );

                let jobsCount = parseInt( $( "#jobsOperations" ).text() );
                $( "#batchProgressBar", $resultContainer ).progressbar( "option", "max", jobsCount );

                console.log( "jobsCount: ", jobsCount, "tasksRemaining: ", tasksRemaining )

                $( "#batchProgressBar", $resultContainer ).progressbar(
                    "value",
                    jobsCount - tasksRemaining );
            }

            if ( responseJson.jobsRemaining > 0
                || responseJson.tasksRemaining > 0 ) {

                _resultRefreshTimer = setTimeout( function () {
                    console.log( `Scheduling refresh: ${ RESULT_REFRESH_INTERVAL } ` );
                    upateJobAndTaskCountsUntilDone( $resultContainer );
                }, RESULT_REFRESH_INTERVAL );

            } else {
                batchCompeted = true;
                $( "#batchProgressLabel", $resultContainer ).text(
                    "Batch tasks completed: verify service logs and performance" ).css(
                        "left", "1em" );

                utils.enableButtons( $showBatchButton );
            }

        } )
    }


}
