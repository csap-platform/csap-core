

define( [ "ace/ace", "ace/ext-modelist", "jsYaml/js-yaml" ], function ( aceEditor, aceModeListLoader, jsYaml ) {


    console.log( "Module loaded" ) ;
    let _aceEditors = null ;

    let _jsonParseTimer = null ;
    let SAMPLE = "*Sample" ;
    let _currentDialog = null ;
    const ROOT = "ROOT" ;
    let _updateDefinitionFunction = null ;


    let dialog_loaded_defer ;
    let lag_attempts = 0 ;
    let data_loaded_defer ;
    let configure_done_defer ;
    let _notifyChangeFunction = null ;

    let _updateGlobalDefinitionFunction = null ;

    let _childResizeFunction = null ;

    let _aceDefaults = {
        tabSize: 2,
        useSoftTabs: true,
        newLineMode: "unix",
        theme: "ace/theme/chrome", //kuroir  Xcode tomorrow, tomorrow_night, dracula, crimson_editor
        printMargin: false,
        fontSize: "11pt",
        wrap: true
    } ;

    let _yamlError ;

    let utils ;

    let commentBlocks = [
        "alerts", "environmentVariables", "docker", "scheduledJobs", "performance", "files",
        "hosts", "template-references", "masters", "monitors",
        "configuration-maps", "vsphere"
    ] ;



    let jsonForms = {

        //
        // Convenience Methods for working with JSON objects
        //
        getValue: function ( jsonKey, definitionJson ) {
            let defManager = new DefinitionManager( definitionJson, jsonKey, false ) ;
            return defManager.getValue() ;
        },

        mergeJson: function ( dotPathToUpdate, updatedJson, ) {

            let defManager = new DefinitionManager( fullDefinitionJson, dotPathToUpdate, false ) ;
            let  parsedJson = JSON.parse( updatedJson ) ;
            defManager.updateValue( parsedJson ) ;

            return defManager.getValue() ;
        },

        //
        //  methods for csap dialog editors
        //

        getJsonText: function ( $textArea ) {

            return getJsonText( $textArea ) ;

        },

        setAceDefaults: function ( aceDefaults ) {
            _aceDefaults = aceDefaults ;
        },

        setUtils: function ( _utils ) {
            utils = _utils ;
        },

        assign_editor_mode: function ( $targetText, file_name ) {
            let _aceModeList = aceEditor.require( "ace/ext/modelist" ) ;
            let fileMode = _aceModeList.getModeForPath( file_name ).mode ;
            $targetText.data( "ace-mode", fileMode ) ;

        },

        getRoot: function () {
            return ROOT ;
        },

        showDialog: function ( dialogId, _resizeFunction, editDialogHtml, updateDefinitionFunction ) {

            destroyDialog() ;
            _childResizeFunction = _resizeFunction ;
            _updateDefinitionFunction = updateDefinitionFunction ;
            reset_load_events() ;
            showDialog( dialogId, editDialogHtml, updateDefinitionFunction ) ;
            return ;
        },

        closeDialog: function () {
            destroyDialog() ;
        },

        // used to alert when changes are completed
        registerForChangeNotification: function ( notifyChangeFunction ) {

            _notifyChangeFunction = notifyChangeFunction ;
        },

        registerOperations: function ( updateDefinitionFunction ) {

            _updateDefinitionFunction = updateDefinitionFunction ;
            registerOperations() ;
        },
        areThereErrors: function () {
            let numErrors = $( ".jsonFormsError" ).length ;
            if ( numErrors > 0 ) {
                alertify.alert( "Request aborted", "Parsing errors must be corrected prior to further processing" ) ;
                return true
            }
            return false ;
        },
        //
        //  need to reload definiton in js editor after updates
        //
        configureUpdateFunction: function ( updateFunction ) {
            _updateGlobalDefinitionFunction = updateFunction ;
        },
        runUpdateFunction: function () {
            console.log( "runUpdateFunction() ", _updateGlobalDefinitionFunction ) ;
            if ( _updateGlobalDefinitionFunction != null )
                _updateGlobalDefinitionFunction( true ) ;
        },
        //
        //
        loadValues: function ( dialogId, definitionJson, defaultValues ) {

            $.when( dialog_loaded_defer ).done( function () {
                loadValues( dialogId, definitionJson, defaultValues ) ;
                data_loaded_defer.resolve() ;
            } ) ;

        },

        isDialogBuilt: function ( dialogId ) {
            return alertify[ dialogId ] ;
        },
        //
        updateDefinition: function ( definitionJson, $inputChanged, optionalValue ) {
            return updateDefinition( definitionJson, $inputChanged, optionalValue ) ;
        },
        resizeVisibleEditors: function ( definitionId, dialogContainer, editorPanelId, readOnly = false ) {
            $.when( dialog_loaded_defer, data_loaded_defer, configure_done_defer ).done( function () {
                resizeVisibleEditors( definitionId, dialogContainer, editorPanelId, readOnly ) ;
            } ) ;
        },
        configureJsonEditors: function ( getDefinitionFunction, dialogContainer, editorPanelId, definitionDomId ) {

            console.log( `configureJsonEditors() dialogContainer: ${dialogContainer} editorPanelId: ${editorPanelId}` ) ;
            if ( _aceEditors == null ) {
                reset_load_events() ; // hack for desktop development
            }

            let isCsapJsonBrowser = ( dialogContainer == "fitContent" ) ;
            let isCsapDesktopTesting = ( dialogContainer == "body" ) ;
            if ( isCsapJsonBrowser || isCsapDesktopTesting ) {
                console.log( `configureJsonEditors() externalAce` ) ;
                // non dialog based usages - no need to defer loading
                dialog_loaded_defer.resolve() ;
                configure_done_defer.resolve() ;
            }

            $.when( dialog_loaded_defer, data_loaded_defer ).done( function () {
                configureJsonEditors( getDefinitionFunction,
                        dialogContainer,
                        editorPanelId,
                        definitionDomId ) ;
                configure_done_defer.resolve() ;

                if ( !isCsapJsonBrowser ) {
                    resizeVisibleEditors( definitionDomId, dialogContainer, editorPanelId ) ;
                }
            } ) ;

        },
        parseAndUpdateJsonEdits: function ( definitionJson, $jsonTextArea, definitionDomId ) {
            return parseAndUpdateJsonEdits( definitionJson, $jsonTextArea, definitionDomId ) ;
        },

        showUpateResponseDialog: function ( resultsTitle, responseMessage, okFunction ) {
            showUpateResponseDialog( resultsTitle, responseMessage, okFunction ) ;
        }
    }
    return jsonForms ;

    function destroyDialog() {
        // multiple instances will conflict on selectors

        if ( _currentDialog != null ) {
            console.log( `destroyDialog() Closing Dialog, and deleting instance` ) ;
            _currentDialog.destroy() ;
            _currentDialog = null ;
        } else {
            console.log( `destroyDialog() __currentDialog is empty` )
        }
    }

    function reset_load_events() {
        _aceEditors = new Object() ;
        lag_attempts = 0 ;
        dialog_loaded_defer = new $.Deferred() ;
        configure_done_defer = new $.Deferred() ;
        data_loaded_defer = new $.Deferred() ;
    }

    function showUpateResponseDialog( resultsTitle, responseMessage, okFunction ) {

        // modifying OK function, so we do NOT use the global alert definition

        if ( !alertify.updateResponseDialog ) {
            //define a new dialog
            alertify.dialog( 'updateResponseDialog', function factory() {
                return { } ;
            }, false, "alert" ) ;
        }

        //launch it.
        alertify.updateResponseDialog( resultsTitle, responseMessage, okFunction ) ;
    }



    function loadValues( dialogId, definitionJson, defaultValues ) {
        console.groupCollapsed( "loadValues() for dialog: ", dialogId ) ;
        //console.log( "definitionJson: ", definitionJson, " defaults: ", defaultValues );

        let undefinedAttributes = new Array() ;
        $( dialogId + ' [data-path]' ).each( function () {

            let $itemContainer = $( this ) ;
            let jsonKey = $itemContainer.data( "path" ) ; // (this).data("host")

            let isRemoveNewLines = $itemContainer.data( "removenewlines" ) ;
            let isSort = $itemContainer.data( "sort" ) ;

            //console.log( "Resolving: " + jsonKey );
            let defManager = new DefinitionManager( definitionJson, jsonKey, false ) ;
            let dataValue = defManager.getValue() ;

            if ( jsonKey === ROOT ) {
                dataValue = definitionJson ;
            }

            let currentElementValue = $itemContainer.val() ;
            console.debug( `jsonKey: ${jsonKey}, type: ${$itemContainer.prop( 'tagName' )},`
                    + ` id: ${ $itemContainer.attr( "id" ) },  dataValue: ${ dataValue },`
                    + ` current value: ${ max50( currentElementValue ) }, type: ${ typeof dataValue} ` ) ;

            if ( !$itemContainer.is( "select" )
                    && !$itemContainer.is( "textarea" )
                    && !$itemContainer.is( "input" )
                    ) {
                console.log( `unsupported element type: '${ $itemContainer.prop( 'tagName' ) }'` ) ;
                return ;
            }
            if ( currentElementValue == null ) {
                currentElementValue = "" ;
            }
            if ( dataValue != undefined
                    && dataValue != null ) {

//                if ( currentElementValue.indexOf( SAMPLE ) != 0 ) {
//                    $itemContainer.css( "font-style", "normal" );
//                    $itemContainer.css( "color", "black" );
//                }

                if ( isConvertYaml( $itemContainer ) ) {
                    console.log( `Loading yaml ${ jsonKey } ` ) ;
                    const yamlText = dumpAndIndentAsYaml( dataValue, "root" ) ;
                    $itemContainer.val( yamlText ) ;
                    //$itemContainer.css( "white-space", "pre" ) ;

                } else if ( isConvertLines( $itemContainer ) ) {
                    console.log( "Loading jsonarrayToText", jsonKey )
                    let textLines = "" ;
                    for ( let i = 0 ; i < dataValue.length ; i++ ) {

                        if ( textLines != "" ) {
                            textLines += "\n"
                        }
                        textLines += dataValue[i] ;
                    }
                    $itemContainer.val( textLines ) ;
                    $itemContainer.css( "white-space", "pre" ) ;

                } else if ( typeof dataValue == 'object' || typeof dataValue == 'boolean' ) {

                    if ( isSort ) {

                        if ( dataValue.sort ) {
                            dataValue = dataValue.sort() ;
                        } else {
                            let fields = new Array() ;
                            for ( let field in dataValue )
                                fields.push( field ) ;
                            fields.sort() ;
                            let sortedObject = new Object() ;
                            for ( let i = 0 ; i < fields.length ; i++ ) {
                                sortedObject[ fields[i] ] = dataValue[ fields[i] ] ;
                            }
                            dataValue = sortedObject ;

                        }

                    }

                    let valAsString = JSON.stringify( dataValue, null, "\t" ) ;
                    if ( isRemoveNewLines ) {
                        //$itemContainer.css( "white-space", "normal" ) ;
                        valAsString = JSON.stringify( dataValue, null, " " ) ;
                        valAsString = valAsString.replace( /(\r\n|\n|\r)/gm, "  " ) ;
                    }
                    $itemContainer.val( valAsString ) ;
                } else if ( dataValue != "" ) {
                    if ( isRemoveNewLines ) {
                        $itemContainer.css( "white-space", "normal" ) ;
                    }
                    $itemContainer.val( dataValue ) ;
                }
            } else {
                undefinedAttributes.push( jsonKey ) ;

                let defaultDefinitionManager = new DefinitionManager( defaultValues, jsonKey, false ) ;
                let foundValues = defaultDefinitionManager.getValue() ;
                console.debug( "Defaults: key", jsonKey, foundValues ) ;
                // defaultValues
                if ( foundValues ) {
                    //console.log( "Found Default	for key: ", jsonKey );
                    let displayContent = JSON.stringify( foundValues, null, "\t" ) ;
                    if ( typeof foundValues === 'string' ) {
                        displayContent = foundValues ;
                    }
                    $itemContainer.val( SAMPLE + ": " + displayContent ) ;
//                    $itemContainer.css( "font-style", "italic" );
//                    $itemContainer.css( "color", "grey" );
                    let isPlainText = $itemContainer.data( "plaintext" ) ;
                    if ( isPlainText ) {
                        $itemContainer.val( SAMPLE + ": " + foundValues ) ;
                    }
                } else {
                    console.debug( "no Default for key:", jsonKey ) ;
                    $itemContainer.val( "" ) ;
                }
            }

        } ) ;

        console.log( "Service does not contain the following elements", undefinedAttributes ) ;
        console.groupEnd() ;
    }

    function dumpAndIndentAsYaml( editSnippet, path ) {

        console.log( `dumpAndIndentAsYaml: ${ path } ` )

        let options = {
            indent: 2
//            forceQuotes: true supported 4.x
        }
        let yamlText = jsYaml.dump( editSnippet, options ) ;

        //if ( $( "#pje-yaml-spacing", $projectJsonPre.parent() ).is( ':checked' ) ) {

//        let spaceTopLevel = path.startsWith( "service-templates." )
//                || path.startsWith( "project" ) ;


        yamlText = utils.yamlSpaces(
                yamlText,
                commentBlocks,
                false ) ;
        //}

        return yamlText ;
    }

    function registerOperations() {

        $( ".notifyButton" ).off().click( function () {
            alertify.prompt( "Definition Update Request",
                    'Your account does not have infra admin permissions. After modifying the configuration, provide a summary of the change to be submitted for review:',
                    "sample: Java Heap has been updated based on last LT.",
                    function ( evt, reason ) {
                        //console.log( 'reason: ' + reason );
                        _updateDefinitionFunction( "notify", null, null, null, reason ) ;
                    },
                    function () {
                        console.log( "canceled" ) ;
                    }
            ) ;
        } ) ;

        $( ".addDefButton" ).off().click( function () {

            utils.loading( "Processing Fields" ) ;

            setTimeout( function () {

                let isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" ) ;
                _updateDefinitionFunction( "add", isUpdate, _updateGlobalDefinitionFunction ) ;

            }, 500 ) ;

        } ) ;

        $( ".updateDefButton" ).off().click( function () {

            utils.loading( "Processing Fields" ) ;

            setTimeout( function () {
                let isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" ) ;
                _updateDefinitionFunction( "modify", isUpdate, _updateGlobalDefinitionFunction ) ;
            }, 500 ) ;

        } ) ;

        $( ".deleteDefButton" ).off().click( function () {

            utils.loading( "Processing Fields" ) ;

            setTimeout( function () {

                let isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" ) ;
                _updateDefinitionFunction( "delete", isUpdate, _updateGlobalDefinitionFunction ) ;

            }, 500 ) ;

        } ) ;

        $( ".renameDefButton" ).off().click( function () {

            if ( !$( "#opsNewName" ).is( ':visible' ) ) {

                alertify.prompt( "Renaming Dialog", 'Enter the new name', getNewName(),
                        function ( evt, value ) {
                            console.log( 'You entered: ' + value ) ;
                            let isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" ) ;
                            _updateDefinitionFunction( "rename", isUpdate, _updateGlobalDefinitionFunction, value ) ;
                        },
                        function () {
                            console.log( "canceled" ) ;
                        }
                ) ;
                return ;
            }
        } ) ;

        $( ".pushDefButton" ).off().click( function () {

            if ( !$( "#opsNewName" ).is( ':visible' ) ) {

                alertify.prompt( "Push Dialog", 'Enter the target environment name or leave blank to use the first import', "",
                        function ( evt, value ) {
                            console.log( 'You entered: ' + value ) ;
                            let isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" ) ;
                            _updateDefinitionFunction( "push", isUpdate, _updateGlobalDefinitionFunction, value ) ;
                        },
                        function () {
                            console.log( "canceled" ) ;
                        }
                ) ;
                return ;
            }
        } ) ;
        $( ".copyDefButton" ).off().click( function () {

            if ( !$( "#opsNewName" ).is( ':visible' ) ) {

                alertify.prompt( "Copy Dialog", 'Enter the name for the copy', getNewName(),
                        function ( evt, value ) {
                            console.log( 'You entered: ' + value ) ;
                            let isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" ) ;
                            _updateDefinitionFunction( "copy", isUpdate, _updateGlobalDefinitionFunction, value ) ;
                        },
                        function () {
                            console.log( "canceled" ) ;
                        }
                ) ;
                return ;
            }
        } ) ;

    }

    function getNewName() {
        let newName = "updated-name" ;

        if ( $( "#dialogOpsContainer" ).is( ':visible' ) ) {
            let $newSelect = $( "select", $( "#dialogOpsContainer" ) ) ;
            if ( $newSelect.length = 1 ) {
                newName = $newSelect.val() ;
            }
        }
        return newName ;
    }


    function showDialog( dialogId, editDialogHtml, updateDefinitionFunction ) {


        let $batchContent = $( "<html/>" ).html( editDialogHtml ).find( '#dialogContents' ) ;
        console.log( `showDialog(): '${dialogId}' content Length:  ${ $batchContent.text().length }` ) ;

        let buttons = [ {
                text: "Update",
                className: alertify.defaults.theme.ok, key: 0
            }, {
                text: "Validate",
                className: alertify.defaults.theme.ok, key: 0
            },
            {
                text: "Close",
                invokeOnClose: true,
                className: alertify.defaults.theme.cancel
//                key: 27 // escape key
            } ] ;

        if ( !alertify[ dialogId ] ) {
            //isNewDialog = true;
//            _base = alertify.dialog( dialogId, dialogFactory, true, 'alert' );

            let configuration = {
                content: $batchContent.html(),
                onok: dialogOk,
                onresize: dialogResized,
                buttons: buttons
            }

            let csapDialogFactory = CsapCommon.dialog_factory_builder( configuration ) ;

            alertify.dialog( dialogId, csapDialogFactory, true, 'alert' ) ;
        }

        _currentDialog = alertify[ dialogId ]( $batchContent.html() ) ;

        registerOperations() ;
    }


    function dialogOk( closeEvent = false ) {
        console.log( "dialogClosed(): dialog event:  ", JSON.stringify( closeEvent ) ) ;

        if ( closeEvent && closeEvent.button.text === "Update" ) {
            _updateDefinitionFunction( true, _updateGlobalDefinitionFunction ) ;
            return false ;

        } else if ( closeEvent && closeEvent.button.text === "Validate" ) {
            _updateDefinitionFunction( false ) ;
            return false ;

        } else {
            destroyDialog() ;
        }

        return ;

    }

    function dialogResized( dialogWidth, dialogHeight ) {


        let $ops = $( "#dialogOpsContainer" ) ;
        let opsHeight = Math.round( $ops.outerHeight( true ) ) ;
        let tabsHeight = 30 ;

        $( "div.json-form-autosize-panel" )
                .css( "height", dialogHeight - opsHeight - tabsHeight - 50 )
                .css( "overflow", "auto" ) ;

        console.log( `sizeTheDialog() :  height: ${dialogWidth} opsHeight: ${opsHeight}, off tabsHeight: ${tabsHeight} ` ) ;
        dialog_loaded_defer.resolve() ;
        if ( CsapCommon.isFunction( _childResizeFunction ) ) {
            _childResizeFunction() ;
        }


    }

    function DefinitionManager( _definitionJson, _jsonKey, _isCreateMissing ) {

        let definitionJson = _definitionJson ;
        let jsonKey = _jsonKey ;
        let isCreateMissing = _isCreateMissing ;

        let elementToUpdate = null ;
        let attributeMatched = null ;
        //var attributeIndex = null ;

        // traverses the specified definition to find the object.
        // support for arrays?
        function initialize() {
            //console.log("definitionJson", definitionJson) ;

            let attributeKeys = jsonKey.split( '.' ) ;
            elementToUpdate = definitionJson ;

            let matchAttributeIndex = 0 ;
            for ( matchAttributeIndex = 0 ; matchAttributeIndex < attributeKeys.length - 1 ; matchAttributeIndex++ ) {
                try {

                    let currentPath = attributeKeys[matchAttributeIndex] ;
                    //console.log( "processing path: ", currentPath );
                    let arrayStart = currentPath.indexOf( "[" ) ;
                    let arrayIndex = -1 ;
                    if ( arrayStart > 0 ) {
                        //console.log( "Detected array" );
                        //elementToUpdate = elementToUpdate[   ];
                        arrayIndex = currentPath.substring( arrayStart + 1, currentPath.length - 1 ) ;

                        currentPath = currentPath.substring( 0, arrayStart ) ;
                        //console.log( "Index found: ", arrayIndex, " from: ", currentPath );

                        if ( isCreateMissing && elementToUpdate[ currentPath  ] == undefined ) {
                            console.log( "Creating array: ", currentPath ) ;
                            elementToUpdate[ currentPath  ] = new Array() ;
                        }
                        elementToUpdate = elementToUpdate[ currentPath ] ;

                        // now roll forward to index
                        currentPath = arrayIndex ;

                    }
                    // parent does not exist
                    if ( isCreateMissing && elementToUpdate[ currentPath  ] == undefined ) {
                        console.log( "Creating object: ", currentPath ) ;
                        elementToUpdate[ currentPath  ] = { } ;
                    }

                    //console.log( "Assigning: ", elementToUpdate[ currentPath ] );
                    if ( elementToUpdate ) {
                        elementToUpdate = elementToUpdate[ currentPath ] ;
                        //break;
                    }

                } catch ( e ) {
                    let message = "Failed to locate:  " + jsonKey + ", reason: " + e ;
                    console.log( message, e ) ;
                }
            }

            attributeMatched = attributeKeys[ matchAttributeIndex ] ;
            if ( jsonKey === `docker.deployment-file-names` ) {
                console.log( `DefinitionManager: ${jsonKey} \n attributeIndex: ${ matchAttributeIndex } \n attributeMatched: ${attributeMatched} object: `, elementToUpdate ) ;
            }

        }
        ;

        this.getValue = getValue ;
        function getValue() {
            //console.log( "DefinitionManager: getValue: ", attributeMatched, " object:", elementToUpdate ) ;
            if ( elementToUpdate )
                return elementToUpdate[ attributeMatched ] ;

            //console.log("No value found for: ", jsonKey) ;
            return undefined ;
        }
        this.updateValue = updateValue ;
        function updateValue( stringOrArrayOrObject ) {

            let desc = max50( stringOrArrayOrObject ) ;
            console.log( `\n\n updating  attribute: '${ attributeMatched }' `
                    + `\n\t jsonKey: ${jsonKey} \n\t type: ${ typeof stringOrArrayOrObject} `
                    + ` new value: ${ desc }` ) ;
            console.log( `stringOrArrayOrObject`, stringOrArrayOrObject ) ;

            if ( jsonKey === ROOT ) {
                definitionJson = stringOrArrayOrObject ;
            } else {

                if ( $.isNumeric( stringOrArrayOrObject ) ) {
                    console.debug( ` is a number ${ stringOrArrayOrObject }` ) ;
                    elementToUpdate[ attributeMatched ] = Number.parseInt( stringOrArrayOrObject ) ;
                } else {
                    console.debug( ` is not a number ${ stringOrArrayOrObject }` ) ;
                    elementToUpdate[ attributeMatched ] = stringOrArrayOrObject ;
                }
            }

            if ( _notifyChangeFunction ) {
                _notifyChangeFunction() ;
            }
        }

        this.deleteValue = deleteValue ;
        function deleteValue( ) {
            console.log( "Removing empty element: " + attributeMatched ) ;
            delete elementToUpdate[ attributeMatched ] ;
        }

        initialize() ;
    }


    function max50( someValueObject ) {
        let desc = String( someValueObject ) ;
        if ( desc && desc.length > 50 ) {
            desc = desc.substring( 0, 50 ) + "<truncated>" ;
        }
        return desc ;
    }

    function updateDefinition( definitionJson, $inputChanged, optionalValue ) {
        let inputPath = $inputChanged.data( "path" ) ;

        if ( !inputPath ) {
            console.log( `ignoring ${$inputChanged.val()}, no path` ) ;
            return ;
        }

        let jsonUpdater = new DefinitionManager( definitionJson, inputPath, true ) ;

        let isRawJson = $inputChanged.data( "json" ) ;
        let isRemoveNewLines = $inputChanged.data( "removenewlines" ) ;

        if ( optionalValue ) {
            // usually for textareas with raw json
            jsonUpdater.updateValue( optionalValue ) ;

        } else {

            if ( $inputChanged.is( "span" ) ) {
                console.log( "simple text substition" ) ;
                jsonUpdater.updateValue( $inputChanged.text() ) ;
            } else {

                try {
                    if ( isRawJson ) {

                        let content = $inputChanged.val() ;
                        console.log( `attempting json parse of \n ${content}` ) ;

                        if ( content.length > 0 ) {
                            let  parsedJson = JSON.parse( content ) ;
                            jsonUpdater.updateValue( parsedJson ) ;
                        } else {
                            jsonUpdater.deleteValue( ) ;
                        }


                    } else {
                        let newVal = $inputChanged.val() ;
                        if ( isConvertYaml( $inputChanged ) ) {
                            //let  parsedJson = JSON.parse( newVal ) ;
                            console.log( `attempting load of content as yaml` ) ;
                            const updatedYaml = jsYaml.safeLoad( newVal ) ;
                            newVal = JSON.stringify( updatedYaml, null, 2 )
                            console.debug( `\n\n converted`, newVal ) ;

                        } else if ( isRemoveNewLines ) {
                            console.log( "Removing new lines" ) ;
                            newVal = newVal.replace( /(\r\n|\n|\r)/gm, " " ) ;
                        }
                        jsonUpdater.updateValue( newVal ) ;
                        if ( newVal.trim() == "" ) {
                            jsonUpdater.deleteValue() ;
                        }
                    }
                    $inputChanged.css( "background-color", "yellow" ) ;
//                    $inputChanged.animate( {
//                        "background-color": "yellow"
//                    }, 1000 ).fadeOut( "fast" ).fadeIn( "fast" ) ;

                } catch ( e ) {
                    $inputChanged.css( "background-color", "#F2D3D3" ) ;
//                    $inputChanged.animate( {
//                        "background-color": "#F2D3D3"
//                    }, 1000 ).fadeOut( "fast" ).fadeIn( "fast" ) ;
                    console.error( "Failed parsing input", $inputChanged.val(), e ) ;
                    alertify.csapWarning( "Parsing Errors found in defintion. Correct before continuing." + e ) ;
                }
            }

        }
        //_serviceJson[ jsonKey ] = $( this ).val(); 
        return ;
    }


    function resizeVisibleEditors( definitionId, dialogContainer, editorPanelId, readOnly ) {

//        setTimeout( () => {
//            // handles scroll bars so tab contents scroll
//            let max_height = containerHeight( dialogContainer, editorPanelId );
//            console.log( `resizeVisibleEditors(): tab scroll height: '${max_height}'` );
//            $( "div.json-form-autosize-panel" )
//                    .css( "height", max_height + 50 )
//                    .css( "overflow", "auto" );
//        }, 500 );

        console.groupCollapsed( `resizeVisibleEditors: definition id: '${definitionId}', dialogContainer: '${dialogContainer}', editorPanel: '${editorPanelId}'` ) ;

        let textareafilter = ".ui-tabs-panel:visible textarea" ;

        if ( dialogContainer == "fitContent" ) {
            isExternalAceBuilder = true ;
            textareafilter = "textarea.fitContent"
        }
        console.log( `filtered by: '${textareafilter}'` ) ;

        $( textareafilter, $( dialogContainer + " #dialogOpsContainer" ).parent() ).each( function () {

            let $targetTextArea = $( this ) ;

            if ( $targetTextArea.hasClass( "ace_text-input" ) ) {
                return ;
            }
            let targetTextId = $targetTextArea.attr( "id" ) ;
            console.log( `\n\n Updating textarea: '${ targetTextId }'` ) ;

            if ( targetTextId == undefined ) {
                console.log( `Found undefined textarea, aborting processing`, $targetTextArea.parent().html() ) ;
                return ;
            }

            let targetWidth = containerWidth( dialogContainer, editorPanelId ) ;
            let adjustWidth = $targetTextArea.data( "adjustwidth" ) ;
            if ( adjustWidth ) {
                targetWidth = targetWidth - adjustWidth ;
            }

            let fixedwidth = $targetTextArea.data( "fixedwidth" ) ;
            if ( fixedwidth ) {
                targetWidth = fixedwidth ;
            }


            console.log( `textbox: '${ $targetTextArea.attr( "id" ) }' width:  ${targetWidth}` ) ;
            $targetTextArea.css( "width", targetWidth ) ;

            let textHeight = containerHeight( dialogContainer, editorPanelId ) ;
            let fixedheight = $targetTextArea.data( "fixedheight" ) ;
            if ( fixedheight ) {
                textHeight = fixedheight ;

            } else {

                let isFitSize = $targetTextArea.data( "fit" ) ;
                let adjustSize = $targetTextArea.data( "adjust" ) ;

                if ( !adjustSize ) {
                    adjustSize = 40 ;
                } else {
                    adjustSize = parseInt( adjustSize ) ;
                }

                console.log( " resizeVisibleEditors() container: " + targetTextId + " adjustSize: " + adjustSize ) ;
                $targetTextArea.show() ;
                if ( isFitSize ) {
                    textHeight = $targetTextArea[0].scrollHeight ;
                    console.log( `fit size: ${textHeight}` ) ;
                }

                textHeight += adjustSize ;
            }
            $targetTextArea.hide() ;
//            if ( $targetTextArea.val().contains( SAMPLE )  ) {
//                textHeight = $targetTextArea[0].scrollHeight ;
//            }

            textHeight = Math.round( textHeight ) ;

            if ( targetTextId == "monitors" ) {
                textHeight = "12em" ;
            }


            if ( $targetTextArea.attr( "id" ) != undefined ) {

                let aceEditorId = getAceEditorId( $targetTextArea, dialogContainer ) ;

                if ( !_aceEditors.hasOwnProperty( aceEditorId ) ) {

                    build_ace_editor( targetTextId, aceEditorId, $targetTextArea ) ;

                    update_editor_state( $targetTextArea,
                            _aceEditors[ aceEditorId ],
                            false, readOnly ) ;

                } else {

                    update_editor_state( $targetTextArea,
                            _aceEditors[ aceEditorId ],
                            false, readOnly ) ;

                    // bridging text content back into ace
                    const lagForRaceLoadingMs = 100 ;
                    setTimeout( function () {

                        let originalSourceOptionalYaml = getJsonText( $targetTextArea, true ) ;
                        let workingSource = _aceEditors[ aceEditorId ].getValue() ;
                        //console.log( "\n\n============= pre textArea: ", originalSourceOptionalYaml , "\n\n ============== pre aceEditor: ",  workingSource ) ;
                        //console.log( "\n\n============= textArea: ", workingSource , "\n\n ============== aceEditor: ",  workingSource ) ;

                        if ( originalSourceOptionalYaml != workingSource ) {
                            console.log( `\n\n content of ace ${ aceEditorId }`
                                    + ` differs from text:  ${targetTextId} \n\t forcing update of ace` ) ;
                            _aceEditors[ aceEditorId ].setValue( originalSourceOptionalYaml, 1 ) ;
                        }

                    }, lagForRaceLoadingMs ) ;
                }

                $( "#" + aceEditorId, $targetTextArea.parent() )
                        .css( "height", textHeight )
                        .css( "width", $targetTextArea.css( "width" ) ) ;

                _aceEditors[ aceEditorId ].resize() ;

                let jsonKey = $targetTextArea.data( "path" )
                let lastActivatedEditor = _aceEditors[ aceEditorId ] ;
//                if ( jsonKey === ROOT ) { // only bind once
                let $osFoldCheckbox = $( ".editor-toggle-last" ) ;
                $osFoldCheckbox.off().change( function () {
                    console.log( `editor-toggle-last checked` ) ;
                    if ( $( this ).is( ':checked' ) ) {
                        lastActivatedEditor.getSession().foldAll( 1 ) ;
                    } else {
                        //_yamlEditor.getSession().unfoldAll( 2 ) ;
                        lastActivatedEditor.getSession().unfold( ) ;
                    }
                } ) ;

                let $jsonCheckbox = $( ".editor-toggle-json" ) ;
                let $rootTextArea = $targetTextArea ;
                $jsonCheckbox.off().change( function () {
                    console.log( `json checked` ) ;
                    //aceTextEditor.getSession()._emit('change')
                    lastActivatedEditor.setValue( $rootTextArea.val() ) ;
                } ) ;
//                }
//                setTimeout( function() {
//                    
//                    console.log( `refreshing ${ aceEditorId } again`) ;
//                    _aceEditors[ aceEditorId ].resize() ;
//                }, 1000)

            }

            console.log( `container: '${ $targetTextArea.attr( "id" ) }' height: '${ $targetTextArea.css( "height" )}'  width: '${ $targetTextArea.css( "width" ) }' ` ) ;

        } ) ;
        console.groupEnd() ;
    }

    function getAceEditorId( $targetTextArea, dialogContainer = "body" ) {

        let containerDesc = "" ;
        if ( dialogContainer == "body" ) {
            containerDesc = "-body" ;
        }
        let aceEditorId = $targetTextArea.attr( "id" ) + "-ace" + containerDesc ;

        return aceEditorId ;
    }

    function getJsonText( $textArea, isFormatYaml = false ) {
        let originalSource = $textArea.val() ;

        let isJsonMode = $( ".editor-toggle-json" ).is( ':checked' ) ;

//        console.log( `getJsonText source`, originalSource ) ;

        if ( isConvertYaml( $textArea ) && !isJsonMode ) {
            console.log( `getJsonText converting yaml to json` ) ;
            console.debug( originalSource ) ;
            let jsonSource = JSON.parse( originalSource ) ;

            if ( isFormatYaml ) {
                originalSource = dumpAndIndentAsYaml( jsonSource, "root" ) ;
            } else {
                originalSource = JSON.stringify( jsonSource ) ;
            }

        }
        return originalSource ;
    }

    function build_ace_editor( targetTextId, aceEditorId, $targetTextArea ) {
        console.log( `build_ace_editor() Building: ${ aceEditorId }` ) ;

        $textParent = $targetTextArea.parent() ;
        let $editorPanel = jQuery( '<pre/>', {
            id: aceEditorId
        } ) ;

        $textParent.append( $editorPanel ) ;

        let aceTextEditor = aceEditor.edit( aceEditorId ) ;
        _aceEditors[ aceEditorId ] = aceTextEditor ;


        aceTextEditor.setOptions( _aceDefaults ) ;

        _aceEditors[ aceEditorId ].setValue( $targetTextArea.val(), 1 ) ;


        aceTextEditor.getSession().on( 'change', function () {

            clearTimeout( _jsonParseTimer ) ;
            _jsonParseTimer = setTimeout( function () {
                console.log( `\n\n Ace Editor Updated: ${aceEditorId} \n\t forwarding to backing text area` ) ;

                let currentEditor = _aceEditors[ aceEditorId ] ;
                let workAceContents = currentEditor.getValue() ;

                if ( aceEditorId.startsWith( "propFileText-ace") ) {
                    propertyFileName = $( "#serviceEditor #propFileName" ).val() ;
                    console.log( `propertyFileName: ${ propertyFileName }` ) ;
                    if ( propertyFileName.endsWith( ".yaml" ) || propertyFileName.endsWith( ".yml" ) ) {
                        isValidYaml( aceEditorId, currentEditor ) ;
                    }
                }
                console.debug( `workAceContents`, workAceContents ) ;

                if ( isConvertYaml( $targetTextArea ) ) {
                    // leave backing set at json
                    $( ".ui-tabs-active .status-warning" ).remove() ;
                    if ( isValidYaml( aceEditorId, currentEditor ) ) {
                        console.log( `converting yaml to json: ${ aceEditorId }` ) ;
                        const updatedYamlObject = jsYaml.safeLoad( workAceContents ) ;
                        let newVal = JSON.stringify( updatedYamlObject, null, 2 ) ;
                        $targetTextArea.val( newVal ) ;
                        update_editor_state( $targetTextArea, currentEditor, true ) ;
                        $targetTextArea.trigger( "change" ) ;
                    } else {
                        console.log( `found yaml errors - skipping updates` ) ;
                        let message = "Failed to parse document: " + _yamlError ;
                        markTabWithError( message ) ;
                    }

                } else {
                    $targetTextArea.val( workAceContents ) ;
                    update_editor_state( $targetTextArea, currentEditor, true ) ;
                    $targetTextArea.trigger( "change" ) ;
                }

            }, 500 ) ;

        } ) ;
    }

    function isValidYaml( aceEditorId, aceEditor ) {

        let isPassed = false ;
        try {
            clearTabError() ;
            let workAceContents = aceEditor.getValue() ;

            console.log( `\n\n validating yaml in editor: ${aceEditorId}`, workAceContents ) ;
            // https://github.com/nodeca/js-jsYaml
            jsYaml.safeLoadAll( workAceContents ) ;
            aceEditor.getSession().clearAnnotations() ;

            isPassed = true ;

        } catch ( e ) {

            _yamlError = e ;

            let defManager = new DefinitionManager( e, "mark.line", false ) ;

            let lineNumber = defManager.getValue() ;
            if ( !lineNumber ) {
                lineNumber = 0 ;
            }
            console.log( ` line: ${ lineNumber }, message: ${ e.message }` ) ;
            aceEditor.getSession().setAnnotations( [ {
                    row: lineNumber,
                    column: 0,
                    text: e.message, // Or the Json reply from the parser 
                    type: "error" // also "warning" and "information"
                } ] ) ;

            markTabWithError( e.message ) ;
        }

        return isPassed ;
    }

    function update_editor_state( $targetTextArea, aceTextEditor, isUpdate, readOnly = false ) {

        console.log( `update_editor_state(): updating ace readOnly: '${readOnly}' ` ) ;

        let isNotJson = $targetTextArea.data( "plain" ) ;

        // // iplastic github pastel_on_dark textmate solarized_light
        let theme = _aceDefaults.theme ;

        if ( $targetTextArea.data( "init-load" ) == "true" ) {
            isUpdate = false ;
        }

        if ( isUpdate ) {
            theme = "ace/theme/solarized_light"
        }

        let fontSize = _aceDefaults.fontSize ;
        let editMode = "ace/mode/json" ;

        if ( isNotJson ) {
            editMode = "ace/mode/sh" ;
        } else if ( isConvertYaml( $targetTextArea ) ) {
            editMode = "ace/mode/yaml" ;
        }


        let overRideMode = $targetTextArea.data( "ace-mode" ) ;
        if ( overRideMode ) {
            console.log( `overriding mapped editor modes: ${overRideMode}` ) ;
            editMode = overRideMode ;
        }

        //let fontFamily = "Menlo, Consolas, Monaco, monospace" ;
        if ( readOnly ) {
            //theme = "ace/theme/iplastic" ;
            //fontFamily = "Courier" ;
            fontSize = "9pt" ;
            theme = "ace/theme/tomorrow_night_blue" ;
        }

        if ( $targetTextArea.val().startsWith( SAMPLE ) ) {
            // json sample will errror iut unless done 
            editMode = "ace/mode/sh" ;
            fontSize = "9pt" ;
            // iplastic github pastel_on_dark
            theme = "ace/theme/gob" ;
        }



        aceTextEditor.setOptions( {
            //fontFamily: fontFamily,
            fontSize: fontSize
        } ) ;

        aceTextEditor.setReadOnly( readOnly ) ;
        aceTextEditor.setTheme( theme ) ;
        aceTextEditor.setFontSize( fontSize ) ;
        aceTextEditor.getSession().setMode( editMode ) ;

        // update text for size calculations
        $targetTextArea.css( "font-size", fontSize ) ;
    }

    function configureJsonEditors(
            getDefinitionFunction,
            dialogContainer,
            editorPanelId,
            definitionDomId ) {

        console.groupCollapsed( "configureJsonEditors(): Updating all textAreas: " + editorPanelId ) ;

        //$( editorPanelId + " textarea" ).css( "width", containerWidth( dialogContainer, editorPanelId ) );
        $( editorPanelId + " textarea" ).each( function () {
            let $targetTextArea = $( this ) ;

            $targetTextArea.change( function ( e ) {
                let $theTextArea = $( this ) ;
                console.log( "textarea updated - json editor: " + $theTextArea.attr( "id" ) ) ;
                clearTimeout( _jsonParseTimer ) ;
                parseAndUpdateJsonEdits( getDefinitionFunction(), $theTextArea, definitionDomId )
            } ) ;
        } ) ;

        console.groupEnd() ;
    }

    function isConvertLines( $textArea ) {
        return $textArea.data( "convert_lines" ) ;
    }

    function isConvertYaml( $textArea ) {
        return $textArea.data( "convert_yaml" ) ;
    }

    function parseAndUpdateJsonEdits( definitionJson, $jsonTextArea, definitionDomId ) {
        let isPlainData = $jsonTextArea.data( "plain" ) ;

        let success = true ;

        const itemPath = $jsonTextArea.data( "path" ) ;
        const initLoad = $jsonTextArea.data( "init-load" ) ;
        console.log( `\n\n ` ) ;
        console.group( `textArea modified: ${ $jsonTextArea.attr( "id" ) },`
                + ` path: '${ itemPath }' \n\t propogating to ace, initLoad: ${initLoad}` ) ;




        if ( initLoad === "true" ) {
            console.log( `skipping initial update - supports lazy loads` ) ;
            return ;
        }

        if ( isConvertYaml( $jsonTextArea ) ) {

            updateDefinition( definitionJson, $jsonTextArea ) ;

            $jsonTextArea.css( "background-color", "grey" ) ;

            //let aceEditorId = getAceEditorId( $jsonTextArea ) ;
            //_aceEditors[ aceEditorId ].setValue( $jsonTextArea.val(), 1 ) ;
            // clear timeout right away to avoid looping


        } else if ( isConvertLines( $jsonTextArea ) ) {

            let rawText = $jsonTextArea.val() ;
            let lines = rawText.split( "\n" ) ;
            let jsonLines = new Array() ;

            console.log( `tagged with 'convert_lines', converting text to json array, lines: '${ lines.length }'` ) ;
            for ( let text_line of  lines ) {
                jsonLines.push( text_line ) ;
            }

            updateDefinition( definitionJson, $jsonTextArea, jsonLines ) ;

            $jsonTextArea.css( "background-color", "#D5F7DE" ) ;

        } else if ( isPlainData ) {
            console.log( `tagged with 'plain', data will be updated as is` ) ;
            updateDefinition( definitionJson, $jsonTextArea ) ;

        } else {
            console.log( `json processing` ) ;
//            $jsonTextArea.css( "font-style", "normal" );
//            $jsonTextArea.css( "color", "black" );


            clearTabError() ;
            try {
                if ( $jsonTextArea.val().indexOf( SAMPLE ) == 0 ) {
                    return ; // no parsing on sample
                }

                let editorId = $jsonTextArea.attr( "id" ) ;

                let parsedJson = "" ;
                if ( $jsonTextArea.val() != "" ) {
                    console.log( "running JSON.parse on: " + editorId ) ;
                    parsedJson = JSON.parse( $jsonTextArea.val() ) ;
                }
                $jsonTextArea.css( "background-color", "#D5F7DE" ) ;

                if ( editorId == $( definitionDomId ).attr( "id" ) ) {
                    console.log( "Root document updated" ) ;
                    //definitionJson = parsedJson; // root node
                } else {
                    let jsonKey = $jsonTextArea.data( "path" ) ;
                    console.log( "updating: " + jsonKey ) ;
                    updateDefinition( definitionJson, $jsonTextArea, parsedJson ) ;
                    //console.log("parseAndUpdateJsonEdits() replacing: " + JSON.stringify( _settingsJson[ jsonKey ], null, "\t" ))
                    //_settingsJson[ jsonKey ] = parsedJson;

                }

                $( ".ui-tabs-active" ).attr( "title", "Parsing Successful" ) ;

            } catch ( e ) {
                // console.error( e ) ;
                let message = "Failed to parse document: " + e ;
                $jsonTextArea.css( "background-color", "#F2D3D3" ) ;

                markTabWithError( message ) ;


                success = false ;
            }
        }
        console.groupEnd() ;

        return success ;
    }

    function clearTabError() {
        $( ".ui-tabs-active .status-warning" ).remove() ;
    }

    function markTabWithError( message ) {
        let $error = jQuery( '<a/>', {
            class: "csap-link-icon status-warning"
        } ).css( "position", "absolute" ).css( "height", "20px" ) ;
        $( ".ui-tabs-active" ).prepend( $error ) ;
        $( ".ui-tabs-active" ).attr( "title", message ) ;
    }

    function getContainer( editorPanelId ) {
        let $editor = $( editorPanelId ).parent() ;
        //console.log(`getContainer(): container: ${ $editor.attr( "id") } height: ${ $editor.css( "height")}  width: ${ $editor.css( "width") } `) ;
        return $editor ;
    }

    function containerHeight( dialogContainer, editorPanelId ) {

        let containerHeight = getContainer( editorPanelId ).outerHeight( true ) - 150 ;

        if ( containerHeight < 500 ) {
            containerHeight = 500 ;
        }

        if ( dialogContainer === "body" ) {
            containerHeight = $( window ).outerHeight( true ) - 300 ;
        }

        console.log( `dialogContainer ${ dialogContainer } containerHeight ${containerHeight}` ) ;

        return    Math.round( containerHeight ) ;
    }

    function containerWidth( dialogContainer, editorPanelId ) {

        let width = $( window ).outerWidth( true ) - 100 ;

        if ( dialogContainer === "body"  ) {
            width = getContainer( editorPanelId ).outerWidth( true ) - 100 ;
        }

        //console.log( `containerWidth(): editorPanelId: '${ editorPanelId }' width: ${width} ` ) ;
        return Math.round( width ) ;

    }



} ) ;

