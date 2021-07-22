define( [ "browser/utils", "ace/ace" ], function ( utils, aceEditor ) {

    console.log( "Module loaded" ) ;


    let _progressViewer ;


    let $progressText, $progressDialog ;

    // resources
    let _dockerRefreshFunction = null, _kubernetesRefreshFunction = null ;
    let _dockerCommandUrl = explorerUrl + "/docker"
    let _dockerContainerPath = "docker/container/" ;
    let _containerCommandUrl = explorerUrl + "/" + _dockerContainerPath ;

    let _containerName, _hostName, _podLogUrl ;

    // tail and logs
    let _logSince = -1 ; // triggers content to be replaced
    let _tailParameters ;
    let _firstTail = false ;

    let _progressTimer, _results_resize_timer ;



    return {

        initialize: function () {
            initialize() ;
        },

        show: function ( operation, commandResults, commandUrl ) {
            show_progress_dialog( operation, commandResults ) ;
        },

        docker: function ( containerName, delay, id ) {
            _containerName = containerName ;
            _hostName = null ;
            _podLogUrl = null ;
            buildDockerRefreshFunction( containerName, id ) ;
            show_progress_dialog( "Docker Logs: " + containerName ) ;
            docker_progress( delay ) ;
        },

        kubernetes: function ( delay, podLogUrl, containerName, host ) {

            _containerName = "/" + containerName ;
            _hostName = host ;
            _podLogUrl = podLogUrl ;

            buildKuberntesRefreshFunction( podLogUrl, containerName ) ;
            show_progress_dialog( "Kubernetes Logs:" + containerName ) ;
            kubernetes_progress( delay ) ;
        },

        image: function ( progressUrl, offset, onCompleteFunction ) {
            show_progress_dialog( "Image Pull:" ) ;
            image_progress( progressUrl, offset, onCompleteFunction ) ;
        }

    }


    function initialize() {


        $progressDialog = $( "#progress-results-dialog" ) ;
        $progressText = $( "#progress-results-text", $progressDialog ) ;

        $( "#progress-clear-button" ).click( function () {
            console.log( "clearing" ) ;
            //$progressText.text( "" ) ;
            _progressViewer.setValue( "", -1 ) ;
            return false ;
        } ) ;


        $( "#previous-terminated" ).change( function () {
            $( "#progress-clear-button" ).trigger( "click" ) ;
            $( "#progress-line-select" ).trigger( "change" ) ;
        } ) ;

        $( "#progress-line-select" ).change( function () {

            if ( _dockerRefreshFunction || _kubernetesRefreshFunction ) {
                _progressViewer.setValue( "" ) ;
                _logSince = 0 ;
                _firstTail = true ;
                if ( _dockerRefreshFunction )
                    docker_progress( 100 ) ;
                if ( _kubernetesRefreshFunction )
                    kubernetes_progress( 100 ) ;

            } else {
                console.log( "ignored" ) ;
            }
        } ) ;

        $( "#wrapOutput" ).change( function () {

            if ( $( this ).is( ":checked" ) ) {
                _progressViewer.session.setUseWrapMode( true ) ;
            } else {
                _progressViewer.session.setUseWrapMode( false ) ;
            }
        } ) ;



        $( "#progress-new-window" ).click( openLogsInNewWindow ) ;

        _progressViewer = aceEditor.edit( "progress-results-text" ) ;
        //editor.setTheme("ace/theme/twilight");
        //editor.session.setMode("ace/mode/yaml");

        _progressViewer.setOptions( utils.getAceDefaults("ace/mode/sh", true) ) ;

    }

    function openLogsInNewWindow() {

        let theLogHost = utils.getHostName() ;
//        let launchUrl = uiSettings.baseUrl + "file/FileMonitor" ;

        if ( _hostName != null ) {
            theLogHost = _hostName ;
//            let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, _hostName ) ;
//            launchUrl = baseUrl + "/file/FileMonitor" ;
//
//            if ( isIpAddress( _hostName ) ) {
//                launchUrl = `http://${ _hostName }${ AGENT_ENDPOINT }/file/FileMonitor` ;
//                console.log( "Warning: stripping domain name from IP:", launchUrl ) ;
//            }
        }

//        let parameters = `fileName=${ "__docker__" + _containerName}&hostName=${theLogHost}&podName=${_podLogUrl}` ;
//        let encodedUrl = launchUrl + "?" + encodeURI( parameters ) ;
            let paramaters = {
                fileName: `__docker__${ _containerName }`,
                podName: _podLogUrl ,
                hostName: theLogHost
            } ;
            
            
        console.log( `openLogsInNewWindow:`, paramaters ) ;

            utils.openAgentWindow(
                    theLogHost,
                    `${ utils.getFileUrl() }/FileMonitor`,
                    paramaters ) ;

//        window.open( encodedUrl, '_blank' );

//        let parameters = {
//            fileName: "__docker__" + _containerName,
//            serviceName: null,
//            hostName: theLogHost,
//            podName: _podLogUrl
//        } ;
//
//
//        console.log( "tailing container in new window: ",
//                parameters ) ;
//
//        postAndRemove( "_blank",
//                launchUrl,
//                parameters ) ;

        return false ;
    }


    function progress_resize() {

        clearTimeout( _results_resize_timer ) ;

        _results_resize_timer = setTimeout( function () {

            let logControlsHeight = Math.round( $( "#logControls" ).outerHeight( true ) ) ;
            let maxHeight = Math.round( $progressText.parent().parent().outerHeight( true ) )
                    - 45 ;

            let maxWidth = Math.round( $progressText.parent().parent().outerWidth( true ) ) - 10 ;
            console.log( "maxHeight:", maxHeight, " logControlsHeight:", logControlsHeight ) ;
            $progressText.css( "height", maxHeight ) ;
            $progressText.css( "width", maxWidth ) ;
            _progressViewer.resize() ;
            //$resultsText.css( "width", Math.round($resultsText.parent().parent().outerWidth( true ) - 200) );
        }, 500 ) ;
    }


    function show_progress_dialog( operation ) {

        _progressViewer.setValue( "" ) ;
        _logSince = 0 ;
        _firstTail = true ;

        let $tailButton = $( "#tailLogs" ) ;
        $tailButton.hide() ;
        $( "#logControls" ).hide() ;

        let progress_alertify = progress_build() ;

        progress_alertify.setting( {
            title: "Operation: " + operation
        } ) ;

//        progress_resize() ;

    }

    function progress_scroll() {
        if ( $( "#autoScrollResults" ).is( ":checked" ) ) {
            // $progressText.scrollTop( $progressText[0].scrollHeight ) ;
            //_progressViewer.gotoLine( _progressViewer.session.getLength() ) ;
            _progressViewer.scrollToLine( _progressViewer.session.getLength() ) ;
        }
    }

    function progress_close() {
        console.log( "Clearing progress timer", _progressTimer ) ;
        clearTimeout( _progressTimer ) ;
        _dockerRefreshFunction = null ;
        _kubernetesRefreshFunction = null ;
        _progressTimer = null ;
        //restoreTemplate( $resultsDialog );
    }
    function progress_build(  ) {

        // Lazy create
        if ( !alertify.progressResults ) {

            let csapDialogFactory = CsapCommon.dialog_factory_builder( {
                content: $progressDialog.show()[0],
                onresize: progressViewerResize,
                onclose: progress_close
            } ) ;

            alertify.dialog( 'progressResults', csapDialogFactory, false, 'alert' ) ;
        }
        let instance = alertify.progressResults().show() ;
        return instance ;

    }

    function progressViewerResize( dialogWidth, dialogHeight ) {

        setTimeout( function () {

            let $header = $( ":first-child", $progressText.parent() ) ;
            let maxWidth = dialogWidth - 10 ;
            $progressText.css( "width", maxWidth ) ;

            let maxHeight = dialogHeight
                    - Math.round( $header.outerHeight( true ) )
                    - 10 ;

            $progressText.css( "height", maxHeight ) ;

            console.log( `kubernetes_yaml_dialog() dialogWidth: ${dialogWidth}, dialogHeight: ${dialogHeight}` ) ;
            _progressViewer.resize() ;

        }, 500 ) ;

    }

    function kubernetes_progress( delay ) {
        clearTimeout( _progressTimer ) ;
        _progressTimer = setTimeout( _kubernetesRefreshFunction, delay ) ;
    }

    function buildKuberntesRefreshFunction( podLogUrl, containerName ) {
        _kubernetesRefreshFunction = function () {


            _tailParameters = {
                "since": _logSince,
                "containerName": containerName,
                "previousTerminated": $( "#previous-terminated" ).is( ":checked" ),
                numberOfLines: $( "#progress-line-select" ).val()
            }


            console.log( "performContainerTail: ", podLogUrl )

            $.get( podLogUrl, _tailParameters )
                    .done( function ( commandResults ) {
                        // showContainerTail( commandUrl, commandResults );
                        if ( _progressTimer == null ) {
                            console.log( "window closed - skipping update" ) ;
                            return ;
                        }
                        progress_append( commandResults ) ;
                        _logSince = commandResults.since ;
                        kubernetes_progress( 2000, podLogUrl, containerName ) ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;

        }
    }



    function docker_progress( delay ) {

        clearTimeout( _progressTimer ) ;
        _progressTimer = setTimeout( _dockerRefreshFunction, delay ) ;

    }

    function buildDockerRefreshFunction( containerName, id ) {
        _dockerRefreshFunction = function () {

            if ( id ) {
                _tailParameters = {
                    "id": id,
                    "since": _logSince,
                    numberOfLines: $( "#progress-line-select" ).val()
                } ;
            } else {
                _tailParameters = {
                    "name": containerName,
                    "since": _logSince,
                    numberOfLines: $( "#progress-line-select" ).val()
                } ;
            }

            console.debug( "tail_using_docker: ", containerName )

            $.get( _containerCommandUrl + "tail", _tailParameters )
                    .done( function ( commandResults ) {
                        // showContainerTail( commandUrl, commandResults );
                        if ( _progressTimer == null ) {
                            console.log( "window closed - skipping update" ) ;
                            return ;
                        }
                        progress_append( commandResults ) ;
                        _logSince = commandResults.since ;
                        docker_progress( 2000 ) ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;

        }
    }

    function progress_append( result ) {

        //console.log("updateResultText() ", result.plainText.length,   $resultsText.text().length)

        let emptyLogsMessage = "No container logs found - verify start has been issued" ;
        let aceEditorSession = _progressViewer.session ;

        if ( ( _firstTail && result.plainText && result.plainText.length == 0 ) ||
                emptyLogsMessage == $progressText.text() ) {
            _progressViewer.setValue( emptyLogsMessage, -1 ) ;

        } else if ( result.plainText && result.plainText.length != _progressViewer.getValue().length ) {
            console.log( "updateResultText() newContent length: ", result.plainText.length, "editor length: ", _progressViewer.getValue().length ) ;
            aceEditorSession.insert( {
                row: aceEditorSession.getLength(),
                column: 0
            }, result.plainText ) ;

        } else if ( result.error ) {

            let message = "\n\t Error: '" + result.error + "'\n\n\t Reason: '" + result.reason + "'" ;
            _progressViewer.setValue( message, -1 ) ;
        }

        let maxSelectedToDisplay = 2 * $( "#progress-line-select" ).val() ;
        console.debug( "editor lines: ", aceEditorSession.getLength(),
                " max setting: ", maxSelectedToDisplay ) ;

        while ( aceEditorSession.getLength() > maxSelectedToDisplay ) {
            let Range = ace.require( 'ace/range' ).Range ;
            let deleteBlock = new Range( 0, 0, 10, 0 ) ;
            console.log( "Removing: ", deleteBlock ) ;
            aceEditorSession.remove( deleteBlock ) ;
            $( "#progress-truncate-message" ).text( "(Output Truncated)" ) ;
        }

        progress_scroll() ;

        $( "#progress-refresh-time" ).text( result.time ) ;

        _firstTail = false ;
    }

    function image_progress( progressUrl, offset, onCompleteFunction ) {
        //$resultsText.append() ;
        clearTimeout( _progressTimer ) ;
        utils.loading( "image progress" ) ;
        _progressTimer = setTimeout( function () {
            let requestParms = {
                "offset": offset
            } ;

            $.get(
                    progressUrl,
                    requestParms )

                    .done( function ( responseText ) {
                        let aceEditorSession = _progressViewer.session ;

                        progress_append( { plainText: responseText } ) ;

//                        if ( responseText.contains( '"error":' ) ) {
//                            console.log("Got an error", responseText) ;
//                            $mainLoading.hide() ;
//                            
//                        } else 
                        if ( !responseText.contains( "__Complete__" ) ) {
                            image_progress(
                                    progressUrl,
                                    $progressText.text().length,
                                    onCompleteFunction ) ;
                        } else {
                            console.log( onCompleteFunction ) ;
                            onCompleteFunction() ;
                            utils.loadingComplete() ;
                        }

                    } )

                    .fail( function ( jqXHR, textStatus, errorThrown ) {

                        handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown ) ;
                    } ) ;
        }, 2000 ) ;
    }



} ) ;
