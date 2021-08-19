
define( [ "browser/utils", "file/log-formatters", "ace/ace", "ace/ext-modelist", "jsYaml/js-yaml" ], function ( utils, logFormatters, aceEditor, aceModeListLoader, yaml ) {

    console.log( "module loaded file-monitor ddd3" ) ;
    let $container, $header, $fileSelection, $csapFileNameFilterEnable, $clearFilterButton, $options, $logDefinition, $formatSelection, $scrollCheck, $languageMode, $stripAnsi, $editorTheme ;
    let serviceName, serviceFullName, hostName ;

    let _defaultMode = "ace/mode/yaml" ;

    let _parserNewLineWords, _parserColumnCount, _parserColumnOrder, _parserThrottleWords, _parserThrottleInterval, _parserThrottleCount, _parserTrimAnsi ;

    let _throttleEnabled = true ;


    //
    //  log offsets: either file (bytes) or time (containers
    //
    let _containerMillisOffset = -1 ;
    let _fileByteOffset = "-1" ;
    let _autoSelectViewFormat = true ;

    let logPadLeft = 14 ;

    let _is_auto_scroll_in_progress = true ;
    let _scrollEventCount = 0 ;
    let $maxBuffer ;

    let _isAutoAssignTheme = true ;

    let $currentView ;
    let $enhancedViewer ;

    let  _aceModeList ;

    let activePanelId ;
    let activeEditors = new Object() ;
    let $fileSelect ;

    let _viewLoadedTimer ;


    // note \r only needed for testing on windows
    let jsonPattern = new RegExp( "^{.*}[ ,\r]*$" ) ;

    let ansiPattern = new RegExp( "[\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]", "g" ) ;


    const logLevelMapping = {
        "I": "INFO",
        "E": "ERROR",
        "W": "WARN"
    }


    let checkForChangesTimer = null ;



    return {

        getActivePanelId: function () {
            return  activePanelId ;
        },

        show: function ( $panel, host, service, $theFileSelection ) {



            //
            // FileMonitor.html  dynamically loaded - hence the dynamic binding here
            //

            activePanelId = $panel.attr( "id" ) ;

            $container = $( ".csap-file-monitor", $panel ) ;
            $header = $( "header", $container ) ;
            $fileSelection = $( "#logFileSelect", $header ) ;
            $csapFileNameFilterEnable = $( "button.csap-search", $header ) ;
            $clearFilterButton = $( "#clear-filter-button", $header ) ;


            $options = $( "#monitor-display-options", $header ) ;
            $logDefinition = $( "#log-column-defs", $options ) ;

            $formatSelection = $( "#log-format", $options ) ;
            $stripAnsi = $( "#strip-ansi", $options ) ;
            $languageMode = $( "#log-mode", $options ) ;
            $editorTheme = $( "#log-theme", $options ) ;


            $scrollCheck = $( "input.auto-scroll-logs", $options.parent() ) ;


            $enhancedViewer = $( ".enhanced-view", $container ) ;
            $enhancedViewer.attr( "id", "editor-" + activePanelId ) ;

            $maxBuffer = $( '#bufferSelect', $header ) ;
            //alert(`$maxBuffer size:  '${ $maxBuffer.val() }'`)

            hostName = host ;

            serviceName = service ;
            serviceFullName = service ;

            $fileSelect = $theFileSelection ;

            // placeholder in the file monitor API
            let $podPlaceholder = $( 'option[value="kubernetes-pods-detected"]', $fileSelect ) ;
            $podPlaceholder.remove() ;

            setTimeout( show, 100 ) ;
            //return getFileBrowser( $menuContent, forceHostRefresh, menuPath ) ;

        }

    } ;

    function activeEditor( editor ) {

        if ( !editor ) {
            return activeEditors [ activePanelId ] ;
        }
        activeEditors [ activePanelId ] = editor ;
        return editor ;
    }

    function show() {

        // disable cors
        // bearerAuth = null ;
        // CsapCommon.enableCorsCredentials();


        $currentView = $enhancedViewer ;

        $( "button.csap-tools", $header ).click( function () {
            $options.toggle() ;
        } ) ;

        $( "button.csap-remove", $options ).click( function () {
            $options.hide() ;
        } ) ;


        $( 'button.csap-download', $options.parent() ).click( function () {

            $options.hide() ;

            let podSettings = buildPodSettings() ;
            if ( podSettings ) {
                let tailParameters = {
                    numberOfLines: 10000
                }
                let path = `/explorer/kubernetes/pods/logs/${podSettings.namespace}/${podSettings.podName}/${podSettings.containerName}` ;
                utils.openAgentWindow( hostName, path, tailParameters ) ;
                return ;
            }

            let fileNameShownInUrl = $( "option:selected", $fileSelection ).text() ;
            if ( fileNameShownInUrl.indexOf( "\\" ) != -1 ) {
                fileNameShownInUrl = fileNameShownInUrl.substring( fileNameShownInUrl.indexOf( "\\" ) + 1 ) ;
            }
            if ( fileNameShownInUrl.lastIndexOf( "/" ) != -1 ) {
                fileNameShownInUrl = fileNameShownInUrl.substring( fileNameShownInUrl.lastIndexOf( "/" ) + 1 ) ;
            }
            console.log( "fileNameShownInUrl: " + fileNameShownInUrl ) ;

            let parameters = {
                fromFolder: $fileSelection.val(),
                serviceName: serviceName,
                hostName: hostName
            } ;

            let theUrl = utils.agentUrl( hostName, "/file/downloadFile/" + fileNameShownInUrl ) ;
            // alert( $("#logFileSelect option:selected").text() ) ;

            postAndRemove( "_blank", theUrl, parameters ) ;

        } ) ;

        let commandScreen = utils.agentUrl( hostName, "/os/command" ) ;

        $( '#grepButton', $container ).click( function () {
            $options.hide() ;
            let inputMap = {
                fromFolder: $fileSelection.val(),
                serviceName: serviceFullName,
                command: "script",
                template: "file-grep.sh",
                //hostName: hostName
            } ;
            alertify.notify( "Launching script grep for file: " + $fileSelection.val() ) ;


            postAndRemove( "_blank", commandScreen, inputMap ) ;
            return false ;
        } ) ;

        $( '#monitorDiv', $container ).keyup( function ( event ) {
            //alertify.notify("checking search on " + event.keyCode) ;
            if ( event.keyCode == 83 ) {
                //alertify.notify("triggering search on ") ;
                $( '#searchButton' ).trigger( "click" ) ;
            }
        } ) ;

        $( '#searchButton', $container ).click( function () {

            $options.hide() ;

            let selText = activeEditor().getSelectedText() ;
//                    let selText = "" ;
//                    if ( window.getSelection ) {
//                        selText = window.getSelection().toString() ;
//                    } else if ( document.selection && document.selection.type != "Control" ) {
//                        selText = document.selection.createRange().text ;
//                    }

            let inputMap = {
                //fromFolder: $( "#logFileSelect option:selected" ).text(),
                fromFolder: $fileSelection.val(),
                serviceName: serviceFullName,
                searchText: selText,
                command: "logSearch",
                hostName: hostName
            } ;
            alertify.notify( "Launching search for file: " + $fileSelection.val() ) ;
            postAndRemove( "_blank", commandScreen, inputMap ) ;
            return false ;
        } ) ;

        $( '#tailButton', $container ).click( function () {

            $options.hide() ;

            let inputMap = {
                fromFolder: $fileSelection.val(),
                serviceName: serviceFullName,
                command: "script",
                template: "file-tail.sh",
                //hostName: hostName
            } ;
            alertify.notify( "Launching script tail for file: " + $fileSelection.val() ) ;
            postAndRemove( "_blank", commandScreen, inputMap ) ;
            return false ;
        } ) ;


        $( 'button.csap-empty', $options.parent() ).click( function () {


            $options.hide() ;

            _is_auto_scroll_in_progress = true ;
            activeEditor().setValue( "" ) ;
            setTimeout( function () {
                _is_auto_scroll_in_progress = false ;
            }, 500 ) ;

        } ) ;

//        $( "#logFileSelect" ).selectmenu( {
//            width: "25em",


        $csapFileNameFilterEnable.click( function () {
            buildFileCombo() ;
            $csapFileNameFilterEnable.hide() ;
        } )


        _autoSelectViewFormat = !utils.isLogAutoFormatDisabled() ;
        $fileSelection.change( function () {

            _autoSelectViewFormat = !utils.isLogAutoFormatDisabled() ;


            if ( $( this ).val().indexOf( ".gz" ) != -1 || $( this ).val().indexOf( ".zip" ) != -1 ) {
                let inputMap = {
                    fromFolder: $( this ).val(),
                    hostName: hostName
                } ;

                let downloadName = $( "option:selected", $fileSelection ).text() ;
                let lastIndex = downloadName.indexOf( "/" ) ;
                // hook for windows testing
                if ( lastIndex == -1 )
                    lastIndex = downloadName.indexOf( "\\" ) ;
                if ( lastIndex != -1 )
                    downloadName = downloadName.substring( lastIndex + 1 ) ;
                console.log( "downloadName " + downloadName ) ;
                postAndRemove( "_blank", "downloadFile/" + downloadName, inputMap ) ;
                return ;
            }

            lastSelectedFile = $fileSelection.val() ;
            reloadViewer() ;
            return false ; // prevents link
        } ) ;

        $( "input.pod-previous", $options ).change( function () {
            console.log( `showing previous` ) ;
            $options.hide() ;
            reloadViewer() ;
        } ) ;


        let logChoice = utils.getLogChoice() ;
        if ( logChoice ) {
            let logName = logChoice ;
            if ( logChoice == "startServer" ) {
                logName = `${ serviceName }-start.log` ;

            } else if ( logChoice == "stopServer" ) {
                logName = `${ serviceName }-stop.log` ;

            } else if ( logChoice == "killServer" ) {
                logName = `${ serviceName }-kill.log` ;

            } else if ( logChoice == "deploy" ) {
                logName = `${ serviceName }-deploy.log` ;
            }

            _is_auto_scroll_in_progress = true ;
            $scrollCheck.prop( "checked", true ) ;
            $( `option`, $fileSelection ).each( function () {
                let $logOption = $( this ) ;
                //console.log( `log option: ${ $logOption.text() }`) ;
                if ( $logOption.text() == logName ) {
                    $logOption.attr( 'selected', true ) ;
                }
            } ) ;
            //$( `#logFileSelect option:contains("${ logName }")`, $container ).attr( 'selected', true ) ;

            utils.resetLogChoice() ;
        }

        console.log( `selected log: ${ $fileSelection.val() }` ) ;



        $( "#progress-line-select", $container ).change( function () {
            reloadViewer() ;
        } ) ;

        $formatSelection.change( function () {
            $options.hide() ;
            reloadViewer() ;
        } ) ;


        $maxBuffer.change( function () {
            $options.hide() ;
            reloadViewer() ;
        } ) ;


        $( "#monitor-wrap-text", $options ).change( function () {

            activeEditor().getSession().setUseWrapMode( $( this ).is( ":checked" ) ) ;

        } ) ;


        $logDefinition.empty() ;
        let parsers = utils.getLogParsers() ;
        if ( Array.isArray( parsers ) ) {

            for ( let parser of parsers ) {
                let optionItem = jQuery( '<option/>', {
                    value: parser.match,
                    text: parser.match
                } ) ;
                $logDefinition.append( optionItem ) ;
            }

            $logDefinition.change( function () {



                for ( let parser of parsers ) {
                    if ( parser.match === $logDefinition.val() ) {
//                        let updatedCols = parser.columns.join( ',' ) ;
                        let updatedCols = joinCsv( parser.columns ) ;
                        console.log( `updatedCols: ${ updatedCols}` ) ;
                        $( "#log-column-display", $options ).val( updatedCols ) ;
                        _parserColumnOrder = parser.columns ;
                        _parserColumnCount = largestColumnInteger( _parserColumnOrder ) ;
                        _parserNewLineWords = parser.newLineWords ;
                        _parserThrottleWords = parser.throttleWords ;
                        _parserTrimAnsi = parser.trimAnsi ;
                        reloadViewer() ;
                        break ;
                    }
                }
            } ) ;
        }

        $( "#log-column-display", $options ).change( function () {
            _parserColumnOrder = splitCsv( $( this ).val() ) ;
            _parserColumnCount = largestColumnInteger( _parserColumnOrder ) ;
            reloadViewer() ;
        } ) ;


        _parserThrottleInterval = $( "#log-throttle-interval", $options ).val() ;
        $( "#log-throttle-interval", $options ).change( function () {
            _parserThrottleInterval = splitCsv( $( this ).val() ) ;
            reloadViewer() ;
        } ) ;

        $clearFilterButton.click( function () {
            _throttleEnabled = !_throttleEnabled ;
            reloadViewer() ;
        } ) ;

        $( "#log-throttle-words", $options ).change( function () {
            _parserThrottleWords = splitCsv( $( this ).val() ) ;
            reloadViewer() ;
        } ) ;

        $( "#log-column-keywords", $options ).change( function () {
            _parserNewLineWords = $( "#log-column-keywords", $options ).val().split( "," ) ;
            reloadViewer() ;
        } ) ;

        $( "#log-column-all", $options ).change( function () {
            reloadViewer() ;
        } ) ;

        $( ".column-options", $options ).hide() ;

        $( '#refreshSelect', $container ).change( function () {
            $options.hide() ;
            latest_changes_request() ;
        } ) ;

        $stripAnsi.change( function () {
            reloadViewer() ;
        } ) ;

        $languageMode.change( updatedEditorMode ) ;

        $editorTheme.change( function () {

            let userTheme = $editorTheme.val() ;

            if ( $formatSelection.val( ) == "text" 
                    && ! isCsapDeployFileSelected() ) {
                userTheme = utils.getAceDefaults( "ace/mode/yaml", true ).theme ;
            }

            console.log( `Updating theme: ${ userTheme }` )
            activeEditor().setTheme( userTheme ) ;
        } ) ;


        let $aceFoldCheckbox = $( "#ace-fold-checkbox", $container ) ;
        $aceFoldCheckbox.change( function () {
//            let targetFile = $fileSelection.val() ;
//            if ( targetFile.endsWith( ".log" ) ) {
//                targetFile = "placeholder.sh" ;
//            }

            if ( $( this ).is( ':checked' ) ) {
                console.log( "Initiating fold" ) ;
                //let fileMode = _aceModeList.getModeForPath( targetFile ).mode ;
//                activeEditor().session.setMode( fileMode ) ;
                setTimeout( function () {
                    activeEditor().getSession().foldAll( 2 ) ;
                }, 100 )

            } else {
                //_yamlEditor.getSession().unfoldAll( 2 ) ;
                activeEditor().getSession().unfold( ) ;
            }
        } )

        $( '#ace-wrap-checkbox', $container ).change( function () {
            if ( $( "#ace-wrap-checkbox" ).prop( 'checked' ) ) {
                activeEditor().session.setUseWrapMode( true ) ;

            } else {
                activeEditor().session.setUseWrapMode( false ) ;
            }
        } ) ;

        build_file_viewer() ;
        reloadViewer() ;

//        setTimeout( function () {
//            console.log( "adding a lag for requirejs loading" ) ;
//            build_file_viewer() ;
//            //latest_changes_request();
//            reloadViewer() ;
//        }, 100 ) ;
    }

    function buildFileCombo() {

        $.widget( "custom.combobox", {
            _create: function () {
                this.wrapper = $( "<span>" )
                        .addClass( "custom-combobox" )
                        .insertAfter( this.element ) ;

                this.element.hide() ;
                this._createAutocomplete() ;
                this._createShowAllButton() ;
            },

            _createAutocomplete: function () {


                console.log( `build _createAutocomplete` ) ;

                let selected = this.element.children( ":selected" ),
                        value = selected.val() ? selected.text() : "" ;

                this.input = $( "<input>" )
                        .appendTo( this.wrapper )
                        .val( value )
                        .attr( "title", "" )
                        .addClass( "custom-combobox-input ui-widget ui-widget-content ui-state-default ui-corner-left" )
                        .autocomplete( {
                            delay: 0,
                            minLength: 0,
                            source: $.proxy( this, "_source" )
                        } )
                        .tooltip( {
                            classes: {
                                "ui-tooltip": "ui-state-highlight"
                            }
                        } ) ;

                this._on( this.input, {
                    autocompleteselect: function ( event, ui ) {
                        ui.item.option.selected = true ;
                        this._trigger( "select", event, {
                            item: ui.item.option
                        } ) ;
                    },

                    autocompletechange: "_removeIfInvalid"
                } ) ;
            },

            _createShowAllButton: function () {
                let input = this.input,
                        wasOpen = false ;

                $( "<a>" )
                        .attr( "tabIndex", -1 )
                        .attr( "title", "Show All Items" )
                        .tooltip()
                        .appendTo( this.wrapper )
                        .button( {
                            icons: {
                                primary: "ui-icon-triangle-1-s"
                            },
                            text: false
                        } )

                        .removeClass( "ui-corner-all" )

                        .addClass( "custom-combobox-toggle ui-corner-right" )

                        .on( "mousedown", function () {
                            wasOpen = input.autocomplete( "widget" ).is( ":visible" ) ;
                        } )

                        .on( "click", function () {
                            input.trigger( "focus" ) ;

                            // Close if already visible
                            if ( wasOpen ) {
                                return ;
                            }

                            console.log( `invoking autocomplete` ) ;

                            // Pass empty string as value to search for, displaying all results
                            input.autocomplete( "search", "" ) ;
                        } ) ;
            },

            _source: function ( request, response ) {

                console.log( `build source2` ) ;

                let matcher = new RegExp( $.ui.autocomplete.escapeRegex( request.term ), "i" ) ;
//                response( this.element.children( "option" ).map( function () {
                response( $( "option", $fileSelection ).map( function () {
                    let text = $( this ).text() ;
//                    console.log( `build source: ${text}`) ;
                    if ( this.value && ( !request.term || matcher.test( text ) ) )
                        return {
                            label: text,
                            value: text,
                            option: this
                        } ;
                } ) ) ;
            },

            _removeIfInvalid: function ( event, ui ) {

                // Selected an item, nothing to do
                if ( ui.item ) {
                    return ;
                }

                // Search for a match (case-insensitive)
                let value = this.input.val(),
                        valueLowerCase = value.toLowerCase(),
                        valid = false ;
                this.element.children( "option" ).each( function () {
                    if ( $( this ).text().toLowerCase() === valueLowerCase ) {
                        this.selected = valid = true ;
                        return false ;
                    }
                } ) ;

                // Found a match, nothing to do
                if ( valid ) {
                    return ;
                }

                // Remove invalid value
                this.input
                        .val( "" )
                        .attr( "title", value + " didn't match any item" )
                        .tooltip( "open" ) ;
                this.element.val( "" ) ;
                this._delay( function () {
                    this.input.tooltip( "close" ).attr( "title", "" ) ;
                }, 2500 ) ;
                this.input.autocomplete( "instance" ).term = "" ;
            },

            _destroy: function () {
                this.wrapper.remove() ;
                this.element.show() ;
            }
        } ) ;

        console.log( `Building combo box for ${ $( "option", $fileSelection ).length } items` ) ;
        $fileSelection.combobox( {
            select: function ( event, ui ) {
                $fileSelection.trigger( "change" ) ;
            }
        } ) ;

        $( "input.custom-combobox-input", $header ).val( $( "option:selected", $fileSelection ).text() ) ;
    }

    function joinCsv( arrayItems ) {
        let joinedLine = arrayItems.join( ',' ) ;
        joinedLine = joinedLine.replaceAll( "\n", "\\n" ) ;
        return joinedLine ;
//        for (item of arrayItems ) {
//            if ( item === "\n" ) {
//                joingLine += "NEW_LINE" ;
//            }
//        }
    }

    function splitCsv( csv ) {
        let csvArray = null ;
        csv = csv.trim() ;
        if ( csv.length != 0 ) {
            csvArray = csv.split( "," ) ;
        }
        return csvArray ;
    }

    function updatedEditorMode() {

        let selectedMode = $languageMode.val() ;

        if ( selectedMode == "auto" ) {
            let targetFile = $fileSelection.val() ;
            if ( targetFile.endsWith( ".log" ) ) {
                targetFile = "placeholder.sh" ;
            }
            selectedMode = _aceModeList.getModeForPath( targetFile ).mode ;
        }
        activeEditor().getSession().setMode( selectedMode ) ;
    }


    function reloadViewer() {

        clearTimeout( checkForChangesTimer ) ;

        _containerMillisOffset = -1 ;
        _fileByteOffset = "-1" ;
        _parserThrottleCount = 0 ;
        $clearFilterButton.hide() ;
        filterCount = -1 ;
        activeEditor().setValue( "" ) ;
        $( "#fileSize", $header ).text( "-" ) ;
//        utils.flash( $( "#fileSize", $header ), true ) ;

        if ( false && activeEditor() ) {
            console.log( `Create new editor session` ) ;
            activeEditor().setValue( "" ) ;
            // ensures clean context: undos, etc are wiped out
            activeEditor().setSession( new aceEditor.createEditSession( "" ) ) ;


            activeEditor().setOptions( getMonitorOptions() ) ;

            registerDisableScrollFunction() ;
        }


        utils.loading( "(re) loading viewer" ) ;
        setTimeout( function () {
            // delayed in case large file is loading and hogging thread
            latest_changes_request() ;
        }, 10 )
    }

    function getMonitorOptions() {
        let editorMode = _defaultMode ;
        let currentFormat = $formatSelection.val() ;
        if ( currentFormat == "json" ) {
            editorMode = "ace/mode/json5" ;
            // editorMode  = "ace/mode/jsoniq" ;
        } else if ( currentFormat == "yaml" ) {
            editorMode = "ace/mode/yaml" ;
        }
        $languageMode.val( editorMode ) ;

        let options = utils.getAceDefaults( editorMode, true ) ;
        options.theme = "ace/theme/merbivore_soft" ;
        $editorTheme.val( options.theme ) ;

        return options ;
    }

    function buildPodSettings() {
        let podSettings = null ;

        $( ".pod-previous", $options ).hide() ;

        //console.log(`buildPodSettings $fileSelect `) ;
        if ( $fileSelect ) {

            let containerName = $( "option:selected", $fileSelect ).data( "container" ) ;
            let podName = $( "option:selected", $fileSelect ).data( "pod" ) ;
            let hostName = $( "option:selected", $fileSelect ).data( "host" ) ;
            let namespace = $( "option:selected", $fileSelect ).data( "namespace" ) ;
            console.debug( `buildPodSettings: podName: ${podName} namespace: ${namespace} containerName: ${containerName}` ) ;

            if ( namespace ) {

                $( ".pod-previous", $options ).show() ;

                podSettings = {
                    "since": _containerMillisOffset,
                    podName: podName,
                    namespace: namespace,
                    "containerName": containerName,
                    "previousTerminated": $( "input.pod-previous", $options ).is( ":checked" ),
                    numberOfLines: getLineCount(),
                    project: utils.getActiveProject(),
                }
            }
        }

        return podSettings ;
    }

    function getLineCount() {
        return $( "#progress-line-select", $container ).val() ;
    }


    function latest_changes_request() {

        clearTimeout( checkForChangesTimer ) ;

        let podSettings = buildPodSettings() ;
        //console.debug(`latest_changes_request: podSettings`, podSettings) ;
        if ( podSettings ) {
            getPodLogs( podSettings ) ;
            return ;
        }


        //$('#serviceOps').css("display", "inline-block") ;

        let selectedItem = $fileSelection.val() ;


        let desktopTesting = utils.getParameterByName( "desktop" ).length > 0 ;

        let initialDisplay = Math.round( $maxBuffer.val() * 1024 / 2 ) ;

        let parameters = {
            fromFolder: selectedItem,
            dockerLineCount: getLineCount(),
            dockerSince: _containerMillisOffset,
            bufferSize: initialDisplay,
            logFileOffset: _fileByteOffset,
            useLocal: desktopTesting
        } ;
        if ( serviceFullName != null ) {
            $.extend( parameters, {
                serviceName: serviceFullName,
                hostName: hostName
            } ) ;
        }

        // console.log( `Bearer:  '${bearerAuth}'` );

        // CsapCommon.enableCorsCredentials();
        $.getJSON(
                utils.getFileUrl() + "/getFileChanges",
                parameters )

                .done( function ( dockerLogReport ) {
                    processLogReport( dockerLogReport ) ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    CsapCommon.handleConnectionError( "Retrieving changes for file " + $fileSelection.val(), errorThrown ) ;
                } ) ;


    }


    function getPodLogs( parameters ) {

        //clearTimeout( _podLogRefreshTimer ) ;


        let podLogUrl = `${APP_BROWSER_URL }/kubernetes/pod/logs`




        console.debug( `getPodLogs: ${ podLogUrl }`, parameters ) ;

        $.getJSON(
                podLogUrl,
                parameters )

                .done( function ( podLogs ) {

                    processLogReport( podLogs ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving volume summary", errorThrown ) ;
                } ) ;

    }



    function build_file_viewer() {
        clearTimeout( checkForChangesTimer ) ;

        $currentView = $enhancedViewer ;

        $enhancedViewer.text( "" ) ;

        _fileByteOffset = "-1" ;

        console.log( `Creating editor: ${ $enhancedViewer.attr( "id" ) } ` ) ;
        let theNewEditor = aceEditor.edit( $enhancedViewer.attr( "id" ) ) ;
        activeEditor( theNewEditor ) ;
        activeEditor().setOptions( getMonitorOptions() ) ;
        registerDisableScrollFunction() ;

        //activeEditor().setOptions( utils.getAceDefaults( _defaultMode, true ) ) ;

        $enhancedViewer.show() ;

        setTimeout( function () {
            activeEditor().gotoLine( activeEditor().session.getLength() ) ;
            registerDisableScrollFunction() ;
        }, 500 ) ;



    }

    function registerDisableScrollFunction() {

        $scrollCheck.change( function () {
            _scrollEventCount = 0 ;
        } ) ;

        activeEditor().session.on( "changeScrollTop", function () {
            //console.log(`scroll position: ${activeEditor().session.getScrollTop()}, session length: ${activeEditor().session.getLength()}` ) ;

            if ( !_is_auto_scroll_in_progress
                    && $scrollCheck.prop( "checked" ) ) {
                _scrollEventCount++ ;
                if ( _scrollEventCount > 5 ) {
                    $scrollCheck.prop( "checked", false ) ;
                    _scrollEventCount = 0 ;
                }
            }

        } ) ;
    }


    function add_content_to_viewer( dockerOrPodReport ) {

        let maxSelectedToDisplay = $maxBuffer.val() * 1024 ;

        if ( dockerOrPodReport.since
                || dockerOrPodReport.source == "journalctl" ) {

            if ( dockerOrPodReport.since == -1 ) {
                // docker files are simply tailed and updated (versus docker container logs)
                activeEditor().setValue( "" ) ;
            }

            $( "#dockerControls", $header ).show() ;
            _containerMillisOffset = dockerOrPodReport.since ;
            //return ;
        } else {
            $( "#dockerControls", $header ).hide() ;
        }

        let aceEditorSession = activeEditor().getSession() ;

        let latestText = dockerOrPodReport.plainText ;
        if ( Array.isArray( dockerOrPodReport.contents ) ) {
            latestText = dockerOrPodReport.contents.join( "" ) ;
        }
        let changeSize = 0 ;

        if ( latestText && latestText.length > 0 ) {
            changeSize = latestText.length ;

            if ( latestText.length > ( maxSelectedToDisplay + 200 ) ) {
                let removeBytes = 0 ;
                if ( $formatSelection.val() === "text" ) {
                    removeBytes = latestText.length - maxSelectedToDisplay ;
                }

                console.log( `currentBytes: ${ latestText.length}, removing: ${removeBytes}` ) ;
                aceEditorSession.setValue(
                        formatOutput( latestText.substring( removeBytes ) ) ) ;
            } else {

                // handle split lines
                let currentFormat = $formatSelection.val() ;
                let editorLineCount = activeEditor().session.getLength() ;
                if ( editorLineCount > 0
                        && currentFormat !== "text"
                        && !latestText.startsWith( "{" ) ) {

                    let lastLine = activeEditor().getSession().getLine( editorLineCount - 1 ) ;
                    console.debug( `lastLine: ${ lastLine }` ) ;
                    latestText = lastLine + latestText ;
                }


                // 
                console.debug( `activePanelId: ${activePanelId}` ) ;
                //_currentText += dockerOrPodReport.contents.join( "" ) ;
                aceEditorSession.insert( {
                    row: aceEditorSession.getLength(),
                    column: 0
                }, formatOutput( latestText ) ) ;

            }




            if ( $scrollCheck.is( ':checked' ) ) {
                //_enhancedViewer.navigateFileEnd() ;
                //_enhancedViewer.gotoLine( _enhancedViewer.session.getLength() ) ;
                _is_auto_scroll_in_progress = true ;

                let lineNumber = aceEditorSession.getLength() ;
                console.debug( `add_content_to_viewer lineNumber: ${lineNumber}` )

                try {
                    activeEditor().scrollToLine( lineNumber ) ;
                } catch ( err ) {
                    console.error( `Failed scroll attempt`, err )
                }

                setTimeout( function () {
                    _is_auto_scroll_in_progress = false ;
                }, 500 ) ;
            } else {
                activeEditor().resize( true ) ;
            }
        }


//        console.log( "editor lines: ", aceEditorSession.getLength(),
//                " current buffer size: " + aceEditorSession.getValue().length
//                + " max setting: ", maxSelectedToDisplay ) ;


        let currentContent = aceEditorSession.getValue() ;
        let currentBytes = currentContent.length ;
        if ( currentBytes > ( maxSelectedToDisplay + 200 ) ) {
            let removeBytes = currentBytes - maxSelectedToDisplay ;
            console.debug( `currentBytes: ${ currentBytes}, removing: ${removeBytes}` ) ;
            utils.flash( $( "#fileSize", $header ), true ) ;
            aceEditorSession.setValue( currentContent.substring( removeBytes ) ) ;
        } else {
            utils.flash( $( "#fileSize", $header ), false ) ;
        }

        let aceUpdateDelay = 10 ;
        if ( changeSize > ( 500 * 1024 ) ) {
            aceUpdateDelay = 500 ;
        }

        setTimeout( function () {
            // give aceEditor a chance to render content
            schedule_next_tail( dockerOrPodReport ) ;
        }, aceUpdateDelay ) ;


    }

    function formatOutput( fileOutput ) {

        console.debug( `formatOutput()  _autoSelectViewFormat ${_autoSelectViewFormat }` ) ;

        if ( _autoSelectViewFormat ) {
            _autoSelectViewFormat = false ;
            $formatSelection.val( "text" ) ;

            determineParser( fileOutput ) ;

        }
        let currentFormat = $formatSelection.val() ;
        let formattedLogs = fileOutput ;

        if ( currentFormat !== "text"
                || ( _parserThrottleWords && Array.isArray( _parserThrottleWords ) ) ) {

            console.debug( `Parsing using ${ currentFormat }, throttles: `, _parserThrottleWords ) ;

            let logLines = fileOutput.split( `\n` ) ;
            formattedLogs = `` ;

            for ( let logLine of logLines ) {

                if ( logLine.length <= 1 ) {
                    continue ;
                }

                if ( $stripAnsi.is( ":checked" ) || _parserTrimAnsi ) {
//                    console.log(`trim ansi`) ;
                    logLine = logLine.replace( ansiPattern, '' ) ;
                }

                if ( _parserThrottleWords && Array.isArray( _parserThrottleWords ) ) {
                    let throttleLine = false ;
                    for ( let throttleWord of _parserThrottleWords ) {

                        if ( logLine.includes( throttleWord ) ) {

                            _parserThrottleCount++ ;
                            if ( _throttleEnabled ) {
                                //console.log(`_parserThrottleCount ${_parserThrottleCount}, _parserThrottleInterval ${ _parserThrottleInterval}`) ;
                                if ( _parserThrottleCount != 1
                                        && ( _parserThrottleCount % _parserThrottleInterval != 0 ) ) {
                                    //console.log(`hiding ${logLine}`) ;
                                    throttleLine = true ;
                                    break ;
                                }
                                //logLine = logLine.replace( throttleWord, throttleWord + "(*throttled)" ) ;
                            }

                        }

                    }
                    if ( throttleLine ) {
                        continue ;
                    }

                }
                let formatedLine = formatLine( logLine, currentFormat ) ;
                //alert( formatedLine )
                if ( formatedLine.length > 0 ) {
                    formattedLogs += formatedLine ;
                }

            }

            if ( _parserThrottleCount > 0 ) {
                let desc = "disabled" ;
                if ( _throttleEnabled ) {
                    desc = "enabled" ;
                }
                $clearFilterButton.text( `${ _parserThrottleCount } lines, filters: ${desc}` )
                $clearFilterButton.show() ;
            }

        } else {


            if ( $stripAnsi.is( ":checked" ) ) {
                console.log( `stripping ansi` ) ;
                formattedLogs = formattedLogs.replace( ansiPattern, '' ) ;
            }

            if ( _isAutoAssignTheme ) {
                let theme = "ace/theme/katzenmilch" ; // ace/theme/merbivore  ace/theme/tomorrow
                if ( isCsapDeployFileSelected() ) {
                    theme = "ace/theme/terminal" // merbivore tomorrow_night katzenmilch cobalt chaos
                }
                $editorTheme.val( theme ) ;
                $editorTheme.trigger( "change" ) ;
            }
        }

        return formattedLogs ;
    }

    function isCsapDeployFileSelected() {

        return $fileSelection.val().startsWith( "__working__" )
                || $fileSelection.val().startsWith( "__root__/opt/csap/csap-platform/working" ) ;

    }

    function checkApplicationLogTemplates( selectedFilePath, outputLines ) {

        if ( isCsapDeployFileSelected() ) {
            return false ;
        }

        let parsers = utils.getLogParsers() ;
        _parserNewLineWords = null ;
        _parserColumnCount = null ;
        _parserColumnOrder = null ;
        _parserThrottleWords = null ;
        _parserTrimAnsi = null ;

        function checkParsers( parserSourcePath ) {
            for ( let parser of parsers ) {
                if ( parserSourcePath.match( parser.match ) ) {
                    $logDefinition.val( parser.match ) ;
                    console.log( `checkLogParsers() - match: '${ parser.match }', source: '${ parserSourcePath  }' ` ) ;
                    _parserColumnOrder = parser.columns ;
                    _parserColumnCount = largestColumnInteger( _parserColumnOrder ) ;
                    _parserNewLineWords = parser.newLineWords ;
                    _parserThrottleWords = parser.throttleWords ;
                    _parserTrimAnsi = parser.trimAnsi ;
                    $( "#log-column-display", $options ).val( joinCsv( _parserColumnOrder ) ) ;
                    if ( _parserNewLineWords ) {
                        $( "#log-column-keywords", $options ).val( _parserNewLineWords.join( ',' ) ) ;
                    }
                    if ( _parserThrottleWords ) {
                        $( "#log-throttle-words", $options ).val( _parserThrottleWords.join( ',' ) ) ;
                    }
                    return true ;
                }
            }
            return false ;
        }
        if ( checkParsers( selectedFilePath ) ) {
            return true ;
        }

        // hardcode netmet log4j
        if ( outputLines.length > 3 ) {

            let linesTested = 0 ;
            let numColMatches = 0 ;
            for ( let testLine of outputLines ) {

                if ( ++linesTested > 30 ) {
                    break ;
                }

                let fields = utils.stringSplitWithRemainder( testLine, " ", 10 ) ;
                console.debug( `fields.length ${fields.length }` )
                //alert( fields) 
                if ( fields.length == 10
                        && fields[8] === ":" ) {

                    numColMatches++ ;
                    if ( numColMatches > 3 ) {
                        console.log( `checkLogParsers() - hardcoded netmet-field8Colon` ) ;
                        return  checkParsers( "netmet-field8Colon" ) ;
                    }
                } else if ( fields.length == 10
                        && fields[5] === ":" ) {

                    numColMatches++ ;
                    if ( numColMatches > 3 ) {
                        console.log( `checkLogParsers() - hardcoded matcher crni-field6Colon` ) ;
                        return  checkParsers( "crni-field6Colon" ) ;
                    }
                } else if ( fields.length == 10
                        && fields[8] === "|~" ) {

                    numColMatches++ ;
                    if ( numColMatches > 3 ) {
                        console.log( `checkLogParsers() - hardcoded matcher crni-field9Tilde` ) ;
                        return  checkParsers( "crni-field9Tilde" ) ;
                    }
                } else if ( fields.length == 10
                        && fields[4] === "---" ) {

                    numColMatches++ ;
                    if ( numColMatches > 3 ) {
                        console.log( `checkLogParsers() - hardcoded matcher crni-field5Dash` ) ;
                        return  checkParsers( "crni-field5Dash" ) ;
                    }
                } else if ( fields.length == 10
                        && fields[5] === "---" ) {

                    numColMatches++ ;
                    if ( numColMatches > 3 ) {
                        console.log( `checkLogParsers() - hardcoded matcher netmet-field6Dash` ) ;
                        return  checkParsers( "netmet-field6Dash" ) ;
                    }
                } else {
                    console.debug( `skipping: \n'${ testLine}' ` ) ;
                }
            }
        }

        return false ;

    }

    function largestColumnInteger( columns ) {
        let maxColumn = 0 ;
        for ( let column of columns ) {
            let columnInt = Number.parseInt( column ) ;
            if ( !isNaN( columnInt )
                    && columnInt > maxColumn ) {
                maxColumn = columnInt ;
            }

        }

        return maxColumn ;
    }

    function determineParser( fileOutput ) {
        let selectedFilePath = $fileSelection.val() ;
        let outputLines = fileOutput.split( `\n` ) ;

        // defaults
        $formatSelection.val( "simple" ) ;

        console.log( `determineParser() selectedFilePath: ${ selectedFilePath }` ) ;

        if ( isCsapDeployFileSelected() ) {
            $formatSelection.val( "text" ) ;
        }

        if ( checkApplicationLogTemplates( selectedFilePath, outputLines ) ) {
            console.log( `Found a matching column parser` ) ;
            $formatSelection.val( "columns" ) ;
            $( ".column-options", $options ).show() ;

        } else if ( selectedFilePath.endsWith( "kubelet" ) ) {
            $formatSelection.val( "kubelet" ) ;

//            } else if ( source.endsWith( "netmet-log4j.log" ) ) {
//                $formatSelection.val( "columns" ) ;

        } else {

            if ( outputLines.length > 3 ) {

                let linesTested = 0 ;
                for ( let testLine of outputLines ) {

                    if ( testLine.length === 0
                            || testLine.charAt( 0 ) === " "
                            || testLine.charAt( 0 ) === "\n"
                            || testLine.charAt( 0 ) === "\t" ) {
                        continue ;
                    }

                    if ( ++linesTested > 10 ) {
                        break ;
                    }
                    console.debug( `formatFileLines: test ${ linesTested }: \n'${ testLine }'` ) ;

                    if ( jsonPattern.test( testLine ) ) {
                        try {
                            let lineAsJson = JSON.parse( testLine ) ;
                            console.debug( `formatFileLines: test line parsed json: '${ testLine }'` ) ;
                            $formatSelection.val( "yaml" ) ;

                            if ( lineAsJson.loggerFqcn ) {
                                $formatSelection.val( "log4j" ) ;

                            } else if ( lineAsJson.t && lineAsJson.s ) {
                                $formatSelection.val( "mongo" ) ;

                            } else if ( lineAsJson.tags
                                    && lineAsJson["@timestamp"]
                                    && lineAsJson.type ) {
                                $formatSelection.val( "kibana" ) ;

                            } else if ( lineAsJson.type
                                    && lineAsJson.component
                                    && lineAsJson["node.name"] ) {
                                $formatSelection.val( "elastic" ) ;

                            } else {
                                //alert( "setting mode to yaml" ) ;
                                //activeEditor().getSession().setMode( "ace/mode/yaml" ) ;
                                $languageMode.val( "ace/mode/yaml" ) ;
                                $languageMode.trigger( "change" ) ;
                            }
                            break ;  // quit on success
                        } catch ( err ) {
                            console.warn( `Failed parsing json line: '${ testLine}'`, err ) ;
                        }
                    }
                }
            } else {
                console.log( `outputLines.length: ${outputLines.length}` ) ;

            }
        }
    }

    function formatLine( logLine, currentFormat ) {

        let outputLineSeparator = "\n" ;

        if ( currentFormat === "simple" ) {

            logLine = logFormatters.simpleFormatter( logLine ) ;

        } else if ( currentFormat === "kubelet" ) {
            //
            // format 1: typical
            // Mar 22 09:41:42 csap-dev07.***REMOVED*** kubelet[650075]: E0322 09:41:42.876820  650075 reflector.go:138] object-"kube-system"/"kubernetes-services-endpoint":
            // format 2: wrapped
            // Mar 31 09:00:36 csap-dev05.***REMOVED*** kubelet[8146]: 2021-03-31 09:00:36.536 [INFO][19709] k8s.go 569: Teardown processing complete. ContainerID="xxx"
            // fromat 3:
            // Mar 31 09:00:36 csap-dev05.***REMOVED*** kubelet[8146]: time="2021-03-31T09:00:36-04:00" level=info msg="Released host-wide IPAM lock." source="ipam_plugin.go:369"
            //
            // Mar 31 15:35:55 csap-dev07.***REMOVED*** systemd[1]: Stopping kubelet: The Kubernetes Node Agent...

            outputLineSeparator = "\n\n---" ;

            try {
                let level = " \n" ;
                let messageIndex = logLine.indexOf( "]:" ) ;

                let headerFields = [ ] ;
                if ( messageIndex > 0
                        && messageIndex < ( logLine.length - 5 ) ) {
                    messageIndex = messageIndex + 2 ; // advance to next word
                    headerFields = utils.stringSplitWithRemainder( logLine.substr( 0, messageIndex ), " ", 5 ) ;
                }

                if ( headerFields.length === 5 ) {

                    let message = logLine.substr( messageIndex ) ;
                    let mfieldCount = 6 ;
                    let messageFields = utils.stringSplitWithRemainder( message, " ", mfieldCount ) ;
                    //console.log(messageFields)
                    //alert(messageFields)

                    let source = headerFields[ headerFields.length - 1 ] ;

                    if ( messageFields.length == mfieldCount
                            && messageFields[3].endsWith( "]" ) ) {

                        let sourceField = messageFields[3] ;
                        source = sourceField.replaceAll( "]", "" ) ;

                        let msgOffset = message.indexOf( sourceField ) + sourceField.length + 1 ;
                        message = message.substr( msgOffset ) ;
                    }


                    let newLineWords = [ "on node", "name:", "Container ID:", "usage:",
                        "InnerVolumeSpecName", "PluginName", "VolumeGidValue", "(UniqueName:",
                        "ContainerID", "Workload", "HandleID", "host=", "cidr=", "handle=",
                        "skipping:", "Failed", "Get ", "Error:", "request:",
                        "failed:", "CrashLoopBackOff" ] ;
                    for ( keyword of newLineWords ) {
                        message = message.replaceAll( `${ " " + keyword }`, `\n${ padLeft( keyword )} ` ) ;
                    }

                    if ( messageFields.length == mfieldCount ) {
                        let levelField = messageFields[0] ;

                        let format2WrappedFields = messageFields[2].split( "]" ) ;
                        //console.log( messageFields ) ;
                        if ( messageFields[1].includes( "level=" ) ) {
                            //console.log(`format 3 field=value: ${ message }`) ;
                            level = messageFields[1] + "\n" ;
                            let msgOffset = message.indexOf( "msg=" ) ;
                            if ( msgOffset > 0 ) {
                                message = message.substr( msgOffset + 5 ) ;
                                let messSource = message.split( "source\=" ) ;
                                if ( messSource.length > 2 ) {
                                    message = messSource[0] ;
                                    source = messSource[1] ;
                                }
                            }

                        } else if ( levelField.length == "W0331".length ) {
                            //console.log( levelField )
                            if ( levelField.startsWith( "E" )
                                    || message.includes( "rror" ) ) {
                                level = " level.ERROR\n" ;
                            } else if ( levelField.startsWith( "W" ) ) {
                                level = " level.WARNING\n" ;
                            } else if ( levelField.startsWith( "I" ) ) {
                                level = " level.INFO\n" ;
                            } else {
                                level = levelField ;
                            }

                        } else if ( format2WrappedFields.length > 2 ) {
                            level = format2WrappedFields[0].replaceAll( "[", "" ) + format2WrappedFields + "\n" ;
                            source = `${ messageFields[3] }:${ messageFields[4].replaceAll( ":", "" )  }` ;
                            let msgOffset = message.indexOf( messageFields[4] ) + messageFields[4].length + 1 ;
                            message = message.substr( msgOffset ) ;
                        } else {
                            level = "level-not-parsed\n" ;
                        }

                        logLine = `${ padLeft( "time:" )} ${ headerFields[0] } ${ headerFields[1] } ${ headerFields[2] }`
                                + `\n${ padLeft( "source:" )} ${ source }  \n${ padLeft( "message:" )} ${ message }` ;

                    } else {
                        level = "line-not-parsed\n"
                        logLine = `  ${ logLine }` ;
                    }

                } else {
                    level = "header-not-parsed\n"
                    logLine = `  ${ logLine }` ;
                }
                outputLineSeparator += ` # ${ level }` ;
            } catch ( err ) {
                console.warn( `Failed parsing kubelet columns: \n'${ logLine}' `, err ) ;
            }
            //logLine = yaml.dump( lineAsJson ) ;
        } else if ( currentFormat === "columns" ) {

            $( ".column-options", $options ).show() ;

            let colsToDisplay = _parserColumnOrder ;
            let numColumns = _parserColumnCount ;
            outputLineSeparator = "\n---" ;

            let fields = utils.stringSplitWithRemainder( logLine, " ", numColumns ) ;
            // console.log(`numColumns: ${numColumns} fields: ${fields.length}`) ;
            if ( fields.length == ( numColumns ) ) {
                // 2021-03-22 17:34:02.482  WARN [ingestion-towers-allinone,f1f4d78109c64bbb,224ef947a2ccb184,false] 1 --- [fka-poll-thread] c.s.n.i.t.h.GlobalSystemCountersHandler  : Global counter has a null timestamp, using the tgbToi 1616434441176
                //logLine = `${ fields[0] } ${ fields[1] } ${ fields[2] }  ${ fields[3] } \n ${ fields[ numColumns ] }` ;
                let composedLine = "" ;
                try {

                    for ( let index = 0 ; index < colsToDisplay.length ; index++ ) {

                        let columnSelected = colsToDisplay[ index ] ;
                        let separator = " " ;

                        let columnIndex = Number.parseInt( columnSelected ) ;
                        if ( !isNaN( columnIndex ) ) {

                            let arrayIndex = columnIndex - 1 ;
                            columnSelected = fields[ arrayIndex ] ;


                            //
                            //  Final column typically contains message body: apply keywords and throttles
                            //
                            if ( columnIndex == numColumns ) {

                                if ( columnSelected.startsWith( 'msg="' ) ) {

                                    try {
                                        let dockerFields = columnSelected.match( /(".*?"|[^" \s]+)(?=\s* |\s*$)/g ) ;

                                        if ( dockerFields.length > 0 ) {
                                            let dockerLine = dockerFields[0] + "\n\t\t" ;
                                            let restOfLineOffset = columnSelected.indexOf( dockerFields[0] ) + dockerFields[0].length + 1 ;
                                            if ( restOfLineOffset < columnSelected.length ) {
                                                dockerLine += columnSelected.substr( restOfLineOffset ) ;
                                            }

                                            columnSelected = dockerLine.replaceAll( "=", ": " )
                                        }
                                    } catch ( err ) {
                                        console.warn( `Failed parsing docker: \n'${ logLine}' `, err ) ;
                                    }
                                }

                                // breaks are bypassed when docker msg= is detected
                                if ( _parserNewLineWords && Array.isArray( _parserNewLineWords ) ) {

                                    for ( let keyword of _parserNewLineWords ) {
                                        columnSelected = columnSelected.replaceAll( `${ keyword }`, `\n${ padLeft( keyword ) }` ) ;
                                    }
                                }



                            }

                        } else {
                            //console.log( `columnSelected: '${columnSelected}'` ) ;
                            columnSelected = columnSelected.replaceAll( "\\n", "\n" ) ;
                            if ( columnSelected.endsWith( ":" ) ) {
                                columnSelected = "\n" + padLeft( columnSelected ) ;
                            }
                        }

                        composedLine += columnSelected + separator ;
                    }

                    if ( $( "#log-column-all", $options ).is( ":checked" ) ) {
                        for ( let index = 0 ; index < fields.length ; index++ ) {
                            let column = `${ index + 1}: ${ fields[ index] }` ;
                            if ( column.length < 25 ) {
                                column = column.padEnd( 25, ' ' ) ;
                                if ( ( index + 1 ) % 4 == 0 ) {
                                    column += "\n" ;
                                }
                            } else {
                                column = "\n" + column ;
                            }
                            composedLine += column ;
                        }
                        composedLine += "\n\n" ;
                    }
                    logLine = composedLine ;
                } catch ( err ) {
                    console.warn( `Failed parsing columns: \n'${ logLine}' `, err ) ;
                }
            } else {
                outputLineSeparator = "\n" ;
            }


        } else if ( jsonPattern.test( logLine ) ) {
            outputLineSeparator = "\n" ;
            try {
                let lineAsJson = JSON.parse( logLine ) ;
                if ( currentFormat === "log4j" ) {
                    logLine = logFormatters.formatLog4j2Output( lineAsJson ) ;
                    if ( !logLine ) {
                        return "" ;
                    }

                } else if ( currentFormat === "log4jDetails" ) {
                    logLine = logFormatters.formatLog4j2Output( lineAsJson, true ) ;
                    if ( !logLine ) {
                        return "" ;
                    }

                } else if ( currentFormat === "mongo" ) {
                    //alert("mongo format") ;
                    logLine = formatMongoJsonLine( lineAsJson ) ;
                    if ( !logLine ) {
                        return "" ;
                    }

                } else if ( currentFormat === "kibana" ) {
                    //alert("mongo format") ;
                    logLine = formatKibanaJsonLine( lineAsJson ) ;
                    if ( !logLine ) {
                        return "" ;
                    }

                } else if ( currentFormat === "elastic" ) {
                    //alert("mongo format") ;
                    logLine = formatElasticSearchJsonLine( lineAsJson ) ;
                    if ( !logLine ) {
                        return "" ;
                    }

                } else if ( currentFormat === "yaml" ) {
                    outputLineSeparator = "\n---\n" ;
                    logLine = yaml.dump( lineAsJson ) ;
                } else {

                    logLine = JSON.stringify(
                            lineAsJson,
                            null, 2 ) ; // spacing level = 2
                }
            } catch ( err ) {
                console.warn( `Failed parsing json line: \n'${ logLine}' `, err ) ;
            }
        } else {
            outputLineSeparator = "\n" ;
            console.debug( `skipping: \n'${ logLine}' ` ) ;
        }

        return outputLineSeparator + logLine ;

    }




    function padLeft( message ) {
        if ( !message.endsWith( ":" ) ) {
            return  " ".padStart( logPadLeft ) + " " + message ;
        } else {
            return  message.padStart( logPadLeft ) ;
        }
    }



    function formatElasticSearchJsonLine( lineAsJson ) {

        let date = lineAsJson.timestamp ;
        let subsystem = `[${ lineAsJson.type}]`.padEnd( 15 ) + `[${ lineAsJson[ "node.name" ] }] ${ lineAsJson[ "cluster.name" ] }` ;
        let logLevel = lineAsJson.level ;


        let message = lineAsJson.message ;
        let logOutput = `\n--- # ${ logLevel }  \n`
                + `${ padLeft( "time:" )} ${ date } \n`
                + `${ padLeft( "source:" )} ${ subsystem }\n`
                + `${ padLeft( "message:" )} ${ message }\n` ;

        if ( logLevel.toUpperCase() === "ERROR"
                || logLevel.toUpperCase() === "WARN" ) {

            // always print 
            logOutput += logFormatters.dumpAndIndentAsYaml( lineAsJson ) ;
            return logOutput ;

        }
        // _parserThrottleInterval, _parserThrottleCount

//        if ( lineAsJson.type === "response" ) {
//            _parserThrottleCount++ ;
//
//
//            if ( _throttleEnabled ) {
//
//                if ( _parserThrottleCount % _parserThrottleInterval !== 0 ) {
//                    // continue ;
//                    return null ;
//                }
//                logOutput += padLeft( `*log-throttled ${_parserThrottleInterval}` ) ;
//            }
//
//
//        }

        return logOutput ;
    }

    function formatKibanaJsonLine( lineAsJson ) {

        let date = lineAsJson["@timestamp"] ;
        let subsystem = "" ;
        let logLevel = "" ;
        if ( Array.isArray( lineAsJson.tags ) ) {
            if ( lineAsJson.tags.length > 0 ) {
                logLevel = lineAsJson.tags[0] ;
                lineAsJson.tags.shift() ;

                for ( let tag of lineAsJson.tags ) {
                    subsystem += `[${ tag }]`.padEnd( 15 ) + " "
                }
                //subsystem = lineAsJson.tags.join() ;

            }
        }


        let message = lineAsJson.message ;
        let logOutput = `\n--- # ${ logLevel }  \n`
                + `${ padLeft( "time:" )} ${ date } \n`
                + `${ padLeft( "tags:" )} ${ subsystem }\n`
                + `${ padLeft( "message:" )} ${ message }\n` ;

        if ( logLevel.toUpperCase() === "ERROR"
                || logLevel.toUpperCase() === "WARNING" ) {

            // always print 
            logOutput += logFormatters.dumpAndIndentAsYaml( lineAsJson ) ;
            return logOutput ;

        }
        // _parserThrottleInterval, _parserThrottleCount

        if ( lineAsJson.type === "response" ) {
            _parserThrottleCount++ ;


            if ( _throttleEnabled ) {

                if ( _parserThrottleCount % _parserThrottleInterval !== 0 ) {
                    // continue ;
                    return null ;
                }
                logOutput += padLeft( `*log-throttled ${_parserThrottleInterval}` ) ;
            }


        }

        return logOutput ;
    }

    function formatMongoJsonLine( lineAsJson ) {

        let date = lineAsJson["t"]["$date"] ;
        let subsystem = lineAsJson.c ;
        if ( !subsystem ) {
            subsystem = "unknown" ;
        }
        let logLevel = lineAsJson.s ;
        if ( !logLevel ) {
            logLevel = "unknown" ;
        }

        if ( logLevelMapping[ logLevel ] ) {
            logLevel = logLevelMapping[ logLevel ] ;
        }

        let message = lineAsJson.msg ;
        let mongoLine = `\n--- # ${ logLevel }  \n`
                + `${ padLeft( "time:" )} ${ date } \n`
                + `${ padLeft( "source:" )} ${ subsystem }\n`
                + `${ padLeft( "message:" )} ${ message }\n` ;

        if ( logLevel === "ERROR"
                || logLevel === "WARN" ) {

            // always print 
            mongoLine += logFormatters.dumpAndIndentAsYaml( lineAsJson ) ;
            return mongoLine ;

        }
        // _parserThrottleInterval, _parserThrottleCount

        if ( subsystem === "NETWORK"
                || subsystem === "ACCESS" ) {
            _parserThrottleCount++ ;


            if ( _throttleEnabled ) {

                if ( _parserThrottleCount % _parserThrottleInterval !== 0 ) {
                    // continue ;
                    return null ;
                }
                mongoLine += padLeft( `*log-throttled ${_parserThrottleInterval}` ) ;
            }


        }

        if ( lineAsJson.attr ) {

            let attributes = lineAsJson.attr ;

            if ( attributes.durationMillis ) {
                mongoLine += `${padLeft( "duration:" )} ${ attributes.durationMillis}ms\n`
            }

            if ( attributes.planSummary ) {
                mongoLine += `${padLeft( "planSummary:" )} ${ attributes.planSummary}\n`
            }

            if ( ( subsystem === "COMMAND"
                    || message === "Slow query" )
                    && attributes.command ) {

                mongoLine += logFormatters.dumpAndIndentAsYaml( attributes.command ) ;

            }
            if ( subsystem === "CONTROL" ) {
                mongoLine += logFormatters.dumpAndIndentAsYaml( attributes ) ;

            } else if ( attributes.message ) {
                mongoLine += logFormatters.dumpAndIndentAsYaml( attributes.message ) ;
            }

            if ( attributes.planSummary ) {
                mongoLine += logFormatters.dumpAndIndentAsYaml( attributes.planSummary ) ;
            }
        }


        return mongoLine ;
    }

    function  processLogReport( dockerOrPodLogReport ) {


        if ( dockerOrPodLogReport.error ) {

            if ( dockerOrPodLogReport.error ) {
                //$htmlViewer.append( '<span class="error">' + changesJson.error + "</span>" );


                let message = dockerOrPodLogReport.error ;

                message += ", reason: \n\n" ;

                if ( dockerOrPodLogReport.reason ) {
                    message += ", reason: \n\n" + dockerOrPodLogReport.reason ;
                }


                activeEditor().setValue( message ) ;
                utils.loadingComplete() ;


                if ( message.includes( "Try the root tail option" ) ) {
                    let quickUrl = utils.getFileUrl() + "/downloadFile/quickView" ;
                    let inputMap = {
                        fromFolder: $fileSelection.val(),
                        serviceName: serviceFullName,
                        "browseId": "",
                        forceText: true
                    } ;

                    $.post( quickUrl, inputMap )

                            .done( function ( fileContents ) {
                                //console.log( `inlineEdit{} `, fileContents ) ;
                                let firstLineOffset = fileContents.indexOf( "\n" ) + 1 ;
                                if ( firstLineOffset > 10 ) {
                                    fileContents = fileContents.substring( firstLineOffset ) ;
                                }
                                activeEditor().setValue(
                                        "NFS detected: scrolling disabled. Click on logs in left nav to refresh\n"
                                        + "----------------------------------------------------------------------\n"
                                        + fileContents ) ;

                            } )

                            .fail( function ( jqXHR, textStatus, errorThrown ) {
                                alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact support" ) ;
                            }, 'json' ) ;

                }
                return ;
            }

            console.log( "No results found, rescheduling" ) ;
            checkForChangesTimer = setTimeout( latest_changes_request,
                    2000 ) ;
            return ;
        }




//        if ( _autoSelectViewFormat ) {
//            utils.loading( "Processing content" ) ;
//        }

        add_content_to_viewer( dockerOrPodLogReport ) ;




    }

    function isNumber( o ) {
        return Object.prototype.toString.call( o ) == '[object Number]' ;
    }

    function schedule_next_tail( changeReport ) {

        //alert(`got here`) ;

        if ( !$container.is( ":visible" ) ) {
            console.debug( "container hidden" ) ;
//            console.log( ` Pausing tail until container is visible` ) ;
//            return ;
        }
        _fileByteOffset = changeReport.newOffset ;

        let currentSize = activeEditor().getSession().getValue().length ;
//        let docSize = "";
//        if ( isNumber( changeReport.currLength ) 
//                && ( Math.abs(changeReport.currLength - currentSize) > 10 )) {
//            console.log(`changeReport.currLength ${changeReport.currLength}, currentSize: ${ currentSize }`) ;
//            docSize = ` of ${ utils.bytesFriendlyDisplay( changeReport.currLength ) } `;
//        }

        let notes ;
        if ( changeReport.lastPosition ) {
            notes = `Source file size: ${ utils.bytesFriendlyDisplay( changeReport.lastPosition )}` ;
        } else if ( changeReport.since ) {
            notes = `Latest logs from: ${ changeReport.since }` ;
        }


        $( "#fileSize", $header )
                .attr( "title", notes )
                .text( utils.bytesFriendlyDisplay( currentSize ) ) ;

        let refreshTimer = $( "#refreshSelect" ).val() * 1000 ;

        if ( changeReport.newOffset != changeReport.currLength ) {
            refreshTimer = 50 ;
            utils.loading( `Loading ${ utils.bytesFriendlyDisplay( changeReport.newOffset ) }`
                    + ` of ${ utils.bytesFriendlyDisplay( changeReport.currLength ) }` ) ;
            //$( "#fileSize", $header ).html(  utils.bytesFriendlyDisplay (changeReport.lastPosition) ) ;
        } else {
            let status = $( "#fileSize", $header ).text() ;
            utils.loadingComplete( `schedule_next_tail ${ status}` ) ;
//            if ( status.length > 1 &&  !status.includes("loading") ) {
//                setTimeout( function () {
//                    // delayed in case large file is loading and hogging thread
//                    utils.loadingComplete( `schedule_next_tail ${ status}` ) ;
//                }, 500 ) ;
//            }
        }

        checkForChangesTimer = setTimeout( function () {
            latest_changes_request()
        }, refreshTimer ) ;
    }



} ) ;