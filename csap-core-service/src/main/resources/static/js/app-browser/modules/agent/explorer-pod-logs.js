define( [ "browser/utils", "file/log-formatters", "ace/ace", "ace/ext-modelist" ], function ( utils, logFormatters, aceEditor, aceModeListLoader ) {


    const $podLogPanel = $( "#agent-tab-explorer-pod-logs" ) ;
    const $hideOutput = $( "#hide-pod-logs", $podLogPanel ) ;
    const $podSelectedNavs = utils.findNavigation( "span.agent-pod-selected" ) ;
    let $logContainerSelect = $( "#pod-log-container-name", $podLogPanel ) ;
    let $logPodSelect = $( "#pod-log-pod-name", $podLogPanel ) ;
    let $maxLineCount = $( "#pod-log-line-count", $podLogPanel ) ;

    let $podLogsAutoFormat = $( "#pod-logs-auto-format", $podLogPanel ) ;

    let $podTail = $( "#pod-tail", $podLogPanel ) ;
    let $podScroll = $( "#pod-scroll", $podLogPanel ) ;
    let _podLogRefreshTimer ;
    let _podLogViewer = null ;
    let _podLogUpdate = false ;
    let _logSince ;

    let _scrollEventCount = 0 ;


    let scrollFlashTimer ;

    let _podName, _podNamespace, _containers, _podHost, _relatedPods ;

    let containerMode = false ;
    let _containerName ;


    return {

        configure: function ( podName, podNamespace, containerNames, podHost, relatedPods ) {

            console.log( `configuration updated: ${podName}` ) ;

            $maxLineCount.val( 5000 ) ;

            _podName = podName ;
            containerMode = false ;

            if ( !podNamespace ) {
                // container mode
                containerMode = true ;

                console.log( `containerMode: ${containerMode}` ) ;
                _containerName = podName ;
                _containers = containerNames ;

            } else {
                _podNamespace = podNamespace ;
                _containers = containerNames ;
                _podHost = podHost ;
                _relatedPods = relatedPods ;
            }

        },

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            if ( !_podName && !_containerName ) {
                utils.launchMenu( "agent-tab,explorer" ) ;
                return null ;
            }

            $podSelectedNavs.show() ;

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


            $podScroll.change( function () {
                _scrollEventCount = 0 ;
            } ) ;

            _podLogViewer.getSession().on( "changeScrollTop", function () {
                if ( !_podLogUpdate
                        && $podScroll.prop( "checked" ) ) {

                    _scrollEventCount++ ;
                    if ( _scrollEventCount > 5 ) {
                        console.log( "Disabling pod scroll" ) ;
                        $podScroll.prop( "checked", false ) ;
                    }
                }
            } ) ;

            $hideOutput.off().click( function () {
                utils.launchMenu( "agent-tab,explorer" )
                $podSelectedNavs.hide() ;
                return false ;
            } ) ;


            $( "button.csap-download", $podLogPanel ).off().click( function () {
                let tailParameters = {
                    numberOfLines: 10000
                }
                let path = `/explorer/kubernetes/pods/logs/${_podNamespace}/${$logPodSelect.val()}/${$logContainerSelect.val()}` ;
                utils.openAgentWindow( _podHost, path, tailParameters ) ;
            } )


            $podTail.off().change( function () {
                if ( $( this ).is( ":checked" ) ) {
                    refreshLogs(  ) ;
                }
            } ) ;

            $( "button.csap-empty", $podLogPanel ).click( function () {
                _podLogUpdate = true ;
                _podLogViewer.setValue( "" ) ;
                setTimeout( function () {
                    _podLogUpdate = false ;
                }, 100 ) ;
            } )

            $( "select,input#pod-previous-terminated", $podLogPanel ).change( function () {
                utils.loading( "loading logs" ) ;
                resetPodLogs() ;
                refreshLogs( ) ;
            } ) ;

            $podLogsAutoFormat.change( function () {

                utils.loading( "loading logs" ) ;

                let isPodLogFormat = $podLogsAutoFormat.prop( "checked" ) ;
                let aceOptions = utils.getAceDefaults( "ace/mode/yaml", true ) ;

                let theme = aceOptions.theme ;
                if ( isPodLogFormat ) {
                    theme = "ace/theme/merbivore_soft" ;
                }

                _podLogViewer.setTheme( theme ) ;
                resetPodLogs() ;
                refreshLogs( ) ;
            } ) ;

            $( "button.launch-window", $podLogPanel ).click( function () {
                // podName=/explorer/kubernetes/pods/logs/kube-system/calico-kube-controllers-578894d4cd-zwkd4
                let targetHost = _podHost ;

                if ( containerMode ) {
                    targetHost = utils.getHostName() ;
                }

                let dockerContainer = $logContainerSelect.val() ;
                // ensure starts with / for backend log path
                if ( !dockerContainer.startsWith( "/" ) ) {
                    dockerContainer = "/" + dockerContainer ;
                }

                let fileManagerUrl = utils.agentUrl( targetHost, "logs" )
                        + "?fileName=__docker__" + dockerContainer ;

                if ( !containerMode ) {
                    fileManagerUrl += "&podName=" + $logPodSelect.val() ;
                }

                console.log( `launching: ${fileManagerUrl}` ) ;

                utils.launch( fileManagerUrl ) ;
            } ) ;
        }


        resetPodLogs() ;
        let $contentLoaded = new $.Deferred() ;

        if ( containerMode ) {

            $logPodSelect.parent().hide() ;
            $( "input#pod-previous-terminated", $podLogPanel ).parent().hide() ;

            $logContainerSelect.empty() ;

            for ( let container of _containers ) {
                jQuery( '<option/>', {
                    text: container.substring( 1 ),
                    value: container
                } ).appendTo( $logContainerSelect ) ;
            }

            $logContainerSelect.val( _containerName ) ;

        } else {

            $logPodSelect.parent().show() ;
            $( "input#pod-previous-terminated", $podLogPanel ).parent().show() ;

            $logPodSelect.empty() ;
            for ( podName of _relatedPods ) {
                jQuery( '<option/>', {
                    text: podName
                } ).appendTo( $logPodSelect ) ;
            }


            $logContainerSelect.empty() ;

            for ( let container of _containers ) {
                jQuery( '<option/>', {
                    text: container
                } ).appendTo( $logContainerSelect ) ;
            }


        }

        setTimeout( function () {
            utils.loading( "loading logs" ) ;
            setTimeout( refreshLogs, 100 ) ;
        }, 50 ) ;


        return $contentLoaded ;

    }

    function refreshLogs() {

        if ( containerMode ) {
            getContainerLogs() ;
        } else {
            getPodLogs() ;
        }
    }

    function getContainerLogs() {

        clearTimeout( _podLogRefreshTimer ) ;
        let _dockerContainerPath = "docker/container/" ;
        let _containerCommandUrl = explorerUrl + "/" + _dockerContainerPath ;


        let _tailParameters = {
            "name": $logContainerSelect.val(),
            "since": _logSince,
            numberOfLines: $maxLineCount.val()
        } ;


        console.debug( `getContainerLogs: ${ _containerCommandUrl }`, _tailParameters ) ;

        $.getJSON( _containerCommandUrl + "tail", _tailParameters )

                .done( function ( containerLogs ) {
                    utils.loadingComplete() ;
                    showLogs( containerLogs ) ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                } ) ;

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
                    utils.loadingComplete() ;

                    showLogs( podLogs ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
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
            }, 500 ) ;

            let currentEditorLines = editSession.getLength() ;
            let maxLines = $maxLineCount.val() ;
            if ( currentEditorLines > maxLines ) {
                let Range = ace.require( 'ace/range' ).Range ;
                let linesToRemove = new Range( 0, 0, currentEditorLines - maxLines, 0 ) ;
                console.log( `reducing content: size: ${ currentEditorLines }, maxLines: ${maxLines}` ) ;
                editSession.remove( linesToRemove ) ;
            }


        }

        $( "#pod-log-count", $podLogPanel ).text( _podLogViewer.getSession().getLength() + "" ) ;

        if ( podLogs.time ) {
            $( "#pod-load-time", $podLogPanel ).text( podLogs.time ) ;
            _logSince = podLogs.since ;
        }

        let doRefresh = isPodTail && $podTail.is( ":visible" ) ;
        if ( doRefresh ) {

            _podLogRefreshTimer = setTimeout( refreshLogs, 2000 ) ;
        } else {
            console.log( `aborting log refresh ` ) ;
        }


    }


} ) ;