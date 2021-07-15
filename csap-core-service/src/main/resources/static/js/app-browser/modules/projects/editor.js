define( [ "projects/env-editor/life-editor", "projects/attribute-editor", "projects/browser", "editor/json-forms", "browser/utils" ], function ( lifeEdit, attributesEditor, defBrowser, jsonForms, utils ) {

    console.log( "Module loadedaa" ) ;

    const $contentPane = $( "#projects-tab-content" ) ;
    const $editorPane = $( "#projects-tab-editor", $contentPane ) ;
    const $definition = $( "#json", $contentPane ) ;
    const $buttonContainer = $( "#definition-operations", $editorPane ) ;

    const $checkinDialog = $( "#ciDiv", $editorPane ) ;


    const $reloadPanel = $( "#reloadDiv", $editorPane ) ;

    let _lastTabSelected ;
    let _lastProjectShown ;
    let $contentLoaded ;

    let newName = "" ; // handle rename
    let intervalInSeconds = 5 ;

    let fileOffset = "-1" ;
    let fromFolder = "" ;
    let isLogFile = false ;
    let checkForChangesTimer = null ;
    const warnRe = new RegExp( "warning", 'gi' ) ;
    const errorRe = new RegExp( "error", 'gi' ) ;
    const infoRe = new RegExp( "info", 'gi' ) ;
    const debugRe = new RegExp( "debug", 'gi' ) ;

    const winHackRegEx = new RegExp( "\r", 'g' ) ;
    const newLineRegEx = new RegExp( "\n", 'g' ) ;
    const _LOADING_MESSAGE = "loading..." ;

    let definitionName ;


    let resultsDialog = null ;
    let lineOnly = false ;
    let validateTimer = 0 ;



    const viewChangeMessage = "Click OK to proceed to the Life Cycle Editor. Any uncommited changes will be lost."
            + "\n\nClick cancel to commit changes first" ;

    return {

        initialize: function () {
            jsonForms.setUtils( utils ) ;
            initialize() ;
        },

        updatePackageMap: function ( packageMap ) {
            defBrowser.updatePackageMap( packageMap ) ;
        },

        show: function ( $menuContent, forceHostRefresh, menuPath ) {


            //if ( menuPath != "editor" ) {

            let forceRefresh = true ;
            _lastTabSelected = menuPath ;
            console.log( `show()  forceRefresh: ${ forceRefresh } ` )
            get_project_source( forceRefresh ) ;

            //}
            return $contentLoaded ;
        }

    } ;


    function initialize() {

        console.log( "main::initialize" ) ;

        defBrowser.addOnChangeFunction( project_source_updated ) ;
        attributesEditor.addOnChangeFunction( project_source_updated ) ;

        utils.disableButtons( $( "button", $buttonContainer ) ) ;
        utils.enableButtons( $( "#showReloadButton", $buttonContainer ) ) ;


        $( "#showPackageDialog" ).click( function () {
            showPackageDialog() ;
            return false ;
        } )


        // Update definition when needed for apply
        jsonForms.configureUpdateFunction( get_project_source ) ;


        $( '#showCiButton' ).click( function () {
            if ( !isValidDefinition( false ) ) {
                alertify.csapWarning( "Errors found in defintion. Correct before continuing." ) ;
//					return;
            }
            showCheckinDefintionDialog() ;
        } ) ;

        $( '.regionButton' ).click( function () {
            // $("#json").select() ;
            // from commonJQ
            // highlightRegion();
            //treeEditor.highlightTextEditorRegion();
            return false ;
        } ) ;

        $( '#applyButton', $editorPane ).click( function () {

            let message = `<div class="hquote">Changes will be applied to: <span>${ hostLoadMessage() }</span>`
                    + `<br/> in lifecycle: <span>${ utils.getActiveEnvironment() }</span> `
                    + "<br/><br/>Note changes may be overwritten if not checked in (backups are available in csap saved folder)."
                    + "<br/><br/></div>" ;

            let applyDialog = alertify.confirm( message ) ;

            applyDialog.setting( {
                title: "Application Update Confirmation",
                'onok': function () {
                    applyCluster()
                },
                'oncancel': function () {
                    alertify.warning( "Operation Cancelled" ) ;
                }

            } ) ;

            return false ;
        } ) ;

        $( '#validateConfig' ).click( function () {
            alertify.notify( "Validating definition" ) ;
            checkDefaultUser() ;
            validateDefinition( false ) ;
            return false ;
        } ) ;

        $( '#showReloadButton' ).click( function () {
            // $('#reloadDiv').toggle();
            showReloadDefintionDialog() ;

        } ) ;

        $( '#cleanFsButton' ).click( function () {
            let inputMap = {
                fromFolder: "__platform__/build/" + definitionName,
                hostName: CSAP_HOST_NAME,
                command: "delete"
            } ;
            postAndRemove( "_blank", OS_URL + "/command", inputMap ) ;

            $( "#editorMain" ).focus() ;
            return false ;
        } ) ;

        $( '#open-definition-button' ).click(
                function () {

                    let urlAction = FILE_URL + "/FileManager?quickview=CSAP Application Definition" + "&fromFolder=__platform__/definition&" ;

                    openWindowSafely( urlAction, CSAP_HOST_NAME + "ClusterFiles" ) ;

                    $( "#editorMain" ).focus() ;
                    return false ;
                } ) ;

        // resetFocus to avoid visual glitches
        $( '#rawButton' ).click( function () {
            let urlAction = baseUrl + "api/capability" ;
            openWindowSafely( urlAction, CSAP_HOST_NAME + "Definition" ) ;
            $( "#editorMain" ).focus() ;

            return false ;
        } ) ;

        $( '#defButton' ).click( function () {
            let urlAction = "summary" + "?project=" + utils.getActiveProject( ) ;
            openWindowSafely( urlAction, "_blank" ) ;
            $( "#editorMain" ).focus() ;

            return false ;
        } ) ;

        if ( $.urlParam( "path" ) != null ) {
            // alertify.alert("found path");
            $( "#tabs" ).tabs( "option", "active", 1 ) ;
        }

    }


    function showReloadDefintionDialog() {
        console.log( "showReloadDefintionDialog() - init" ) ;
        // Lazy create
        if ( !alertify.appReloadDialog ) {
            console.log( "showReloadDefintionDialog() - creating new dialog" ) ;

            alertify.dialog( 'appReloadDialog', reloadDialogFactory, false, 'confirm' ) ;

        }

        alertify.appReloadDialog().show() ;


    }

    function hostLoadMessage() {
        let hostCount = `${ utils.findNavigation( "#host-count" ).text() } hosts` ;

        if ( utils.isAgent() ) {
            hostCount = `only current host (not recommended unless testing)` ;
        }

        return hostCount ;
    }


    function showCheckinDefintionDialog() {
        // Lazy create
        if ( !alertify.checkInDefinition ) {

            let message = `Changes will be applied to <span>${ hostLoadMessage() }</span>`
                    + ` in lifecycle: <span>${ utils.getActiveEnvironment() }</span> ` ;

            $( ".hquote", $checkinDialog ).html( message ) ;

            let ciFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( $checkinDialog.show()[0] ) ;
                        this.setting( {
                            'onok': function () {
                                ciCluster() ;
                            },
                            'oncancel': function () {
                                alertify.warning( "Cancelled Request" ) ;
                            }
                        } ) ;
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Commit Changes", className: alertify.defaults.theme.ok },
                                { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                            ],
                            options: {
                                title: "Check In Application Definition :",
                                resizable: false, movable: false, maximizable: false
                            }
                        } ;
                    }

                } ;
            } ;
            alertify.dialog( 'checkInDefinition', ciFactory, false, 'confirm' ) ;

            $( '#ciPass' ).keypress( function ( e ) {
                if ( e.which == 13 ) {
                    alertify.closeAll() ;
                    ciCluster() ;
                }
            } ) ;
        }
        $( "#ciUser" ).val( utils.getScmUser() ) ;

        alertify.checkInDefinition().show() ;


    }


    function isValidDefinition( isReload ) {
        try {
            if ( $definition.val() != _LOADING_MESSAGE ) {

                // console.log("Starting parse") ;
                if ( isReload ) {

                    // let parsedJson = JSON.parse( $definition.val() );
                    console.log( "isValidDefinition(): Scheduling reload" ) ;
                    setTimeout( function () {
                        let parsedJson = JSON.parse( $definition.val() ) ;
                        load_project_source( parsedJson ) ;
                    }, 200 ) ;
                }

            }

        } catch ( e ) {
            let message = "Failed to parse document: " + e ;

            alertify.alert( message ) ;
            return false ;
        }
        return true ;
    }

    function project_source_updated() {

        console.log( `project_source_updated() posting source to server` ) ;

        $.post( `${ DEFINITION_URL }/project/source`, {
            project: utils.getActiveProject( ),
            source: $definition.val()
        } )

                .done( function ( changeReport ) {

                    console.log( `project_source_updated() ${ changeReport } ` ) ;

                    if ( changeReport.errors ) {
                        alertify.csapWarning(
                                `Failed storing edits on server: ${changeReport.reason}` ) ;
                    }
                    checkPendingEdits( changeReport ) ;


                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "project_source_updated", errorThrown ) ;
                } ) ;

    }

    function get_project_source( forceUpdate ) {

        console.log( "getDefinitionFromServer(): forceUpdate: ", forceUpdate ) ;

        if ( ( !forceUpdate ) && $definition.val() != _LOADING_MESSAGE ) {
            console.log( "Skipping definition get - re using existing" ) ;
            return ;
        }
        utils.loading( "Loading definition..." ) ;

        // only lagged to display loading icon
        $contentLoaded = new $.Deferred() ;
        let projectName = utils.getActiveProject( ) ;
        setTimeout( () => {

            $.getJSON( `${ DEFINITION_URL }/project/source`, {
                project: projectName
            } )

                    .done( function ( sourceReport ) {
                        load_project_source( sourceReport ) ;
                        _lastProjectShown = projectName ;
                        utils.loadingComplete() ;
                        $contentLoaded.resolve() ;

                    } )

                    .fail(
                            function ( jqXHR, textStatus, errorThrown ) {

                                handleConnectionError( "Retrieving definitionGetSuccess " + CSAP_HOST_NAME, errorThrown ) ;
                            } ) ;
        }, 500 ) ;

    }

    function checkPendingEdits( sourceReport ) {
        let $userPanel = utils.findNavigation( "#pending-edits" ) ;
        $userPanel.text( "" ) ;
        if ( sourceReport.user.length > 0 ) {
            $userPanel.text( `(${ sourceReport.user })` ) ;
            utils.enableButtons( $( "button", $buttonContainer ) ) ;
        }
    }

    function load_project_source( sourceReport ) {

        console.log( "load_project_source() _lastTabSelected: " + _lastTabSelected ) ;
        definitionName = sourceReport.name ;

        //console.log("getDefinitionSuccess() News: " + JSON.stringify( capabilityJson.clusterDefinitions.dev.settings.newsItems, null, "\t" ) ) ;

        if ( sourceReport.error != undefined ) {
            let msg = "Unable to retrieve definition due to: \n"
                    + sourceReport.error
                    + "\n\nRecommendation: select package by click button in title bar." ;
            alertify.csapWarning( msg ) ;
            return ;

        }


        checkPendingEdits( sourceReport ) ;


        let projectDefinition = sourceReport.source ;
        let definitionText = JSON.stringify( projectDefinition, null, "\t" ) ;
        $definition.val( definitionText ) ;

        switch ( _lastTabSelected ) {
            case "attribute":
                attributesEditor.show( $definition ) ;
                break ;

            case "code" :
                defBrowser.reset( projectDefinition ) ;
                defBrowser.show( projectDefinition, $definition ) ;
                break ;

            case "environment":
                lifeEdit.showSummaryView(
                        null,
                        $( "#lifeEditor" ),
                        utils.getActiveProject() ) ;
                break ;

            default:
                console.log( "Unexpected tab: " + _lastTabSelected ) ;
        }

    }

    function showReloadDefintionDialog() {
        console.log( "showReloadDefintionDialog() - init" ) ;
        // Lazy create
        if ( !alertify.appReloadDialog ) {
            console.log( `showReloadDefintionDialog() - creating new dialog user: ${utils.getCsapUser() }` ) ;

            alertify.dialog( 'appReloadDialog', reloadDialogFactory, false, 'confirm' ) ;

        }

        $( "#edit-user", $reloadPanel ).val( utils.getScmUser() ) ;
        alertify.appReloadDialog().show() ;

    }

    function reloadDialogFactory() {
        return{
            build: function () {
                // Move content from template
                this.setContent( $( "#reloadDiv" ).show()[0] ) ;
                this.setting( {
                    'onok': function () {
                        console.log( "showReloadDefintionDialog(): ok pressed" ) ;
                        reloadCluster() ;
                    },
                    'oncancel': function () {
                        alertify.warning( "Cancelled Request" ) ;
                    }
                } ) ;
            },
            setup: function () {
                return {
                    buttons: [
                        { text: "Perform Reload", className: alertify.defaults.theme.ok, key: 0 /* enter */ },
                        { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                    ],
                    options: {
                        title: "Reload Application Definition :",
                        resizable: false,
                        movable: false,
                        maximizable: true
                    }
                } ;
            }

        } ;
    }



    function checkDefaultUser() {
        if ( $( "#json" ).val().indexOf( "defaultUser" ) != -1 ) {
            let msg = 'Warning: Multiple instances of string <span class="error">defaultUser</span> found in definition.' ;
            msg += "Switch to text view and update all references to defaultUser." ;
            msg += "Operational Support requires either a person, a document, a wiki, or similar." ;
            alertify.alert( msg ) ;
            // window.alert( msg ) ;
        }
    }

    function ciCluster() {

        if ( !isValidDefinition( false ) )
            return ;
        // note that host command triggers handling on server side
//        if ( $( "#comment" ).val() == defaultComment || $( "#comment" ).val().length < 10 ) {
//
//            alertify.csapWarning(
//                    'Check in comments are mandatory<br><br>'
//                    + 'Validation fails if comment is fewer then 10 characters<br><br> Add comment and try again'
//                    )
//
//            return ;
//        }

        if ( $( "#ciPass" ).val().length == 0 ) {

            alertify.csapWarning(
                    'Password is required<br><br>'
                    + 'Add Password and try again'
                    )
            return ;
        }
        let paramObject = {
            scmUserid: $( "#ciUser" ).val(),
            scmPass: $( "#ciPass" ).val(),
            scmBranch: $( "#ciBranch" ).val(),
            comment: $( "#comment" ).val(),
            serviceName: "HostCommand",
            project: utils.getActiveProject( ),
            applyButNoCheckin: false,
            isUpdateAll: $( '#ciUpdateAll' ).is( ":checked" )
        } ;

        send_command( "applicationCheckIn", paramObject, "Check In Editor Changes and Load cluster" ) ;
    }

    function applyCluster() {

        // note that host command triggers handling on server side
        if ( !isValidDefinition( false ) )
            return ;
        let paramObject = {
            scmUserid: $( "#scmUserid" ).val(),
            scmPass: $( "#scmPass" ).val(),
            scmBranch: $( "#scmBranch" ).val(),
            serviceName: "HostCommand",
            applyButNoCheckin: true,
            project: utils.getActiveProject( )
        } ;

        send_command( "applicationApply", paramObject, "Apply Editor Changes to Cluster" ) ;
    }

    function reloadCluster() {


        let paramObject = {
            scmUserid: $( "#edit-user" ).val(),
            scmPass: $( "#edit-pass" ).val(),
            scmBranch: $( "#edit-branch" ).val(),
            serviceName: "HostCommand" // triggers check out of definition
        } ;

        send_command( "applicationReload", paramObject, "Reloading definition from git" ) ;

    }

    function send_command( command, paramObject, desc ) {

        console.log( `send_command() ${ command }` ) ;
        //show_results( "Performing: " + desc + "\n" ) ;
        $( 'body' ).css( 'cursor', 'wait' ) ;

        fileOffset = "-1" ;
        fromFolder = "//" + command + ".log" ;


        checkForChangesTimer = setTimeout( function () {
            tail_results_request() ;
        }, 2000 ) ;

        $.post( DEFINITION_URL + "/" + command, paramObject )

                .done( function ( results ) {

                    console.log( `send_command() ${ command } completed` ) ;

                    utils.refreshStatus( true ) ;

                    setTimeout( function () {
                        utils.refreshStatus( true ) ;
                    }, 1000 ) ;


                    haltProgressRefresh() ;
                    setTimeout( function () {
                        // letting in progress queryies complete
                        show_results( "\n Command Completed" ) ;
                        show_results_add_details( command, results ) ;
                    }, 500 ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( CSAP_HOST_NAME + ":" + command, errorThrown ) ;
                    haltProgressRefresh() ;
                } ) ;

    }

    function haltProgressRefresh() {
        clearTimeout( checkForChangesTimer ) ;
        checkForChangesTimer = 0 ;
    }


    function tail_results_request() {

        console.log( `tail_results_request ()` ) ;


        clearTimeout( checkForChangesTimer ) ;
        // $('#serviceOps').css("display", "inline-block") ;
        // console.log("Hitting Offset: " + fileOffset) ;
        let requestParms = {
            serviceName: AGENT_ID,
            hostName: CSAP_HOST_NAME,
            fromFolder: fromFolder,
            bufferSize: 100 * 1024,
            logFileOffset: fileOffset,
            isLogFile: isLogFile
        } ;

        $.getJSON( FILE_URL + "/getFileChanges", requestParms )

                .done( function ( hostJson ) {
                    tail_results_response( hostJson ) ;
                } )

                .fail(
                        function ( jqXHR, textStatus, errorThrown ) {

                            handleConnectionError( "Failed getting status from host file: " + fromFolder, errorThrown ) ;

                        } ) ;
    }


    function tail_results_response( latest_file_changes ) {

        if ( latest_file_changes.error ) {
            console.log( "Failed getting status from host due to:" + latest_file_changes.error ) ;
            console.log( "Retrying..." ) ;
        } else {
            // $("#"+ hostName + "Result").append("<br>peter") ;
            // console.log( JSON.stringify( changesJson ) ) ;
            // console.log("Number of changes :" + changesJson.contents.length);
            for ( let i = 0 ; i < latest_file_changes.contents.length ; i++ ) {
                let fileChanges = latest_file_changes.contents[i] ;
                let htmlFormated = fileChanges.replace( warnRe, '<span class="warn">WARNING</span>' ) ;
                htmlFormated = htmlFormated.replace( errorRe, '<span class="error">ERROR</span>' ) ;
                htmlFormated = htmlFormated.replace( debugRe, '<span class="debug">DEBUG</span>' ) ;
                htmlFormated = htmlFormated.replace( infoRe, '<span class="info">INFO</span>' ) ;

                // htmlFormated = htmlFormated.replace(winHackRegEx, '') ;
                // htmlFormated = htmlFormated.replace(newLineRegEx, '<br>') ;
                // displayResults( '<span class="chunk">' + htmlFormated +
                // "</span>", true);
                // $("#"+ hostName + "Result").append( '<span class="chunk">' +
                // htmlFormated + "</span>" ) ;
                show_results( '<span class="chunk">' + htmlFormated + "</span>", true ) ;
            }

            fileOffset = latest_file_changes.newOffset ;
            // $("#fileSize").html("File Size:" + changesJson.currLength) ;
        }
        let refreshTimer = 2 * 1000 ;

        if ( checkForChangesTimer != 0 ) {

            checkForChangesTimer = setTimeout( function () {
                tail_results_request() ;
            }, refreshTimer ) ;
        }

    }

    function show_results( results, append ) {

        if ( !append ) {
            $( "#edit-results-content" ).html( "" ) ;
            $( 'body' ).css( 'cursor', 'default' ) ;
        }
        $( "#edit-results-content" ).append( results ) ;

        showResultsDialog() ;

    }

    function show_results_add_details( command, resultsJson ) {

        let host = CSAP_HOST_NAME ;
        let results = resultsJson.plainText ;



        let compressRegEx = new RegExp( 'start-messages-token(.|\n|\r)*end-messages-token' ) ;
        results = results.replace( compressRegEx, "\n...snipped git messages...\n" ) ;

//        let compressRegEx = new RegExp( 'Compressing objects(.|\n|\r)*Compressing' ) ;
//        results = results.replace( compressRegEx, "Compressing ...snipped..." ) ;
//
//
//        let receiveRegEx = new RegExp( 'Receiving objects(.|\n|\r)*Receiving' ) ;
//        results = results.replace( receiveRegEx, "Receiving ...snipped..." ) ;
//
//        let resolveRegEx = new RegExp( 'Resolving deltas(.|\n|\r)*Resolving' ) ;
//        results = results.replace( resolveRegEx, "Resolving ...snipped..." ) ;

        // console.log("Updated match: ", results) ;

        let isDetailsShown = false ;
        let style = 'style="display: none; font-size: 0.9em"' ;
        if ( results.indexOf( "__ERROR" ) != -1 || results.indexOf( "__WARN" ) != -1 ) {
            style = 'style="display: block; font-size: 0.9em"' ;
            isDetailsShown = true ;
        }
        let hostHtml = '\n\n ' + host + ': ' + command + ' completed.(<a class="simple" style="display:inline" id="' + host
                + 'Toggle" href="toggle">Results</a>)' ;
        hostHtml += '\n<div class="note" ' + style + ' id="' + host + 'Result" > ' + results + '</div>' ;

        $( "#edit-results-content" ).append( hostHtml ) ;

        $( '#' + host + 'Toggle' ).click( function () {
            let $resultPanel = $( '#' + host + 'Result' ) ;

            if ( $resultPanel.is( ":visible" ) ) {
                $resultPanel.hide() ;
                restoreDialog() ;
            } else {
                $resultPanel.show() ;
                maximizeDialog() ;
            }


            return false ; // prevents link
        } ) ;
        //$( "#edit-results-content" ).append( "\nNote: Use refresh page to load updated cluster configuration.\n" ) ;

        if ( isDetailsShown ) {

            jQuery( '<div/>', {
                class: "warn",
                text: "Found errors or warnings in output"
            } ).appendTo( $( "#edit-results-content" ) ) ;
        }

        showResultsDialog() ;
        if ( !isDetailsShown ) {
            restoreDialog() ;
        }

        // alertify confirm collides with alert. We put inside to avoid conflict
        checkDefaultUser() ;

    }


    function maximizeDialog() {
        let targetWidth = $( window ).outerWidth( true ) - 100 ;
        let targetHeight = $( window ).outerHeight( true ) - 100 ;
        resultsDialog.resizeTo( targetWidth, targetHeight )
    }
    function restoreDialog() {
        resultsDialog.show() ;
    }


    function showResultsDialog() {

        // if dialog is active, just updated scroll and return
        if ( resultsDialog != null ) {
            let heightToScroll = $( "#edit-results-content" )[0].scrollHeight ;
            // console.log("Scrolling to bottom: " + heightToScroll) ;
            $( ".ajs-content" ).scrollTop( heightToScroll ) ;
            return ;
        }

        resultsDialog = alertify.alert( '<div id="edit-results"></div>' ) ;

        resultsDialog.setting( {
            title: "CSAP Service Commands",
            resizable: true,
            'label': "Close after reviewing output",
            'onok': function () {
                $( "#resultsSection" ).append( $( "#edit-results-content" ) ) ;
                resultsDialog = null ;
                get_project_source( true ) ;
            }

        } ) ;

        maximizeDialog() ;

        $( "#edit-results" ).append( $( "#edit-results-content" ) ) ;

    }

    function scheduleWindowResize() {
        return ;

        clearTimeout( resizeTimer ) ;
        resizeTimer = setTimeout( function () {
            windowResize() ;
        }, 200 ) ;

    }

    function windowResize() {

        console.log( "Resizing window" )

        let $jsonBrowser = $( "#jsonFileBrowser" ) ;
        let $tree = $( "ul.fancytree-container", $jsonBrowser ) ;

        let browserHeight = Math.round( $( window ).outerHeight( true ) - $jsonBrowser.offset().top - 30 ) ;
        console.log( "browserHeight", browserHeight ) ;
        $tree.css( "height", browserHeight ) ;
    }



    function validateDefinition( full ) {

        $( ".textWarning" ).empty().hide() ;

        lineOnly = full ;
        $( 'body' ).css( 'cursor', 'wait' ) ;

        let paramObject = {
            project: utils.getActiveProject( ),
            updatedConfig: $( "#json" ).val()
        } ;

        $.post( DEFINITION_URL + "/validateDefinition", paramObject )

                .done( function ( resultsJson ) {
                    // displayResults(results);
                    validateDefinitionSuccess( resultsJson ) ;
                    haltProgressRefresh() ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( CSAP_HOST_NAME + ": validateDefinition", errorThrown ) ;
                    haltProgressRefresh() ;
                } ) ;

    }

    function validateDefinitionSuccess( resultsJson ) {

        // console.log("Results: " + resultsJson) ;
        $( 'body' ).css( 'cursor', 'default' ) ;
        let containerJQ = jQuery( '<div/>', { } ) ;

        processValidationResults( resultsJson ).appendTo( containerJQ ) ;

        if ( !lineOnly ) {
            alertify.alert( "Application Definition Validation", containerJQ.html() ) ;
        }
    }

    function processValidationResults( parsedJson ) {

        // Clear previous errors from text area
        $( ".errorIcon" ).removeClass( "errorIcon" ) ;

        let resultsContainer = jQuery( '<div/>', {
            id: "parseResults"
        } ).css( "font-size", "1.2em" ) ;


        let resultsClass = "status-green" ;
        let resultsText = "Parsing Successful" ;


        if ( !parsedJson.success ) {
            resultsClass = "status-red" ;
            resultsText = "Parsing Failed" ;
        }

        jQuery( '<span/>', {
            class: resultsClass,
            text: resultsText
        } ).css( "height", "1.2em" ).appendTo( resultsContainer ) ;

        resultsContainer.append( "<br>" ) ;

        if ( parsedJson.errors && parsedJson.errors.length > 0 ) {

            let errorsObj = jQuery( '<div/>', {
                class: "warning"
            } ).text( "Errors: " ).css( {
                "overflow-y": "auto"
            } ) ;
            let listJQ = jQuery( '<ol/>', {
                class: "error"
            } ) ;
            for ( let i = 0 ; i < parsedJson.errors.length ; i++ ) {

                // 2 scenarios: a parsing error with a line number, and a semantic
                // error with just contents
                $( ".textWarning" ).html( "Found some Errors<br> Run validator to view" ).show() ;
                let error = parsedJson.errors[i] ;
                let errorMessage = parsedJson.errors[i] ;
                if ( error.line ) {
                    console.log( "Found error: " + error.line ) ;
                    errorMessage = '<span style="font-weight: bold"> Line: ' + error.line + "</span> Message: <br>"
                            + error.message ;
                    // $(".line" + error.line).addClass("errorIcon");
                    $( '.lineno:contains("' + error.line + '")' ).addClass( "errorIcon" ) ;
                    $( ".errorIcon" ).qtip( {
                        content: {
                            title: "Error Information",
                            text: errorMessage
                        }
                    } ) ;
                } else {
                    errorMessage = JSON.stringify( error, null, "\t" ) ;
                    errorMessage = errorMessage.replace( "__ERROR", "Error" ) ;
                }
                jQuery( '<li/>', {
                    class: "error"
                } ).html( errorMessage ).appendTo( listJQ ) ;

            }
            listJQ.appendTo( errorsObj ) ;
            errorsObj.appendTo( resultsContainer ) ;
        } else {
            if ( parsedJson.warnings && parsedJson.warnings.length > 0 ) {

                let errorsObj = jQuery( '<div/>', {
                    class: "warning"
                } ).text( "Warnings: " ) ;
                let listJQ = jQuery( '<ol/>', {
                    class: "error"
                } ) ;
                for ( let i = 0 ; i < parsedJson.warnings.length ; i++ ) {
                    $( ".textWarning" ).html( "Found some Warnings<br> Run validator to view" ).show() ;
                    let noteItem = parsedJson.warnings[i] ;
                    noteItem = noteItem.replace( "__WARN:", "" ) ;
                    jQuery( '<li/>', {
                        class: "error"
                    } ).html( noteItem ).appendTo( listJQ ) ;
                }
                listJQ.appendTo( errorsObj ) ;
                errorsObj.appendTo( resultsContainer ) ;
            }
        }

        resultsContainer.append( "<br>" ) ;

        return resultsContainer ;
    }

} ) ;