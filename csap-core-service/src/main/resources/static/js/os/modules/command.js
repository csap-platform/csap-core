// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
    paths: {
        jsYaml: baseUrl + "webjars/js-yaml/3.14.0/dist",
        ace: baseUrl + "webjars/ace-builds/1.4.11/src"
    },
} ) ;
require( [ ], function () {
    console.log( "module loaded 3" ) ;

    $( document ).ready( function () {
        console.log( "Document ready" ) ;
        deferredLoadingToEnableJquerySelectors() ;
    } ) ;

    function deferredLoadingToEnableJquerySelectors() {
        require( [ "ace/ace", "command-template", "command-hosts" ], function ( aceEditor, templates, hostSelection ) {

            console.log( "\n\n initializing selectors\n\n" ) ;

            const $aceSettings = $( "#command-header #ace-editor-settings" ) ;
            const $aceFoldCheckbox = $( "#ace-fold-checkbox", $aceSettings ) ;

            const $resultsContainer = $( "#resultsContainer" ) ;
            const $resultsTableContainer = $( "#results-table-container", $resultsContainer ) ;
            const $resultsTable = $( "#resultsTable", $resultsContainer ) ;

            let _command_editor ;

            let _resizeTimer ;

            let $resultSelect ;

            let aceEditorId = "ace-editor" ;

            let checkForChangesTimer = null ;
            let warnRe = new RegExp( "warning", 'gi' ) ;
            let errorRe = new RegExp( "error", 'gi' ) ;
            let infoRe = new RegExp( "info", 'gi' ) ;
            let debugRe = new RegExp( "debug", 'gi' ) ;

            let winHackRegEx = new RegExp( "\r", 'g' ) ;
            let newLineRegEx = new RegExp( "\n", 'g' ) ;


            $( document ).ready( function () {

                CsapCommon.configureCsapAlertify() ;
                initialize() ;

                $resultSelect = $( "#results-select" ) ;

            } ) ;

            let _refreshTimer ;
            let $templateFilter ;

            let tailTemplate = '#!/bin/bash\n\n# modify as needed \n\ntail -f  /opt/csapUser/processing/httpd_8080/logs/access.log | stdbuf -o0 grep -i admin' ;
            let lsTemplate = $( "#scriptText" ).val() ;

            function initialize() {

                updateJobId() ;

                hostSelection.initialize() ;

                $( "#tabs" ).tabs( {
                    activate: function ( event, ui ) {

                        console.log( "Loading: " + ui.newTab.text() ) ;
                        //windowResize() ;
                    }
                } ) ;

                $( "#tabs" ).css( "visibility", "visible" ) ;

                let $aceWrapCheckbox = $( '#ace-wrap-checkbox', $aceSettings ) ;
                $aceWrapCheckbox.change( function () {
                    if ( $aceWrapCheckbox.is( ":checked" ) ) {
                        _command_editor.session.setUseWrapMode( true ) ;

                    } else {
                        _command_editor.session.setUseWrapMode( false ) ;
                    }
                } ) ;

                $aceFoldCheckbox.prop( "checked", true ) ;
                $aceFoldCheckbox.change( updateCodeFolding ) ;

                $( "#ace-theme-select", $aceSettings ).change( function () {
                    _command_editor.setTheme( $( this ).val() ) ;
                } ) ;




                $templateFilter = $( "#command-table-filter input" ) ;

                $templateFilter.off().keyup( function () {
                    console.log( "Applying template filter" ) ;
                    clearTimeout( _refreshTimer ) ;
                    _refreshTimer = setTimeout( function () {
                        applyFilter() ;
                    }, 500 ) ;
                } ) ;


                $( ".pushButton" ).hover(
                        function () {
                            $( this ).css( "text-decoration", "underline" ) ;
                            $( this ).css( 'cursor', 'pointer' ) ;
                        }, function () {
                    $( this ).css( "text-decoration", "none" ) ;
                    $( this ).css( 'cursor', 'default' ) ;
                }
                ) ;
                $resultsTable.tablesorter( {
                    sortList: [ [ 0, 0 ] ],
                    theme: 'csapSummary'
                } ) ;

                $( "#help-link-hover" ).hover( function () {
                    $( "#help-hover-notes" ).show() ;
                }, function () {
                    $( "#help-hover-notes" ).hide() ;
                } ) ;


                $( "#wrapTextLines" ).change( function () {

                    if ( $( this ).is( ":checked" ) ) {
                        $( "#resultsTextArea" ).css( "white-space", "pre-wrap" ) ;
                        $( ".outputColumn" ).css( "white-space", "pre-wrap" ) ;
                    } else {

                        $( "#resultsTextArea" ).css( "white-space", "pre" ) ;
                        $( ".outputColumn" ).css( "white-space", "pre" ) ;
                    }
                } )

                $( "#initRadio" ).prop( 'checked', true ) ;

                if ( command == "script" ) {

                    build_command_editor() ;
                    templates.initialize( _command_editor, updateCodeFolding ) ;

                    if ( hasContentsParam ) {
                        $( "#script" ).show() ;
                    } else {
                        templates.load( template ) ;
                    }
                }



                if ( command == "logSearch" ) {
                    initLogSearch() ;
                }


                if ( $( "#resultPre" ).html() != "null" ) {
                    $( "#resultsTextArea" ).show() ;
                }

                $( "#operationSection" ).show() ;

                $( window ).resize( function () {
                    clearTimeout( _resizeTimer ) ;
                    _resizeTimer = setTimeout( windowResize, 200 ) ;
                } ) ;

                windowResize() ;

                $( '.fileInSearchFolder' ).click( function () {

                    let itemToSearchFor = $( this ).text() ;
                    if ( itemToSearchFor.includes( "*" ) ) {
                        itemToSearchFor = "*" ;
                    }
                    // console.log("search item clicked: " + $(this).text() ) ;
                    let currentFolder = $( "#searchIn" ).val() ;
                    let relativeFolder = currentFolder.substring( 0, currentFolder.lastIndexOf( "/" ) ) ;
                    $( "#searchIn" ).val( relativeFolder.trim() + "/" + itemToSearchFor ) ;

                    $( "#searchIn" ).css( "border-color", "red" ) ;

                    if ( $( this ).text().contains( ".gz" ) ) {
                        $( "#zipSearch" ).prop( "checked", true )
                                .parent().css( "background-color", "var(--panel-header-bg)" ) ;
                    }
                } ) ;

                if ( !isAdmin ) {
                    $( "#searchTimeout" ).prop( 'disabled', true ) ;
                }

                $( ".show-root-warning" ).click( function () {
                    if ( $( this ).is( ':checked' ) ) {
                        alertify.csapInfo( "Caution advised: running as root cannot be reverted except by host reimaging" ) ;
                    }
                } ) ;

                $( "#deleteButton" ).click( function () {
                    $( '#delete form' ).ajaxSubmit( buildFormOptions() ) ;
                    return false ;
                } ) ;

                //
                //  http://malsup.com/jquery/form/ and  https://github.com/jquery-form/form
                //

                $( "#uploadButton" ).click( function () {
                    let requestParms = {
                        uploadFilePath: $( "#uploadFileSelect" ).val(),
                        extractDir: $( "#uploadExtractDir" ).val(),
                        skipExtract: $( "#uploadSkipExtract" ).is( ':checked' ),
                        overwriteTarget: $( "#uploadOverwrite" ).is( ':checked' )
                    }
                    console.log( "uploading validation", requestParms ) ;

                    if ( !requestParms.uploadFilePath ) {
                        alertify.alert( "Select a file to upload" ) ;
                        return false ;
                    }

                    try {
                        $.getJSON(
                                "uploadToFsValidate",
                                requestParms )

                                .done( function ( validateResponse ) {

                                    if ( validateResponse.error ) {
                                        alertify.alert( "Upload Alert", "The following item needs to be corrected:<br/>" + validateResponse.error ) ;
                                    } else {

                                        let uploadOptions = buildFormOptions() ;
                                        let myBeforeSubmit = function ( parameters, $form, options ) {
                                            //alert(`about to submit`) ;
                                            for ( let hostName of hostSelection.getSelected() ) {
                                                let hostParam = {
                                                    name: `hosts`,
                                                    value: hostName,
                                                    type: `text`
                                                } ;
                                                parameters.push( hostParam ) ;
                                            }

                                            console.log( `modified parameters need for multipart forms: `, parameters ) ;

                                        }
                                        uploadOptions.beforeSubmit = myBeforeSubmit ;

                                        $( '#upload form' ).ajaxSubmit( uploadOptions ) ;
                                    }
                                } )

                                .fail( function ( jqXHR, textStatus, errorThrown ) {

                                    handleConnectionError( "Retrieving changes for file " + fromFolder, errorThrown ) ;
                                } ) ;

                    } catch ( e ) {
                        alertify.alert( message ) ;
                    }
                    return false ;
                } ) ;



                $( "#executeSubmitButton" ).click( function () {

                    let formOptions = buildFormOptions() ;

                    $( "#cancelInput" ).val( "" ) ; // empty out the cancel flag
                    updateJobId() ; // empty out the cancel flag

                    if ( $( "#executeUserid" ).val() == "root" ) {


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
                        $( '#script form' ).ajaxSubmit( formOptions ) ;
                    }
                    return false ;
                } ) ;

                $( "#cancelButton" ).click( function () {
                    alertify.error( "Cancelling command" ) ;
                    $( "#cancelInput" ).val( "cancel" ) ; // setthe cancel flag
                    $( '#script form' ).ajaxSubmit( buildFormOptions() ) ;

                    return false ;
                } ) ;


                $( "#submitSearchButton" ).click( function () {

                    $( "#cancelInput" ).val( "" ) ; // empty out the cancel flag
                    $( "#jobIdInput" ).val( $.now() ) ; // empty out the cancel flag

                    $( '#logSearch form' ).ajaxSubmit( buildFormOptions() ) ;

                    return false ;
                } ) ;

                $( "#syncSubmitButton" ).click( function () {
                    // alert($("#executeUserid").val()) ; 
                    if ( $( "#syncUserid" ).val() == "root" ) {

                        let newItemDialog = alertify.confirm( "Validate your content carefully<br><br>In case of errors, submitting root level requests require cases to be opened to recover VM." ) ;

                        newItemDialog.setting( {
                            title: 'Caution: Root user speciied',
                            'labels': {
                                ok: 'Proceed with synchronize',
                                cancel: 'Cancel Request'
                            },
                            'onok': function () {
                                alertify.success( "Submitting Request" ) ;
                                $( '#sync form' ).ajaxSubmit( buildFormOptions() ) ;

                            },
                            'oncancel': function () {
                                alertify.warning( "Cancelled Request" ) ;
                            }

                        } ) ;



                    } else {
                        $( '#sync form' ).ajaxSubmit( buildFormOptions() ) ;
                    }
                    return false ;
                } ) ;

                allowTabs() ;

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

            function allowTabs() {

                $( "textarea" ).keydown( function ( e ) {
                    if ( e.keyCode === 9 ) { // tab was pressed
                        // get caret position/selection
                        let start = this.selectionStart ;
                        end = this.selectionEnd ;

                        let $this = $( this ) ;

                        // set textarea value to: text before caret + tab + text after caret
                        $this.val( $this.val().substring( 0, start )
                                + "\t"
                                + $this.val().substring( end ) ) ;

                        // put caret at right position again
                        this.selectionStart = this.selectionEnd = start + 1 ;

                        // prevent the focus lose
                        return false ;
                    }
                } ) ;


            }



// http://jquery.malsup.com/form/#file-upload : jquery form: http://api.jquery.com/jQuery.ajax/#options
            function buildFormOptions() {


                let $progressBar = $( '.bar' ) ;
                let $percentLabel = $( '.percent' ) ;
                let $statusLabel = $( '#status' ) ;

                let targetHosts = hostSelection.getSelected() ;

                console.log( `buildFormOptions() hosts: ${targetHosts } ` ) ;


                let actionForm = {
                    dataType: "json", // json, xml, script

                    data: {
                        'hosts': targetHosts
                    },
                    beforeSend: function () {
                        formBefore( $progressBar, $percentLabel, $statusLabel )
                    },
                    uploadProgress: function ( event, position, total, percentComplete ) {
                        let percentVal = percentComplete + '%' ;
                        $progressBar.width( percentVal ) ;
                        $percentLabel.html( "Upload Progress: " + percentVal ) ;
                    },
                    success: function ( jsonData ) {
                        let percentVal = '100%' ;
                        $progressBar.width( percentVal ) ;
                        $percentLabel.html( "Upload Progress: " + percentVal ) ;
                    },
                    complete: function ( $xmlHttpRequest ) {
                        //console.log( JSON.stringify($xmlHttpRequest) )  ;
                        if ( $xmlHttpRequest.status != 200 ) {
                            alertify.csapWarning( $xmlHttpRequest.status + " : "
                                    + $xmlHttpRequest.statusText + ": Verify your account is a member of the admin group." ) ;
                        }
                        formComplete( $xmlHttpRequest.responseJSON, $progressBar, $percentLabel, $statusLabel )
                    }
                } ;

                return actionForm ;
            }

            function formBefore( $progressBar, $percent, $status ) {

                if ( $( "#cancelInput" ).val() == "cancel" ) {
                    return ;
                }
                $( 'body' ).css( 'cursor', 'wait' ) ;

//	    	$("#resultPre").html('<pre class="result">Starting...</pre>');
                $( "#cancelButton" ).show() ;
                $( ".commandSection" ).hide() ;

//        $( ".browserSection" ).show() ;
                windowResize() ;

//        $( "#resultsSelectorBody" ).empty() ;
                $resultSelect.empty() ;

                $( "#tabs li" ).show() ;
                activateTab( "resultsTab" ) ;
                addHostResultsSelector( "progress" ) ;
                selectHostResults( "progress" ) ;

                displayResults( "Starting Command...", false ) ;
                $resultsContainer.show() ;

                $status.empty() ;
                let percentVal = '0%' ;
                $progressBar.width( percentVal ) ;
                $percent.html( "Upload Progress: " + percentVal ) ;

                // needto update
                fileOffset = "-1" ;
                fromFolder = FROM_BASE + "_sync.log" ;
                if ( command == "delete" ) {
                    fromFolder = FROM_BASE + "_delete.log" ;
                } else if ( command == "script" ) {
                    let fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log" ;
                    fromFolder = SCRIPT_BASE + fullName ;

                    let targetHosts = hostSelection.getSelected() ;
                    console.log( "targetHosts.length: " + targetHosts.length ) ;
                    if ( targetHosts.length == 1 ) {
                        // hook for single host  - progress of command will be shown in progress tab
                        fromFolder = XFER_BASE + fullName ;
                    } else if ( $( "#separateOutput" ).prop( 'checked' ) ) {
                        // displayResults("\n\nUse Progress drop down to monitor results\n\n", true) ;
                        for ( let i = 0 ; i < targetHosts.length ; i++ ) {
                            // let otherHostName = targetHosts[i];

                            addHostResultsSelector( targetHosts[i] ) ;
                        }
                    }
                } else if ( command == "upload" ) {
                    fromFolder = FROM_BASE + "_upload.log" ;
                }

                console.log( "Monitoring log: " + fromFolder ) ;

                registerSelectorClickEvents() ;
                deploySuccess = false ;
                checkForChangesTimer = setTimeout( getChanges, 2000 ) ;

            }

// http://api.jquery.com/jQuery.ajax/#jqXHR 
            function formComplete( commandResponseObject, $progressBar, $percentLabel, $statusLabel ) {

                $( "#cancelInput" ).val( "" ) ;
                $( "#cancelButton" ).hide() ;
                haltChangesRefresh() ;

                $( 'body' ).css( 'cursor', 'default' ) ;
                let percentVal = '100%' ;
                $progressBar.width( percentVal ) ;
                $percentLabel.html( "Upload Progress: " + percentVal ) ;

                $resultSelect.empty() ;

                if ( commandResponseObject.otherHosts != undefined ) {
                    if ( !$( "#separateOutput" ).prop( 'checked' ) )
                        addHostResultsSelector( "TableOutput" ) ;

                    for ( let i = 0 ; i < commandResponseObject.otherHosts.length ; i++ ) {
                        let otherHostName = commandResponseObject.otherHosts[i].host ;

                        if ( $( "#separateOutput" ).prop( 'checked' ) ) {
                            addHostResultsSelector( otherHostName ) ;
                        }
                    }
                }

                addHostResultsSelector( "Unparsed" ) ;
                registerSelectorClickEvents( commandResponseObject ) ;

                let firstSelected = commandResponseObject.scriptHost ;
                if ( !$( "#separateOutput" ).prop( 'checked' ) )
                    firstSelected = "TableOutput" ;

                setTimeout( function () {
                    selectHostResults( firstSelected ) ;
                }, 500 ) ;



                displayResults( JSON.stringify( commandResponseObject, null, "\t" ), false ) ;



            }

            function initLogSearch() {
                for ( let i = 0 ; i < 201 ; i++ ) {

                    if ( i > 5 && i % 10 != 0 )
                        continue ;
                    if ( i > 50 && i % 50 != 0 )
                        continue ;

                    let label = i + " Matches" ;
                    if ( i == 0 ) {
                        label = "Unlimited Matches" ;
                    }




                    let optionItem = jQuery( '<option/>', {
                        value: i,
                        text: label
                    } ) ;
                    $( "#maxMatches" ).append( optionItem ) ;



                    label = "Last " + i + " Line(s)" ;
                    if ( i == 0 )
                        label = "Entire File" ;
                    optionItem = jQuery( '<option/>', {
                        value: i,
                        text: label
                    } ) ;
                    $( "#tailLines" ).append( optionItem ) ;


                    label = i + " Line(s) Before" ;
                    optionItem = jQuery( '<option/>', {
                        value: i,
                        text: label
                    } ) ;
                    $( "#linesBefore" ).append( optionItem ) ;

                    label = i + " Line(s) After" ;
                    optionItem = jQuery( '<option/>', {
                        value: i,
                        text: label
                    } ) ;
                    $( "#linesAfter" ).append( optionItem ) ;
                }

                $( "#linesBefore" ).val( 1 ) ;
                $( "#linesAfter" ).val( 1 ) ;

                $( "#maxMatches" ).val( 10 ) ;

                $( ".searchLine select" ).selectmenu( { width: "15em" } ) ;


                if ( searchText.length > 0 ) {
                    $( "#searchTarget" ).val( searchText ) ;
                }

                console.log( `binding quicksearch` ) ;
                $( "#quick-search" ).css( "display", "inline" ) ;
                $( "#quick-search select" ).selectmenu( {
                    width: "20em",
                    change: function () {

                        let curSelect = $( "#quick-search select" ).val() ;
                        console.log( "Template " + curSelect ) ;
                        $( "#maxMatches" ).val( 1 ).selectmenu( "refresh" ) ;
                        $( "#linesBefore" ).val( 3 ).selectmenu( "refresh" ) ;
                        $( "#linesAfter" ).val( 3 ).selectmenu( "refresh" ) ;
                        $( "#tailLines" ).val( 0 ).selectmenu( "refresh" ) ;
                        $( "#reverseOrder" ).prop( "checked", true ) ;

                        switch ( curSelect ) {
                            case "last10Lines":
                                $( "#searchTarget" ).val( "." ) ;
                                $( "#maxMatches" ).val( 0 ).selectmenu( "refresh" ) ;
                                $( "#tailLines" ).val( 10 ).selectmenu( "refresh" ) ;
                                $( "#reverseOrder" ).prop( "checked", false ) ;
                                break ;

                            case "lastException":
                                $( "#searchTarget" ).val( "Exception" ) ;
                                break ;

                            case "last100Exception":
                                $( "#maxMatches" ).val( 0 ).selectmenu( "refresh" ) ;
                                $( "#tailLines" ).val( 100 ).selectmenu( "refresh" ) ;
                                $( "#reverseOrder" ).prop( "checked", false ) ;
                                $( "#searchTarget" ).val( "Exception" ) ;
                                break ;

                            case "allException":
                                $( "#maxMatches" ).val( 0 ).selectmenu( "refresh" ) ;
                                $( "#searchTarget" ).val( "Exception" ) ;
                                $( "#reverseOrder" ).prop( "checked", false ) ;
                                break ;

                            case "StartupInfoLogger.logStarted":
                                $( "#linesBefore" ).val( 1 ).selectmenu( "refresh" ) ;
                                $( "#linesAfter" ).val( 0 ).selectmenu( "refresh" ) ;
                                $( "#searchTarget" ).val( curSelect ) ;
                                break ;

                            case "CsapBootConfig.java":
                                $( "#linesBefore" ).val( 0 ).selectmenu( "refresh" ) ;
                                $( "#linesAfter" ).val( 50 ).selectmenu( "refresh" ) ;
                                $( "#searchTarget" ).val( curSelect ) ;
                                break ;

                            case "Server startup":
                                $( "#linesBefore" ).val( 0 ).selectmenu( "refresh" ) ;
                                $( "#linesAfter" ).val( 0 ).selectmenu( "refresh" ) ;
                                $( "#searchTarget" ).val( curSelect ) ;
                                break ;

                            default:
                                $( "#searchTarget" ).val( curSelect ) ;
                                console.log( "Skipping " + curSelect ) ;
                        }

                        $( "#submitSearchButton" ).trigger( "click" ) ;
                    }

                } ) ;
            }

            function activateTab( tabId ) {
                let tabIndex = $( 'li[data-tab="' + tabId + '"]', $( "#tabs" ) ).index() ;

                console.log( "Activating tab: " + tabIndex ) ;

                // $("#jmx" ).prop("checked", true) ;

                $( "#tabs" ).tabs( "option", "active", tabIndex ) ;
                // window.scrollTo(0,0);

                return ;
            }

            function selectHostResults( name ) {

                $resultSelect.val( name ) ;
                $resultSelect.trigger( "change" ) ;
            }

            function addHostResultsSelector( name ) {

                let label = name + " results" ;

                if ( name == "script" )
                    label = "Command Editor" ;

                if ( name == "TableOutput" )
                    label = "Table Result" ;

                jQuery( '<option/>', {
                    value: name,
                    text: label
                } ).appendTo( $resultSelect ) ;

            }

            let trimShell = new RegExp( ".*___ STAGING:.*\n" ) ;
//let trimNonRoot = new RegExp( ".*== Running as non root user.*\n" );
            let trimNonRoot = new RegExp( "_CSAP_OUTPUT_.*\n" ) ;
            function trimOutput( textToTrim ) {

                let skipLine = textToTrim.search( trimShell ) ;
                if ( skipLine != -1 ) {
                    textToTrim = textToTrim.substr( skipLine ) ;
                    skipLine = textToTrim.search( new RegExp( "\n" ) ) ;
                    // console.log("skipLine" + skipLine + " hi" +  text.search( new RegExp("hi") ) ) ;
                    textToTrim = textToTrim.substr( skipLine + 1 ) ;
                }

                skipLine = textToTrim.search( trimNonRoot ) ;
                if ( skipLine != -1 ) {
                    textToTrim = textToTrim.substr( skipLine ) ;
                    skipLine = textToTrim.search( new RegExp( "\n" ) ) ;
                    // console.log("skipLine" + skipLine + " hi" +  text.search( new RegExp("hi") ) ) ;
                    textToTrim = textToTrim.substr( skipLine + 1 ) ;
                }

                return textToTrim ;
            }

            function registerSelectorClickEvents( commandResponseObject ) {

                $resultSelect.off().change( function () {

                    let selected = $resultSelect.val() ;

                    $( "#resultsTextArea" ).show() ;
                    $resultsTableContainer.hide() ;


                    if ( selected == "script" ) {
                        // nop

                    } else if ( selected == "progress" ) {
                        $( ".commandSection" ).hide() ;
                        $resultsContainer.show() ;

                    } else if ( selected == "TableOutput" || command == "logSearch" ) {

                        showOutputInTableFormat( commandResponseObject )

                    } else if ( selected == "Unparsed" ) {
                        $( ".commandSection" ).hide() ;
                        $resultsContainer.show() ;
                        displayResults( JSON.stringify( commandResponseObject, null, "\t" ), false ) ;

                    } else {
                        showOutputInTextArea( commandResponseObject, selected ) ;
                    }
                } ) ;
            }

            function showOutputInTableFormat( commandResponseObject ) {
                $( ".commandSection" ).hide() ;
                $resultsContainer.show() ;
                $resultsTableContainer.show() ;
                $( "#resultsTextArea" ).hide() ;
                $( "#resultsTableBody" ).empty() ;

                for ( let i = 0 ; i < commandResponseObject.otherHosts.length ; i++ ) {

                    let output = "" ;

                    if ( commandResponseObject.otherHosts[i].error != undefined ) {
                        output = commandResponseObject.otherHosts[i].error ;

                    } else {
                        let jsonOutput = commandResponseObject.scriptOutput ;
                        if ( commandResponseObject.otherHosts[i].transferResults ) {
                            jsonOutput = commandResponseObject.otherHosts[i].transferResults.scriptResults ;
                            if ( jsonOutput == undefined ) {
                                jsonOutput = commandResponseObject.otherHosts[i].transferResults.coreResults ;
                            }

                            if ( jsonOutput == undefined ) {
                                jsonOutput = commandResponseObject.otherHosts[i].transferResults ;
                            }
                        }

                        for ( let line = 0 ; line < jsonOutput.length ; line++ ) {

                            output += trimOutput( jsonOutput[line] ) ;
                        }
                    }

                    // let grepGroup = new RegExp("__GROUP__", 'g');
                    // output = output.replace(grepGroup, '<span class="info">INFO</span>') ;

                    if ( command == "logSearch" ) {

                        let groups = output.split( "__CSAPDELIM__" ) ;

                        for ( let group = 0 ; group < groups.length ; group++ ) {
                            let curGroup = groups[group] ;
                            if ( curGroup.indexOf( "grep: unrecognized option" ) != -1 ) {
                                curGroup += '<div class="warning"> Contact your admin to upgrade OS. Uncheack the Separate Matches option</div>'
                            }

                            if ( $( "#searchTarget" ).val() != "*" && $( "#searchTarget" ).val() != "." ) {
                                let searchTarget = new RegExp( $( "#searchTarget" ).val(), 'g' ) ;
                                let match = $( "#searchTarget" ).val().replaceAll(`\\`, "") ;
                                console.log(`match: ${match}`) ;
                                curGroup = curGroup.replace( searchTarget, `<span class="matchTarget">${ match }</span>` ) ;
                            }

                            let matchContent = curGroup ;
                            if ( $( "#reverseOrder" ).is( ":checked" ) ) {
                                let matchLines = curGroup.split( "\n" ) ;
                                matchContent = "" ;
                                for ( let line = matchLines.length - 1 ; line >= 0 ; line-- ) {
                                    matchContent += matchLines[line] + "\n" ;
                                }
                            }
                            let hostRow = jQuery( '<tr/>', {
                                html: '<td class="hostColumn">' + commandResponseObject.otherHosts[i].host + '</td>' +
                                        '<td class="outputColumn">' + matchContent + '</td>'
                            } )

                            buildHostLink( $( ".hostColumn", hostRow ), commandResponseObject.otherHosts[i].host,
                                    '<div class="matchLabel"> Match: ' + ( group + 1 ) + '</div>' ) ;

                            hostRow.appendTo( "#resultsTableBody" ) ;
                        }

                    } else {

                        let hostRow = jQuery( '<tr/>', {
                            html: '<td class="hostColumn">' + commandResponseObject.otherHosts[i].host + '</td>' +
                                    '<td class="outputColumn">' + output + '</td>'
                        } )

                        buildHostLink( $( ".hostColumn", hostRow ), commandResponseObject.otherHosts[i].host, "" ) ;


                        hostRow.appendTo( "#resultsTableBody" ) ;
                    }

//				jQuery('<tr/>', {
//					html: '<td class="hostColumn">' + commandOutputJson.otherHosts[i].host + '</td>' +
//							'<td class="outputColumn">' + output + '</td>'
//				}).appendTo("#resultsTableBody") ;

                }

                $resultsTable.trigger( "update" ) ;
                windowResize() ;
            }

            function showOutputInTextArea( commandResponseObject, hostSelected ) {

                console.log( "showOutputInTextArea, cursor is:", $( 'body' ).css( 'cursor' ), " agentHostUrlPattern:", agentHostUrlPattern ) ;


                if ( $( 'body' ).css( 'cursor' ) != 'default' ) {
                    // Launch fileMonitor when command is still running
                    alertify.notify( "Tailing results on host: " + hostSelected ) ;

                    $( ".commandSection" ).hide() ;
                    $resultsContainer.show() ;

                    let fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log" ;
                    let inputMap = {
                        fileName: XFER_BASE + fullName,
                        "u": "1"
                    } ;
                    let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostSelected ) ;

                    let urlAction = baseUrl + `/${ AGENT_NAME }/file/FileMonitor` ;
                    postAndRemove( "_blank", urlAction, inputMap ) ;
                    return ;
                }

                // Update UI for command completed....

                $( "#outputButton" ).show() ;
                $( "#outputButton" ).off() ;
                $( '#outputButton' ).click( function () {
                    alertify.notify( "Showing output on host: " + hostSelected ) ;

                    let fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log" ;
                    let inputMap = {
                        fromFolder: XFER_BASE + fullName
                    } ;
                    let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostSelected ) ;
                    let urlAction = baseUrl + "/file/downloadFile/xfer_" + $( "#scriptName" ).val() + ".log" ;
                    postAndRemove( "_blank", urlAction, inputMap ) ;
                    return false ; // prevents link
                } ) ;
                $( ".commandSection" ).hide() ;
                $resultsContainer.show() ;
                let jsonOutput = commandResponseObject.scriptOutput ;
                // need to skip for synv
                // if ( commandOutputJson.scriptHost != hostSelected ) {

                for ( let i = 0 ; i < commandResponseObject.otherHosts.length ; i++ ) {
                    let otherHostName = commandResponseObject.otherHosts[i].host ;
                    if ( otherHostName == hostSelected ) {


                        if ( commandResponseObject.otherHosts[i].error != undefined ) {
                            jsonOutput = commandResponseObject.otherHosts[i].error ;

                        } else {

                            if ( commandResponseObject.otherHosts[i].transferResults ) {
                                jsonOutput = commandResponseObject.otherHosts[i].transferResults.coreResults ;
                                if ( commandResponseObject.otherHosts[i].transferResults.scriptResults )
                                    jsonOutput = commandResponseObject.otherHosts[i].transferResults.scriptResults ;
                                if ( commandResponseObject.otherHosts[i].transferResults.errors ) {
                                    jsonOutput = commandResponseObject.otherHosts[i].transferResults.errors ;
                                }
                            } else {
                                jsonOutput = commandResponseObject.scriptOutput ;
                            }
                        }
                        //jsonOutput = commandOutputJson.otherHosts[i].transferResults.scriptResults;

                        break ;
                    }
                }

                //}

                output = "\n" ;
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
                let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName ) ;
                let linkUrl = baseUrl + "/app-browser"
                if ( serviceNameParam != "" && command == "logSearch" ) {
                    let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName ) ;
                    linkUrl = baseUrl + "/file/FileMonitor?isLogFile=true&serviceName="
                            + serviceNameParam + "&hostName=" + hostName ;
                }

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

            let commandOutputJson = null ;

            function build_command_editor() {
                _command_editor = aceEditor.edit( aceEditorId ) ;
                _command_editor.setOptions( {
                    mode: "ace/mode/sh",
//            theme: "ace/theme/textmate",
                    theme: "ace/theme/dracula",
                    newLineMode: "unix",
                    tabSize: 2,
                    fontSize: "11pt",
                    useSoftTabs: true,
                    printMargin: false
                } ) ;

                _command_editor.getSession().on( 'change', function () {
                    $( "#scriptText" ).val( _command_editor.getValue() ) ;
                    console.log( "content updated" ) ;

                    //document.getElementById("output").innerHTML=editor.getValue();
                } ) ;

                $( "#" + aceEditorId ).show() ;
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


            function windowResize() {


//        let displayHeight = Math.round( $( window ).outerHeight( true )
//                - $( "header" ).outerHeight( true ) - 10 ) ;
//        let displayWidth = Math.round( $( window ).outerWidth( true ) ) ;
//
//        //$( "form" ).css( "width", displayWidth - 12 ) ;
//
//
//        console.log( "displayHeight: ", displayHeight, "  displayWidth: ", displayWidth ) ;
//
//
//        $( "#tabs>div" ).css( "height", displayHeight - 30 ) ;
//        $( ".commandSection" ).css( "height", displayHeight - 100 ) ;
//        $( ".commandSection" ).css( "width", displayWidth - 12 ) ;
//
//        // $(".commandSection textarea").css("height",  displayHeight-100 ) ;
//
//        if ( _command_editor ) {
//            let container = $( "#scriptText" ).parent().parent() ;
//            container.css( "height", displayHeight - 125 ) ;
//            $( "#" + aceEditorId ).css( "height", displayHeight - 125 ) ;
//            $( "#" + aceEditorId ).css( "width", displayWidth - 50 ) ;
//            _command_editor.resize() ;
//        }
//
//        $( "#resultsTextArea" ).css( "height", displayHeight - 125 ) ;
//        $( "#resultsTextArea" ).css( "width", displayWidth - 80 ) ;

                $( "#contents" ).scrollTop( $( "#contents" ).scrollTop() + 1 ) ;
            }


            let fileOffset = "-1" ;
            let fromFolder = "" ;
            let isLogFile = false ;

            function getChanges() {

                clearTimeout( checkForChangesTimer ) ;
                // $('#serviceOps').css("display", "inline-block") ;


                // console.log("Hitting Offset: " + fileOffset) ;
                let requestParms = {
                    serviceName: AGENT_ID,
                    hostName: hostName,
                    fromFolder: fromFolder,
                    bufferSize: 100 * 1024,
                    logFileOffset: fileOffset,
                    isLogFile: isLogFile
                } ;

                $.getJSON(
                        "../file/getFileChanges",
                        requestParms )

                        .done( function ( hostJson ) {
                            getChangesSuccess( hostJson ) ;
                        } )

                        .fail( function ( jqXHR, textStatus, errorThrown ) {

                            handleConnectionError( "Retrieving changes for file " + fromFolder, errorThrown ) ;
                        } ) ;
            }

            function haltChangesRefresh() {
                clearTimeout( checkForChangesTimer ) ;
                checkForChangesTimer = 0 ;
            }

            function  getChangesSuccess( changesJson ) {

                if ( changesJson.error ) {
                    console.log( "Failed getting status from host due to:" + changesJson.error ) ;
                    console.log( "Retrying..." ) ;

                } else {


                    for ( let i = 0 ; i < changesJson.contents.length ; i++ ) {
                        let fileChanges = changesJson.contents[i] ;
                        displayResults( fileChanges, true ) ;
                        checkResultsScroll( true ) ;
                    }


                    fileOffset = changesJson.newOffset ;
                    // $("#fileSize").html("File Size:" + changesJson.currLength) ;

                }
                let refreshTimer = 2 * 1000 ;

                checkForChangesTimer = setTimeout( getChanges, refreshTimer ) ;


            }


            function isResultsMinSize() {
//	if ( $("#toggleResultsImage").attr('src').indexOf("maxWindow") != -1 ) {
//		return true;
//	}

                return false ;
            }

            function toggleResultsButton( toggleButton ) {

                // alert( $("#toggleResultsImage").attr('src').indexOf("maxWindow") ) ;
                if ( isResultsMinSize() ) {

                    $( "#resultPre" ).css( 'overflow-y', 'visible' ) ;
                    $( "#resultPre" ).css( 'height', 'auto' ) ;

                    $( "#resultPre pre" ).css( 'overflow-y', 'visible' ) ;
                    $( "#resultPre pre" ).css( 'height', 'auto' ) ;

                    $( "#toggleResultsImage" ).attr( 'src', '../images/restoreWindow.gif' ) ;

                    $( "#mainDisplayArea" ).hide() ;
                } else {
                    $( "#resultPre" ).css( 'height', '150px' ) ;
                    $( "#resultPre" ).css( 'overflow-y', 'auto' ) ;


                    $( "#resultPre pre" ).css( 'height', '150px' ) ;
                    $( "#resultPre pre" ).css( 'overflow-y', 'auto' ) ;


                    $( "#mainDisplayArea" ).show() ;
                    $( "#toggleResultsImage" ).attr( 'src', '../images/maxWindow.gif' ) ;
                }
            }

            function displayResults( results, append ) {

                if ( !append ) {
                    $( "#resultsTextArea" ).val( "" ) ;
                }
                //$("#resultPre pre").append(results);
                let testDataToFillOutput = "asdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\n"
                testDataToFillOutput = "" ;

                $( "#resultsTextArea" ).val( $( "#resultsTextArea" ).val() + results + testDataToFillOutput ) ;

                checkResultsScroll( append ) ;

                $( "#resultsTextArea" ).show() ;

            }

            function checkResultsScroll( append ) {

                // needed when results is small
                // $("#resultPre pre").scrollTop($("#resultPre pre")[0].scrollHeight);

                if ( append )
                    $( "#resultsTextArea" ).scrollTop( $( "#resultsTextArea" )[0].scrollHeight ) ;
                else
                    $( "#resultsTextArea" ).scrollTop( 0 ) ;

                // Needed when results are maxed
                if ( !isResultsMinSize() ) {
                    $( "html, body" ).animate( {
                        scrollTop: $( document ).height()
                    }, "fast" ) ;
                }
            }

        } ) ;

    }

} ) ;
