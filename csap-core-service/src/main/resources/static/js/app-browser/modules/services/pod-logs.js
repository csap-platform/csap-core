define( [ "browser/utils", "services/kubernetes", "ace/ace", "ace/ext-modelist" ], function ( utils, kubernetes, aceEditor, aceModeListLoader ) {


    const $podLogPanel = $( "#services-tab-pod-logs" ) ;
    const $hideOutput = $( "#hide-pod-logs", $podLogPanel ) ;
    let $logContainerSelect = $( "#pod-log-container-name", $podLogPanel ) ;
    let $logPodSelect = $( "#pod-log-pod-name", $podLogPanel ) ;
    let $maxLineCount = $( "#pod-log-line-count", $podLogPanel ) ;
    let $podTail = $( "#pod-tail", $podLogPanel ) ;
    let _podLogRefreshTimer ;
    let _podLogViewer = null ;
    let _podLogUpdate = false ;
    let _logSince ;

    let _podName, _podNamespace, _containers, _podHost, _relatedPods ;


    return {

        configure: function ( podName, podNamespace, containerNames, podHost, relatedPods ) {

            console.log( `configuration updated: ${podName}` ) ;

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
            _podLogViewer.setOptions( utils.getAceDefaults( "ace/mode/sh", true ) ) ;


            //$hideOutput.hide() ;

            $hideOutput.click( function () {
                let navMenu = ".pod-selected.level4" ;
                let   launchTarget = "services-tab,kpod-instances" ;

                utils.findNavigation( ".pod-selected.level4" ).hide() ;
                utils.launchMenu( launchTarget ) ;
            } ) ;



            _podLogViewer.getSession().on( "changeScrollTop", function () {
                if ( !_podLogUpdate ) {
                    console.log( "Disabling tail" ) ;
                    clearTimeout( _podLogRefreshTimer ) ;
                    utils.flash( $podTail.parent(), true, 5 ) ;
                    $podTail.prop( "checked", false ) ;
                }
            } ) ;

            $podTail.change( function () {
                if ( $( this ).is( ":checked" ) ) {
                    getPodLogs(  ) ;
                }
            } )
            $( "button.csap-clear", $podLogPanel ).click( function () {
                _podLogUpdate = true ;
                _podLogViewer.setValue( "" ) ;
                setTimeout( function () {
                    _podLogUpdate = false ;
                }, 100 ) ;
            } )


            $( "button.csap-download", $podLogPanel ).click( function () {
                let tailParameters = {
                    numberOfLines: 10000
                }
                let path = `/explorer/kubernetes/pods/logs/${_podNamespace}/${$logPodSelect.val()}/${$logContainerSelect.val()}` ;
                utils.openAgentWindow( _podHost, path, tailParameters ) ;
            } )

            $( "select,input#pod-previous-terminated", $podLogPanel ).change( function () {
                resetPodLogs() ;
                getPodLogs( ) ;
            } ) ;

            $( "button.launch-window", $podLogPanel ).click( function () {
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


        let $contentLoaded = new $.Deferred() ;
        getPodLogs( ) ;

        return $contentLoaded ;

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

        console.log( `getPodLogs: ${ podLogUrl }`, parameters ) ;

        $.getJSON(
                podLogUrl,
                parameters )

                .done( function ( podLogs ) {

//                    if ( $contentLoaded ) {
//                        $contentLoaded.resolve() ;
//                    }
                    utils.loadingComplete() ;

                    showLogs( podLogs ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving volume summary", errorThrown ) ;
                } ) ;

    }

    function showLogs( podLogs ) {

//        console.log( `podLogs: `, podLogs ) ;

        let logContent = null ;
        let isPodTail = $podTail.prop( "checked" ) ;
        if ( podLogs.plainText ) {
            logContent = podLogs.plainText ;

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
            let lastLine = editSession.getLength() ;
            _podLogViewer.scrollToLine( lastLine, false, false ) ;
            setTimeout( function () {
                _podLogUpdate = false ;
            }, 400 ) ;

            let currentEditorLines = editSession.getLength() ;
            let maxLines = $maxLineCount.val() ;
            if ( currentEditorLines > maxLines ) {
                let Range = ace.require( 'ace/range' ).Range ;
                let linesToRemove = new Range( 0, 0, currentEditorLines - maxLines   , 0 ) ;
                console.log( `reducing content: size: ${ currentEditorLines }, maxLines: ${maxLines}` ) ;
                editSession.remove( linesToRemove ) ;
            }

        }

        $( "#pod-log-count", $podLogPanel ).text( _podLogViewer.getSession().getLength()  + "" ) ;

        if ( podLogs.time ) {
            $( "#pod-load-time", $podLogPanel ).text( podLogs.time ) ;
            _logSince = podLogs.since ;
        }

        let doRefresh = isPodTail && $podTail.is( ":visible" ) ;
        if ( doRefresh ) {

            _podLogRefreshTimer = setTimeout( function () {
                getPodLogs() ;
            }, 2000 ) ;
        }


    }


} ) ;