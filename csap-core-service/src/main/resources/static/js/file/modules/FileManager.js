define( [ "browser/utils", "ace/ace", "ace/ext-modelist", "jsYaml/js-yaml" ], function ( utils, aceEditor, aceModeList, yaml ) {

    console.log( "Module loaded 3" ) ;
    let looseChangeMessage = "Warning - file has been modified. Select OK to lose modifications, or select cancel to save." ;
    let no_op_cancel_function = function ( d ) {
        let dialog = alertify.confirm( "hi" ) ;
        if ( alertify.confirm ) {
            dialog.setting( {
                'labels': {
                    ok: "ok",
                    cancel: "cancel"
                }
            } ) ;
        }
    } ;
    class FileManager {

        constructor( hostName, $container, user, serviceName, quickView, folder, isAdmin ) {


            this.$container = $container ;

            this.hostName = hostName ;

            this.quickViewName = folder ;
            if ( quickView ) {
                this.quickViewName = quickView ;
            }

            this.originalfolder = folder ;


            this.user = user ;
            this.$diskPathsForTips = $( `.disk-paths`, this.$container ) ;

            let diskMappings = "" ;
            $( "input", this.$diskPathsForTips ).each( function () {
                let $input = $( this ) ;
                diskMappings += `\n  ${ $input.attr( "class" ) } : ${ $input.val()}` ;
            } ) ;
            console.log( `serviceName: '${serviceName}', folder: '${ folder}' disk mappings:\n`, diskMappings )

            this.serviceName = null ;
            if ( serviceName ) {
                this.serviceName = serviceName ;
            }

            this.folder = folder ;
            this.isAdmin = isAdmin ;

            console.log( `constructor() quickViewName: ${this.quickViewName},  target container: ${$container.closest( "body" ).attr( "class" )} ` ) ;


            this.dockerBase, this.containerName = null ;

            this.label = "" ;
            this.showSizeWarningOnce = true ;
            this.browseId = this.browseGroup = "" ;

            this.propDisk = null ;
            this.processingDisk = null ;

            this._lastNodeSelected = "" ;
            this._lastFolderTouched ;
            this.fileTree, this._quick_view_timer, this.fileEditor, this.$quickViewText, this.$quickviewDialog ;
            this._filterMap = new Object() ;


            this.myDropzone = null ;
            this.$aceWrapCheckbox ;

            this.$listingFilter = null ;
            this.$filterControls = null ;
            this.$fileControls = null ;
            this._nodeTimer = null ;
            this._filterTimer = null ;
            this._lastOpenedFolder = null ;
            this._lastFileSelected = null ;


            this.dropOptions = {
                paramName: "distFile",
                url: `${OS_URL}/uploadToFs`,
                addRemoveLinks: true,
                dictDefaultMessage: "<div class='quote'>Drag and drop files here, or mouse click to open file browser</div>",
            } ;

        }

        configureDockerContainers( containerName, dockerBase ) {

            console.log( ` addDockerSupport ${containerName}` ) ;
            this.containerName = containerName ;
            this.dockerBase = dockerBase ;


            if ( !this.serviceName && !this.quickViewName ) {
                this.quickViewName = containerName ;
            }
        }

        info() {
            console.log( `info() quickViewName: ${this.quickViewName} ` ) ;
        }

        initialize( showShortcuts ) {

            console.log( `initialize() quickViewName: ${this.quickViewName},  target container: ${this.$container.closest( "body" ).attr( "class" )} ` ) ;

            let fileManager = this ;

            let collapseToService = function ( serviceName ) {
                fileManager.collapseToService( serviceName ) ;
            } ;
            utils.setBrowseServiceFunction( collapseToService ) ;

            let pageTitle = `Host: ${this.hostName}` ;
            if ( this.serviceName ) {
                pageTitle += `: ${this.serviceName}` ;
            }
            $( ".fb-title", this.$container ).text( pageTitle ) ;

            this.$fileTree = $( "div.file-browser", this.$container ) ;
            this.$fileTree = $( "div.file-browser", this.$container ) ;

            this.$filterControls = $( ".filterControls", this.$container ) ;
            this.$listingFilter = $( ".listing-filter", this.$filterControls ) ;
            this.$fileControls = $( ".fileControls", this.$container ) ;

            this.$fileSort = $( "input.file-sort-type", this.$container ) ;

            this.$processFileSystemDialog = $( ".file-system-dialog", this.$container ) ;

            //
            // Command Menu
            //
            this.$commandControls = $( "button.tool-menu", this.$container ) ;
            this.$deleteControls = $( "button.delete-items", this.$container ) ;


            this.commandCloseTimer = null ;
            this.$fileMenu = $( "div.csap-button-menu.file-menu", this.$container ) ;
            this.$folderMenu = $( "div.csap-button-menu.folder-menu", this.$container ) ;
            this.$commandMenu = $( "div.csap-button-menu", this.$container ) ;
            this.$commandMenu.hide() ;
            this.$commandMenu.hover( function () {
                clearTimeout( fileManager.commandCloseTimer ) ;
            }, function () {
                fileManager.closeCommandMenu() ;
            } ) ;

            $( "button, .close-menu", this.$commandMenu ).click( function () {
                let command = $( this ).data( "command" ) ;
                fileManager.runCommand( command ) ;
            } ) ;

            this.$header = $( "header", this.$container ) ;
            $( ".file-mode", this.$header ).hide() ;
            //this.$fileHeader.hide();
            this.$header.show() ;
            //this.$header.css("display", "block");


            //
            // item selection text
            //

            this.$copyBuffer = $( ".copy-buffer", this.$container ) ;

            this.$copyBuffer.click( function () {
                fileManager.$copyBuffer.select() ;
                document.execCommand( "copy" ) ;
                $( ".copy-buffer-message", fileManager.$container ).show().fadeOut( 2000 ) ;
            } ) ;

            this.$quickViewText = $( ".file-viewer-text", this.$container ) ;
            let quickviewId = `quick-view-` ;
            for ( let i = 1 ; i < 99 ; i++ ) {
                let testid = quickviewId + i ;
                if ( $( `#${testid}` ).length == 0 ) {
                    quickviewId = testid ;
                    break ;
                }
            }
            this.$quickViewText.attr( "id", quickviewId ) ;
            //this.$quickviewDialog = $( ".file-viewer-dialog", this.$container ) ;

            let $osFoldCheckbox = $( "#ace-fold-checkbox", this.$container ) ;
            $osFoldCheckbox.change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    fileManager.fileEditor.getSession().foldAll( 2 ) ;
                } else {
                    //_yamlEditor.getSession().unfoldAll( 2 ) ;
                    fileManager.fileEditor.getSession().unfold() ;
                }
            } ) ;

            $( "#ace-theme-select", this.$container ).change( function () {
                fileManager.fileEditor.setTheme( $( this ).val() ) ;
            } ) ;

            this.$aceWrapCheckbox = $( '#ace-wrap-checkbox', this.$container ) ;
            this.$aceWrapCheckbox.change( function () {

                if ( fileManager.isAceWrapChecked() ) {
                    fileManager.fileEditor.session.setUseWrapMode( true ) ;
                } else {
                    fileManager.fileEditor.session.setUseWrapMode( false ) ;
                }
            } ) ;

            $( "button.upload-advanced", this.$container ).off().click( function () {
                fileManager.openCommandWindow( "upload" ) ;
                $( ".dnd-upload", this.$container ).hide() ;
            } ) ;


            console.log( "starting up" ) ;


            if ( typeof Dropzone !== 'undefined' ) {
                this.simpleUploadSetup() ;
            }

            $( '.showDu', this.$container ).change( function () {
                console.log( `du checked change` ) ;
                fileManager.showDuDialog( $( this ) ) ;
            } ) ;

            this.setupFileBrowser( showShortcuts ) ;

            $( ".usage-note button", this.$container ).click( function () {
                $( this ).parent().remove() ;
            } )

            this.$fileSort.change( function () {
                console.log( `sort selected: ${fileManager.$fileSort.filter( ":checked" ).val()}` ) ;
                fileManager.refreshLastFolderOperation() ;
            } ) ;

            $( '.useRoot', fileManager.$container ).change( function () {
                fileManager.refreshLastFolderOperation() ;
            } ) ;


            if ( showShortcuts ) {
                fileManager.hideShortcutButton() ;
            } else {
                $( ".show-csap-shortcuts", this.$container ).click( function () {
                    fileManager.hideShortcutButton() ;
                    fileManager.showShortcuts() ;
                } ) ;
            }

        }

        hideShortcutButton() {
            $( ".show-csap-shortcuts,.show-csap-shortcuts:hover", this.$container )
                    .css( "opacity", 0 )
                    .prop( "disabled", true )
                    .css( "cursor", "default" ) ;
        }

        showShortcuts() {
            let fileManager = this ;
            let defaultTree = new Array() ;
            fileManager.addShortcutsToArray( defaultTree ) ;
            //            this.$fileTree.fancytree("getTree").expandAll(false);
            console.log( `collapsing root node` )
            fileManager.$fileTree.fancytree( "getTree" ).expandAll( false ) ;

            let rootNode = fileManager.$fileTree.fancytree( 'getRootNode' ) ;
            rootNode.addChildren( defaultTree ) ;

        }

        runCommand( command ) {

            let fileManager = this ;
            console.log( `commandMenu: ${command} ` ) ;


            let location = this._lastNodeSelected.data.location ;
            let name = this._lastNodeSelected.data.name ;

            let commandMap = {
                "new-browser": function () {
                    fileManager.browseFolderInNewWindow() ;
                },
                "collapse": function () {
                    fileManager.collapseToFolder() ;
                },
                "new-item": function () {
                    fileManager.filesystemAdd() ;
                },
                "copy": function () {
                    fileManager.filesystemRename() ;
                },
                "delete": function () {
                    fileManager.filesystemDelete() ;
                },
                "edit": function () {
                    fileManager.inlineEdit() ;
                },
                "download": function () {
                    fileManager.downloadFile() ;
                },
                "auto-play-preview": function () {
                    fileManager.autoPlay( location, name, false ) ;
                },
                "auto-play-apply": function () {

                    let dialog = alertify.confirm(
                            "Apply auto play",
                            $( "#auto-play-confirmation" ).html(),
                            function () {
                                fileManager.autoPlay( location, name, true ) ;
                            },
                            no_op_cancel_function
                            ) ;
                    //fileManager.updateConfirmLabels( dialog, 'Delete Item(s)', 'Cancel' ) ;

                },
                "upload": function () {
                    fileManager.uploadFile() ;
                },
                "sync": function () {
                    fileManager.openCommandWindow( "sync" ) ;
                },
                "monitor": function () {
                    fileManager.monitorFile() ;
                },
                "browser": function () {
                    fileManager.downloadFile( false ) ;
                },
                "run": function () {
                    fileManager.openCommandWindow( "script" ) ;
                },
                "search": function () {
                    fileManager.searchFilesystem() ;
                }
            } ;

            let targetCommand = commandMap[command] ;

            if ( targetCommand ) {
                targetCommand() ;
            } else {
                console.log( `no command for operation` ) ;
            }

            fileManager.closeCommandMenu() ;
            return ;


        }

        uploadFile() {
            $( ".extractDir" ).val( this._lastNodeSelected.data.location ) ;

            $( ".uploadToSpan" ).text( this._lastNodeSelected.data.location ) ;
            // lastFileNodeSelected.data.name

            $( ".dnd-upload", this.$container ).show() ; //.center() ;
        }

        searchFilesystem() {
            let searchLocation = this._lastNodeSelected.data.location ;
            if ( this.isLastItemAFolder() ) {
                searchLocation += "/*"
            }
            let inputMap = {
                fromFolder: searchLocation,
                serviceName: this.serviceName,
                "browseId": this.browseId,
                hostName: this.hostName,
                command: "logSearch"  // action mapped from search
            } ;
            this.postAndRemove( "_blank", OS_URL + "/command", inputMap ) ;
        }

        monitorFile() {
            let paramaters = {
                fileName: this._lastNodeSelected.data.location,
                serviceName: this.serviceName,
                "browseId": this.browseId,
                hostName: this.hostName
            } ;


            //            this.postAndRemove( "_blank", FILE_URL + "/FileMonitor", inputMap ) ;
            utils.openAgentWindow(
                    utils.getHostName(),
                    `${utils.getFileUrl()}/FileMonitor`,
                    paramaters ) ;
        }

        downloadFile( forceDownload = true ) {
            console.log( "lastFileNodeSelected: " + this._lastNodeSelected ) ;
            let inputMap = {
                fromFolder: this._lastNodeSelected.data.location,
                serviceName: this.serviceName,
                isBinary: forceDownload,
                "browseId": this.browseId,
                hostName: this.hostName
            } ;
            this.postAndRemove( "_blank", FILE_URL + "/downloadFile/"
                    + this._lastNodeSelected.data.name, inputMap ) ;
        }

        closeCommandMenu() {

            let fileManager = this ;

            clearTimeout( this.commandCloseTimer ) ;
            this.commandCloseTimer = setTimeout( function () {
                fileManager.$commandMenu.hide() ;
                fileManager.$commandMenu.css( "opacity", "0.0" ) ;

            }, 50 ) ;
        }

        showCommandMenu( $showButton ) {

            let itemName = this._lastNodeSelected.data.name ;
            ;

            console.log( `showCommandMenu() item: ${itemName},  button: ${$showButton.text()} ` ) ;
            //        clearTimeout( instanceCloseTimer ) ;

            let $menu = this.$fileMenu ;
            $( ".auto-play", $menu ).hide() ;
            if ( this.isLastItemAFolder() ) {
                $menu = this.$folderMenu ;
            } else if ( itemName.includes( "auto-play.yaml" ) ) {
                $( ".auto-play", $menu ).show() ;
            }

            $menu.show() ;

            let windowHeight = $( window ).outerHeight( true ) ;
            let menuHeight = $menu.outerHeight( true ) ;
            let buttonTop = $showButton.offset().top ;
            let buttonLeft = $showButton.offset().left + $showButton.outerWidth( false ) ;

            console.log( `windowHeight: ${windowHeight}, buttonTop: ${buttonTop}, menuHeight: ${menuHeight}` ) ;
            if ( ( buttonTop + menuHeight ) > windowHeight ) {
                buttonTop = windowHeight - menuHeight ;
            }
            //console.log( "panelTop: " + panelTop + " panelLeft: " + panelLeft )

            $menu.offset(
                    {
                        top: Math.round( buttonTop ) - 10,
                        left: Math.round( buttonLeft ) + 10
                    } ) ;
            $menu.show().css( "opacity", "1.0" ) ;

            //        }, 200 ) ;
        }

        isAceWrapChecked() {
            if ( this.$aceWrapCheckbox.prop( 'checked' ) ) {
                return true ;
            } else {
                return false ;
            }

        }

        inlineEdit( fileName, fileFullPath ) {
            console.log( "inlineEdit: ", this._lastFileSelected, this._lastNodeSelected ) ;

            if ( !fileName ) {
                fileName = this._lastFileSelected.data.name ;
                fileFullPath = this._lastFileSelected.data.location ;
            }

            let inputMap = {
                fromFolder: fileFullPath,
                serviceName: this.serviceName,
                "browseId": this.browseId,
                forceText: true
            } ;

            let quickUrl = FILE_URL + "/downloadFile/quickView" ;
            if ( this.browseId != "" ) {
                quickUrl = "../downloadFile/quickView"
            }

            let fileManager = this ;
            $.post( quickUrl, inputMap )

                    .done( function ( fileContents ) {
                        //console.log( `inlineEdit{} `, fileContents ) ;
                        fileManager.showFileEditor( fileContents,
                                fileName,
                                fileFullPath )
                    } )

                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact support" ) ;
                    }, 'json' ) ;

        }

        showFileEditor( fileContents, fileName, location ) {


            let fileManager = this ;


            let $fileHeader = this.$header ;
            let $fileNames = $( "#files-in-editor", $fileHeader ) ;

            $( `option[value='${location}']`, $fileNames ).remove() ;
            jQuery( '<option/>', {
                value: location,
                title: location,
                text: fileName
            } ).prependTo( $fileNames ) ;

            $fileNames.val( location ) ;

            $( ".file-mode", $fileHeader ).show() ;
            $( ".browser-mode", this.$container ).hide() ;

            let $autoPlayButton = $( ".autoplay-file-button", $fileHeader ) ;
            if ( fileName.includes( "auto-play.yaml" ) ) {
                $autoPlayButton.css( "opacity", "1.0" ) ;
                $autoPlayButton.off().click( function () {
                    fileManager.autoPlay( location, fileName, false ) ;
                } )
            } else {
                $autoPlayButton.css( "opacity", "0.0" ) ;
            }


            $( "button.toggle-editor", $fileHeader ).css( "opacity", "1.0" ) ;

            let $save = $( ".save-file-button", $fileHeader ) ;
            this.$save = $save ;

            let $toggleEditor = $( ".toggle-editor", $fileHeader ) ;
            this.$toggleEditor = $toggleEditor ;
            $toggleEditor.removeClass( "file-edit" ) ;
            $toggleEditor.show() ;


            this.$fileTree.hide() ;
            this.$quickViewText.show() ;

            let needsInitialization = !this.fileEditor ;

            if ( needsInitialization ) {
                this.buildFileEditor( fileManager, $save, $fileNames, $fileHeader, $toggleEditor ) ;
            }
            $( ".file-viewer-title span", $fileHeader ).text( fileName ) ;

            this.fileEditor.getSession().setValue( "" ) ;

            //
            // ensures clean context: undos, etc are wiped out
            //
            console.log( `createing new ace editor session` ) ;
            let newSession = new aceEditor.createEditSession( fileContents ) ;
            this.fileEditor.setSession( newSession ) ;
            this.fileEditor.setOptions( utils.getAceDefaults() ) ;


            newSession.on( 'change', function () {

                utils.enableButtons( $save ) ;
                $fileNames.data( "current", $fileNames.val() ) ;
                utils.flash( $save ) ;
                console.log( "content updated" ) ;

                let fileSize = fileManager.fileEditor.getSession().getValue().length ;
                $( "#file-size", $fileHeader ).text( utils.bytesFriendlyDisplay( fileSize ) ) ;
                $( "#file-size", $fileHeader ).css( "background-color", "" ) ;
                if ( fileSize > ( 5 * 1024 * 1024 ) ) {
                    if ( fileManager.showSizeWarningOnce ) {
                        fileManager.showSizeWarningOnce = false ;
                        alertify.csapWarning( "Warning: large files may fail to upload (use download/upload option if this happens)" ) ;
                    }
                    $( "#file-size", $fileHeader ).css( "background-color", "red" ) ;
                }


                let fileNameSelected = $( "option:selected", $fileNames ).text() ;

                if ( fileNameSelected.endsWith( ".yaml" ) || fileNameSelected.endsWith( ".yml" ) ) {
                    try {
                        // https://github.com/nodeca/js-yaml
                        yaml.safeLoadAll( fileManager.fileEditor.getValue() ) ;
                        fileManager.fileEditor.getSession().clearAnnotations() ;
                    } catch ( e ) {
                        utils.markAceYamlErrors( fileManager.fileEditor.getSession(), e ) ;
                    }
                }
            } ) ;

            if ( fileContents.startsWith( utils.getErrorIndicator() ) ) {
                alertify.csapWarning( "NFS file loaded using root:  restrictions: max 3mb, and file edits via: echo <content> > file to preserve permissiona." ) ;
                let warningLineRange = new aceEditor.Range( 0, 0, 2, 0 )
                this.fileEditor.getSession().remove( warningLineRange ) ;
                this.fileEditor.setReadOnly( false ) ;
                $( ".preserve-permissions", $fileHeader ).prop( "checked", true ) ;
                utils.flash( $( ".preserve-permissions", $fileHeader ).parent() ) ;
                //                this.fileEditor.setReadOnly( true ) ;
                //                if ( fileContents.includes() ) {
                //                    fileContents = fileContents.substring(fileContents.indexOf("\n")+1) ;
                //                }
            } else {
                this.fileEditor.setReadOnly( false ) ;
            }
            this.fileEditor.getSession().setMode( this.determineAceExtension( fileName ) ) ;

            // since we just loaded editor fresh - save will be enabled
            setTimeout( function () {
                utils.disableButtons( $save ) ;
                utils.flash( $save, false ) ;
            }, 500 ) ;

        }

        buildFileEditor( fileManager, $save, $fileNames, $fileHeader, $toggleEditor ) {
            console.log( `buildFileEditor{} creating editor`, utils.getAceDefaults() ) ;


            let $newWindow = $( ".edit-in-new-window", $fileHeader ) ;

            fileManager.fileEditor = aceEditor.edit( fileManager.$quickViewText.attr( "id" ) ) ;
            fileManager.fileEditor.setOptions( utils.getAceDefaults() ) ;

            // register editor controls 


            let fileNameChanged = function () {

                if ( !fileManager.$save.prop( 'disabled' ) ) {

                    if ( !confirm( looseChangeMessage ) ) {
                        $fileNames.val( $fileNames.data( "current" ) ) ;
                        return false ;
                    }
                }

                let fileNameSelected = $( "option:selected", $fileNames ).text() ;
                fileManager.inlineEdit( fileNameSelected, $fileNames.val() )
            }
            $fileNames.change( fileNameChanged ) ;
            fileManager.fileEditor.commands.addCommand( {
                name: 'Reload',
                bindKey: { win: 'Ctrl-r', mac: 'Command-r' },
                exec: fileNameChanged,
                readOnly: true // false if this command should not apply in readOnly mode
            } ) ;
            fileManager.fileEditor.commands.addCommand( {
                name: 'Next',
                bindKey: { win: 'Ctrl-e', mac: 'Command-e' },
                exec: function () {
                    $( "option:selected", $fileNames )
                            .prop( "selected", false )
                            .next()
                            .prop( "selected", true ) ;
                    fileNameChanged() ;
                },
                readOnly: true // false if this command should not apply in readOnly mode
            } ) ;


            $newWindow.click( function () {
                let inputMap = {
                    fromFolder: $fileNames.val(),
                    serviceName: fileManager.serviceName,
                    "browseId": fileManager.browseId,
                    hostName: fileManager.hostName
                } ;
                fileManager.postAndRemove( "_blank", FILE_URL + "/editFile", inputMap ) ;
                $toggleEditor.trigger( "click" ) ;
            } ) ;

            $toggleEditor.click( function () {

                console.log( ` save button disabled:  ${ fileManager.$save.prop( 'disabled' ) }` )


                if ( !fileManager.$save.prop( 'disabled' ) ) {
                    if ( !confirm( looseChangeMessage ) ) {
                        return ;
                    }
                }
                $toggleEditor.toggleClass( "file-edit" ) ;
                $( ".file-mode", fileManager.$header ).toggle() ;
                $( ".browser-mode", fileManager.$container ).toggle() ;
                fileManager.$quickViewText.toggle() ;
                fileManager.$fileTree.toggle() ;
                utils.flash( $toggleEditor, false ) ;
                //
            } ) ;

            utils.flash( $toggleEditor ) ;

            let editorSave = function () {

                if ( $save.prop( 'disabled' ) ) {
                    alertify.csapInfo( "no changes made - aborting" ) ;
                    return ;
                }
                let location = $fileNames.val() ;
                let parameters = {
                    fromFolder: location,
                    keepPermissions: $( ".preserve-permissions", $fileHeader ).prop( "checked" ),
                    serviceName: fileManager.serviceName,
                    contents: fileManager.fileEditor.getValue(),
                    chownUserid: $( ".edit-as-user", $fileHeader ).val()
                } ;

                fileManager.updateFileEdits( parameters ) ;
                utils.disableButtons( $save ) ;
                utils.flash( $save, false ) ;
            }
            $save.click( editorSave ) ;
            fileManager.fileEditor.commands.addCommand( {
                name: 'Save',
                bindKey: { win: 'Ctrl-s', mac: 'Command-s' },
                exec: editorSave,
                readOnly: true // false if this command should not apply in readOnly mode
            } ) ;
        }

        updateConfirmLabels( dialog, okLabel, cancelLabel ) {
            dialog.setting( {
                'labels': {
                    ok: okLabel,
                    cancel: cancelLabel
                }
            } ) ;
        }

        updateFileEdits( parameters ) {
            let fileManager = this ;

            if ( parameters.chownUserid == "root" ) {

                let newItemDialog = alertify.confirm( "Validate your content carefully<br><br>In case of errors, submitting root level requests require cases to be opened to recover VM." ) ;


                this.updateConfirmLabels( newItemDialog, 'Save using root', 'Cancel' ) ;

                newItemDialog.setting( {
                    title: 'Caution: Root user specified',
                    'onok': function () {
                        // alertify.success( "Submitting Request" );
                        fileManager.saveFileChangesToServer( fileManager.$save, parameters ) ;

                    },
                    'oncancel': no_op_cancel_function()

                } ) ;

            } else {
                this.saveFileChangesToServer( fileManager.$save, parameters ) ;
            }


        }

        saveFileChangesToServer( $save, parameters ) {

            let saveUrl = FILE_URL + "/update" ;

            let paramsForPrintOnly = Object.assign( { }, parameters ) ;

            if ( paramsForPrintOnly.contents && ( paramsForPrintOnly.contents ).length > 80 ) {
                paramsForPrintOnly.contents = ( paramsForPrintOnly.contents ).substring( 0, 80 ) + ` ...snipped...` ;
            }

            console.log( `saveFileChangesToServer() raw: ${saveUrl}`, this.keepPermissionsMap, paramsForPrintOnly ) ;
//            $.post( { url: saveUrl, data: parameters, timeout: 30000, cache: false })

            utils.loading( "saving changes" ) ;

            // delay until loading message displayed - large files have a lag
            setTimeout( function () {

                $.post( saveUrl, parameters )
                        .always( function () {
                            utils.loadingComplete() ;
                        } )
                        .done( function ( commandResults ) {

                            //                        console.log( commandResults ) ;
                            //alertify.csapInfo( commandResults["plain-text"] ) ;

                            if ( commandResults.error ) {
                                alertify.csapWarning( commandResults.error ) ;
                                $save.attr( "title", commandResults.error ) ;

                            } else if ( commandResults.success ) {
                                $save.attr( "title", commandResults.success ) ;

                            } else {
                                $save.attr( "title", commandResults["plain-text"] ) ;
                            }

                        } )
                        .fail( utils.generalErrorHandler ) ;
            }, 200 ) ;

            return ;

            //sample working with xmlhttprequest direct

//            let xhr = new XMLHttpRequest() ;
//            xhr.open( "POST", saveUrl, true ) ;
//
////Send the proper header information along with the request
//            xhr.setRequestHeader( "Content-Type", "application/x-www-form-urlencoded" ) ;
////            xhr.setRequestHeader('Content-type', 'application/json')
//
//            xhr.onreadystatechange = function () { // Call a function when the state changes.
//                if ( this.readyState === XMLHttpRequest.DONE && this.status === 200 ) {
//                    // Request finished. Do processing here.
//                } else {
//                    console.log( `this.readyState: ${this.readyState} this.status: ${this.status} this.responseText: ${this.responseText}` ) ;
//                }
//            }
//
//            xhr.onerror = function () {
//                console.log( `this.error: ${this.readyState} this.status: ${this.status} this.responseText: ${this.responseText}` ) ;
//            }
//            xhr.addEventListener( "error", xhr.onerror ) ;
//
//            xhr.onprogress = function ( event ) {
//                console.log( `onprogress: ${event.loaded} of ${event.total} ` ) ;
//            } ;
//
//            let searchParams = new URLSearchParams() ;
//            for ( name in parameters ) {
//                searchParams.append( name, parameters[name] ) ;
//            }
//            // console.log( searchParams.toString() ) ;
//            xhr.send( searchParams.toString() ) ;
        }

        determineAceExtension( theFile ) {
            let modelist = aceEditor.require( "ace/ext/modelist" ) ;
            let testFileForExtension = theFile ;

            if ( theFile.endsWith( ".jmx" ) ) {
                testFileForExtension = "assumingJmxIsXml.xml" ;
            } else if ( theFile.endsWith( ".jsonnet" )
                    || theFile.endsWith( ".libsonnet" ) ) {
                testFileForExtension = "assumingJmxIsXml.json5" ;
            }

            let fileMode = modelist.getModeForPath( testFileForExtension ).mode ;
            console.log( `testFileForExtension: ${testFileForExtension}, fileMode: ${fileMode} ` ) ;

            return fileMode ;
        }

        showDuDialog( $duCheckBox ) {
            let fileManager = this ;

            // console.log( "checked" + $(this).prop('checked') ) ;

            if ( $duCheckBox.prop( 'checked' ) ) {
                let message = "Linux timeout command will be used to ensure a maximum time of 60 seconds is used to calculated disk; use CSAP OS commands if more time is needed.<br><br>"
                        + "If max time expires only partial results will appear."
                        + " Note that Linux du command can consume significant resources, CSAP OS dashboard can be used to show."
                        + "<br><br>NOTE: Only availabe on RH6 or later. <br>" ;

                let applyDialog = alertify.confirm( message ) ;
                fileManager.updateConfirmLabels( applyDialog, 'Include Folder Disk Usage', 'Cancel request' ) ;
                applyDialog
                        .setting( {
                            title: "Caution Advised: Calculating Disk Usage may consume signficant resources",

                            'onok': function () {
                                let $radios = fileManager.$fileSort ;
                                console.log( "showDuDialog() selecting size radio button", $radios.length ) ;

                                $radios.filter( '[value="sort-by-size"]' ).prop( 'checked', true ) ;
                                fileManager.refreshLastFolder() ;
                            },
                            'oncancel': function () {
                                $duCheckBox.prop( 'checked', false )
                            }

                        } ) ;

            }
            return ;
        }

        refreshLastFolderOperation() {
            let lastFolder = this._lastFolderTouched ;
            if ( !lastFolder ) {
                return ;
            }
            lastFolder.setExpanded( false ) ;
            setTimeout( () => {
                lastFolder.setExpanded( true ) ;
            }, 500 ) ;
        }

        refreshLastFolder( useChild = true ) {

            let lastFolder = this._lastNodeSelected ;

            if ( !lastFolder ) {
                console.log( `refreshLastFolder() No node selected yet` ) ;
                return ;
            }

            if ( useChild && lastFolder.getParent().title != "root" ) {
                lastFolder = lastFolder.getParent() ;
            }
            console.log( `refreshLastFolder(): `, lastFolder ) ;


            lastFolder.setExpanded( false ) ;
            setTimeout( () => {
                lastFolder.setExpanded( true ) ;
            }, 500 ) ;

        }

        findByPath( treeData, path ) {

            // console.log("findByPath", treeData, path) ;
            return treeData.name == path ;
        }

        simpleUploadSetup() {

            let fileManager = this ;

            this.myDropzone = new Dropzone( ".dropzone", this.dropOptions ) ;


            $( ".hideUpload", this.$container ).click( function () {
                $( ".dnd-upload", this.$container ).hide() ;
                return false ;
            } ) ;

            // http://www.dropzonejs.com/#configuration
            // https://github.com/enyo/dropzone/wiki

            this.myDropzone.on( "complete", function ( file ) {
                fileManager.myDropzone.removeFile( file ) ;
            } ) ;

            this.myDropzone.on( "success", function ( file, response ) {
                let results = JSON.stringify( response.scriptOutput, null, "\t" ) ;
                results = results.replace( /\\n/g, "<br />" ) ;

                alertify.success( results ) ;
            } ) ;

            this.myDropzone.on( "error", function ( file, message ) {
                alertify.csapWarning( "Failed to upload: " + message
                        + ",  Default max size is 500m - confirm file size." ) ;
            } ) ;

            this.myDropzone.on( "failure", function ( file, response ) {
                let results = JSON.stringify( response.scriptOutput, null, "\t" ) ;
                results = results.replace( /\\n/g, "<br />" ) ;

                alertify.success( results ) ;
            } ) ;

            this.myDropzone.on( "sending",
                    function ( file, xhr, formData ) {
                        formData.append( "extractDir",
                                fileManager._lastNodeSelected.data.location ) ;
                        formData.append( "chownUserid", "csap" ) ;
                        formData.append( "skipExtract", "on" ) ;
                        formData.append( "hosts", fileManager.hostName ) ;
                        formData.append( "timeoutSeconds", "30" ) ;
                        formData.append( "serviceName", fileManager.serviceName ) ;
                        formData.append( "overwriteTarget",
                                $( ".overWriteFile", fileManager.$container ).prop( 'checked' ) ) ;
                        alertify.notify( "sending: " + file.name ) ;
                    } ) ;
        }

        isLastItemAFile() {

            let isFile = !this._lastNodeSelected.isFolder() ;

            return isFile ;
        }

        isLastItemAFolder() {

            return this._lastNodeSelected.isFolder() ;
        }

        setupFileBrowser( showShortcuts ) {


            let initialFolders ;

            //let contextMenu = this.buildContextMenu() ;

            if ( this.browseId == "" ) {

                initialFolders = this.buildFileTreeForManager() ;

            } else {

                $( ".usage-note" ).append( "<br>Application Disk Mode" ) ;
                initialFolders = this.buildFileTreeForBrowser() ;

            }

            if ( showShortcuts ) {
                this.addShortcutsToArray( initialFolders ) ;
            }

            // http://wwwendt.de/tech/fancytree/demo/#../3rd-party/extensions/contextmenu/contextmenu.html

            $.ui.fancytree.debugLevel = 1 ;

            //            ( this.$fileTree ).fancytree( this.buildBrowserConfiguration( initialFolders, contextMenu ) ) ;
            this.$fileTree.fancytree( this.buildBrowserConfiguration( initialFolders ) ) ;

            if ( this.quickViewName != ""
                    && !showShortcuts ) {
                this.expandFirstFolder() ;
            }
        }

        getFileBrowser() {
            return this.$fileTree.fancytree( 'getTree' ) ;
        }

        expandFirstFolder() {

            let fileManager = this ;

            let node = this.getFileBrowser().findFirst( function ( node ) {
                return fileManager.findByPath( node.data, fileManager.quickViewName ) ;
            } ) ;

            if ( node ) {
                node.setExpanded( true ) ;
                this._lastNodeSelected = node ;
            }
        }

        buildBrowserConfiguration( initialFolders, contextMenu ) {
            let fileManager = this ;

            let $templates = $( ".jsTemplates", this.$container ) ;

            //            let onMenuSelect = function ( node, action, options ) {
            //                fileManager.performFileOrFolderOperation( node, action, options )
            //            } ;
            let config = {

                //                extensions: [ "contextMenu" ],
                source: initialFolders,
                keyboard: false,

                //                contextMenu: {
                //                    menu: contextMenu,
                //                    actions: onMenuSelect
                //                },
                collapse: function ( event, fancyTreeEvent ) {


                    let fancyNode = fancyTreeEvent.node ;
                    fileManager._lastFolderTouched = fancyNode ;

                    console.log( "buildBrowserConfiguration(): collapse tree", fancyNode ) ;

                    $( ".commands" ).hide() ;
                    $templates.append( this.$filterControls ) ;
                    // console.log("resetting fileControls to template", this.$fileControls) ;  
                    $templates.append( this.$fileControls ) ;
                    $templates.append( this.$commandControls ) ;
                    $templates.append( this.$deleteControls ) ;
                    // logEvent(event, data);

                    if ( $( '#cacheResults:checked' ).length == 0
                            ) {

                        if ( fancyNode.data.neverResetLazy ) {
                            console.log( "node marked for never resetting lazy" ) ;
                        } else {
                            console.log( "Resetting lazy" ) ;
                            fancyNode.resetLazy() ;
                        }
                    }

                },

                expand: function ( e, data ) {
                    //me.loadNode ( me, data) ;
                    fileManager.treeExpandNode( data ) ;
                    let fancyTreeData = data ;
                    let nodeSelected = fancyTreeData.node ;
                    fileManager._lastFolderTouched = nodeSelected ;
                },

                activate: function ( event, data ) {

                    console.log( "buildBrowserConfiguration(): activate tree" ) ;
                    let fancyTreeData = data ;
                    let nodeSelected = fancyTreeData.node ;
                    let nodeData = nodeSelected.data ;

                    // Used for positioning and generation of menu
                    fileManager._lastNodeSelected = nodeSelected ;

                    $( ".last-command", fileManager.$fileTree ).removeClass( "last-command" ) ;
                    nodeSelected.addClass( "last-command" ) ;

                    let $line = $( ".last-command .metaTitle", fileManager.$fileTree ) ;
                    if ( $line.length == 0 ) {
                        $line = $( ".last-command .fancytree-title", fileManager.$fileTree ) ;
                    }
                    $line.append( fileManager.$commandControls ) ;
                    $line.append( fileManager.$deleteControls ) ;
                    fileManager.closeCommandMenu() ;

                    fileManager.$commandControls.off().click( function () {
                        fileManager.showCommandMenu( $( this ) ) ;
                    } )

                    fileManager.$deleteControls.off().click( function () {
                        fileManager.filesystemDelete() ;
                    } )

                    if ( !nodeSelected.folder ) {
                        fileManager._lastFileSelected = nodeSelected ;
                    }

                },
                // https://github.com/mar10/fancytree/blob/master/demo/sample-events.html
                click: function ( event, data ) {

                    try {
                        let location = data.node.data.location ;
                        if ( location.startsWith( "__root__" ) ) {
                            location = location.substr( 8 ) ;
                        }
                        fileManager.$copyBuffer.val( location ) ;
                        fileManager.$copyBuffer.css( "width", location.length + "ch" ) ;

                        let locationWithPlaceholdersResolved = fileManager.updatePlaceHolderName( location ) ;
                        console.log( ` got: location ${location}, swapLocation: ${locationWithPlaceholdersResolved}`, event, data ) ;
                        location = locationWithPlaceholdersResolved ;

                    } catch ( error ) {
                        console.log( "failed deterimining lastSelectDescription" ) ;
                    }

                },

                dblclick: function ( event, data ) {
                    let fancyTreeData = data ;
                    let nodeSelected = fancyTreeData.node ;
                    if ( !nodeSelected.folder ) {
                        fileManager.inlineEdit() ;
                    } else {
                        // refresh a folder conflicts
                        // fileManager.browseFolderInNewWindow() ;
                    }
                },

                renderNode: function ( e, data ) {
                    //me.loadNode ( me, data) ;
                    fileManager.treeRenderNode( fileManager, data ) ;
                },
                lazyLoad: function ( e, data ) {
                    fileManager.loadNode( fileManager, data ) ;
                }
            }

            return config ;
        }

        loadNode( fileManager, data ) {


            $( ".usage-note" ).remove() ;

            let fancyTreeData = data ;

            let fromFolder = fileManager.updatePlaceHolderName( fancyTreeData.node.data.location + "/" ) ;

            let requestParms = {
                "serviceName": this.serviceName,
                "containerName": this.containerName,
                "browseId": this.browseId,
                "fromFolder": fromFolder
            } ;


            console.log( `loadNode(): lazy load tree ${this.quickViewName} `, requestParms ) ;

            if ( $( '.showDu', this.$container ).is( ":checked" ) ) {
                $.extend( requestParms, {
                    showDu: "true"
                } ) ;
            }
            if ( $( '.useRoot', this.$container ).is( ":checked" ) ) {
                $.extend( requestParms, {
                    useRoot: "true"
                } ) ;
            }

            let $deferred_child_request = new $.Deferred() ;
            data.result = $deferred_child_request.promise() ;

            //setTimeout(() => {
            fileManager.getFiles( requestParms, $deferred_child_request ) ;
            //}, 500);
            return ;


        }

        getFiles( requestParms, promisedResult ) {

            console.log( "getFiles() url: " + GET_FILES_URL, " using: ", requestParms ) ;


            // More flexible rendering logic
            $.getJSON(
                    GET_FILES_URL,
                    requestParms )

                    .done( function ( responseJson ) {

                        let fileArray = responseJson ;
                        console.log( "api response:", fileArray ) ;
                        if ( fileArray.length == 0 ) {
                            fileArray = [ {
                                    filter: false,
                                    location: "",
                                    meta: "No items found",
                                    name: "none",
                                    size: 0,
                                    target: "-",
                                    title: "-"
                                } ] ;
                        }
                        promisedResult.resolve( fileArray ) ;

                    } )

                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        console.log( "Failed getting files, request:  ",
                                requestParms,
                                "\n errorThrown", errorThrown, "\n jqXHR data:", jqXHR ) ;

                        fileArray = [ {
                                filter: false,
                                location: "",
                                meta: "Failed to retrieve listing - Refresh page",
                                name: "none",
                                size: 0,
                                target: "-",
                                title: "Error:  "
                            } ] ;
                        promisedResult.resolve( fileArray ) ;
                        //                    handleConnectionError( 
                        //                    		"Failed getting files, request:  ",
                        //                    		JSON.stringify(requestParms), 
                        //                    		"jqXHR data:", JSON.stringify(jqXHR) ) ;
                    } ) ;


        }

        treeExpandNode( data ) {

            let fileManager = this ;

            let fancyTreeData = data ;
            let nodeSelected = fancyTreeData.node ;
            let nodeData = nodeSelected.data ;

            console.log( "expand" ) ;
            $( ".commands" ).hide() ;
            let sortBy = this.$fileSort.filter( ":checked" ).val() ;
            console.log( `Sort id: ${sortBy}` ) ;
            //
            if ( nodeSelected.folder ) {

                $( ".lastOpened", fileManager.$fileTree ).removeClass( "lastOpened" ) ;
                nodeSelected.addClass( "lastOpened" ) ;
                $( ".lastOpened", fileManager.$fileTree ).append( fileManager.$filterControls ) ;
                fileManager._lastOpenedFolder = nodeSelected ;
                fileManager.$listingFilter.val( "" ) ;
                fileManager.$listingFilter.css( "background-color", "white" ) ;
                if ( fileManager._filterMap[fileManager._lastOpenedFolder.data.name] ) {
                    fileManager.$listingFilter.val(
                            fileManager._filterMap[
                                    fileManager._lastOpenedFolder.data.name] ) ;
                    fileManager.$listingFilter.css( "background-color", "yellow" ) ;
                    setTimeout( fileManager.filterLastOpened, 500 ) ;
                }
                fileManager.$listingFilter.off() ;
                fileManager.$listingFilter.focus() ;
                
                setTimeout( function() {
                    //alert( $( "article.content", fileManager.$container).scrollLeft() ) ;
                    $( "article.content", fileManager.$container).scrollLeft( 0 ) ;
                }, 100) ;

                fileManager.$listingFilter.keyup( function () {
                    // 
                    clearTimeout( fileManager._filterTimer ) ;
                    fileManager._filterTimer = setTimeout( function () {
                        fileManager._filterMap[fileManager._lastOpenedFolder.data.name] = fileManager.$listingFilter.val().trim() ;
                        fileManager.$listingFilter.css( "background-color", "yellow" ) ;
                        fileManager.filterLastOpened() ;
                    }, 500 ) ;


                    return false ;
                } ) ;


            }

            if ( nodeSelected.data.neverResetLazy ) {
                console.log( "skipping sort" ) ;

            } else {
                if ( sortBy === "sort-by-name" ) {
                    nodeSelected.sortChildren( this.titleSort ) ;
                } else {
                    nodeSelected.sortChildren( this.sizeSort ) ;
                }
            }
        }

        // Sort folder by item name in alphabetical order
        titleSort( a, b ) {
            //        let x = a.title.toLowerCase(), y = b.title.toLowerCase();
            let x = a.title, y = b.title ;
            return x === y ? 0 : x > y ? 1 : -1 ;
        }

        sizeSort( a, b ) {
            let x = a.data.size, y = b.data.size ;

            // console.log(" x " + x + " y: " + y) ;
            return x === y ? 0 : x > y ? -1 : 1 ;
        }

        treeRenderNode( fileManager, fancyTreeNode ) {

            let userData = fancyTreeNode.node.data ;
            //console.log( "treeRenderNode() ", userData );


            let $description = jQuery( '<div/>', { } ) ;

            let bootRegEx = new RegExp( "application.*yml" ) ;
            let logRegEx = new RegExp( "(.*log)$|(.*txt)$|(catalina.out)$" ) ;
            let propertyRegEx = new RegExp( "(.*properties)$" ) ;
            let shellRegEx = new RegExp( "(.*sh)$" ) ;
            let compressedRegEx = new RegExp( "(.*gz)$|(.*tar)$|(.*zip)$" ) ;
            let javaRegEx = new RegExp( "(.*jar)$" ) ;
            let jsonRegEx = new RegExp( "(.*json)$|(.*yml)$|(.*yaml)$" ) ;
            let mlRegEx = new RegExp( "(.*ml)$" ) ;

            if ( bootRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "boot" ;
            } else if ( logRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "logs" ;
            } else if ( propertyRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "properties" ;
            } else if ( shellRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "shell" ;
            } else if ( compressedRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "compressed" ;
            } else if ( javaRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "java" ;
            } else if ( jsonRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "json" ;
            } else if ( mlRegEx.test( fancyTreeNode.node.title ) ) {
                fancyTreeNode.node.extraClasses = "ml" ;
            } else if ( fancyTreeNode.node.title == "-" ) {
                fancyTreeNode.node.extraClasses = "logs" ;
            }

            if ( userData.meta != undefined
                    && fancyTreeNode.node.title.indexOf( "meta" ) == -1 ) {
                let metaArray = userData.meta
                        .split( "," ) ;

                jQuery( '<span/>', {
                    class: "metaTitle",
                    text: fancyTreeNode.node.title
                } ).appendTo( $description ) ;

                let $extra = jQuery( '<span/>', {
                    class: "meta",
                    text: metaArray[1]
                } ) ;
                $extra.appendTo( $description ) ;

                jQuery( '<span/>', {
                    class: "metaSize",
                    text: metaArray[0]
                } ).prependTo( $extra ) ;

                for ( let i = 2 ; i < metaArray.length ; i++ ) {
                    jQuery( '<span/>', {
                        class: "metaExtra",
                        text: metaArray[i]
                    } ).appendTo( $extra ) ;
                }

                fancyTreeNode.node.setTitle( $description.html() ) ;
                fileManager.registerNodeActions( fileManager ) ;

            }


        }

        filterLastOpened() {
            if ( this._lastOpenedFolder !== null ) {
                let theFilter = this.$listingFilter.val().trim() ;
                let filters = theFilter.split( "," ) ;
                let filesInFolder = this._lastOpenedFolder.getChildren() ;

                let fileNames = filesInFolder.map( kid => kid.data.name ) ;
                console.debug( `filterLastOpened(): ${theFilter} fileNames: ${fileNames}`, this._lastOpenedFolder ) ;

                for ( let ft_file of filesInFolder ) {
                    ft_file.removeClass( "fileHidden" ) ;
                }
                if ( theFilter.length > 0 ) {
                    for ( let ft_file of filesInFolder ) {
                        //console.log( child ) ;
                        let name = ft_file.data.name ;
                        let foundMatch = false ;
                        for ( let filter of filters ) {
                            if ( filter.length > 0 ) {
                                if ( name.toLowerCase().includes( filter.toLowerCase() ) ) {
                                    foundMatch = foundMatch || true ;
                                }
                            }

                        }

                        if ( !foundMatch ) {
                            ft_file.addClass( "fileHidden" ) ;
                        }
                    }
                }

                for ( let ft_file of filesInFolder ) {
                    ft_file.render() ;
                }
            }

        }

        registerNodeActions( fileManager ) {

            clearTimeout( fileManager._nodeTimer ) ;
            //		_nodeTimer = setTimeout( () => {
            //
            //			$( ".fancytree-title" ).attr( "title", "right mouse click for operations" );
            //
            //		}, 500 );
        }

        getTipPath( className ) {

            let path = "" ;
            let $input = $( `input.${className}`, this.$diskPathsForTips ) ;
            if ( $input.length === 1 ) {
                path = $input.val() ;
            }

            console.log( `input class: ${ className} to path: ${path}` ) ;

            return path ;
        }

        updatePlaceHolderName( original ) {
            return original.replaceAll( "SWAP_NAME", utils.getSelectedServiceIdName() ) ;
        }

        buildFileTreeForManager() {

            let defaultTree = new Array() ;

            //
            // Note legacy paths: __props__, __working__, etc. are not used in File manager
            //    cross launches to scripts and commands require full path
            //

            let restApiPrefix = "__root__" ;


            if ( this.dockerBase ) {

                let nodeTitle = this.dockerBase ;
                if ( this.serviceName ) {
                    nodeTitle = this.serviceName ;
                }

                defaultTree.push( {
                    "title": "Container File System: " + nodeTitle + " <span class='meta'>Read Only</span>",
                    "name": "dockerBase",
                    "location": "__docker__" + this.dockerBase,
                    "folder": true,
                    "lazy": true,
                    "extraClasses": "ft_docker",
                    "tooltip": this.dockerBase
                } ) ;
            }

            console.log( `this.serviceName: ${ this.serviceName }, this.folder: ${ this.folder }` ) ;
            if ( ( this.serviceName ) && ( this.folder == "." ) ) {

                let propDisk = this.getTipPath( "propDisk" ) ;
                if ( propDisk.length > 0 ) {

                    let location = propDisk ;
                    if ( !propDisk.startsWith( "dockerContainer:" ) ) {
                        location = restApiPrefix + propDisk ;
                    }


                    defaultTree.push( {
                        "title": this.buildTitleHtml( `${ this.serviceName } [prop]`, propDisk ),
                        "name": "property folder",
                        "location": location,
                        "folder": true,
                        "lazy": true,
                        "extraClasses": "run",
                        "tooltip": propDisk
                    } ) ;

                }

                let appDisk = this.getTipPath( "appDisk" ) ;
                if ( appDisk.length > 0
                        && !appDisk.endsWith( "csap-folder-not-configured" ) ) {


                    let location = appDisk ;
                    if ( !appDisk.startsWith( "dockerContainer:" ) ) {
                        location = restApiPrefix + appDisk ;
                    }

                    defaultTree.push( {
                        "title": this.buildTitleHtml( `${ this.serviceName } [app]`, appDisk ),
                        "name": this.serviceName,
                        "location": location,
                        "folder": true,
                        "cache": false,
                        "lazy": true,
                        "tooltip": appDisk
                    } ) ;


                }

                let workingFolder = this.getTipPath( "fromDisk" ) ;
                if ( workingFolder.length > 0 ) {
                    defaultTree.push( {
                        "title": this.buildTitleHtml( `${ this.serviceName } [deploy]`, workingFolder ),
                        "name": this.serviceName,
                        "location": restApiPrefix + workingFolder,
                        "folder": true,
                        "cache": false,
                        "lazy": true,
                        "tooltip": workingFolder
                    } ) ;
                }


                let jmeterFolder = this.getTipPath( "jmeterDisk" ) ;
                if ( jmeterFolder.length > 0 ) {
                    defaultTree.push( {
                        "title": this.buildTitleHtml( `${ this.serviceName } [jmeter]`, jmeterFolder ),
                        "name": "jmeter",
                        "location": "__working__" + "/" + this.serviceName + "/jmeter/reports",
                        "folder": true,
                        "lazy": true,
                        "extraClasses": "ft_jmeter",
                        "tooltip": jmeterFolder
                    } ) ;
                }
            } else {
                // handle filemanager launchs

                let labelParam = this.label ;

                let viewName = this.folder ; // default to folder
                if ( this.quickViewName != "" ) {
                    viewName = this.quickViewName ;
                }
                if ( labelParam != "" ) {
                    viewName = labelParam ;
                }


                let variablesTestRegEx = new RegExp( "__.*__" ) ;
                let csapKey = this.folder.match( variablesTestRegEx ) ;
                console.log( `viewName: ${viewName}, csapKey match: ${csapKey} ` ) ;

                if ( !variablesTestRegEx.test( this.folder ) ) {


                    defaultTree.push( {
                        "title": viewName,
                        "name": viewName,
                        "location": this.folder,
                        "folder": true,
                        "lazy": true,
                        "extraClasses": "run"
                    } ) ;

                } else {

                    if ( this.quickViewName == "" && labelParam == "" ) {
                        viewName = this.folder.substring( csapKey[0].length ) ;
                    }

                    defaultTree.push( {
                        "title": viewName,
                        "name": viewName,
                        "location": this.folder,
                        "folder": true,
                        "lazy": true,
                        "extraClasses": "run",
                        "tooltip": this.getTipPath( "fromDisk" )
                    } ) ;
                }
            }

            if ( this.quickViewName == "" ) {
                let shortCutChildren = new Array() ;
                this.addShortcutsToArray( shortCutChildren ) ;


                defaultTree.push( {
                    "title": "File System Shortcuts",
                    "folder": true,
                    "children": shortCutChildren,
                    "lazy": false,
                    "extraClasses": "ft_shortcuts",
                    "tooltip": "File System Shortcuts",
                    // custom fields under data
                    "name": "File System Shortcuts",
                    "neverResetLazy": true
                } ) ;
            }


            return defaultTree ;
        }

        buildTitleHtml( name, location ) {

            let $description = jQuery( '<div/>', { } ) ;
            jQuery( '<span/>', {
                class: "metaTitle",
                text: name
            } ).appendTo( $description ) ;

            let $extra = jQuery( '<span/>', {
                class: "meta",
                text: location
            } ).appendTo( $description ) ;
            ;

            return $description.html() ;
        }

        addShortcutsToArray( defaultTree ) {

            this.hideShortcutButton() ;

            defaultTree.push( {
                "title": this.buildTitleHtml( "CSAP Working", "" ),
                "name": "processing",
                "location": "__working__",
                "folder": true,
                "lazy": true,
                "tooltip": this.getTipPath( "processingDisk" ),
                "extraClasses": "ft_folderopen"
            } ) ;

            defaultTree.push( {
                "title": this.buildTitleHtml( "CSAP Platform", "" ),
                "name": "staging",
                "location": "__platform__",
                "folder": true,
                "lazy": true,
                "tooltip": this.getTipPath( "stagingDisk" )
            } ) ;

            //            if ( installDisk != homeDisk ) {
            //                defaultTree.push( {
            //                    "title": "CSAP Install                           ",
            //                    "name": "csapInstall",
            //                    "location": "__platform__/..",
            //                    "folder": true,
            //                    "lazy": true,
            //                    "tooltip": this.getTipPath("jmeterDisk")installDisk
            //                } ) ;
            //            }

            defaultTree.push( {
                "title": this.buildTitleHtml( "CSAP home", this.getTipPath( "homeDisk" ) ),
                "name": "csapHome",
                "location": "__home__",
                "folder": true,
                "lazy": true,
                "tooltip": this.getTipPath( "homeDisk" ),
                "extraClasses": "ft_homedir"
            } ) ;


            let dockerDisk = this.getTipPath( "dockerDisk" ) ;
            if ( dockerDisk !== "" ) {

                let description = "docker exec ls ..." ;
                let isFolder = true ;
                if ( dockerDisk === "_error_" ) {
                    description = "not accessible" ;
                    isFolder = false ;
                }
                defaultTree.push( {
                    "title": this.buildTitleHtml( "docker containers", description ),
                    "name": "docker",
                    "location": "__docker__",
                    "folder": isFolder,
                    "lazy": isFolder,
                    "extraClasses": "ft_docker",
                    "tooltip": dockerDisk + "/containers"
                } ) ;


                defaultTree.push( {
                    "title": this.buildTitleHtml( "docker filesystem", dockerDisk ),
                    "name": "dockerlib",
                    "location": "__root__" + dockerDisk,
                    "folder": true,
                    "lazy": true,
                    "tooltip": dockerDisk,
                    "extraClasses": "root"
                } ) ;

            }

            if ( this.getTipPath( "kubernetesDisk" ) ) {
                defaultTree.push( {
                    "title": this.buildTitleHtml( "kubernetes filesystem", "/var/lib/kubelet" ),
                    "name": "systemd",
                    "location": "__root__/var/lib/kubelet",
                    "folder": true,
                    "lazy": true,
                    "extraClasses": "root"
                } ) ;
            }


            defaultTree.push( {
                "title": this.buildTitleHtml( "linux systemd", "/etc/systemd/system" ),
                "name": "systemd",
                "location": "__root__/etc/systemd/system",
                "folder": true,
                "lazy": true,
                "extraClasses": "root"
            } ) ;

            defaultTree.push( {
                "title": this.buildTitleHtml( "linux Systemd lib", "/usr/lib/systemd/system" ),
                "name": "sysconfig",
                "location": "__root__/usr/lib/systemd/system",
                "folder": true,
                "lazy": true,
                "extraClasses": "root"
            } ) ;

            defaultTree.push( {
                "title": this.buildTitleHtml( "linux sysconfig", "/etc/sysconfig/" ),
                "name": "sysconfig",
                "location": "__root__/etc/sysconfig/",
                "folder": true,
                "lazy": true,
                "extraClasses": "root"
            } ) ;



            defaultTree.push( {
                "title": this.buildTitleHtml( "linux logs", "/var/log" ),
                "name": "syslogs",
                "location": "__root__/var/log",
                "folder": true,
                "lazy": true,
                "extraClasses": "root"
            } ) ;

            defaultTree.push( {
                "title": this.buildTitleHtml( "linux root", "/" ),
                "name": "RootFS",
                "location": "__root__",
                "folder": true,
                "lazy": true,
                "extraClasses": "root"
            } ) ;

        }

        buildFileTreeForBrowser() {

            let defaultTree = new Array() ;


            defaultTree.push( {
                "title": this.browseId,
                "name": "Browse",
                "location": this.browseId,
                "folder": true,
                "lazy": true
            } ) ;

            return defaultTree ;
        }

        collapseToService( serviceName ) {

            this.folder = `${this.originalfolder}/resources/${serviceName}` ;
            this.quickViewName = `Application Definition/resources/${serviceName}` ;

            this.$fileTree.fancytree( "destroy" ) ;
            this.setupFileBrowser() ;

            let isFileMode = $( ".file-mode", this.$container ).is( ":visible" ) ;
            if ( isFileMode ) {
                this.$toggleEditor.trigger( "click" ) ;
            }
        }

        collapseToFolder() {

            let location = this._lastNodeSelected.data.location ;
            let name = this._lastNodeSelected.data.name ;

            this.folder = location ;
            this.quickViewName = name ;

            //this.initialize() ;
            this.$fileTree.fancytree( "destroy" ) ;
            this.setupFileBrowser() ;
        }

        browseFolderInNewWindow() {
            console.log( "newWindow: ", this._lastFileSelected, this._lastNodeSelected ) ;

            // alertify.alert("launch browser");
            let location = this._lastNodeSelected.data.location ;
            let name = this._lastNodeSelected.data.name ;
            let urlAction =
                    `${MANAGER_URL}?quickview=${name}&fromFolder=${location}` ;

            if ( this.serviceName ) {
                urlAction += "&serviceName=" + this.serviceName
            }

            openWindowSafely( urlAction, "_blank" ) ;
            console.log( "lastFileNodeSelected: " + this._lastNodeSelected ) ;
        }

        autoPlay( location, name, apply = false ) {

            let fileManager = this ;

            console.log( `autoPlay() location: ${location}` ) ;

            let autoplayUrl = DEFINITION_URL + "/autoplay" ;
            let parameters = {
                filePath: location,
                isApply: apply
            }

            utils.loading( `autoplay: ${name}  ` ) ;
            $.post( autoplayUrl, parameters )

                    .done( function ( autoplayResults ) {

                        let parsingSummary = autoplayResults["parsing-summary"] ;
                        if ( !parsingSummary ) {
                            parsingSummary = "no-summary" ;
                        } else {
                            autoplayResults["parsing-summary"] = "see above" ;
                        }


                        let parsingResults = autoplayResults["parsing-results"] ;
                        if ( parsingResults ) {
                            autoplayResults["parsing-results"] = "see below" ;

                            let textResponse = utils.json( "plainText", parsingResults ) ;
                            if ( textResponse ) {
                                parsingResults = textResponse ;
                            }
                        }

                        let message = "Summary: " + parsingSummary + "\n\n\n Autoplay results:" + JSON.stringify( autoplayResults, "\n", "\t" ) ;
                        message += "\n\n" + parsingResults ;

                        let foundErrors = message.includes( "autoplay-errors" )
                                || message.includes( "unmapped-host" )
                                || message.includes( "__WARN" )
                                || message.includes( "__ERROR" ) ;

                        if ( foundErrors ) {
                            let yamlParseErrors = utils.json( "autoplay-results.autoplay-errors", autoplayResults ) ;
                            if ( yamlParseErrors ) {
                                message = yamlParseErrors ;
                            }
                        }

                        if ( foundErrors ) {
                            alertify.csapWarning( $( "#auto-play-fail" ).html() + "\n" + message ) ;
                        } else {
                            alertify.csapInfo( $( "#auto-play-pass" ).html() + "\n" + message ) ;
                        }



                        utils.loadingComplete() ;
                        fileManager.refreshLastFolder() ;
                    } )

                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact support" ) ;
                    }, 'json' ) ;

        }

        filesystemAdd() {
            console.log( `filesystemAdd() creating dialog` ) ;

            let fileManager = this ;


            let $operationsContainers = $( ".file-system-operations>div", this.$processFileSystemDialog ) ;
            let $newItems = $( ".file-system-new-items", this.$processFileSystemDialog ) ;
            let $workingFolder = $( ".file-system-working-folder", this.$processFileSystemDialog ) ;
            let $newFolder = $( ".file-system-new-folder", this.$processFileSystemDialog ) ;
            let $newFile = $( ".file-system-new-file", this.$processFileSystemDialog ) ;

            let $useRoot = $( ".file-system-use-root", this.$container ) ;


            $workingFolder.text( fileManager._lastNodeSelected.data.location ) ;
            $newFolder.val( "" ) ;
            $newFile.val( "" ) ;

            let restoreFunction = function () {
                no_op_cancel_function() ;
                fileManager.$processFileSystemDialog.appendTo( $( 'aside .jsTemplates', fileManager.$container ) ) ;
            }

            let okFunction = function () {
                let inputMap = {
                    fromFolder: fileManager._lastNodeSelected.data.location,
                    serviceName: fileManager.serviceName,
                    newFolder: $newFolder.val(),
                    newFile: $newFile.val(),
                    root: $useRoot.is( ':checked' )
                } ;

                restoreFunction() ;

                let addUrl = FILE_URL + "/filesystem" ;

                $.post( addUrl, inputMap )

                        .done( function ( filesystemResults ) {
                            alertify.csapInfo( filesystemResults["plain-text"], true ) ;
                            fileManager.refreshLastFolder( false ) ;
                        } )

                        .fail( function ( jqXHR, textStatus, errorThrown ) {
                            alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact support" ) ;
                        }, 'json' ) ;
            }




            //let $description = jQuery( '<div/>', { "id:", ""} );
            let dialog = alertify.confirm(
                    "Create Folder/File",
                    '<div id="file-folder-create-prompt"></div>',
                    okFunction,
                    function () {
                        restoreFunction() ;
                    }
            ) ;

            this.updateConfirmLabels( dialog, 'Create', 'Cancel' ) ;

            $useRoot.prop( "checked", false ) ;
            this.$processFileSystemDialog.show() ;
            $operationsContainers.hide() ;
            $newItems.show() ;
            this.$processFileSystemDialog.appendTo( $( '#file-folder-create-prompt' ) ) ;


        }

        filesystemDelete() {
            console.log( `filesystemDelete() creating dialog` ) ;
            let fileManager = this ;

            let $operationsContainers = $( ".file-system-operations>div", this.$processFileSystemDialog ) ;
            let $deleteItems = $( ".file-system-delete-items", this.$processFileSystemDialog ) ;
            let $deleteRecursive = $( ".file-system-delete-recursive", this.$processFileSystemDialog ) ;
            let $useRoot = $( ".file-system-use-root", this.$processFileSystemDialog ) ;
            let $workingFolder = $( ".file-system-working-folder", this.$processFileSystemDialog ) ;

            $workingFolder.text( fileManager._lastNodeSelected.data.location ) ;

            $( "button", $deleteItems ).off().click( function () {
                fileManager.openCommandWindow( "delete" ) ;
                alertify.closeAll() ;

            } )

            let restoreFunction = function () {
                no_op_cancel_function() ;
                fileManager.$processFileSystemDialog.appendTo( $( 'aside .jsTemplates', fileManager.$container ) ) ;
            }

            let okFunction = function () {
                let inputMap = {
                    fromFolder: fileManager._lastNodeSelected.data.location,
                    serviceName: this.serviceName,
                    recursive: $deleteRecursive.is( ':checked' ),
                    root: $useRoot.is( ':checked' )
                } ;

                let resourceUrl = FILE_URL + "/filesystem" ;

                restoreFunction() ;

                utils.loading( ` Deleting: ${fileManager._lastNodeSelected.data.location}` ) ;

                $.delete( resourceUrl, inputMap )

                        .done( function ( filesystemResults ) {
                            utils.loadingComplete() ;
                            alertify.csapInfo( filesystemResults["plain-text"], true ) ;
                            fileManager.refreshLastFolder() ;
                        } )

                        .fail( function ( jqXHR, textStatus, errorThrown ) {
                            alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact support" ) ;
                        }, 'json' ) ;
            }

            //let $description = jQuery( '<div/>', { "id:", ""} );
            let dialog = alertify.confirm(
                    "Delete Confirmation",
                    '<div id="file-folder-delete-prompt"></div>',
                    okFunction,
                    function () {
                        restoreFunction() ;
                    }
            ) ;


            fileManager.updateConfirmLabels( dialog, 'Delete Item(s)', 'Cancel' ) ;

            $operationsContainers.hide() ;
            $deleteItems.show() ;
            $deleteRecursive.prop( "checked", false ) ;
            $useRoot.prop( "checked", false ) ;
            this.$processFileSystemDialog.show() ;
            this.$processFileSystemDialog.appendTo( $( '#file-folder-delete-prompt' ) ) ;


        }

        filesystemRename() {
            let fileManager = this ;

            console.log( `filesystemRename() creating dialog` ) ;
            let $operationsContainers = $( ".file-system-operations>div", this.$processFileSystemDialog ) ;
            let $renameItems = $( ".file-system-rename-items", this.$processFileSystemDialog ) ;

            let $renameItem = $( ".file-system-new-name", this.$processFileSystemDialog ) ;
            let $workingFolder = $( ".file-system-working-folder", this.$processFileSystemDialog ) ;

            let $useRoot = $( ".file-system-use-root", this.$processFileSystemDialog ) ;
            let $useRename = $( ".file-system-use-rename", this.$processFileSystemDialog ) ;


            $workingFolder.text( fileManager._lastNodeSelected.data.location ) ;
            $renameItem.val( fileManager._lastNodeSelected.data.location ) ;

            let restoreFunction = function () {
                no_op_cancel_function() ;
                fileManager.$processFileSystemDialog.appendTo( $( 'aside .jsTemplates', fileManager.$container ) ) ;
            }

            let okFunction = function () {
                let inputMap = {
                    fromFolder: fileManager._lastNodeSelected.data.location,
                    serviceName: fileManager.serviceName,
                    newName: $renameItem.val(),
                    rename: $useRename.is( ':checked' ),
                    root: $useRoot.is( ':checked' )
                } ;

                restoreFunction() ;

                let resourceUrl = FILE_URL + "/filesystem" ;

                $.put( resourceUrl, inputMap )

                        .done( function ( filesystemResults ) {
                            alertify.csapInfo( filesystemResults["plain-text"], true ) ;
                            fileManager.refreshLastFolder() ;
                        } )

                        .fail( function ( jqXHR, textStatus, errorThrown ) {
                            alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact support" ) ;
                        }, 'json' ) ;
            }

            //let $description = jQuery( '<div/>', { "id:", ""} );
            let dialog = alertify.confirm(
                    "Rename Dialog",
                    '<div id="file-folder-rename-prompt"></div>',
                    okFunction,
                    function () {
                        restoreFunction() ;
                    }
            ) ;


            this.updateConfirmLabels( dialog, 'Rename/Copy Item(s)', 'Cancel' ) ;

            $useRoot.prop( "checked", false ) ;
            $useRename.prop( "checked", false ) ;
            $operationsContainers.hide() ;
            $renameItems.show() ;
            this.$processFileSystemDialog.show() ;
            this.$processFileSystemDialog.appendTo( $( '#file-folder-rename-prompt' ) ) ;


        }

        //        performFileOrFolderOperation( node, action, options ) {
        //
        //            console.log( 'Selected action "' + action + '" on node ' + node ) ;
        //
        //            switch ( action ) {
        //
        //                case "newWindow":
        //                    this.browseFolderInNewWindow() ;
        //                    break ;
        //
        //                case "filesystemAdd":
        //                    this.filesystemAdd() ;
        //                    break ;
        //
        //                case "filesystemDelete":
        //                    this.filesystemDelete() ;
        //                    break ;
        //
        //                case "filesystemRename":
        //                    this.filesystemRename() ;
        //                    break ;
        //
        //                case "viewEditInCurrentWindow":
        //                    this.inlineEdit() ;
        //                    break ;
        //
        //                case "view":
        //                    console.log( "lastFileNodeSelected: " + this._lastNodeSelected ) ;
        //                    let inputMap = {
        //                        fromFolder: this._lastNodeSelected.data.location,
        //                        serviceName: this.serviceName,
        //                        "browseId": this.browseId,
        //                        hostName: this.hostName
        //                    } ;
        //                    this.postAndRemove( "_blank", contextUrl + "downloadFile/"
        //                            + this._lastNodeSelected.data.name, inputMap ) ;
        //                    break ;
        //
        //                case "download":
        //                    console.log( "lastFileNodeSelected: " + this._lastNodeSelected ) ;
        //                    let inputMap = {
        //                        fromFolder: this._lastNodeSelected.data.location,
        //                        serviceName: this.serviceName,
        //                        isBinary: true,
        //                        "browseId": this.browseId,
        //                        hostName: this.hostName
        //                    } ;
        //                    this.postAndRemove( "_blank", contextUrl + "downloadFile/"
        //                            + this._lastNodeSelected.data.name, inputMap ) ;
        //                    break ;
        //
        //                case "tail":
        //                    let inputMap = {
        //                        fileName: this._lastNodeSelected.data.location,
        //                        serviceName: this.serviceName,
        //                        "browseId": this.browseId,
        //                        hostName: this.hostName
        //                    } ;
        //                    this.postAndRemove( "_blank", contextUrl + "FileMonitor", inputMap ) ;
        //                    break ;
        //
        //                case "uploadSimple":
        //
        //                    $( "#extractDir" ).val( this._lastNodeSelected.data.location ) ;
        //
        //                    $( "#uploadToSpan" ).text( this._lastNodeSelected.data.location ) ;
        //                    // lastFileNodeSelected.data.name
        //
        //                    $( ".dnd-upload", this.$container ).show() ; //.center() ;
        //                    break ;
        //
        //                case "search":
        //                    let command = action ;
        //                    let searchLocation = this._lastNodeSelected.data.location ;
        //                    if ( this.isLastItemAFolder() ) {
        //                        searchLocation += "/*"
        //                    }
        //                    let inputMap = {
        //                        fromFolder: searchLocation,
        //                        serviceName: this.serviceName,
        //                        "browseId": this.browseId,
        //                        hostName: this.hostName,
        //                        command: "logSearch"  // action mapped from search
        //                    } ;
        //                    this.postAndRemove( "_blank", OS_URL + "/command", inputMap ) ;
        //                    break ;
        //                default:
        //                    this.openCommandWindow( action ) ;
        //
        //
        //            }
        //        }

        openCommandWindow( action ) {
            let parameters = {
                fromFolder: this._lastNodeSelected.data.location,
                serviceName: this.serviceName,
                "browseId": this.browseId,
                command: action
            } ;
            //
            if ( action === "script" ) {
                if ( utils.isAgent() ) {
                    utils.launchScript( parameters ) ;
                } else {
                    utils.openAgentWindow( utils.getHostName(), "/app-browser#agent-tab,script", parameters ) ;
                }
            } else {
                this.postAndRemove( "_blank", OS_URL + "/command", parameters ) ;
            }
        }

        logEvent( event, data, msg ) {
            // let args = $.isArray(args) ? args.join(", ") :
            msg = msg ? ": " + msg : "" ;
            $.ui.fancytree.info( "Event('" + event.type + "', node=" + data.node + ")"
                    + msg ) ;
        }

        postAndRemove( windowFrameName, urlAction, inputMap ) {

            let $form = $( '<form id="temp_form"></form>' ) ;

            $form.attr( "action", urlAction ) ;
            $form.attr( "method", "post" ) ;
            $form.attr( "target", getValidWinName( windowFrameName ) ) ;

            $.each( inputMap, function ( k, v ) {

                if ( v != null ) {
                    $form.append( '<input type="hidden" name="' + k + '" value="' + v
                            + '"/>' ) ;
                }
            } ) ;

            console.log( "Post to '" + urlAction + "'", inputMap ) ;

            $( "body" ).append( $form ) ;

            $form.submit() ;
            $form.remove() ;

        }

    }

    return FileManager ;


} ) ;