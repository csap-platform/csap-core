// define( [ "file/log-formatters", "browser/utils", "ace/ace" ], function ( logFormatters, utils, aceEditor ) {

//     console.log( "Module loaded" ) ;


import _dom from "../../utils/dom-utils.js";

import utils from "../utils.js"


import {aceEditor, jsYaml} from "../../libs/file-editor.js"


// import explorerOps from "./explorer-operations.js"
// import hostOps from "./host-operations.js"

//import commandRunner from "./command-runner.js"


export const explorerProgress = explorer_progress();

export default explorerProgress ;


function explorer_progress() {



    let _progressViewer ;


    //let $progressText, $progressDialog ;

    const $progressDialog = $( "#progress-results-dialog" ) ;
    const $progressText = $( "#progress-results-text", $progressDialog ) ;

    const $progressFormatCheckbox = $( "#progress-auto-format", $progressDialog ) ;

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

        image: function ( progressUrl, offset, onCompleteFunction ) {
            show_progress_dialog( "Image Pull:" ) ;
            image_progress( progressUrl, offset, onCompleteFunction ) ;
        }

    }


    function initialize() {



        $( "#progress-clear-button" ).click( function () {
            console.log( "clearing" ) ;
            //$progressText.text( "" ) ;
            _progressViewer.setValue( "", -1 ) ;
            return false ;
        } ) ;


        $progressFormatCheckbox.change( function () {

            let theme = utils.getAceDefaults( "ace/mode/yaml", true ).theme ;
            let isLogFormat = $progressFormatCheckbox.prop( "checked" ) ;

            if ( isLogFormat ) {
                theme = "ace/theme/merbivore_soft" ;
            }
            _progressViewer.setTheme( theme ) ;

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

        $( "#wrapOutput", $progressDialog ).change( function () {

            if ( $( this ).is( ":checked" ) ) {
                _progressViewer.session.setUseWrapMode( true ) ;
            } else {
                _progressViewer.session.setUseWrapMode( false ) ;
            }
        } ) ;



        $( "#progress-new-window" ).click( openLogsInNewWindow ) ;

        // console.log( ` aceEditor `, aceEditor ) ;
        _progressViewer = aceEditor.edit( "progress-results-text" ) ;

        let aceOptions = utils.getAceDefaults( "ace/mode/yaml", true ) ;
        aceOptions.theme = "ace/theme/merbivore_soft" ;
        _progressViewer.setOptions( aceOptions ) ;

    }

    function openLogsInNewWindow() {

        let theLogHost = utils.getHostName() ;
//        let launchUrl = uiSettings.baseUrl + "file/FileMonitor" ;

        if ( _hostName != null ) {
            theLogHost = _hostName ;

        }

        let paramaters = {
            fileName: `__docker__${ _containerName }`,
            podName: _podLogUrl,
            hostName: theLogHost
        } ;


        console.log( `openLogsInNewWindow:`, paramaters ) ;

        utils.openAgentWindow(
                theLogHost,
                `${ utils.getFileUrl() }/FileMonitor`,
                paramaters ) ;



        return false ;
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

        if ( ( _firstTail
                && result.plainText
                && result.plainText.length === 0 )
                || emptyLogsMessage === $progressText.text() ) {

            _progressViewer.setValue( emptyLogsMessage, -1 ) ;

        } else if ( result.plainText
                && result.plainText.length != _progressViewer.getValue().length ) {

            console.log( "updateResultText() newContent length: ", result.plainText.length, "editor length: ", _progressViewer.getValue().length ) ;


            let isLogFormat = $progressFormatCheckbox.prop( "checked" ) ;
            let logContent = result.plainText ;

            if ( isLogFormat ) {
                logContent = logFormatters.simpleFormatter( result.plainText ) ;
            }


            aceEditorSession.insert( {
                row: aceEditorSession.getLength(),
                column: 0
            }, logContent ) ;

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



}
