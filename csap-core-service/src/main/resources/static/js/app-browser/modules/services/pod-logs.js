define( [ "browser/utils", "file/log-formatters", "services/kubernetes", "ace/ace", "ace/ext-modelist" ], function ( utils, logFormatters, kubernetes, aceEditor, aceModeListLoader ) {


    //
    // Cloned to explorer-pod-logs and pod-logs
    //
    const $targetLogViewerPanel = $( "#services-tab-pod-logs" ) ;
    const $hideOutput = $( "#hide-pod-logs", $targetLogViewerPanel ) ;
    let $logContainerSelect = $( "#pod-log-container-name", $targetLogViewerPanel ) ;
    let $logPodSelect = $( "#pod-log-pod-name", $targetLogViewerPanel ) ;
    let $maxLineCount = $( "#pod-log-line-count", $targetLogViewerPanel ) ;


    let $podLogsAutoFormat = $( "#pod-logs-auto-format", $targetLogViewerPanel ) ;

    let $podTail = $( "#pod-tail", $targetLogViewerPanel ) ;
    let $podScroll = $( "#pod-scroll", $targetLogViewerPanel ) ;


    let _podLogRefreshTimer ;
    let _podLogViewer = null ;
    let _podLogUpdate = false ;
    let _logSince ;

    let scrollFlashTimer ;

    let _scrollEventCount = 0 ;

    let _podName, _podNamespace, _containers, _podHost, _relatedPods ;


    return {

        configure: function ( podName, podNamespace, containerNames, podHost, relatedPods ) {

            console.log( `configuration updated: ${podName}` ) ;

            $maxLineCount.val( 5000 ) ;

            _podName = podName ;
            _podNamespace = podNamespace ;
            _containers = containerNames ;
            _podHost = podHost ;
            _relatedPods = relatedPods ;

        },

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            return show( forceHostRefresh ) ;

        }
    } ;



    function resetPodLogs() {
        _logSince = -1 ;
    }

    function show( forceHostRefresh ) {


        if ( !_podLogViewer ) {
            _podLogViewer = aceEditor.edit( "kubernetes-log-viewer" ) ;

            let aceOptions = utils.getAceDefaults( "ace/mode/yaml", true ) ;
            aceOptions.theme = "ace/theme/merbivore_soft" ;

            _podLogViewer.setOptions( aceOptions ) ;



            //$hideOutput.hide() ;

            let hideFunction = function () {
                let navMenu = ".pod-selected.level4" ;
                let   launchTarget = "services-tab,kpod-instances" ;

                utils.findNavigation( ".pod-selected.level4" ).hide() ;
                utils.launchMenu( launchTarget ) ;
            }

            $hideOutput.click( hideFunction ) ;



            $podScroll.change( function () {
                _scrollEventCount = 0 ;
            } ) ;
            _podLogViewer.getSession().on( "changeScrollTop", function () {
                if ( !_podLogUpdate
                        && $podScroll.prop( "checked" ) ) {
                    _scrollEventCount++ ;
                    if ( _scrollEventCount > 5 ) {
                        console.log( "Disabling scroll" ) ;
                        $podScroll.prop( "checked", false ) ;
                    }
                }
            } ) ;

            $podTail.change( function () {
                if ( $( this ).is( ":checked" ) ) {
                    getPodLogs(  ) ;
                }
            } )
            $( "button.csap-empty", $targetLogViewerPanel ).click( function () {
                _podLogUpdate = true ;
                _podLogViewer.setValue( "" ) ;
                setTimeout( function () {
                    _podLogUpdate = false ;
                }, 100 ) ;
            } )


            $( "button.csap-download", $targetLogViewerPanel ).click( function () {
                let tailParameters = {
                    numberOfLines: 10000
                }
                let path = `/explorer/kubernetes/pods/logs/${_podNamespace}/${$logPodSelect.val()}/${$logContainerSelect.val()}` ;
                utils.openAgentWindow( _podHost, path, tailParameters ) ;
            } )

            $( "select,input#pod-previous-terminated", $targetLogViewerPanel ).change( function () {

                utils.loading( "loading logs" ) ;
                resetPodLogs() ;
                getPodLogs( ) ;
            } ) ;

            $podLogsAutoFormat.change( function () {

                utils.loading( "loading" ) ;

                let isPodLogFormat = $podLogsAutoFormat.prop( "checked" ) ;
                let aceOptions = utils.getAceDefaults( "ace/mode/yaml", true ) ;

                let theme = aceOptions.theme ;
                if ( isPodLogFormat ) {
                    theme = "ace/theme/merbivore_soft" ;
                }

                _podLogViewer.setTheme( theme ) ;
                resetPodLogs() ;
                getPodLogs( ) ;
            } ) ;

            $( "button.launch-window", $targetLogViewerPanel ).click( function () {
                // podName=/explorer/kubernetes/pods/logs/kube-system/calico-kube-controllers-578894d4cd-zwkd4
                let fileManagerUrl = utils.agentUrl( _podHost, "logs" )
                        + "?fileName=__docker__/" + $logContainerSelect.val()
                        + "&podName=" + $logPodSelect.val() ;

                console.log( `launching: ${fileManagerUrl}` ) ;

                utils.launch( fileManagerUrl ) ;
            } ) ;
        }
        resetPodLogs() ;

        $logPodSelect.empty() ;
        for ( podName of _relatedPods ) {
            jQuery( '<option/>', {
                text: podName
            } ).appendTo( $logPodSelect ) ;
        }


        $logContainerSelect.empty() ;
        let $selectedContainer = kubernetes.selectedContainer() ;
        console.log( `$selectedContainer`, $selectedContainer ) ;
        let containerNameToShow = "" ;
        if ( $selectedContainer.length > 0 ) {
            containerNameToShow = `${$selectedContainer.data( "name" )}` ;
        }
        for ( containerName of _containers ) {
            let $option = jQuery( '<option/>', {
                text: containerName
            } )
            console.log( `containerName: ${containerName}  containerNameToShow: ${ containerNameToShow }` ) ;
            if ( containerName === containerNameToShow ) {
                $option.attr( "selected", "selected" ) ;
            }
            $option.appendTo( $logContainerSelect ) ;
        }

        setTimeout( function () {
            utils.loading( "loading logs" ) ;
            setTimeout( getPodLogs, 100 ) ;
        }, 50 ) ;

        return null ;

    }

    function getPodLogs( ) {

        clearTimeout( _podLogRefreshTimer ) ;


        let selectedPodName = $logPodSelect.val() ;
        let podLogUrl = `${APP_BROWSER_URL }/kubernetes/pod/logs`


        let parameters = {
            "since": _logSince,
            podName: selectedPodName,
            namespace: _podNamespace,
            "containerName": $logContainerSelect.val(),
            "previousTerminated": $( "#pod-previous-terminated" ).is( ":checked" ),
            numberOfLines: $maxLineCount.val(),
            project: utils.getActiveProject(),
        }

        console.debug( `getPodLogs: ${ podLogUrl }`, parameters ) ;

        $.getJSON(
                podLogUrl,
                parameters )

                .done( function ( podLogs ) {

//                    if ( $contentLoaded ) {
//                        $contentLoaded.resolve() ;
//                    }

                    showLogs( podLogs ) ;

                    utils.loadingComplete() ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving volume summary", errorThrown ) ;
                } ) ;

    }

    function showLogs( podLogs ) {

//        console.log( `podLogs: `, podLogs ) ;

        let logContent = null ;
        let isPodTail = $podTail.prop( "checked" ) ;
        let isPodLogFormat = $podLogsAutoFormat.prop( "checked" ) ;

        if ( podLogs.plainText ) {



            if ( isPodLogFormat ) {
                logContent = logFormatters.simpleFormatter( podLogs.plainText ) ;
            } else {
                logContent = podLogs.plainText ;
            }

        } else if ( podLogs.error ) {

            logContent = "\n\t Error: '" + podLogs.error + "'\n\n\t Reason: '" + podLogs.reason + "'" ;
        } else if ( _logSince == -1 ) {
            logContent = "no-logs-found" ;
        }

        if ( logContent ) {
            // temporarily disabled notifications
            _podLogUpdate = true ;

            let editSession = _podLogViewer.getSession() ;
            if ( _logSince == -1 ) {
                _podLogViewer.setValue( "" ) ;
            }
            editSession.insert( {
                row: editSession.getLength(),
                column: 0
            }, logContent ) ;
            _podLogViewer.resize( true ) ;


            if ( $podScroll.prop( "checked" ) ) {
                let lastLine = editSession.getLength() ;
                _podLogViewer.scrollToLine( lastLine, false, false ) ;
            }



            setTimeout( function () {
                _podLogUpdate = false ;
            }, 400 ) ;

            let currentEditorLines = editSession.getLength() ;
            let maxLines = $maxLineCount.val() ;
            if ( currentEditorLines > maxLines ) {
                let Range = ace.require( 'ace/range' ).Range ;
                let linesToRemove = new Range( 0, 0, currentEditorLines - maxLines, 0 ) ;
                console.log( `reducing content: size: ${ currentEditorLines }, maxLines: ${maxLines}` ) ;
                editSession.remove( linesToRemove ) ;
            }

        }

        $( "#pod-log-count", $targetLogViewerPanel ).text( _podLogViewer.getSession().getLength() + "" ) ;

        if ( podLogs.time ) {
            $( "#pod-load-time", $targetLogViewerPanel ).text( podLogs.time ) ;
            _logSince = podLogs.since ;
        }

        let doRefresh = isPodTail && $podTail.is( ":visible" ) ;
        if ( doRefresh ) {

            _podLogRefreshTimer = setTimeout( function () {
                getPodLogs() ;
            }, 2000 ) ;
        } else {
            console.log( `aborting log refresh ` ) ;
        }


    }


} ) ;