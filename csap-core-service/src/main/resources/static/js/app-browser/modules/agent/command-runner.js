

define( [ "browser/utils", "ace/ace", "agent/command-template", "agent/command-hosts" ], function ( utils, aceEditor, templates, hostSelection ) {

    console.log( "Module loaded" ) ;
    const $scriptsPanel = $( "#agent-tab-content #agent-tab-script" ) ;
    const $configurationPanel = $( "#command-header", $scriptsPanel ) ;

    const $resultsContainer = $( "#agent-tab-content #agent-tab-command-output #resultsContainer" ) ;
    const $commandOutputHeader = $( "#command-output-header", $resultsContainer ) ;
    const $resultsPanel = $( "#command-output-results", $resultsContainer ) ;
    const $resultTableContainer = $( ">div", $resultsPanel ) ;
    const $resultsTextContainer = $( ">textarea", $resultsPanel ) ;


    const $resultsAceViewer = $( ">pre", $resultsPanel ) ;
    let _resultsAceViewer ;



    const $outputNav = utils.findNavigation( `#command-output-nav` ) ;

    const SUMMARY_OUTPUT = "summary-output" ;
    const RAW_OUTPUT = "raw" ;
    const $outputSelector = $( "#results-select", $commandOutputHeader ) ;
    const $scrollCheckbox = $( "#scroll-command-results", $commandOutputHeader ) ;
    const $resultEditorFoldCheckbox = $( "#fold-command-results", $commandOutputHeader ) ;
    const $wrapCheckbox = $( "#wrap-command-results", $commandOutputHeader ) ;

    const $commandTimeout = $( "#command-timeout", $configurationPanel ) ;

    const $hideOutput = $( "#hide-output", $commandOutputHeader ) ;
    const $downloadOutput = $( "#download-output", $commandOutputHeader ) ;
    const $cancelButton = $( "#cancel-command", $commandOutputHeader ) ;


    const $aceSettings = $( "#command-header #ace-editor-settings", $scriptsPanel ) ;
    const $aceFoldCheckbox = $( "#ace-fold-checkbox", $aceSettings, $scriptsPanel ) ;


    let _command_editor ;

    let $menuLoaded ;

    let command ;
    let template = "linux-uptime.sh" ;
    let serviceName = "no-service" ;
    let pid = "no-pids" ;
    let fromFolder = "no-folder" ;

    let loadUsingScriptLocation = true ;


    let fileOffset = "-1" ;
    let isLogFile = false ;



    let aceEditorId = "ace-editor" ;

    let checkForChangesTimer = null ;

    let SCRIPT_BASE = "__platform__/saved/scripts-run/" ;
    let FROM_BASE = SCRIPT_BASE + utils.getCsapUser() ;

    // used for progress on single host scripts
    let XFER_BASE = SCRIPT_BASE + "xfer_" ;




//    $( document ).ready( function () {
//
//        CsapCommon.configureCsapAlertify() ;
//        initialize() ;
//
//        $resultSelect 
//
//    } ) ;

    let _refreshTimer ;
    let $templateFilter ;

    return {
        show: function ( $menuContent, forceHostRefresh, menuPath ) {


            command = menuPath ;

            if ( utils.getPageParameters().has( "template" ) ) {
                let templateParam = utils.getPageParameters().get( "template" ) ;
                if ( templateParam != template ) {
                    template = templateParam ;
                }
            }

            if ( utils.getPageParameters().has( "serviceName" ) ) {
                let serviceNameParam = utils.getPageParameters().get( "serviceName" ) ;
                if ( serviceNameParam != serviceName ) {
                    serviceName = serviceNameParam ;
                }
            }

            if ( utils.getPageParameters().has( "pid" ) ) {
                let pidParam = utils.getPageParameters().get( "pid" ) ;
                if ( pidParam != pid ) {
                    pid = pidParam ;
                }
            }

            if ( utils.getPageParameters().has( "fromFolder" ) ) {
                let fromParam = utils.getPageParameters().get( "fromFolder" ) ;
                if ( fromParam != fromFolder ) {
                    fromFolder = fromParam ;
                }
            }

            if ( utils.getPageParameters().has( "scriptRefresh" ) ) {
                utils.getPageParameters().delete( "scriptRefresh" ) ;
                forceHostRefresh = true ;
                loadUsingScriptLocation = true ;
            }

            if ( !_command_editor || forceHostRefresh ) {

                $menuLoaded = new $.Deferred() ;
                getHostConfiguration() ;
            } else {
                // leave display along - editing may have occured
            }

            return $menuLoaded ;

        }
    } ;


    function getHostConfiguration() {

        console.log( "getHostConfiguration: " ) ;

        $.getJSON( utils.getCsapBrowserUrl() + "/host-configuration", {
            fromFolder: utils.getPageParameters().get( "fromFolder" )
        } )
                .done( function ( configurationReport ) {
                    console.log( `Got Report` )
                    initialize( configurationReport ) ;


                } ) ;


    }

    function initialize( configurationReport ) {



        updateJobId() ;

        hostSelection.initialize( configurationReport ) ;

        $hideOutput.off().click( function () {
            utils.launchMenu( "agent-tab,script" )
            $outputNav.hide() ;
            return false ;
        } ) ;

        //$aceFoldCheckbox.prop( "checked", true ) ;
        $aceFoldCheckbox.off().change( updateCodeFolding ) ;



        $templateFilter = $( "#command-table-filter input" ) ;

        $templateFilter.off().keyup( function () {
            console.log( "Applying template filter" ) ;
            clearTimeout( _refreshTimer ) ;
            _refreshTimer = setTimeout( function () {
                applyFilter() ;
            }, 500 ) ;
        } ) ;



        $( "#resultsTable" ).tablesorter( {
            sortList: [ [ 0, 0 ] ],
            theme: 'csapSummary'
        } ) ;

        $( "#initRadio" ).prop( 'checked', true ) ;


        build_ace_editors() ;

        try {
            templates.initialize( configurationReport, _command_editor, updateCodeFolding ) ;
        } catch ( err ) {
            console.error( `Failed to initialize templates`, err )
        }

        $( "#scriptName", $scriptsPanel ).val( utils.getCsapUser() + "-" + configurationReport.locationName ) ;
        if ( loadUsingScriptLocation && configurationReport.fileContents ) {
            loadUsingScriptLocation = false ;
            _command_editor.getSession().setValue( configurationReport.fileContents, -1 ) ;
        } else {
            templates.load( template ) ;
        }
        $menuLoaded.resolve() ;

        $( "#scriptName", $scriptsPanel ).off().change( function () {

            let scriptName = $( this ).val() ;
            console.log( `scriptName updated: ${scriptName } ` ) ;
//            if ( scriptName.includes( "install" ) 
//                    && $commandTimeout.val( ) !== "5m") {
//                $commandTimeout.val( "5m" ) ;
//                alertify.notify("command timeout update to 5 minutes") ;
//                 //$( "#command-timeout", $configurationPanel ).trigger("change") ;
//            }
        } ) ;
        $( "#scriptName", $scriptsPanel ).trigger( "change" ) ;



        $( "#operationSection" ).show() ;

        if ( !isAdmin ) {
            $( "#searchTimeout" ).prop( 'disabled', true ) ;
        }

        $( ".show-root-warning", $configurationPanel ).off().click( function () {
            if ( $( this ).is( ':checked' ) ) {
                alertify.csapInfo( "Caution advised: running as root cannot be reverted except by host reimaging" ) ;
            }
        } ) ;


        $( "#run-command-script", $configurationPanel ).off().click( submit_script ) ;

        $cancelButton.off().click( function () {
            alertify.error( "Cancelling command" ) ;
            $( "#cancelInput", $scriptsPanel ).val( "cancel" ) ; // setthe cancel flag
            $( 'form', $scriptsPanel ).ajaxSubmit( buildFormOptions() ) ;

            return false ;
        } ) ;




    }

    function submit_script() {
        console.log( `Running script` ) ;

        let userTimeout = $commandTimeout.val() ;

        if ( !userTimeout ) {
            console.error( `failed to resolved $commandTimeout. Defaulting to 5m` ) ;
            userTimeout = `5m` ;
        }

        let userSeconds = userTimeout.split( "s" )[0] ;
        if ( userSeconds.endsWith( "m" ) ) {
            userSeconds = userTimeout.split( "m" )[0] * 60 ;
        }
        $( "#form-timeout", $configurationPanel ).val( userSeconds ) ;


        $outputNav.show() ;

        let formOptions = buildFormOptions() ;

        $( "#cancelInput" ).val( "" ) ; // empty out the cancel flag
        updateJobId() ; // empty out the cancel flag

        if ( $( "#executeUserid", $scriptsPanel ).val() == "root" ) {


            let newItemDialog = alertify.confirm( "Validate your content carefully<br><br>In case of errors, submitting root level requests require cases to be opened to recover VM." ) ;

            newItemDialog.setting( {
                title: 'Caution: Root user speciied',
                'labels': {
                    ok: 'Execute',
                    cancel: 'Cancel Request'
                },
                'onok': function () {
                    alertify.success( "Submitting Request" ) ;
                    $( '#script form' ).ajaxSubmit( formOptions ) ;

                },
                'oncancel': function () {
                    alertify.warning( "Cancelled Request" ) ;
                }

            } ) ;



        } else {
            $( 'form', $scriptsPanel ).ajaxSubmit( formOptions ) ;
        }
        return false ;
    }


    function updateJobId() {

        //let jobId = $.now() ;
        let jobId = formatDate( new Date(), '%M.%d-%H.%m.%s' ) ;
        $( "#jobIdInput" ).val( jobId ) ;
    }

    function formatDate( date, fmt ) {
        function pad( value ) {
            return ( value.toString().length < 2 ) ? '0' + value : value ;
        }
        return fmt.replace( /%([a-zA-Z])/g, function ( _, fmtCode ) {
            switch ( fmtCode ) {
                case 'Y':
                    return date.getUTCFullYear() ;
                case 'M':
                    return pad( date.getUTCMonth() + 1 ) ;
                case 'd':
                    return pad( date.getUTCDate() ) ;
                case 'H':
                    return pad( date.getUTCHours() ) ;
                case 'm':
                    return pad( date.getUTCMinutes() ) ;
                case 's':
                    return pad( date.getUTCSeconds() ) ;
                default:
                    throw new Error( 'Unsupported format code: ' + fmtCode ) ;
            }
        } ) ;
    }
    jQuery.expr[':'].icontains = function ( a, i, m ) {
        return jQuery( a ).text().toUpperCase()
                .indexOf( m[3].toUpperCase() ) >= 0 ;
    } ;
    function applyFilter() {

        let $body = $( "#template-body" ) ;
        let $rows = $( 'tr', $body ) ;

        let filter = $templateFilter.val() ;

        console.log( "filter", filter ) ;

        if ( filter.length > 0 ) {
            $rows.hide() ;
            $( 'td:icontains("' + filter + '")', $rows ).parent().show() ;
        } else {
            $rows.show() ;
        }
    }


// http://jquery.malsup.com/form/#file-upload : jquery form: http://api.jquery.com/jQuery.ajax/#options
    function buildFormOptions() {

        let targetHosts = hostSelection.getSelected() ;


        let actionForm = {
            dataType: "json", // json, xml, script

            data: {
                'hosts': targetHosts
            },
            beforeSend: function () {
                formBefore(  ) ;
                $resultEditorFoldCheckbox.prop("checked", false) ;
                $resultEditorFoldCheckbox.parent().css( "opacity", 0.2 ) ;
            },
            uploadProgress: function ( event, position, total, percentComplete ) {
                console.log( `uploadProgress`, event, position, total, percentComplete )
            },
            success: function ( jsonData ) {
                console.log( `success` ) ;
            },
            complete: function ( $xmlHttpRequest ) {
                //console.log( JSON.stringify($xmlHttpRequest) )  ;
                if ( $xmlHttpRequest.status != 200 ) {
                    alertify.csapWarning( $xmlHttpRequest.status + " : "
                            + $xmlHttpRequest.statusText + ": Verify your account is a member of the admin group." ) ;
                }
                formComplete( $xmlHttpRequest.responseJSON )
                $resultEditorFoldCheckbox.parent().css( "opacity", 1.0 ) ;
            }
        } ;

        return actionForm ;
    }

    function formBefore( ) {

        _resultsAceViewer.getSession().setUseWrapMode( $wrapCheckbox.is( ":checked" ) ) ;

        if ( $( "#cancelInput" ).val() == "cancel" ) {
            return ;
        }

        $cancelButton.show() ;
        $( ".commandSection" ).hide() ;

        $outputSelector.empty() ;

        utils.launchMenu( "agent-tab,command-output" ) ;
        addHostResultsSelector( "progress" ) ;
        selectHostResults( "progress" ) ;

        displayResults( "Starting Command...", false ) ;
        $( "#resultsContainer" ).show() ;

        // needto update
        fileOffset = "-1" ;


        let fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log" ;
        let resultsFile = SCRIPT_BASE + fullName ;

        let targetHosts = hostSelection.getSelected() ;
        console.log( "targetHosts.length: " + targetHosts.length ) ;
        if ( targetHosts.length == 1 ) {
            // hook for single host  - progress of command will be shown in progress tab
            resultsFile = XFER_BASE + fullName ;
        }



        console.log( "Monitoring log: " + resultsFile ) ;

        registerSelectorClickEvents() ;
        deploySuccess = false ;
        checkForChangesTimer = setTimeout( function () {
            getChanges( resultsFile ) ;
        }, 2000 ) ;

    }

// http://api.jquery.com/jQuery.ajax/#jqXHR 
    function formComplete( commandResponseObject ) {

        console.log( `job completed` ) ;
        $( "#cancelInput", $commandOutputHeader ).val( "" ) ;
        $cancelButton.hide() ;
        haltChangesRefresh() ;

        $outputSelector.empty() ;

        let otherHostResponses = commandResponseObject.otherHosts ;

        if ( otherHostResponses ) {
            addHostResultsSelector( SUMMARY_OUTPUT ) ;
//            if ( !$( "#separateOutput" ).prop( 'checked' ) ) {
//                addHostResultsSelector( SUMMARY_OUTPUT ) ;
//            }

            for ( let hostResponse of otherHostResponses ) {
                let otherHostName = hostResponse.host ;
                //if ( $( "#separateOutput" ).prop( 'checked' ) ) {
                addHostResultsSelector( otherHostName ) ;
                //}
                let scriptOutputs = utils.json( "transferResults.scriptResults", hostResponse ) ;
                if ( Array.isArray( scriptOutputs ) ) {
                    let totalSize = 0 ;
                    for ( let scriptOutput of scriptOutputs ) {
                        totalSize += scriptOutput.length ;
                    }

                }
            }
        }

        addHostResultsSelector( RAW_OUTPUT ) ;
        registerSelectorClickEvents( commandResponseObject ) ;

        let firstSelected = commandResponseObject.scriptHost ;
        if ( otherHostResponses
                && otherHostResponses.length > 1 ) {
            firstSelected = SUMMARY_OUTPUT ;
        }

        $outputSelector.sortSelect() ;

        setTimeout( function () {
            selectHostResults( firstSelected ) ;
        }, 500 ) ;



        displayResults( JSON.stringify( commandResponseObject, null, "\t" ), false ) ;



    }


    function selectHostResults( name ) {

        $outputSelector.val( name ) ;
        $outputSelector.trigger( "change" ) ;
    }

    function addHostResultsSelector( name ) {

        let label = name + " results" ;

        if ( name == "script" ) {
            label = "Command Editor" ;
        }

        if ( name == SUMMARY_OUTPUT ) {
            label = "all: summary" ;
        }

        jQuery( '<option/>', {
            value: name,
            text: label
        } ).appendTo( $outputSelector ) ;

    }

    function trimOutput( textToTrim ) {


//        let trimShell = new RegExp( ".*___ STAGING:.*\n" ) ;
//
//        let skipLine = textToTrim.search( trimShell ) ;
//        if ( skipLine != -1 ) {
//            textToTrim = textToTrim.substr( skipLine ) ;
//            skipLine = textToTrim.search( new RegExp( "\n" ) ) ;
//            // console.log("skipLine" + skipLine + " hi" +  text.search( new RegExp("hi") ) ) ;
//            textToTrim = textToTrim.substr( skipLine + 1 ) ;
//        }

        let trimNonRoot = new RegExp( "_CSAP_OUTPUT_.*\n" ) ;
        let skipLine = textToTrim.search( trimNonRoot ) ;
        if ( skipLine !== -1 ) {
            textToTrim = textToTrim.substr( skipLine ) ;
            skipLine = textToTrim.search( new RegExp( "\n" ) ) ;
            // console.log("skipLine" + skipLine + " hi" +  text.search( new RegExp("hi") ) ) ;
            if ( skipLine !== -1 ) {
                textToTrim = textToTrim.substr( skipLine + 1 ) ;
            }
        }

        return textToTrim ;
    }

    function registerSelectorClickEvents( commandResponseObject ) {

        $wrapCheckbox.off().change( function () {

            console.log( `wrapping output` ) ;
            _resultsAceViewer.getSession().setUseWrapMode( $wrapCheckbox.is( ":checked" ) ) ;

        } ) ;
        
        
        $resultEditorFoldCheckbox.off().change( function () {
            console.log( "toggling code folds in result editor" ) ;

            if ( $resultEditorFoldCheckbox.is( ':checked' ) ) {
//                _resultsAceViewer.getSession().setMode( "ace/mode/yaml" ) ;
//                setTimeout( function() {
                    _resultsAceViewer.getSession().foldAll( 1 ) ;
//                }, 200)
            } else {
                _resultsAceViewer.getSession().unfold( ) ;
//                setTimeout( function() {
//                    _resultsAceViewer.getSession().setMode( "ace/mode/sh" ) ;
//                }, 200)
                
            }

        } ) ;

        $outputSelector.off().change( function () {

            console.log( `showing output` ) ;

            let selected = $outputSelector.val() ;

            $resultsAceViewer.show() ;
            $resultTableContainer.hide() ;


            if ( selected == "script" ) {
                // nop

            } else if ( selected == "progress" ) {
                $( ".commandSection" ).hide() ;
                $( "#resultsContainer" ).show() ;

            } else if ( selected == SUMMARY_OUTPUT ) {

                showOutputInTableFormat( commandResponseObject )

            } else if ( selected == RAW_OUTPUT ) {
                $( ".commandSection" ).hide() ;
                $( "#resultsContainer" ).show() ;
                displayResults( JSON.stringify( commandResponseObject, null, "\t" ), false ) ;

            } else {
                showOutputInTextArea( commandResponseObject, selected ) ;
            }
        } ) ;
    }

    function showOutputInTableFormat( commandResponseObject ) {
        $( ".commandSection" ).hide() ;
        $resultsPanel.parent().show() ;
        $resultTableContainer.show() ;
        $resultsAceViewer.hide() ;
        $( "#resultsTableBody" ).empty() ;

        let numberOfHosts = 1 ;

        for ( let hostResponse of commandResponseObject.otherHosts ) {

            numberOfHosts = commandResponseObject.otherHosts.length ;
            let hostCommandResult = "" ;
            if ( hostResponse.error != undefined ) {
                hostCommandResult = hostResponse.error ;

            } else {
                let jsonOutput = commandResponseObject.scriptOutput ;
                if ( hostResponse.transferResults ) {
                    jsonOutput = hostResponse.transferResults.scriptResults ;
                    if ( jsonOutput == undefined ) {
                        jsonOutput = hostResponse.transferResults.coreResults ;
                    }

                    if ( jsonOutput == undefined ) {
                        jsonOutput = hostResponse.transferResults ;
                    }
                }

                for ( let line = 0 ; line < jsonOutput.length ; line++ ) {

                    hostCommandResult += trimOutput( jsonOutput[line] ) ;
                }
            }



            let $hostRow = jQuery( '<tr/>', { } ) ;

            jQuery( '<td/>', {
                class: `hostColumn`,
                text: hostResponse.host
            } ).appendTo( $hostRow )


            let maxLinesToShow = Math.round( 80 / numberOfHosts ) ;
            if ( maxLinesToShow < 10 ) {
                maxLinesToShow = 10 ;
            }
            let outputLines = hostCommandResult.split( '\n', maxLinesToShow + 1 ) ;
            if ( outputLines.length > maxLinesToShow ) {
                outputLines[maxLinesToShow] = "\n --- snipped ---" ;
            }
            let firstLines = outputLines.join( "\n" ) ;

            jQuery( '<td/>', {
                class: `outputColumn`,
                text: firstLines
            } ).appendTo( $hostRow )

            buildHostLink( $( ".hostColumn", $hostRow ), hostResponse.host, "" ) ;


            $hostRow.appendTo( "#resultsTableBody" ) ;



        }

        $( "#resultsTable" ).trigger( "update" ) ;

    }

    function showOutputInTextArea( commandResponseObject, hostSelected ) {

        console.log( `showOutputInTextArea: ${ hostSelected } ` ) ;

        // Update UI for command completed....

        $downloadOutput.off().click( function () {
            alertify.notify( "Showing output on host: " + hostSelected ) ;

            let fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log" ;
            let inputMap = {
                fromFolder: XFER_BASE + fullName
            } ;
//            let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostSelected ) ;
            let baseUrl = utils.agentUrl( hostSelected, "" ) ;
            let urlAction = baseUrl + "/file/downloadFile/xfer_" + $( "#scriptName" ).val() + ".log" ;
            postAndRemove( "_blank", urlAction, inputMap ) ;
        } ) ;
        $( ".commandSection" ).hide() ;
        $( "#resultsContainer" ).show() ;
        let jsonOutput = commandResponseObject.scriptOutput ;

        for ( let hostResponse of commandResponseObject.otherHosts ) {
            let otherHostName = hostResponse.host ;
            if ( otherHostName == hostSelected ) {


                if ( hostResponse.error != undefined ) {
                    jsonOutput = hostResponse.error ;

                } else {

                    if ( hostResponse.transferResults ) {
                        jsonOutput = hostResponse.transferResults.coreResults ;
                        if ( hostResponse.transferResults.scriptResults )
                            jsonOutput = hostResponse.transferResults.scriptResults ;
                        if ( hostResponse.transferResults.errors ) {
                            jsonOutput = hostResponse.transferResults.errors ;
                        }
                    } else {
                        jsonOutput = commandResponseObject.scriptOutput ;
                    }
                }
                break ;
            }
        }

        let output = "\n" ;
        for ( let line = 0 ; line < jsonOutput.length ; line++ ) {
            let text = jsonOutput[line] ;
            output += trimOutput( jsonOutput[line] ) ;
        }

        let trimTrailingSpacesRegExp = new RegExp( " *\n", "g" ) ;
        let result = output.replace( trimTrailingSpacesRegExp, "\n" )
        // console.log("result: " + result) ;
        displayResults( result ) ;

    }

    function buildHostLink( $cell, hostName, content ) {

        let linkUrl = utils.agentUrl( hostName, "host-dash" ) ;


        let logLink = jQuery( '<a/>', {
            href: linkUrl,
            class: "simple",
            target: "_blank",
            title: "Open in new window",
            html: hostName
        } ) ;

        $cell.html( logLink ) ;
        $cell.append( content ) ;
    }


    function build_ace_editors() {
        _command_editor = aceEditor.edit( aceEditorId ) ;

        _command_editor.setOptions( utils.getAceDefaults( "ace/mode/sh" ) ) ;

        _command_editor.getSession().on( 'change', function () {
            $( "#scriptText" ).val( _command_editor.getValue() ) ;
            console.log( "content updated" ) ;
        } ) ;

//          _command_editor.on("linkClick", function( e ) {
//            alert( "got here") ;
//            console.log("", e) ;
//        })

        $( "#" + aceEditorId ).show() ;


        _resultsAceViewer = aceEditor.edit( $resultsAceViewer.attr( "id" ) ) ;
        _resultsAceViewer.setOptions( utils.getAceDefaults( "ace/mode/yaml", true ) ) ;
        _resultsAceViewer.setTheme( "ace/theme/tomorrow_night" ) ;

    }

    function updateCodeFolding() {
        console.log( `ace folding: ${ $aceFoldCheckbox.is( ":checked" ) } ` ) ;

        setTimeout( function () {
            if ( $aceFoldCheckbox.is( ':checked' ) ) {
                _command_editor.getSession().foldAll( 1 ) ;
            } else {
                //_yamlEditor.getSession().unfoldAll( 2 ) ;
                _command_editor.getSession().unfold( ) ;
            }
        }, 500 )
    }



    function getChanges( resultsFile ) {

        clearTimeout( checkForChangesTimer ) ;
        // $('#serviceOps').css("display", "inline-block") ;


        // console.log("Hitting Offset: " + fileOffset) ;
        let requestParms = {
            serviceName: AGENT_ID,
            hostName: utils.getHostName(),
            fromFolder: resultsFile,
            bufferSize: 100 * 1024,
            logFileOffset: fileOffset,
            isLogFile: isLogFile
        } ;

        $.getJSON(
                utils.getFileUrl( ) + "/getFileChanges",
                requestParms )

                .done( function ( hostJson ) {
                    getChangesSuccess( hostJson, resultsFile ) ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving changes for file " + fromFolder, errorThrown ) ;
                } ) ;
    }

    function haltChangesRefresh() {
        clearTimeout( checkForChangesTimer ) ;
        checkForChangesTimer = 0 ;
    }

    function  getChangesSuccess( changesJson, resultsFile ) {

        if ( changesJson.error ) {
            console.log( "Failed getting status from host due to:" + changesJson.error ) ;
            console.log( "Retrying..." ) ;

        } else {


            for ( let i = 0 ; i < changesJson.contents.length ; i++ ) {
                let fileChanges = changesJson.contents[i] ;
                displayResults( fileChanges, true ) ;

            }


            fileOffset = changesJson.newOffset ;
            // $("#fileSize").html("File Size:" + changesJson.currLength) ;

        }
        let refreshTimer = 2 * 1000 ;

        checkForChangesTimer = setTimeout( function () {
            getChanges( resultsFile ) ;
        }, refreshTimer ) ;


    }


    function displayResults( results, append ) {

        let aceEditorSession = _resultsAceViewer.getSession() ;

        if ( !append ) {
            aceEditorSession.setValue( "" ) ;
        }
        //$("#resultPre pre").append(results);
        let testDataToFillOutput = "asdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\n"
        testDataToFillOutput = "" ;

        aceEditorSession.insert( {
            row: aceEditorSession.getLength(),
            column: 0
        }, results + testDataToFillOutput ) ;

        if ( append
                && $scrollCheckbox.is( ":checked" ) ) {
            let lineNumber = aceEditorSession.getLength() ;
            try {
                _resultsAceViewer.scrollToLine( lineNumber ) ;
            } catch ( err ) {
                console.error( `Failed scroll attempt`, err )
            }
        }


        $resultsAceViewer.show() ;

    }



} ) ;