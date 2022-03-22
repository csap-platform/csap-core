
// const hostExplorerImports = [
//     "browser/utils",
//     "agent/dialog-create-container",
//     "agent/explorer-operations",
//     "agent/host-operations",
//     "editor/json-forms",
//     "ace/ace",
//     "jsYaml/js-yaml" ] ;
// //
// //
// //
// define( hostExplorerImports, function ( utils, createContainerDialog, explorerOps, hostOps, jsonForms, aceEditor, jsYaml ) {


import _dom from "../../utils/dom-utils.js";


import _dialogs from "../../utils/dialog-utils.js";


import { aceEditor, jsYaml } from "../../libs/file-editor.js"


import loadTreeComponents from "../../libs/fancy-tree.js"

import utils from "../utils.js"


import createContainerDialog from "./dialog-create-container.js"
import explorerOps from "./explorer-operations.js"
import hostOps from "./host-operations.js"
import jsonForms from "../../editor/json-forms.js";


//import commandRunner from "./command-runner.js"


export const hostExplorer = host_explorer();

// export default agent ;


function host_explorer() {

    _dom.logHead( "Module loaded" );


    console.log( "Module loaded 99" );

    const $agentExplorerTab = utils.findContent( "#agent-tab-explorer" );
    const $explorerTree = $( "#dockerTree", $agentExplorerTab );
    const $explorerHeader = $( "header.related", $agentExplorerTab );
    const $cliRunner = $( "select#cli-runner", $explorerHeader );

    let _explorerTree; // fancy tree object
    let _explorerTreeScollPosition;

    let _jsonDisplayEditor, _jsonDisplayLatestContent;
    let $jsonDisplayPre = $( "#jsonDisplay-editor-container" );

    // filter controls
    let isK8ContainerFiltering = true;
    let _filterTimer = null;
    let $filterControls;
    let _filterMap = new Object();
    let $treeFilter = null;

    let $packageInfo = $( "#node-info-panel" );


    let $lastNodeTextContainer = null;

    let _lastOpenedFolder;

    const $templateRepo = $( "#kubernetes-dashboard-templates" );

    let _initComplete = false;


    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {
            // lazy
            if ( !_initComplete ) {
                // restore templates

                if ( _initComplete ) {
                    console.log( `restoring templates` );
                    $templateRepo.append( $( ".k8-dash-templates" ) );
                }

                initialize();
                _initComplete = true;
            }


            // let hostStatusPromise2


            // let hostStatusPromise = new Promise( (resolve, reject) => {

            //     loadTreeComponents()
            //     .then( () => {

            //         if ( forceHostRefresh || !_explorerTree ) {
                        
            //             _dom.logHead( `Building explorer tree: fancytree` );

            //             explorerOps.hideTreeCommands( true );
            //             _explorerTree = $explorerTree.fancytree( buildTreeConfiguration() );

            //         } else {
            //             console.log( `Scrolling to: ${ _explorerTreeScollPosition } ` );
            //             $explorerTree.scrollTop( _explorerTreeScollPosition );
            //         }

            //         explorerOps.refresh_host_status()
            //             .then( () => {
            //                 resolve("host status updated") ;
            //             }) ;



            //     } )
            //     .catch( ( e ) => {
            //         console.warn( e );
            //     } );

            // })


            async function loadTreeAndThenRunHostStatus(  ) {

                await loadTreeComponents() ;

                if ( forceHostRefresh || !_explorerTree ) {
                        
                    _dom.logHead( `Building explorer tree: fancytree` );

                    explorerOps.hideTreeCommands( true );
                    _explorerTree = $explorerTree.fancytree( buildTreeConfiguration() );

                } else {
                    console.log( `Scrolling to: ${ _explorerTreeScollPosition } ` );
                    $explorerTree.scrollTop( _explorerTreeScollPosition );
                }

                await explorerOps.refresh_host_status() ;
            }

            // aborts when agent tab hidden - restart
            return loadTreeAndThenRunHostStatus();

        }
    }

    function initialize() {
        console.log( "Initializing tree" );

        utils.registerForNavChanges( navigationListener );



        $cliRunner.change( function () {

            console.log( `cli-header` );
            hostOps.commandRunner( $( this ) );

            let firstOption = $cliRunner.children( ":first" ).attr( "value" );
            setTimeout( function () {

                $cliRunner.val( firstOption );
                $( "input.cliRunner-combobox-input", $cliRunner.parent() ).val( firstOption );
            }, 500 )

            return false;
        } )

        $( "button.csap-search", $explorerHeader ).click( function () {

            $( this ).css( `opacity`, "0" );
            buildCommandCombo();
            $( "input.cliRunner-combobox-input", $cliRunner.parent() ).click( function () {
                $( this ).val( `` );
            } );
        } );


        $lastNodeTextContainer = $( "#last-selected-text" );
        $lastNodeTextContainer.click( function () {
            $( this ).select();
            document.execCommand( "copy" );
        } );


        createContainerDialog.initialize( function () {
            explorerOps.refreshFolder( hostOps.categories().dockerImages );
        } );

        $filterControls = $( "#filterControls" );
        $treeFilter = $( "#tree-display-filter" );

        explorerOps.initialize( $explorerTree );


        $( "#close-all-explorer-tree" ).click( treeCloseAllOpen );

    }


    function buildCommandCombo() {

        $.widget( "custom.cliComboBox", {
            _create: function () {
                this.wrapper = $( "<span>" )
                    .addClass( "custom-combobox" )
                    .insertAfter( this.element );

                this.element.hide();
                this._createAutocomplete();
                this._createShowAllButton();
            },

            _createAutocomplete: function () {


                console.log( `build _createAutocomplete` );

                let selected = this.element.children( ":selected" ),
                    value = selected.val() ? selected.text() : "";

                this.input = $( "<input>" )
                    .appendTo( this.wrapper )
                    .val( value )
                    .attr( "title", "" )
                    .addClass( "cliRunner-combobox-input ui-widget ui-widget-content ui-state-default ui-corner-left" )
                    .autocomplete( {
                        delay: 0,
                        minLength: 0,
                        source: $.proxy( this, "_source" )
                    } )
                    .tooltip( {
                        classes: {
                            "ui-tooltip": "ui-state-highlight"
                        }
                    } );

                this._on( this.input, {
                    autocompleteselect: function ( event, ui ) {
                        ui.item.option.selected = true;
                        this._trigger( "select", event, {
                            item: ui.item.option
                        } );
                    },

                    autocompletechange: "_removeIfInvalid"
                } );
            },

            _createShowAllButton: function () {
                let input = this.input,
                    wasOpen = false;

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
                        wasOpen = input.autocomplete( "widget" ).is( ":visible" );
                    } )

                    .on( "click", function () {
                        input.trigger( "focus" );

                        // Close if already visible
                        if ( wasOpen ) {
                            return;
                        }

                        console.log( `invoking autocomplete` );

                        // Pass empty string as value to search for, displaying all results
                        input.autocomplete( "search", "" );
                    } );
            },

            _source: function ( request, response ) {

                console.log( `build source2` );

                let matcher = new RegExp( $.ui.autocomplete.escapeRegex( request.term ), "i" );
                //                response( this.element.children( "option" ).map( function () {
                response( $( "option", $cliRunner ).map( function () {
                    let optionText = $( this ).text();
                    let optionValue = $( this ).attr( "value" );

                    //                    console.log( `build source: ${text}`) ;
                    if ( this.value && ( !request.term || matcher.test( optionValue ) ) ) {
                        return {
                            label: optionValue,
                            value: optionValue,
                            option: this
                        };
                    }
                } ) );
            },

            _removeIfInvalid: function ( event, ui ) {

                // Selected an item, nothing to do
                if ( ui.item ) {
                    return;
                }

                // Search for a match (case-insensitive)
                let value = this.input.val(),
                    valueLowerCase = value.toLowerCase(),
                    valid = false;
                this.element.children( "option" ).each( function () {
                    if ( $( this ).text().toLowerCase() === valueLowerCase ) {
                        this.selected = valid = true;
                        return false;
                    }
                } );

                // Found a match, nothing to do
                if ( valid ) {
                    return;
                }

                // Remove invalid value
                this.input
                    .val( "" )
                    .attr( "title", value + " didn't match any item" )
                    .tooltip( "open" );
                this.element.val( "" );
                this._delay( function () {
                    this.input.tooltip( "close" ).attr( "title", "" );
                }, 2500 );
                this.input.autocomplete( "instance" ).term = "";
            },

            _destroy: function () {
                this.wrapper.remove();
                this.element.show();
            }
        } );

        console.log( `Building combo box for ${ $( "option", $cliRunner ).length } items` );
        $cliRunner.cliComboBox( {
            select: function ( event, ui ) {
                $cliRunner.trigger( "change" );
            }
        } );

        $( "input.cliRunner-combobox-input", $explorerHeader ).val( $( "option:selected", $cliRunner ).attr( "value" ) );
    }

    function navigationListener( lastNavigationClickedLabel ) {
        if ( $agentExplorerTab.attr( "id" ) === lastNavigationClickedLabel ) {
            _explorerTreeScollPosition = $explorerTree.scrollTop();
            console.log( `navigationListener(): updating scroll context for: ${ $explorerTree.attr( "id" ) } : ${ _explorerTreeScollPosition }` );
        }
    }

    function treeCloseAllOpen() {
        console.log( "closing all" );
        $explorerTree.fancytree( "getTree" ).expandAll( false );
    }

    function buildTreeConfiguration() {

        console.log( "buildTreeConfiguration(): ", hostOps.categoryUI() );
        let config = {
            source: children_using_array(),
            keyboard: false,

            types: hostOps.categoryUI(),

            icon: function ( event, data ) {
                return data.typeInfo.icon;
            },
            iconTooltip: function ( event, data ) {
                return data.typeInfo.iconTooltip;
            },

            collapse: tree_collapse_folder,

            expand: tree_expand_folder,

            activate: tree_activate_node,

            renderNode: function ( event, data ) {

                let fancyTreeData = data;
                // console.log( "fancyTreeData: " , fancyTreeData );
                tree_render_item( fancyTreeData.node );
            },

            lazyLoad: tree_lazy_load_item
        };

        return config;
    }

    function tree_collapse_folder( event, data ) {

        let fancyTreeData = data;
        let nodeType = fancyTreeData.node.type;
        let nodeSelected = fancyTreeData.node;
        let nodeData = nodeSelected.data;

        console.log( "treeCollapseNode()", nodeType, nodeData );


        hide_node_info_panel();
        explorerOps.refresh_host_status();

        explorerOps.hideTreeCommands();
        hide_treeFilter();

        // reload values from definition - could optimize and only reset when modified
        //        if ( nodeType == hostOperations.categories().linuxProcesses && nodeData.attributes != undefined ) {
        //            // process rendering will not work when child attributes are closed then re-opened
        //            console.log( "Cached data will still be used." ) ;
        //        } else {
        //            console.log( "Node will be reloaded" ) ;
        //            nodeSelected.resetLazy() ;
        //        }

        if ( nodeData.path && nodeData.path == "/" ) {
            console.log( "Node will be reloaded" );
            nodeSelected.resetLazy();

        } else if ( nodeData.attributes && nodeData.attributes.folderUrl ) {
            console.log( "Node will be reloaded" );
            nodeSelected.resetLazy();

        } else {
            console.log( "Cached data will still be used." );
        }

        nodeSelected.setActive();


    }

    function tree_lazy_load_item( fancyTreeEvent, data ) {

        let fancyTreeData = data;
        let nodeType = fancyTreeData.node.type;
        let nodeSettings = fancyTreeData.node.data;

        console.log( `tree_lazy_load_item() type: ${ nodeType },  fancyTreeData:`, fancyTreeData, "nodeSettings:", nodeSettings );

        if ( nodeSettings.jsonEdit ) {
            console.error( `Warning jsonEdit deprecated - add content viewer if needed` );
        }

        if ( nodeSettings.attributes &&
            ( !nodeSettings.attributes.folderUrl ) ) {

            return data.result =
                children_using_parent_attributes(
                    nodeType,
                    fancyTreeData.node );
        }


        let serviceName = "";
        if ( nodeType == hostOps.categories().linuxServices ) {
            serviceName = nodeSettings.filterValue;
        }


        // build parameters
        let requestParameters = {};
        let paramSelector = nodeType;
        if ( nodeType.startsWith( "kubernetes" ) )
            paramSelector = "kubernetes";

        if ( nodeType.startsWith( "vsphere" ) )
            paramSelector = "vsphere";

        switch ( paramSelector ) {

            case "vsphere":
                requestParameters = { "configurationFilter": $( "#vsphere-dc-checkbox" ).is( ":checked" ) };
                break;

            case "kubernetes":
                requestParameters = {
                    "namespace": explorerOps.kubernetesNameSpace(),
                    "maxEvents": $( "#max-events" ).val()
                };
                break;

            case hostOps.categories().dockerContainers:
                requestParameters = { "showFilteredItems": $( "#showFilteredItems input" ).is( ":checked" ) };
                break;

            case hostOps.categories().linuxServices:
                requestParameters = { "serviceName": serviceName };
                break;
        }

        let folderUri = nodeType;

        // data driven uris...use uris
        if ( nodeSettings.attributes
            && nodeSettings.attributes.folderUrl ) {

            folderUri = nodeSettings.attributes.folderUrl;
            $.extend( requestParameters, {
                "path": nodeSettings.attributes.path,
                "namespace": explorerOps.kubernetesNameSpace()
            } );

        }

        let $deferred_child_request = new $.Deferred();

        let sourceUrl = explorerUrl + "/" + folderUri;
        console.log( `tree_lazy_load_item: loading from ${ sourceUrl } with: `, requestParameters );
        // More flexible rendering logic
        $.getJSON(
            sourceUrl,
            requestParameters )

            .done( function ( resourceReport ) {

                if ( isObject( resourceReport ) ) {


                    $deferred_child_request.resolve(
                        children_using_object( nodeType, fancyTreeEvent, resourceReport )
                    );

                } else {
                    $deferred_child_request.resolve(
                        children_using_array( nodeType, fancyTreeEvent, resourceReport )
                    );

                }
                $deferred_child_request = null;


            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown );
            } );

        data.result = $deferred_child_request.promise();
    }

    function hide_treeFilter() {
        console.log( "hide_treeFilter()" );
        $( "#jsTemplates" ).append( $filterControls );
    }

    function tree_activate_node( event, treeActivationObject ) {

        hide_node_info_panel();
        //hide_treeFilter() ;

        let nodeSelected = treeActivationObject.node;
        let nodeType = nodeSelected.type;
        let nodeData = nodeSelected.data;

        $( this ).css( "border", "5px" );

        console.log( `tree_activate_node() title: ${ nodeSelected.title } path: ${ nodeData.path } \n type: ${ nodeType }, tree width: ${ $( "#linuxTree" ).parent().outerWidth( true ) }`,
            "\n\t data:", treeActivationObject,
            "\n\t nodeSelected: ", nodeSelected,
            "\n\t nodeData:", nodeData );


        try {
            let lastSelectedNodeInfo = jsonForms.getValue( "originalTitle", nodeData );
            let filterValue = jsonForms.getValue( "filterValue", nodeData );
            if ( filterValue ) {
                lastSelectedNodeInfo = filterValue;
            }
            let metaName = jsonForms.getValue( "attributes.metadata.name", nodeData );
            if ( metaName ) {
                lastSelectedNodeInfo = metaName;
            }
            if ( typeof lastSelectedNodeInfo === 'string' ) {
                $lastNodeTextContainer.val( lastSelectedNodeInfo );
                $lastNodeTextContainer.css( "width", lastSelectedNodeInfo.length + "ch" );
            }
        } catch ( error ) {
            console.log( "failed deterimining lastSelectDescription" );
        }


        if ( nodeData.path != "/" ) {

            let targetWidth = $( window ).outerWidth( true ) - $( "#linuxTree" ).parent().outerWidth( true ) - 100;
            $packageInfo.css( "max-width", Math.round( targetWidth ) );
            //$packageInfo.css("max-height", Math.round(targetWidth)) ;

            switch ( nodeType ) {


                case hostOps.categories().jsonEditor:
                    setTimeout( function () {
                        showContentViewer( nodeData.attributes );

                        setTimeout( function () {
                            nodeSelected.parent.setActive();
                        }, 500 );
                    }, 100 );

                    break;


                case hostOps.categories().linuxPackages:
                    tree_activate_show_linux_package( nodeSelected, nodeData );
                    break;


                case hostOps.categories().linuxServices:
                    tree_activate_show_linux_service( nodeSelected, nodeData );
                    break;
            }
        }

        explorerOps.updateSelectedNode( nodeSelected );

    }


    function showContentViewer( objectToView ) {

        console.log( `attribute_title_aceEditor` );
        _jsonDisplayLatestContent = JSON.stringify( objectToView, "\n", "\t" );

        if ( !alertify.jsonDisplay ) {

            _jsonDisplayEditor = aceEditor.edit( $jsonDisplayPre.attr( "id" ) );
            _jsonDisplayEditor.setOptions( utils.getAceDefaults( "ace/mode/yaml", true ) );

            let csapDialogFactory = _dialogs.dialog_factory_builder( {
                content: $( "#jsonDisplay-editor-dialog" ).show()[ 0 ],
                onresize: jsonDisplayEditorResize
            } );

            alertify.dialog( 'jsonDisplay', csapDialogFactory, false, 'confirm' );

            $( "#jde-json-mode", $jsonDisplayPre.parent() ).change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    _jsonDisplayEditor.getSession().setValue( _jsonDisplayLatestContent );
                    _jsonDisplayEditor.getSession().setMode( "ace/mode/json" );
                } else {
                    _jsonDisplayEditor.getSession().setValue( dumpAndIndentAsYaml( _jsonDisplayLatestContent ) );
                    _jsonDisplayEditor.getSession().setMode( "ace/mode/yaml" );
                }
            } );

            $( "#jde-fold-mode", $jsonDisplayPre.parent() ).change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    _jsonDisplayEditor.getSession().foldAll( 2 );
                } else {
                    _jsonDisplayEditor.getSession().unfold();
                }
            } );
        }

        // reset view back to default
        $( "#jde-json-mode", $jsonDisplayPre.parent() ).prop( "checked", false );
        $( "#jde-fold-mode", $jsonDisplayPre.parent() ).prop( "checked", false );
        _jsonDisplayEditor.getSession().setMode( "ace/mode/yaml" );
        _jsonDisplayEditor.getSession().setValue( dumpAndIndentAsYaml( _jsonDisplayLatestContent ) );


        alertify.jsonDisplay().show();

        _jsonDisplayEditor.focus();


    }


    function jsonDisplayEditorResize( dialogWidth, dialogHeight ) {

        setTimeout( function () {



            let maxWidth = dialogWidth - 10;
            $jsonDisplayPre.css( "width", maxWidth );
            $jsonDisplayPre.css( "margin-left", "5px" );
            $jsonDisplayPre.css( "margin-top", "5px" );

            let maxHeight = dialogHeight
                - Math.round( $( "div.flex-container", $jsonDisplayPre.parent() ).outerHeight( true ) )
                - 20;
            $jsonDisplayPre.css( "height", maxHeight );

            console.log( `kubernetes_yaml_dialog() launched/resizing yaml editor` );
            _jsonDisplayEditor.resize();


        }, 500 );

    }

    function dumpAndIndentAsYaml( text ) {

        let yamlText = jsYaml.dump( JSON.parse( text ) );
        yamlText = utils.yamlSpaces(
            yamlText,
            [ "metadata", "spec", "status" ],
            false );

        return yamlText;
    }

    function hide_node_info_panel() {

        $packageInfo.hide();
        $( ">div", $packageInfo ).hide();


        $( "button.csap-remove", $packageInfo ).off().click( function () {
            $packageInfo.hide();
        } );

        $( "#wrap-info-panel" ).off().change( function () {

            $packageDivs = $( "div>div", $packageInfo );
            if ( $( this ).is( ":checked" ) ) {
                $packageDivs.css( "white-space", "pre-wrap" );
            } else {
                $packageDivs.css( "white-space", "pre" );
            }
        } );
    }

    function tree_activate_show_linux_service( nodeSelected, nodeData ) {
        let requestParms = {
            //"csapFilter": nodeSettings.csapFilter,
            "name": nodeSelected.title
        };

        $.getJSON(
            explorerUrl + "/" + nodeSelected.type + "/info",
            requestParms )

            .done( function ( responseJson ) {
                //console.log (responseJson) ;

                let $info = jQuery( '<div/>', {} );

                $info.append( jQuery( '<button/>', {
                    text: 'View Logs',
                    class: 'csap-button'
                } ) );
                $info.append( "<br/>" );

                $( ".description", $packageInfo ).html( $info.html() + responseJson.description );

                $( "button", $( "#linux-service-info" ) ).off().click( function () {
                    hide_node_info_panel();
                    let parameterMap = {
                        journal: nodeSelected.title
                    };
                    utils.launchLogs( parameterMap );
                    //                        let parameters = `fileName=${ "__journal__/" + nodeSelected.title}&hostName=${uiSettings.hostName}` ;
                    //                        let encodedUrl = `${ uiSettings.baseUrl }file/FileMonitor?${ encodeURI( parameters ) }` ;
                    //                        console.log( `opening logs in ${encodedUrl }` ) ;
                    //
                    //                        window.open( encodedUrl, '_blank' ) ;
                    //                        let parameters = {
                    //                            fileName: "__journal__/" + nodeSelected.title,
                    //                            serviceName: null,
                    //                            hostName: uiSettings.hostName
                    //                        } ;
                    //                        postAndRemove( "_blank",
                    //                                uiSettings.baseUrl + "file/FileMonitor",
                    //                                parameters ) ;
                } );

                $packageInfo.show();
                $( "#linux-service-info" ).show();
            } );
    }

    function tree_activate_show_linux_package( nodeSelected, nodeData ) {
        let requestParms = {
            //"csapFilter": nodeSettings.csapFilter,
            "name": nodeSelected.title
        };

        $.getJSON(
            explorerUrl + "/" + nodeSelected.type + "/info",
            requestParms )

            .done( function ( responseJson ) {
                //console.log (responseJson) ;

                let $info = jQuery( '<div/>', {} );
                if ( responseJson.url != "" ) {
                    $info.append( jQuery( '<a/>', {
                        class: 'simple',
                        href: responseJson.url,
                        text: responseJson.url,
                        target: '_blank',
                    } ) );
                    $info.append( "<br/>" );
                }
                $( ".description", $packageInfo ).html( $info.html() + responseJson.description );
                let $details = $( ".details", $packageInfo );
                $details
                    .html( responseJson.details )
                    .hide();

                $( "button", $( "#linux-package-info" ) ).off().click( function () {
                    $details.toggle();
                } );

                $packageInfo.show();
                $( "#linux-package-info" ).show();
            } );
    }

    function tree_expand_folder( event, data ) {

        explorerOps.refresh_host_status();
        let fancyTreeData = data;
        let nodeSelected = fancyTreeData.node;
        let nodeType = fancyTreeData.node.type;
        let nodeData = nodeSelected.data;

        console.log( `tree_expand_folder() type: ${ nodeType } path: ${ nodeData.path }` );
        console.debug( `fancyTreeData`,
            fancyTreeData,
            "\n\t nodeData:", nodeData );
        //explorerOps.hideTreeCommands() ;


        if ( nodeData.attributes && nodeData.attributes.treeCommand != null ) {
            console.log( "Showing output" );
            // operations.showResultsDialog("dockerstats", nodeData.attributes, "dockerstats")
            explorerOps.adminOperation(
                "",
                nodeData,
                nodeData.attributes.treeCommand )
        }

        if ( nodeSelected.folder ) {

            $( ".lastOpened", $explorerTree ).removeClass( "lastOpened" );
            nodeSelected.addClass( "lastOpened" );

            explorerOps.addCsapTreeCommands( "lastOpened", $filterControls );

            // images and containers default to hide some items
            if ( ( nodeType == hostOps.categories().dockerImages ||
                nodeType == hostOps.categories().dockerContainers ) &&
                nodeData.path == "/" ) {
                $( "#showFilteredItems" ).show();
            } else {
                $( "#showFilteredItems" ).hide();
            }

            _lastOpenedFolder = nodeSelected;

            $( "#showFilteredItems input" ).off();
            $( "#showFilteredItems input" ).change( function () {
                console.log( "showFilteredItems _lastOpenedFolder", _lastOpenedFolder );
                if ( _lastOpenedFolder.type == hostOps.categories().dockerContainers ) {
                    explorerOps.refreshFolder( hostOps.categories().dockerContainers );
                } else {
                    explorerOps.refreshFolder( hostOps.categories().dockerImages );
                }
            } );

            $treeFilter.val( "" );
            $treeFilter.css( "background-color", "white" );
            $treeFilter.off();
            $treeFilter.focus().select();


            $treeFilter.keyup( function () {
                // 
                clearTimeout( _filterTimer );
                _filterTimer = setTimeout( function () {
                    $treeFilter.css( "background-color", "yellow" );
                    filterLastOpened();

                }, 500 );


                return false;
            } );


            if ( _filterMap[ _lastOpenedFolder.title ] ) {
                $treeFilter.val( _filterMap[ _lastOpenedFolder.title ] );
                $treeFilter.focus().select();
                $treeFilter.css( "background-color", "yellow" );
                filterLastOpened();
            }

            if ( nodeType == hostOps.categories().kubernetesServices ) {
                registerServiceTools();
            }

        }

    }

    function registerServiceTools() {
        let $servicesContainer = $( ".lastOpened", $explorerTree ).parent();

        $( ".csap-tools", $servicesContainer ).click( function () {
            let $link = $( this );
            explorerOps.service_proxy_dialog( $link );
            //alertify.csapInfo( `${name}  ${port}` ) ;
            return false;
        } );
    }

    function filterLastOpened() {
        let currentFilter = $treeFilter.val().trim();
        console.log( "filterLastOpened()", _lastOpenedFolder.title, " filter:" + currentFilter );

        if ( _lastOpenedFolder != null ) {

            _filterMap[ _lastOpenedFolder.title ] = currentFilter;
            if ( _lastOpenedFolder.type == hostOps.categories().dockerContainers &&
                ( !currentFilter.startsWith( "ns:" ) ) ) {
                console.log( "container filter updated - disabling auto k8 container filtering" );
                isK8ContainerFiltering = false;
            }

            let kids = _lastOpenedFolder.getChildren();
            for ( let i = 0; i < kids.length; i++ ) {
                let child = kids[ i ];
                let treeFilterValue = child.data.filterValue;
                //console.log("child title", name)

                if ( currentFilter.length > 0 &&
                    ( currentFilter != "ns:all" ) &&
                    ( filter_not_matched( currentFilter, treeFilterValue ) ) ) {
                    child.addClass( "fileHidden" );
                } else {
                    child.removeClass( "fileHidden" );
                }
                //child.render( ) ;
            }
        }

    }

    function filter_not_matched( currentFilter, treeFilterValue ) {

        let filters = currentFilter.split( "," );
        for ( let filter of filters ) {
            if ( filter.length > 0 ) {
                let regex = new RegExp( filter, "i" );
                if ( regex.test( treeFilterValue ) ) {
                    return false; // found a match. Any match return false
                }
            }
        }

        // console.log( "currentFilter: " + currentFilter, treeFilterValue + "", regex.test( treeFilterValue ) )
        //return !regex.test( treeFilterValue ) ;
        return true;
    }


    function children_root_load() {

        //console.log( "event", event, "data", data );
        let children = new Array();



        children.push( {
            "title": "CSAP",
            "folder": true,
            "expanded": true,
            "children": children_root_load_csap()
        } );

        //        
        //        children.push( {
        //            "title": "CSAP",
        //            "folder": true,
        //            "expanded": true,
        //            "children": children_root_load_csap()
        //        } );


        children.push( {
            "title": title_for_kubernetes_root(),
            "folder": true,
            "expanded": true,
            "children": children_root_load_kubernetes()
        } );

        children.push( {
            "title": "Host Containers",
            "folder": true,
            "expanded": true,
            "children": children_root_load_docker()
        } );


        children.push( {
            "title": title_with_comment( "OS", "", "linuxDefTree" ),
            "folder": true,
            "expanded": true,
            "children": children_root_load_linux()
        } );

        return children;
    }

    function children_root_load_csap() {

        let children = new Array();

        children.push( {
            "title": title_with_comment( "Active Model", "", "csapDefTree" ),
            "type": hostOps.categories().csapDefinition,
            "csapFilter": true,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Services", "loading", "csapServiceTree" ),
            "type": hostOps.categories().csapServices,
            "csapFilter": true,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        if ( vsphereEnabled ) {
            children.push( {
                "title": title_for_vsphere( "Vsphere", "vsphere integration", "vsphereTree" ),
                "type": hostOps.categories().vsphere,
                "csapFilter": true,
                "path": "/",
                "folder": true,
                "lazy": true
            } );
        }


        return children
    }

    function children_root_load_docker() {

        let children = new Array();

        children.push( {
            "title": title_with_comment( "Configuration", "loading", "configTree" ),
            "type": hostOps.categories().dockerConfig,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Containers", "loading", "containerTree" ),
            "type": hostOps.categories().dockerContainers,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "CRI-O Containers", "loading", "loadDynamically" ),
            "type": hostOps.categories().crioContainers,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": "Will Be Replaced by buildRootImageTitle",
            "type": hostOps.categories().dockerImages,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Volumes", "loading", "volumeTree" ),
            "type": hostOps.categories().dockerVolumes,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": "Will be replaced by buildRootNetworkTitle() ",
            "type": hostOps.categories().dockerNetworks,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        return children;
    }





    function children_root_load_kubernetes() {

        let children = new Array();
        children.push( {
            "title": title_with_comment( "System", "", "k8ConfigurationTree" ),
            "type": hostOps.categories().kubernetesConfig,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Events", "", "k8EventTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesEvents,
            "path": "/",
            "folder": true,
            "lazy": true
        } );


        children.push( {
            "title": title_with_comment( "Network: Services & Ingresses", "", "k8ServiceTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesServices,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "helm", "", "helmTree" ),
            "type": hostOps.categories().kubernetesHelm,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Deployments", "", "k8DeployTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesDeployments,
            "path": "/",
            "folder": true,
            "lazy": true
        } );


        children.push( {
            "title": title_with_comment( "Stateful Sets", "", "k8StatefulSetTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesStatefulSets,
            "path": "/",
            "folder": true,
            "lazy": true
        } );


        children.push( {
            "title": title_with_comment( "Daemon Sets", "", "k8DaemonSetTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesDaemonSets,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "ConfigMaps", "", "k8ConfigMapTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesConfigMaps,
            "path": "/",
            "folder": true,
            "lazy": true
        } );



        children.push( {
            "title": title_with_comment( "Batch: Jobs & CronJobs", "", "k8JobTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesJobs,
            "path": "/",
            "folder": true,
            "lazy": true
        } );


        children.push( {
            "title": title_with_comment( "Pods", "", "k8PodTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesPods,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Persistent Volume Claims", "", "k8VolumeClaimTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesVolumeClaims,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Replica Sets", "", "k8ReplicaSetTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesReplicaSets,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Endpoints", "", "k8EndpointTree", "k8-loading csap-loading" ),
            "type": hostOps.categories().kubernetesEndpoints,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        return children;
    }



    function children_root_load_linux() {
        let children = new Array();
        children.push( {
            "title": title_with_comment( "Systemd Services", "loading", "linuxTree" ),
            "type": hostOps.categories().linuxServices,
            "csapFilter": true,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Processes", "loading", "processTree" ),
            "type": hostOps.categories().linuxProcesses,
            "csapFilter": false,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Packages (rpms)", "loading", "packageTree" ),
            "type": hostOps.categories().linuxPackages,
            "csapFilter": true,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Cpu Cores", "loading", "cpuTree" ),
            "type": hostOps.categories().cpu,
            "csapFilter": false,
            "path": "/",
            "folder": true,
            "lazy": true
        } );


        children.push( {
            "title": title_with_comment( "Network Interfaces", "source: ip a", "ID_UPDATED_IN_TITLE_GENERATION" ),
            "type": hostOps.categories().linuxNetworkDevices,
            "csapFilter": false,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Memory", "loading", "memoryTree" ),
            "type": hostOps.categories().memory,
            "csapFilter": false,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "File Systems", "loading", "diskTree" ),
            "type": hostOps.categories().disk,
            "csapFilter": false,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Socket Connections", "tcp", "portTree" ),
            "type": hostOps.categories().socketConnections,
            "csapFilter": false,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        children.push( {
            "title": title_with_comment( "Socket Listeners", "tcp", "portTree" ),
            "type": hostOps.categories().socketListeners,
            "csapFilter": false,
            "path": "/",
            "folder": true,
            "lazy": true
        } );

        return children;
    }

    function children_using_parent_dockerPorts( portArray ) {

        console.debug( "buildDockerPortsTable", portArray );
        $portTable = $( "#dockerPortsTemplate" ).clone();

        $portBody = $( "tbody", $portTable );

        for ( let portSetting of portArray ) {

            let $row = jQuery( '<tr/>', {} );
            let portIp = portSetting.IP;

            if ( portIp == "::" ) {
                console.debug( `skipping null ip`, portSetting );
                continue;
            }

            jQuery( '<td/>', {
                text: portIp
            } ).appendTo( $row );

            $publicCol = jQuery( '<td/>', {} );
            $publicCol.appendTo( $row );

            console.debug( "window.location.hostname ", window.location.hostname );



            if ( !$.isNumeric( portSetting.PublicPort ) ) {
                jQuery( '<span/>', {
                    text: "not-assigned"
                } )
                    .css( "font-style", "italic" )
                    .appendTo( $publicCol );
            } else {
                // default to url shown
                let portUrl = "http://" + window.location.hostname + ":" + portSetting.PublicPort;

                // desktop settings
                if ( portUrl.contains( "localhost" ) ) {
                    portUrl = containerUrl.substring( 0, containerUrl.length - 4 ) + portSetting.PublicPort;
                    if ( portUrl.startsWith( "tcp" ) ) {
                        portUrl = "http" + portUrl.substring( 3 );
                    }

                    console.info( "localhost workaround - using docker Url", portUrl );
                }

                jQuery( '<a/>', {
                    href: portUrl,
                    class: "csap-link-icon launch-window",
                    target: "_blank",
                    text: portSetting.PublicPort
                } ).appendTo( $publicCol );
            }


            jQuery( '<td/>', {
                text: portSetting.PrivatePort
            } ).appendTo( $row );
            jQuery( '<td/>', {
                text: portSetting.Type
            } ).appendTo( $row );

            $row.appendTo( $portBody );

        }

        return $portTable;
    }


    function moveElementInArray( array, value ) {
        let oldIndex = array.indexOf( value );
        if ( oldIndex > -1 ) {
            let newIndex = 0;

            if ( newIndex < 0 ) {
                newIndex = 0
            } else if ( newIndex >= array.length ) {
                newIndex = array.length
            }

            let arrayClone = array.slice();
            arrayClone.splice( oldIndex, 1 );
            arrayClone.splice( newIndex, 0, value );

            return arrayClone
        }
        return array
    }

    function children_using_parent_attributes(
        parentNodeType,
        parentFancyTreeNode ) {

        let nodeData = parentFancyTreeNode.data
        let attributes = nodeData.attributes;
        console.log( `children_using_parent_attributes() nodeData.parentType: ${ nodeData.parentType }`,
            parentFancyTreeNode )
        let treeArray = new Array();

        let attributesKeys = Object.keys( attributes );
        if ( ( attributes.csapNoSort == null )
            && !isArray( attributes ) ) {
            attributesKeys = attributesKeys.sort();
        }


        if ( parentFancyTreeNode.type == hostOps.categories().dockerContainers ) {
            children_using_parent_dockerContainer( nodeData.containerName, attributesKeys, treeArray );

        } else if ( parentFancyTreeNode.type == hostOps.categories().crioContainers ) {
            children_using_parent_crioContainer( attributes, treeArray );

        } else if ( parentFancyTreeNode.type == hostOps.categories().kubernetesPods ) {
            children_using_parent_k8_pod( attributes, treeArray );

        }



        treeArray.push( {
            "title": "Content Viewer",
            "folder": false,
            "lazy": false,
            "type": hostOps.categories().jsonEditor,

            // data fields for rendering
            "attributes": attributes,
            "parentType": parentNodeType,
            "filterValue": "contents"
        } );

        //attributesKeys.forEach( function ( attributeName ) {
        for ( let attributeName of attributesKeys ) {

            if ( attributeName == "csapNoSort" ) {
                continue;
            }

            let attributeValue = attributes[ attributeName ];
            let childAttributes = null;

            let isFolder = false,
                isLazy = false;

            let extraClasses = "ft_attribute";


            if ( attributeValue == null || attributeValue == "" ) {
                attributeValue = '<span style="font-style: italic" >not specified</span>'

            } else if ( isArray( attributeValue ) ) {

                if ( attributeValue.length == 0 ) {
                    attributeValue = '<span style="font-style: italic" >none</span>'

                } else if ( attributeName == "Ports" ) {
                    attributeValue = children_using_parent_dockerPorts( attributeValue ).html();
                    extraClasses = "csap-table-value";

                } else {
                    isFolder = true, isLazy = true;
                    extraClasses = "default"
                    childAttributes = attributeValue;
                    attributeValue = "";
                }
            } else if ( isObject( attributeValue ) ) {

                if ( Object.keys( attributeValue ).length === 0 ) {
                    attributeValue = '<span style="font-style: italic" >none</span>'
                } else {
                    isFolder = true, isLazy = true;
                    childAttributes = attributeValue;
                    attributeValue = "";
                }

            }


            // console.log("attribute ", attributeName,  attributeValue) ;

            //let title = attributeName + ": " + attributeValue ;
            let customLabel = attributeName;
            let customValue = attributeValue;

            if ( Array.isArray( attributes ) ) {

                // check for well known array attributes
                let parentAttributes = attributes[ attributeName ];
                let name = jsonForms.getValue( "Name", parentAttributes );
                if ( !name ) {
                    name = jsonForms.getValue( "name", parentAttributes );
                }
                let key = jsonForms.getValue( "Key", parentAttributes );
                let diskpath = jsonForms.getValue( "DiskPath", parentAttributes );
                let diskName = jsonForms.getValue( "DiskFile.0", parentAttributes );
                // console.log(`diskName: ${diskName}`, parentAttributes) ;
                if ( name ) {
                    customLabel = name;

                } else if ( key ) {
                    customLabel += ": " + key;

                } else if ( diskpath ) {
                    customLabel += '<div class="value" >' + diskpath + '</div>';

                } else if ( diskName ) {
                    customLabel += ": " + diskName;
                }
            }


            let labels = customLabel.split( "," );
            if ( labels.length == 2 ) {
                customLabel = title_with_comment( labels[ 0 ], labels[ 1 ], "" );
            }


            //console.log( "childLabels:", childLabels) ;

            let attrType = hostOps.categories().jsonContainer;
            if ( !isFolder )
                attrType = hostOps.categories().jsonContainer;

            if ( nodeData.parentType == hostOps.categories().linuxProcesses ) {
                if ( isArray( attributes ) && attributes[ attributeName ].pid ) {
                    customLabel = "pid " + attributes[ attributeName ].pid;
                    customValue = attributes[ attributeName ].parameters
                    delete attributes[ attributeName ].parameters;
                }

            } else if ( nodeData.parentType == hostOps.categories().kubernetesConfig ) {


                if ( nodeData.filterValue == "Nodes" ) {
                    attrType = hostOps.categories().host;
                    let notes = "";
                    let childLabels = jsonForms.getValue( "metadata.labels", childAttributes );
                    if ( isObject( childLabels ) &&
                        childLabels[ "node-role.kubernetes.io/master" ] != undefined ) {
                        notes = "Master";
                    } else {
                        notes = "Worker";
                    }

                    extraClasses = "ft-host-ready";
                    let conditions = jsonForms.getValue( "status.conditions", childAttributes );
                    if ( isArray( conditions ) ) {

                        for ( let condition of conditions ) {

                            switch ( condition.type ) {
                                case "Ready":
                                    if ( condition.status != "True" ) {
                                        extraClasses = "ft-host-error";
                                        notes += " not ready";
                                    }
                                    break;

                                case "DiskPressure":
                                case "MemoryPressure":
                                case "NetworkUnavailable":
                                case "PIDPressure":
                                    if ( condition.status != "False" ) {
                                        extraClasses = "ft-host-conditions";
                                        notes += " " + condition.type;
                                    }
                                    break;
                            }
                        }
                    }
                    let unschedulable = jsonForms.getValue( "spec.unschedulable", childAttributes );
                    if ( unschedulable == true ) {
                        extraClasses = "ft-host-unscheduled";
                        notes += " spec.unschedulable";
                    }
                    customLabel = title_with_comment( attributeName, notes, "" );

                } else if ( parentFancyTreeNode.title.startsWith( "Role" ) ||
                    ( parentFancyTreeNode.title == "Secrets" ) ) {
                    //attrType = hostOps.categories().security ;
                    //                    let childLabels = jsonForms.getValue( "rules", childAttributes ) ;
                    //                    customLabel = title_with_comment( attributeName, childLabels.length, "" ) ;


                }
            }

            if ( !isFolder ) {
                customValue = attributeValue;
            }



            console.log( "customLabel ", customLabel, "customValue", customValue );
            treeArray.push( {
                "title": customLabel,
                "folder": isFolder,
                "lazy": isLazy,
                "type": attrType,
                "extraClasses": extraClasses,

                // data fields for rendering
                "customLabel": customLabel,
                "customValue": customValue,
                "parentType": parentNodeType,
                "filterValue": customLabel + "," + customValue,

                // recursively find childrn
                "attributes": childAttributes
            } );
        }

        return treeArray;
    }

    function children_using_parent_k8_pod( attributes, treeArray ) {

        console.log( "children_using_parent_k8_pod(): ", attributes );
        if ( attributes.spec && isArray( attributes.spec.containers ) ) {
            let containersArray = attributes.spec.containers;
            for ( let container of containersArray ) {

                treeArray.push( {
                    "title": container.name,
                    "type": hostOps.categories().kubernetesPodContainer,
                    "folder": true,
                    "lazy": true,
                    "extraClasses": "",
                    // data fields for rendering

                    //"filterValue": customLabel + "," + customValue,
                    "attributes": container

                } );
            }

            //.${initContainer.name}.state.terminated
            let initContainerStatus = new Object();
            let statusList = jsonForms.getValue( `status.initContainerStatuses`, attributes );
            if ( statusList && Array.isArray( statusList ) ) {
                for ( let status of statusList ) {
                    initContainerStatus[ status.name ] = "running"
                    if ( status.state.terminated && status.state.terminated.reason == "Completed" ) {
                        initContainerStatus[ status.name ] = "pass";
                    } else {
                        initContainerStatus[ status.name ] = "fail";
                    }
                }
            }

            let initContainers = attributes.spec.initContainers;
            if ( initContainers ) {
                for ( let initContainer of initContainers ) {
                    initContainer.status = initContainerStatus[ initContainer.name ];
                    initContainer.isInit = true;
                    treeArray.push( {
                        "title": initContainer.name,
                        "type": hostOps.categories().kubernetesPodContainer,
                        "folder": true,
                        "lazy": true,
                        // data fields for rendering

                        //"filterValue": customLabel + "," + customValue,
                        "attributes": initContainer

                    } );
                }
            }
        }
    }

    function children_using_parent_crioContainer( attributes, treeArray ) {
        //        attributesKeys = moveElementInArray( attributesKeys, "Status", 0 )
        //        attributesKeys = moveElementInArray( attributesKeys, "Create Date", 0 )
        //        attributesKeys = moveElementInArray( attributesKeys, "Created", 0 )

        // add a runtime folder for dynamically pulling attributes
        let customLabel = "container runtime settings";
        let customValue = "";

        let containerId = attributes.id;

        treeArray.push( {
            "title": customLabel,
            "folder": true,
            "lazy": true,
            "extraClasses": "ft-docker-runtime",
            // data fields for rendering
            "customLabel": customLabel,
            "customValue": customValue,

            "filterValue": customLabel + "," + customValue,

            // "type": explorerOps.containerCommandPath() + "info?name=" + containerName
            "type": explorerOps.crioContainerCommandPath() + "info?id=" + containerId
        } );
    }

    function children_using_parent_dockerContainer( containerName, attributesKeys, treeArray ) {
        attributesKeys = moveElementInArray( attributesKeys, "Status", 0 )
        attributesKeys = moveElementInArray( attributesKeys, "Create Date", 0 )
        attributesKeys = moveElementInArray( attributesKeys, "Created", 0 )

        // add a runtime folder for dynamically pulling attributes
        let customLabel = "container runtime settings";
        let customValue = "";
        treeArray.push( {
            "title": customLabel,
            "folder": true,
            "lazy": true,
            "extraClasses": "ft-docker-runtime",
            // data fields for rendering
            "customLabel": customLabel,
            "customValue": customValue,

            "filterValue": customLabel + "," + customValue,

            "type": explorerOps.containerCommandPath() + "info?name=" + containerName
        } );
    }


    function children_using_object( nodeType, event, rawObject ) {

        console.log( `building tree using object attributes for node:  ${ nodeType } ` );
        console.debug( "rawObject", rawObject );

        let treeArray = new Array();

        let keys = Object.keys( rawObject );
        if ( rawObject.csapNoSort == null ) {
            keys = keys.sort();
        }

        //alert("got here") ;
        treeArray.push( {
            "title": "Content Viewer",
            "folder": false,
            "lazy": false,
            "type": hostOps.categories().jsonEditor,

            // data fields for rendering
            "attributes": rawObject,
            "filterValue": "contents"
        } );

        for ( let key of keys ) {

            if ( key == "csapNoSort" ) {
                continue;
            }

            if ( key === "manifest"
                && nodeType === hostOps.categories().kubernetesHelm ) {
                continue;
            }

            let extraClasses = "";

            let item = rawObject[ key ];
            let isFolder = false;

            let containerName = null;
            let imageName = null;
            let linuxServiceName = null;
            let label = key;
            let value = rawObject[ key ];
            let attributes = null;


            let attrType = hostOps.categories().jsonContainer;
            if ( !isFolder ) {
                attrType = hostOps.categories().jsonValue;
            }

            if ( isObject( value ) && value.folderUrl ) {

                attrType = hostOps.categories().folder;
                if ( value.isFile ) {
                    attrType = hostOps.categories().jsonValue;

                } else if ( value.folderUrl == "vsphere/datastore/files" ) {

                    label = title_for_vsphere_datastore_file(
                        label,
                        value.path,
                        value.folderUrl );

                }
                isFolder = true;
                attributes = value;
                value = "";

            } else if ( isObject( value )
                && nodeType === hostOps.categories().kubernetesHelm
                && value.description
                && value.name ) {

                label = title_with_comment( value.name, value.description );
                isFolder = true;
                attributes = value;

            } else if ( key == "DriverStatus" && isArray( value ) ) {

                attributes = new Object();
                value.forEach( function ( driverItem ) {
                    attributes[ driverItem[ 0 ] ] = driverItem[ 1 ];
                } );

                value = "";
                isFolder = true;

            } else if ( isArray( value ) || isObject( value ) ) {
                attributes = value;
                value = "";
                isFolder = true;
                if ( nodeType == hostOps.categories().cpu ) {
                    let comment = 'User: ' + item.puser + '%</div>';
                    label = title_with_comment( item.cpu, comment );
                }
                if ( nodeType == hostOps.categories().disk ) {
                    let comment = 'Used: ' + item.used + ", Available: " + item.avail;
                    label = title_with_comment( label, comment );
                }
            }

            let nodeData = {
                "title": label,
                "folder": isFolder,
                "lazy": isFolder,
                "type": attrType,
                "extraClasses": extraClasses,

                // data fields for rendering
                "customLabel": label,
                "customValue": value,
                "filterValue": label,
                "containerName": containerName,
                "imageName": imageName,
                "linuxServiceName": linuxServiceName,

                "attributes": attributes
            };
            // console.log("nodeData", nodeData ) ;
            treeArray.push( nodeData );
        }
        ;


        return treeArray;
    }

    function label_finder( configuration, label ) {
        if ( configuration &&
            configuration.attributes &&
            configuration.attributes.Labels ) {

            if ( label == "" )
                return true;
            return configuration.attributes.Labels[ label ];
        }

        return null;
    }

    // this is done prior to rendering - allowing for sorting - and rendering once.
    function children_using_array( parentType, event, folderItems ) {

        console.log( `children_using_array() - parentType: ${ parentType }, children: `, folderItems, "\n event ", event );

        if ( parentType
            && parentType === hostOps.categories().dockerContainers ) {

            let containerNames = new Array();
            for ( let folderItem of folderItems ) {
                containerNames.push( folderItem.label );
            }

            explorerOps.setDockerContainers( containerNames );
        }


        if ( parentType == hostOps.categories().kubernetesPods ) {
            let podsByOwner = new Object();

            for ( let podFolder of folderItems ) {
                let podName = jsonForms.getValue( "attributes.metadata.name", podFolder );
                let owner = jsonForms.getValue( "attributes.metadata.ownerReferences.0.name", podFolder );

                if ( podName && owner ) {
                    let references = podsByOwner[ owner ];
                    if ( !references ) {
                        podsByOwner[ owner ] = new Array();
                        references = podsByOwner[ owner ];
                    }
                    references.push( podName );

                }
            }

            explorerOps.setPodsByOwner( podsByOwner );
            console.log( `podsByOwner `, podsByOwner );
        }

        if ( !folderItems ) {
            return children_root_load();
        }

        let treeArray = new Array();
        // generate fields in sorted order
        folderItems.sort( superSort( "label" ) );


        if ( parentType == hostOps.categories().kubernetesEvents ) {
            folderItems.reverse();
        }

        //for ( let i = 0; i < rawChildrenArray.length; i++ ) {
        //rawChildrenArray.forEach( function ( child ) {
        //console.log("child", child ) ;

        for ( let folderItem of folderItems ) {

            let defaultTitle = folderItem.label;
            //            if ( folderItem.description ) {
            //                comment = folderItem.description;
            //                defaultTitle  = title_with_comment( defaultTitle, comment, "" ); 
            //                console.log(`children_using_array() parentType: ${parentType} defaultTitle: '${defaultTitle}'`) ;
            //            }

            let nodeType = parentType;
            let title = defaultTitle;
            let isFolder = true;
            let isLazy = true;
            let fieldValue = "";
            let extraClasses = "";
            let user = "";
            let filterValue = folderItem.label;
            let containerName = null;
            let podName = null;
            let k8ServiceName = null;
            let linuxServiceName = null;
            let imageName = null;

            if ( title ) {
                let labels = title.split( "," );
                if ( labels.length == 2 ) {
                    title = title_with_comment( labels[ 0 ], labels[ 1 ], "" );
                }
            }


            if ( folderItem.error ) {
                title = folderItem.error;
                extraClasses = "ft-empty";
                isFolder = false;
                isLazy = false;

            } else if ( parentType == hostOps.categories().kubernetesEvents ) {
                title = title_for_events( folderItem );
                filterValue = jsonForms.getValue( "attributes.message", folderItem )
                    + jsonForms.getValue( "attributes.simpleHost", folderItem );

            } else if ( nodeType == hostOps.categories().folder &&
                folderItem.attributes && folderItem.attributes.isFile ) {
                nodeType = hostOps.categories().jsonValue;

            } else if ( nodeType == hostOps.categories().csapDefinition ) {
                nodeType = hostOps.categories().jsonContainer;

            } else if ( nodeType == hostOps.categories().api ) {
                let comment = "";
                if ( folderItem.comment ) {
                    comment = folderItem.comment;
                }
                console.log( `folderItem.comment: ${ folderItem.comment }` );
                title = title_with_comment( title, comment, "" );

            } else if ( nodeType == hostOps.categories().vsphere ) {
                //nodeType = hostOps.categories().jsonContainer;
                //let comment = folderItem.description;

                if ( folderItem.label == "datastores" ) {
                    nodeType = hostOps.categories().vsphereDatastores;
                } else if ( folderItem.label == "vms" ) {
                    nodeType = hostOps.categories().vsphereVms;
                }
                //title = title_with_comment( title, comment, "" );

            } else if ( nodeType == hostOps.categories().vsphereVms ) {
                //nodeType = hostOps.categories().dockerNetworks;
                if ( folderItem.description && folderItem.description == "folder" ) {
                    extraClasses = "ft-host-folder";
                }
                ;

            } else if ( nodeType == hostOps.categories().kubernetesConfig ) {
                nodeType = hostOps.categories().jsonContainer;

                // console.log(`parsing hostOps.categories().kubernetesConfig`) ;
                let comment = "total: <span class='comment-number'>" + Object.keys( folderItem.attributes ).length + "</span>";
                if ( title == "Nodes" ) {
                    nodeType = hostOps.categories().host;

                } else if ( title == "Events" ) {
                    nodeType = hostOps.categories().events;

                } else if ( title == "Secrets" ) {
                    nodeType = hostOps.categories().security;
                    console.log( "child: ", folderItem );

                } else if ( title.startsWith( "Api" )
                ) {
                    isFolder = true;
                    isLazy = true;
                    nodeType = hostOps.categories().api;
                    comment = "";
                } else if ( title.includes( "Auth:" ) ) {
                    nodeType = hostOps.categories().roles;
                    console.log( "child: ", folderItem );
                    if ( title.includes( "Auth: Secrets" ) ) {
                        comment = ""; // dynamically loaded - very large
                    }

                } else if ( title.includes( "Storage:" ) ) {
                    nodeType = hostOps.categories().kubernetesVolumeClaims;
                    console.log( "child: ", folderItem );

                }


                title = title_with_comment( title, comment, "" );

            } else if ( nodeType == hostOps.categories().kubernetesPods ) {
                podName = folderItem.label;
                extraClasses = k8_pod_state( folderItem.attributes );

            } else if ( nodeType == hostOps.categories().kubernetesVolumeClaims ) {
                extraClasses = k8_volumeClaim_state( folderItem.attributes );

            } else if ( nodeType == hostOps.categories().kubernetesServices ) {
                k8ServiceName = folderItem.label;
                let ingressFirstPath = jsonForms.getValue( "attributes.spec.rules.0.http.paths.0", folderItem );
                if ( ingressFirstPath ) {
                    nodeType = hostOps.categories().jsonContainer;
                }

            } else if ( nodeType == hostOps.categories().kubernetesJobs ) {
                let cronJobSpec = jsonForms.getValue( "attributes.spec.jobTemplate", folderItem );
                if ( cronJobSpec ) {
                    nodeType = hostOps.categories().kubernetesCronJob;

                    let cronImage = jsonForms.getValue( "attributes.spec.jobTemplate.spec.template.spec.containers.0.image", folderItem );
                    let cronNamespace = jsonForms.getValue( "attributes.metadata.namespace", folderItem );
                    let cronSchedule = jsonForms.getValue( "attributes.spec.schedule", folderItem );
                    if ( cronImage ) {
                        let cronTitle = `<span class=cron><span>${ cronSchedule }</span> <span>ns:${ cronNamespace }</span> <span>${ cronImage }</span></span>`;
                        title = title_with_comment( title, cronTitle );
                    }
                } else {
                    extraClasses = "ft-k8-job-running";
                    let jobType = jsonForms.getValue( "attributes.status.conditions.0.type", folderItem );
                    let jobNamespace = jsonForms.getValue( "attributes.metadata.namespace", folderItem );
                    let cronTitle = `<span class=cron><span></span> <span>ns:${ jobNamespace }</span> <span></span></span>`;
                    title = title_with_comment( title, cronTitle );
                    if ( jobType && jobType == "Complete" ) {
                        let jobStatus = jsonForms.getValue( "attributes.status.conditions.0.status", folderItem );
                        if ( jobStatus && jobStatus == "True" ) {
                            extraClasses = "ft-k8-job-stopped";
                            let jobSuccess = jsonForms.getValue( "attributes.status.succeeded", folderItem );
                            if ( jobSuccess ) {
                                extraClasses = "ft-k8-job-completed";
                            }
                        }
                    }
                }

            } else if ( nodeType == hostOps.categories().kubernetesDeployments ) {
                k8Deployname = folderItem.label;

            } else if ( nodeType == hostOps.categories().crioContainers ) {

                extraClasses = "ft-docker-stopped";

                if ( folderItem.attributes.state && folderItem.attributes.state.includes( "RUNNING" ) ) {
                    extraClasses = "ft-docker-running";
                }

            } else if ( nodeType == hostOps.categories().dockerContainers ) {
                extraClasses = "ft-docker-stopped";
                containerName = folderItem.label;
                filterValue += ",image:" + folderItem.attributes[ "Image" ] +
                    "," + nameSpaceFilterLabel(
                        label_finder( folderItem, "io.kubernetes.pod.namespace" )
                    );
                if ( folderItem.attributes.Status && folderItem.attributes.Status.startsWith( "Up" ) ) {
                    extraClasses = "ft-docker-running";
                    //console.log( "docker check child.attributes", child.attributes) ;
                    if ( label_finder( folderItem, "io.kubernetes.docker.type" ) != null ) {
                        let app = label_finder( folderItem, "k8s-app" );
                        let pod = label_finder( folderItem, "io.kubernetes.pod.name" );
                        let type = label_finder( folderItem, "io.kubernetes.container.name" );

                        if ( app != null ) {
                            extraClasses = "ft-docker-k8s-app";
                        } else if ( ( pod != null ) && ( type == "POD" ) ) {
                            extraClasses = "ft-docker-k8s-pod";
                        } else if ( type != null ) {
                            extraClasses = "ft-docker-k8s";
                        }
                    }
                }
            } else if ( nodeType == hostOps.categories().csap ) {
                extraClasses = csap_icon_finder( folderItem.attributes );

            } else if ( nodeType == hostOps.categories().linuxServices ) {
                isFolder = false;
                isLazy = false;
                linuxServiceName = folderItem.label;

            } else if ( nodeType == hostOps.categories().linuxNetworkDevices ) {
                isFolder = false;
                isLazy = false;
                extraClasses = "unknown";
                if ( title.includes( "UP" ) ) {
                    extraClasses = "up";
                } else if ( title.includes( "DOWN" ) ) {
                    extraClasses = "down";
                }
                title = title_with_comment( title, folderItem.description );


            } else if ( nodeType == hostOps.categories().socketConnections ) {
                title = title_with_comment( title, folderItem.description );
                filterValue += "," + folderItem.description;

            } else if ( nodeType == hostOps.categories().socketListeners ) {
                title = title_with_comment( title, folderItem.description );

            } else if ( nodeType == hostOps.categories().linuxPackages ) {
                isFolder = false;
                isLazy = false;

            } else if ( nodeType == hostOps.categories().linuxProcesses ) {
                extraClasses = getProcessIcon( title, folderItem.attributes );
                user = folderItem.attributes[ 0 ].user;
                filterValue += "," + user;

            } else if ( nodeType == hostOps.categories().dockerImages ) {
                imageName = folderItem.label;
                if ( folderItem.attributes.RepoTags && folderItem.attributes.RepoTags.length > 1 ) {
                    title += '<div class="comment">' +
                        folderItem.attributes.RepoTags.length +
                        ' more </div>';
                }

            } else {
                if ( folderItem.description ) {
                    title = title_with_comment( title, folderItem.description );
                }
            }
            let subsets = jsonForms.getValue( "attributes.subsets", folderItem );
            //            if ( subsets != undefined ) {
            //                title += "(endpoint)"
            //            }


            let nodeData = {
                "title": title,
                "type": nodeType,
                "folder": folderItem.folder,
                "lazy": isLazy,
                "extraClasses": extraClasses,

                // data fields for rendering
                "originalTitle": title,
                "parentType": parentType,
                "filterValue": filterValue,
                "containerName": containerName,
                "imageName": imageName,
                "linuxServiceName": linuxServiceName,
                "podName": podName,
                "k8ServiceName": k8ServiceName,

                "user": user,
                "attributes": folderItem.attributes
            };
            treeArray.push( nodeData );
            console.debug( `pushing: ${ title }` );
        }


        if ( parentType == hostOps.categories().dockerContainers &&
            hostOps.isKubernetesEnabled() ) {

            let namespace = explorerOps.kubernetesNameSpace();

            if ( isK8ContainerFiltering ) {

                setTimeout( function () {
                    $treeFilter.val( nameSpaceFilterLabel( namespace ) );
                    $treeFilter.focus().select();
                    filterLastOpened();
                }, 500 );
            }

        }
        //console.log( "treeArray", treeArray ) ;

        return treeArray;
    }

    function nameSpaceFilterLabel( namespace ) {
        return "ns:" + namespace;
    }

    function k8_volumeClaim_state( attributes ) {

        console.log( "k8_volumeClaim_state(): ", attributes );
        let state = "ft-k8-volume-claim-running";

        if ( attributes.status && attributes.status.phase && attributes.status.phase != "Bound" ) {
            state = "ft-k8-volume-claim-stopped";
            if ( attributes.status.phase == "Pending" ) {
                state = "ft-k8-volume-claim-pending";
            }
        }

        return state;
    }


    function k8_pod_state( attributes ) {

        let state = "ft-k8-pod-running";

        if ( attributes.status && attributes.status.phase && attributes.status.phase != "Running" ) {
            state = "ft-k8-pod-stopped";
            if ( attributes.status.phase == "Pending" ) {
                state = "ft-k8-pod-pending";
            } else if ( attributes.status.phase == "Succeeded" ) {
                state = "ft-k8-pod-completed";
            }
        }



        if ( ( state != "ft-k8-pod-completed" ) && attributes.status && attributes.status.containerStatuses &&
            isArray( attributes.status.conditions ) ) {

            let conditions = attributes.status.conditions;
            for ( let condition of conditions ) {
                if ( condition
                    && condition.status
                    && ( condition.status != "True" ) ) {

                    state = "ft-k8-pod-pending";
                    break;
                }
            }
        }

        if ( ( state != "ft-k8-pod-completed" ) && attributes.status && attributes.status.containerStatuses &&
            isArray( attributes.status.containerStatuses ) ) {

            let containers = attributes.status.containerStatuses;
            for ( let container of containers ) {

                if ( !container.ready ) {
                    state = "ft-k8-pod-stopped";
                    break;
                }
            }
        }



        return state;
    }

    function getProcessIcon( title, attributes ) {
        let icon = "";

        if ( title.contains( "java" ) ) {
            icon = "ft-os-process-java";
        } else if ( title.contains( "kube" ) ) {
            icon = "ft-os-process-k8s";
        } else if ( title.startsWith( "k8-" ) ) {
            icon = "ft-os-process-k8s";
        } else if ( title.contains( "docker" ) ) {
            icon = "ft-os-process-docker";
        }
        return icon;

    }

    function csap_icon_finder( attributes ) {
        let icon = "ft-csap-process-script";

        console.log( attributes.serviceName, attributes.iconType );

        switch ( attributes.iconType ) {
            case "runtime":
            case "csap-api":
                icon = "ft-csap-process-api";
                break;

            case "database":
                icon = "ft-csap-process-db";
                break;
            case "messaging":
                icon = "ft-csap-process-jms";
                break;
            case "webServer":
                icon = "ft-csap-process-web";
                break;

            case "docker":
                icon = "ft-csap-process-docker";
                break;

            case "kubernetes":
                icon = "ft-csap-process-kubernetes";
                break;

            case "monitor":
            case "os":
                icon = "ft-csap-process-os";
                break;
            case "script":
            case "scripts":
            case "package":
                icon = "ft-csap-process-script";
                break;
            case "SpringBoot":
                icon = "ft-csap-process-springboot";
                break;
            default:
                icon = "ft-csap-process-tomcat";
        }
        if ( attributes.serverType != "script" && attributes.cpuUtil && attributes.cpuUtil == "-" ) {
            icon = "ft-csap-process-stopped";
        }

        return icon;
    }

    function singleSort( property ) {
        let sortOrder = 1;
        if ( property[ 0 ] === "-" ) {
            sortOrder = -1;
            property = property.substr( 1 );
        }
        return function ( a, b ) {
            let result = ( a[ property ] < b[ property ] ) ? -1 : ( a[ property ] > b[ property ] ) ? 1 : 0;
            return result * sortOrder;
        }
    }

    function superSort() {
        /*
         * save the arguments object as it will be overwritten
         * note that arguments object is an array-like object
         * consisting of the names of the properties to sort by
         */
        let props = arguments;
        console.log( "props", props.length, props );
        return function ( obj1, obj2 ) {
            let i = 0,
                result = 0,
                numberOfProperties = props.length;
            /* try getting a different result from 0 (equal)
             * as long as we have extra properties to compare
             */
            while ( result === 0 && i < numberOfProperties ) {
                result = singleSort( props[ i ] )( obj1, obj2 );
                i++;
            }
            return result;
        }
    }

    function isObject( theReference ) {

        if ( theReference == null )
            return false;
        if ( isArray( theReference ) )
            return false;
        return typeof theReference === "object";
    }

    function isArray( theReference ) {
        return Array.isArray( theReference );
    }

    function isBoolean( o ) {
        return Object.prototype.toString.call( o ) == '[object Boolean]';
    }

    function isNumber( o ) {
        return Object.prototype.toString.call( o ) == '[object Number]';
    }

    function isString( o ) {
        return Object.prototype.toString.call( o ) == '[object String]';
    }


    function tree_render_item( fancyTreeNode ) {

        let configuration = fancyTreeNode.data;
        let nodeType = fancyTreeNode.type;
        let parentType = configuration.parentType;

        //console.log( "treeRenderNode() ", fancyTreeNode );
        // note - no references to title are used or rendering is broken

        if ( configuration.user && configuration.user != "" ) {
            // process groups
            fancyTreeNode.setTitle( title_for_process( configuration ).html() );

        } else if ( configuration.customLabel && parentType == hostOps.categories().linuxProcesses ) {
            fancyTreeNode.setTitle( title_for_attribute( configuration ).html() )

        } else if ( configuration.customLabel && !fancyTreeNode.folder ) {
            fancyTreeNode.setTitle( title_for_attribute( configuration ).html() )

        } else if ( isPodNode( configuration ) ) {
            fancyTreeNode.setTitle( title_for_pod( configuration ).html() )

        } else if ( isPodContainerNode( fancyTreeNode, configuration ) ) {
            fancyTreeNode.setTitle(
                title_for_pod_container( fancyTreeNode, configuration ) )

        } else if ( isK8ServiceNode( configuration ) ) {
            try {
                fancyTreeNode.setTitle( title_for_k8_service( configuration ).html() )
            } catch ( err ) {
                console.log( err.message );
            }
        } else if ( isContainerNode( configuration ) ) {
            fancyTreeNode.setTitle( title_for_container( configuration ).html() )

        } else if ( isImageNode( configuration ) ) {
            fancyTreeNode.setTitle( title_for_image( fancyTreeNode, configuration ).html() )

        } else if ( nodeType == hostOps.categories().linuxNetworkDevices && configuration.path == "/" ) {
            fancyTreeNode.setTitle( title_for_items_with_shell( "Networks", "Linux network commands: ip routes, ...", "network-routes" ).html() )

        } else if ( nodeType == hostOps.categories().dockerImages && configuration.path == "/" ) {
            fancyTreeNode.setTitle( title_for_image_root( configuration ).html() )

        } else if ( nodeType == hostOps.categories().dockerNetworks && configuration.path == "/" ) {
            fancyTreeNode.setTitle( title_for_items_with_shell( "Networks", "Run Docker network commands", "docker-network" ).html() )

        } else if ( nodeType == hostOps.categories().dockerVolumes && configuration.path == "/" ) {
            fancyTreeNode.setTitle( title_for_items_with_shell( "Volumes", "Run Docker volume commands", "docker-volume" ).html() )

        } else if ( nodeType == hostOps.categories().crioContainers && configuration.path == "/" ) {
            fancyTreeNode.setTitle( title_for_items_with_shell( "CRI-O Containers", "Run crictl and podman commands", "cri-commands" ).html() )

        } else if ( nodeType == hostOps.categories().kubernetesHelm && configuration.path == "/" ) {
            fancyTreeNode.setTitle( title_for_items_with_shell( "Helm", "Run helm commands", "helm-commands" ).html() )
        }


    }

    function title_for_items_with_shell(
        label,
        description,
        commandTemplateName ) {

        let $description = jQuery( '<div/>', {} );

        let $label = jQuery( '<label/>', {
            class: "summary",
            html: label
        } );

        $label.appendTo( $description );

        let marginLeft = "0";
        //        if ( "docker-volume network-routes".contains(commandTemplateName) ) {
        //            marginLeft="0";
        //        }
        let $commentDiv = jQuery( '<div/>', {
            class: "comment"
        } )
            .css( "margin-left", marginLeft );
        $commentDiv.appendTo( $description );


        let $comment = jQuery( '<div/>', {
            class: "comment",
            id: commandTemplateName + "Tree",
            text: "loading"
        } );
        $comment.appendTo( $commentDiv );


        if ( commandTemplateName == "node-describe" ) {
            $comment.text( description );
            buildNodeButton( label ).appendTo( $commentDiv );

        } else {
            buildCommandButton( commandTemplateName, description )
                .appendTo( $commentDiv );

            if ( commandTemplateName == "docker-volume" ) {
                //            buildVolumeStatisticsButton()
                //                    .appendTo( $commentDiv ) ;
                buildCommandButton( "docker-volume-report", "Volume Reporter", "images/16x16/x-office-spreadsheet.png" )
                    .appendTo( $commentDiv );
            }
        }


        return $description;
    }
    function buildNodeButton( nodeName ) {

        let launchButtonId = "node-describe" + nodeName.split( "." )[ 0 ];

        let $button = jQuery( '<button/>', {
            id: launchButtonId,
            class: "csap-button-icon tree",
            title: "view node information (kubectl node describe)"
        } )


        jQuery( '<img/>', {
            src: baseUrl + "images/16x16/preferences-system.png"
        } ).appendTo( $button );

        setTimeout( () => {
            $( "#" + launchButtonId ).click( function () {

                hostOps.httpGet( explorerUrl + "/kubernetes/node/describe/" + nodeName, nodeName );
            } )
        }, 500 );

        return $button;
    }
    //    
    //    function buildVolumeStatisticsButton () {
    //
    //        let launchButtonId = "VolumeStatsTreeButton" ;
    //
    //        let $button = jQuery( '<button/>', {
    //            id: launchButtonId,
    //            class: "csap-button tree",
    //            title: "Run docker volume report"
    //        } )
    //
    //
    //        jQuery( '<img/>', {
    //            class: "custom",
    //            src: baseUrl + "images/16x16/x-office-spreadsheet.png"
    //        } ).appendTo( $button ) ;
    //
    //        setTimeout( () => {
    //            $( "#" + launchButtonId ).click( function () {
    //
    //                explorerOps.adminOperation(
    //                        "",
    //                        null,
    //                        "/system/df" )
    //            } )
    //        }, 500 ) ;
    //
    //        return $button ;
    //    }

    function buildCommandButton( commandTemplateName, description, iconPath = "images/16x16/shell.png" ) {

        let launchButtonId = commandTemplateName + "TreeButton";

        let $button = jQuery( '<button/>', {
            id: launchButtonId,
            class: "csap-button-icon tree",
            title: description + " ..."
        } )


        jQuery( '<img/>', {
            src: baseUrl + iconPath
        } ).appendTo( $button );

        let launchFunction = function () {
            let targetItem = "none yet";

            utils.launchScript( {
                serviceName: `${ targetItem }`,
                template: `${ commandTemplateName }.sh`
            } );

            //            commandUrl = commandScreen + '?command=script&' +
            //                    'template=' + commandTemplateName + ".sh&" +
            //                    'serviceName=' + targetItem + '&' ;
            //
            //            openWindowSafely( commandUrl, "_blank" ) ;
        }
        registerTreeButton( launchButtonId, launchFunction );


        return $button;
    }

    function registerTreeButton( buttonId, buttonFunction, postOpFunction ) {

        // schedule in background to register POST rendering
        setTimeout( () => {
            $( "#" + buttonId ).click( function () {
                buttonFunction( $( this ), postOpFunction );
            } )
        }, 500 );

    }

    function title_for_kubernetes_root() {

        let $description = jQuery( '<div/>', {} );
        let label = "Kubernetes";

        let $label = jQuery( '<label/>', {
            class: "summary",
            html: label
        } );

        $label.appendTo( $description );

        let $comment = jQuery( '<div/>', {
            class: "comment"
        } );
        $comment.appendTo( $description );

        jQuery( '<span/>', {
            html: ""
        } ).css( "margin-right", "1.2em" ).appendTo( $comment );

        let yamlApplyId = "yaml-apply-button";
        let $yamlApplyButton = jQuery( '<button/>', {
            id: yamlApplyId,
            class: "csap-button-icon csap-edit-white",
            title: "Click to create/delete components using kubernetes yaml specifications"
        } );

        $yamlApplyButton.appendTo( $comment );

        //        jQuery( '<img/>', {
        //            src: baseUrl + "images/yml.png"
        //        } ).css( "width", "28px" ).css( "margin-right", "5px" ).css( "vertical-align", "bottom" ).appendTo( $yamlApplyButton ) ;
        $yamlApplyButton.append( "Editor" );
        $yamlApplyButton.css( "padding-left", "25px" );


        setTimeout( () => {
            $( "#" + yamlApplyId ).click( function () {
                // console.log() ;
                explorerOps.kubernetes_yaml_dialog();
            } )

        }, 500 );


        let $yamlReloadLabel = jQuery( '<label/>', { title: "open folders will be refreshed when changes occur " } );

        let $yamlReloadButton = jQuery( '<input/>', {
            id: "kubernetes-reload-checkbox",
            class: "tree-filter-checkbox",
            type: "checkbox",
            checked: "checked",

        } );
        $comment.append( $yamlReloadLabel );
        $yamlReloadLabel.append( $yamlReloadButton );
        $yamlReloadLabel.append( "auto reload" );

        return $description.html();
    }

    function title_for_image_root( configuration ) {

        let $description = jQuery( '<div/>', {} );
        let label = "Images";

        let $label = jQuery( '<label/>', {
            class: "summary",
            html: label
        } );

        $label.appendTo( $description );

        let $comment = jQuery( '<div/>', {
            class: "comment"
        } );
        $comment.appendTo( $description );

        jQuery( '<span/>', {
            id: "imageTree",
            html: "loading"
        } ).css( "margin-right", "3.5em" ).appendTo( $comment );

        let $pullImageButton = jQuery( '<button/>', {
            id: "image-pull-button",
            class: "csap-button-icon tree",
            title: "Download (docker pull) new image ..."
        } )

        $pullImageButton.appendTo( $comment );

        jQuery( '<img/>', {
            src: baseUrl + "images/16x16/download.png"
        } ).appendTo( $pullImageButton );

        let $cleanButton = jQuery( '<button/>', {
            id: "image-clean-button",
            class: "csap-button-icon tree",
            title: "Run container image cleanup ..."
        } )

        $cleanButton.appendTo( $comment );

        jQuery( '<img/>', {
            src: baseUrl + "images/16x16/clean.png"
        } ).appendTo( $cleanButton );


        setTimeout( () => {
            $( "#image-pull-button" ).click( function () {
                // console.log() ;
                explorerOps.showPullImagePrompt();
            } )


            $( "#image-clean-button" ).click( function () {
                // console.log() ;
                explorerOps.showCleanImagePrompt();
            } )
        }, 500 );

        return $description;
    }

    function isLinuxServiceNode( configuration ) {
        return configuration.linuxServiceName && configuration.linuxServiceName != ""
    }

    function isContainerNode( configuration ) {
        return configuration.containerName && configuration.containerName != ""
    }


    function isPodNode( configuration ) {
        return configuration.podName && configuration.podName != ""
    }


    function isPodContainerNode( fancyTreeNode, childConfiguration ) {
        // console.log("isPodContainerNode() : ", fancyTreeNode, configuration)
        let parentConfiguration = fancyTreeNode.getParent().data;
        //console.log( "isPodContainerNode() : ", parentConfiguration, childConfiguration ) ;
        return parentConfiguration.podName &&
            parentConfiguration.podName != "" &&
            childConfiguration.attributes &&
            childConfiguration.attributes.image;

    }

    function isK8ServiceNode( configuration ) {
        return configuration.k8ServiceName && configuration.k8ServiceName != ""
    }

    function isImageNode( configuration ) {
        return configuration.imageName && configuration.imageName != ""
    }

    function title_for_image( fancyTreeNode, configuration ) {

        let $description = jQuery( '<div/>', {} );
        let label = configuration.originalTitle;

        jQuery( '<label/>', {
            class: "image",
            html: label,

        } ).appendTo( $description );

        console.debug( "label", label, configuration );
        let imageSize = configuration.attributes[ "Size" ];

        let imageSizeInMb = imageSize / 1024 / 1024;
        if ( imageSizeInMb > 100 ) {
            imageSizeInMb = Math.round( imageSizeInMb );
        } else {
            imageSizeInMb = imageSizeInMb.toFixed( 2 );
        }
        jQuery( '<div/>', {
            class: "empty",
            html: imageSizeInMb + " Mb"
        } ).appendTo( $description );

        return $description;
    }


    function title_for_pod_container( fancyTreeNode, configuration ) {

        let parentNode = fancyTreeNode.getParent();

        let parentFullName = parentNode.data.originalTitle;
        console.log( "title_for_pod_container() node:", fancyTreeNode,
            "\n parentFullName: ", parentFullName,
            "\n configuration: ", configuration );

        let container = configuration.attributes;

        //console.log("title_for_pod_container()", configuration)

        let $title = jQuery( '<div/>', {} );

        let $podContainer = jQuery( '<div/>', {
            "class": `podContainer comment`
        } );
        $podContainer.appendTo( $title );

        let containerName = container.name;
        if ( container.isInit ) {
            containerName = `init: ${ container.name }`;
            fancyTreeNode.type = hostOps.categories().kubernetesPods;
            fancyTreeNode.extraClasses = "ft-k8-pod-running";
            if ( container.status == "pass" ) {
                fancyTreeNode.extraClasses = "ft-k8-pod-completed";
            } else if ( container.status == "fail" ) {
                fancyTreeNode.extraClasses = "ft-k8-pod-stopped";
            }
        }
        jQuery( '<span/>', {
            "text": containerName
        } ).appendTo( $podContainer );

        jQuery( '<span/>', {
            "text": container.image
        } ).appendTo( $podContainer );


        return $title.html();

    }

    function title_for_pod( configuration ) {

        let $description = jQuery( '<div/>', {} );
        let label = configuration.podName;
        let title = label;

        jQuery( '<label/>', {
            class: "podTitle",
            title: title,
            html: label
        } ).appendTo( $description );

        // console.log( "label", label, configuration ) ;

        let $comment = jQuery( '<div/>', {
            class: "comment"
        } )
        $comment.appendTo( $description );

        let $podMeta = jQuery( '<div/>', {
            class: "pod-meta"
        } );
        $podMeta.appendTo( $comment );

        let podHost = jsonForms.getValue( "attributes.hostname", configuration );
        let targetUrl = utils.agentUrl( podHost, "host-dash" );
        if ( isIpAddress( podHost ) ) {
            targetUrl = `http://${ podHost }${ AGENT_ENDPOINT }/app-browser`;
            console.debug( "IP address detected  - stripping domain name:", targetUrl );
        }


        jQuery( '<a/>', {
            href: targetUrl,
            class: "csap-link-icon launch-window",
            target: "_blank",
            text: podHost,
            title: "Open Dashboard"
        } ).appendTo( $podMeta );

        let containersArray = jsonForms.getValue( "attributes.spec.containers", configuration );
        if ( isArray( containersArray ) ) {
            if ( containersArray.length > 1 ) {
                jQuery( '<span/>', {
                    text: "(" + containersArray.length + ")",
                    title: "Container Count"
                } ).appendTo( $podMeta );
            }

        }


        let nameSpace = jsonForms.getValue( "attributes.metadata.namespace", configuration );
        $comment.append( nameSpaceFilterLabel( nameSpace ) );


        // console.log(`title_for_pod() - configuration: `, configuration) ;
        //let restartCount = jsonForms.getValue( "attributes.status.containerStatuses.0.restartCount", configuration ) ;
        let restartCount = 0;

        let containerStatusReports = jsonForms.getValue( "attributes.status.containerStatuses", configuration );
        if ( containerStatusReports && isArray( containersArray ) ) {
            for ( let statusReport of containerStatusReports ) {
                if ( statusReport.restartCount ) {
                    restartCount += statusReport.restartCount;
                }
            }
        }
        if ( restartCount > 0 ) {
            jQuery( '<span/>', {
                class: "warning",
                text: "Restarts: " + restartCount,
                title: "Restarts found - contact administrator"
            } ).css( "padding-left", "3em" ).appendTo( $comment );
        }

        return $description;
    }

    function isIpAddress( ipaddress ) {
        if ( /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/.test( ipaddress ) ) {
            return true;
        }
        return false;
    }

    function title_for_k8_service( configuration ) {

        let $description = jQuery( '<div/>', {} );
        let label = configuration.k8ServiceName;
        console.debug( `title_for_k8_service() label: ${ label }` );

        jQuery( '<label/>', {
            class: "podTitle",
            title: label,
            html: label
        } ).appendTo( $description );

        // console.log( "label", label, configuration ) ;

        let $comment = jQuery( '<div/>', {
            class: "k8-service-summary"
        } )
        $comment.appendTo( $description );

        let $portDiv = jQuery( '<div/>', {
            title: "Port that expose services",
            class: "service-port-launch"
        } )

        let routeAttributes = jsonForms.getValue( "attributes", configuration );

        let ports = jsonForms.getValue( "spec.ports", routeAttributes );
        let rules = jsonForms.getValue( "spec.rules", routeAttributes );


        $portDiv.appendTo( $comment );

        if ( isArray( rules ) ) {
            console.debug( `rules found, assuming ingress: `, rules );

            let ingressFirstPath = jsonForms.getValue( "attributes.spec.rules.0.http.paths.0", configuration );
            if ( ingressFirstPath ) {
                // $portDiv.text( "route: " + ingressDef.serviceName + ":" + ingressDef.servicePort );
                let description = ingressFirstPath.backend.service.name + ":" + ingressFirstPath.backend.service.port.number;

                if ( !ingressFirstPath.backend.service
                    && ingressFirstPath.backend.serviceName ) {
                    description = ingressFirstPath.backend.serviceName + ":" + ingressFirstPath.backend.servicePort;
                }

                let ingressPath = ingressFirstPath.path;
                //ingressPath += "(/|$)(.*)" ;
                if ( ingressPath ) {
                    ingressPath = ingressPath.split( "\(" )[ 0 ];
                }

                let targetUrl = uiSettings.baseUrl
                    + "location/ingress?path=" + ingressPath
                    + "&serviceName=" + ingressFirstPath.backend.service.name + "&";

                let $ingressLink = jQuery( '<a/>', {
                    title: "launch ingress",
                    href: targetUrl,
                    class: "csap-link-icon csap-window",
                    target: "_blank",
                    text: description
                } );
                $ingressLink.appendTo( $portDiv );
            }

        } else if ( isArray( ports ) ) {
            console.debug( `ports found, assuming service: `, ports );

            let apiPort = ports[ 0 ].port;

            let csapServicePath = jsonForms.getValue( "metadata.annotations.csap-ui-launch-path", routeAttributes ); //.metadata.labels["csap-service-path"]

            if ( csapServicePath === undefined ) {
                csapServicePath = "/";
            }

            console.debug( `csapServicePath after updated: ${ csapServicePath }` );

            let launchPath = "/api/v1/namespaces/" +
                routeAttributes.metadata.namespace + "/services/" +
                label;

            if ( ports[ 0 ].name ) {
                launchPath += ":" + ports[ 0 ].name;
            }
            launchPath += "/proxy" + csapServicePath;



            jQuery( '<a/>', {
                title: "launch the service using kubernetes api proxy",
                href: kubernetesApiUrl + launchPath,
                class: "csap-link-icon csap-tools",
                target: "_blank",
                text: apiPort,
                "data-api": kubernetesApiUrl,
                "data-port": apiPort,
                "data-path": launchPath,
                "data-name": configuration.k8ServiceName
            } ).appendTo( $portDiv );

            if ( ports[ 0 ].nodePort ) {
                let nodeportPort = ports[ 0 ].nodePort;
                let hostname = window.location.hostname;

                if ( hostname.startsWith( "localhost" ) ) {
                    console.warn( "LOCALHOST in nodeport: switching to centos1" );
                    hostname = "centos1";
                }

                let scheme = "http";
                if ( ports[ 0 ].port == 443 ) {
                    scheme = "https";
                }
                let npLaunchUrl = scheme + "://" + hostname + ":" + ports[ 0 ].nodePort + csapServicePath;

                let $np = jQuery( '<a/>', {
                    title: "launch the service using nodeport",
                    href: npLaunchUrl,
                    class: "csap-link-icon csap-window",
                    target: "_blank",
                    text: nodeportPort
                } ).css( "margin-left", "1em" );
                $np.appendTo( $portDiv )
            }

        } else {
            console.warn( "Unexpected route type: ", configuration );
        }

        let nameSpace = jsonForms.getValue( "attributes.metadata.namespace", configuration );
        $comment.append( nameSpace );

        return $description;
    }

    function title_for_container( configuration ) {

        let $description = jQuery( '<div/>', {} );
        let label = configuration.containerName;
        let title = label;
        let k8sNamespace = "";

        if ( label_finder( configuration, "io.kubernetes.docker.type" ) != null ) {

            let app = label_finder( configuration, "k8s-app" );
            let pod = label_finder( configuration, "io.kubernetes.pod.name" );
            k8sNamespace = label_finder( configuration, "io.kubernetes.pod.namespace" );
            let type = label_finder( configuration, "io.kubernetes.container.name" );

            if ( ( app != null ) ) {
                label = app;
            } else if ( ( pod != null ) && ( type == "POD" ) ) {
                label = pod;
            } else if ( type != null ) {
                label = type;
            }

            //label="apiserver" ;
        }

        jQuery( '<label/>', {
            class: "container",
            title: title,
            html: label
        } ).appendTo( $description );

        // console.log( "label", label, configuration ) ;

        let $comment = jQuery( '<div/>', {
            class: "empty"
        } )
        $comment.appendTo( $description );

        jQuery( '<div/>', {
            text: configuration.attributes[ "allVolumesInMb" ] + "Mb",
            title: "Sum of disk of all mounted volumes"
        } )
            .css( "display", "inline-block" )
            .css( "min-width", "6em" )
            .css( "text-align", "right" )
            .css( "margin-right", "2em" )
            .appendTo( $comment );

        let meta = configuration.attributes[ "Image" ];
        if ( k8sNamespace != "" ) {
            meta = nameSpaceFilterLabel( k8sNamespace );
        }
        $comment.append( meta );

        return $description;
    }

    function title_for_attribute( configuration ) {

        let $description = jQuery( '<div/>', {} );
        let label = configuration.customLabel + ":";
        let value = configuration.customValue;

        console.debug( `title_for_attribute() - building label: ${ label }` );

        let valueStyle = "value";
        if ( value == "" || value == null ) {
            value = '-';
            valueStyle = "empty";
        }
        let uiContainer = '<div/>';
        let editId = "edit-" + ( new Date() ).getTime();


        jQuery( '<label/>', {
            class: "name",
            html: label
        } ).appendTo( $description );

        let $value = jQuery( uiContainer, {
            id: editId,
            class: valueStyle,
            html: value
        } );

        $value.appendTo( $description );

        let pid = jsonForms.getValue( "attributes.pid", configuration );

        // pid kill button
        if ( pid ) {
            let killId = "kill-" + configuration.attributes.pid;
            let $button = jQuery( '<button/>', {
                "id": killId,
                "class": "csap-icon csap-remove tree",
                "title": "Kill pid",
                "data-pid": configuration.attributes.pid
            } );

            let postKillFunction = function () {
                explorerOps.refreshFolder(
                    hostOps.categories().linuxProcesses );
            };

            registerTreeButton( killId, hostOps.showKillPidDialog, postKillFunction );

            $button.appendTo( $description );
            $button.css( "margin-left", "2em" );

        }

        // pid report buttons
        if ( configuration.customLabel.toLowerCase() == "pid" ) {
            let pid = value;
            let reportId = "process-report-" + pid;

            let $button = jQuery( '<button/>', {
                "id": reportId,
                "class": "csap-button-icon tree",
                "title": "Pid report",
                "data-targeturl": utils.getOsUrl() + "/process/report/" + pid
            } );

            $button.appendTo( $description );

            jQuery( '<img/>', {
                src: baseUrl + "images/info.png"
            } ).appendTo( $button );

            registerTreeButton( reportId, hostOps.commandRunner, function () { } );
        }

        return $description;
    }



    function title_for_process( configuration ) {

        //console.log( "buildProcessTitle():", configuration ) ;

        let $description = jQuery( '<div/>', {} );
        let fullTitle = configuration.originalTitle;
        let shortenedTitle = fullTitle;
        let maxDisplayLength = 35;
        if ( shortenedTitle.length > maxDisplayLength ) {
            shortenedTitle = "(...)" + shortenedTitle.substr( fullTitle.length - maxDisplayLength );
        }
        let user = configuration.user;

        jQuery( '<label/>', {
            class: "processName",
            title: fullTitle,
            html: shortenedTitle
        } ).appendTo( $description );

        jQuery( '<div/>', {
            class: "user",
            html: user
        } ).appendTo( $description );


        let numProcesses = 1;
        if ( configuration.attributes &&
            isArray( configuration.attributes ) &&
            configuration.attributes.length > 1 ) {
            numProcesses = configuration.attributes.length;
        }
        jQuery( '<div/>', {
            class: "user",
            html: numProcesses
        } ).appendTo( $description );

        //console.log( "$description", $description.html() ) ;

        return $description;
    }

    function title_for_vsphere( label, comment, commentId ) {

        //console.log("renderProcess", configuration);

        let $description = jQuery( '<div/>', {} );

        jQuery( '<label/>', {
            class: "summary",
            html: label
        } ).appendTo( $description );

        let $comment = jQuery( '<div/>', {
            class: "comment",
            id: commentId,
            html: comment
        } );

        $description.append( $comment );

        let $attributeFilter = jQuery( '<label/>', { title: "Filter items based on csap vsphere settings" } );

        let $vsphereFilterButton = jQuery( '<input/>', {
            id: "vsphere-dc-checkbox",
            class: "tree-filter-checkbox",
            type: "checkbox",
            checked: "checked",

        } )

        let govcButtonId = "govc-template-button";
        let $govcButton = jQuery( '<button/>', {
            id: govcButtonId,
            class: "csap-button-icon tree",
            title: "launch govc templates"
        } ).css( "margin-left", "30px" );

        jQuery( '<img/>', {
            src: baseUrl + "images/16x16/shell.png"
        } )
            .appendTo( $govcButton );


        setTimeout( function () {
            $( "#vsphere-dc-checkbox" ).click( function () {
                //alertify.alert( "Filter clicked" );
                explorerOps.refreshFolder( hostOps.categories().vsphereVms );
                explorerOps.refreshFolder( hostOps.categories().vsphereDatastores );
            } );

            $( "#" + govcButtonId ).click( function () {
                //                let govcTemplateUri = commandScreen + "?command=script&template=vsphere-vcgo.sh&fromFolder=__root__/opt/csap" ;
                //                openWindowSafely( govcTemplateUri, "_blank" ) ;
                utils.launchScript( {
                    fromFolder: `__root__/opt/csap`,
                    template: `vsphere-vcgo.sh`
                } );
            } )
        }, 500 );

        $comment.append( $attributeFilter );
        $attributeFilter.append( $vsphereFilterButton );
        $attributeFilter.append( "DC filter" );
        $attributeFilter.append( $govcButton );

        //console.log( "$description", $description.html() ) ;

        return $description.html();
    }

    function title_for_vsphere_datastore_file( label, path, uri ) {

        //console.log("renderProcess", configuration);

        let $description = jQuery( '<div/>', {} );

        jQuery( '<label/>', {
            class: "summary",
            html: label
        } ).appendTo( $description );

        let $comment = jQuery( '<div/>', {
            class: "comment"
        } );

        $description.append( $comment );

        let govcAddDiskId = "govc-datastore-add-button";
        let $govcButton = jQuery( '<button/>', {
            id: govcAddDiskId,
            class: "csap-button-icon tree",
            title: "Add a new disk"
        } ).css( "width", "auto" );

        jQuery( '<img/>', {
            src: baseUrl + "images/16x16/document-new.png"
        } )
            .appendTo( $govcButton );

        jQuery( '<span/>', {
            text: "Disk find/add/remove"
        } )
            .css( "font-size", "9pt" )
            .css( "position", "relative" )
            .css( "top", "2px" )
            .appendTo( $govcButton );


        setTimeout( function () {

            $( "#" + govcAddDiskId ).click( function () {
                console.log( "Adding Disk" );
                explorerOps.showAddDiskPrompt( path, explorerUrl + "/" + uri );
            } );
        }, 500 );

        $comment.append( $govcButton );

        //console.log( "$description", $description.html() ) ;

        return $description.html();
    }



    function title_for_events( eventReport ) {

        console.log( `title_for_events :`, eventReport );

        //title_for_events( labels[0] + '<span title="occurences" class="event-count">' + labels[1] + "</span>", labels[2], "" );

        let $description = jQuery( '<div/>', {} );
        let $summary = jQuery( '<label/>', {
            class: "summary event-summary"
        } )
        $summary.appendTo( $description );

        let latestEventTime = jsonForms.getValue( "attributes.timeOfLatestEvent", eventReport );
        if ( !latestEventTime ) {
            latestEventTime = jsonForms.getValue( "attributes.eventTime", eventReport );
        }
        if ( !latestEventTime ) {
            latestEventTime = "";
        }

        jQuery( '<span/>', {
            class: "event-timestamp",
            text: latestEventTime
        } ).appendTo( $summary );


        let eventInfo = jsonForms.getValue( "attributes.simpleHost", eventReport );
        if ( !eventInfo ) {
            eventInfo = jsonForms.getValue( "attributes.reason", eventReport );
        }
        let eventSimpleName = jsonForms.getValue( "attributes.simpleName", eventReport );
        if ( eventSimpleName ) {
            eventInfo = `<span>${ eventSimpleName }:</span> ${ eventInfo } `;
        }

        jQuery( '<span/>', {
            class: "event-info",
            html: eventInfo
        } ).appendTo( $summary );



        let message = jsonForms.getValue( "attributes.message", eventReport );
        if ( message.length > 120 ) {
            message = message.substr( 0, 120 ) + "...";
        }
        let count = jsonForms.getValue( "attributes.count", eventReport );
        if ( count && ( count > 1 ) ) {
            message = `(${ count } instances)  ${ message }`;
        }

        jQuery( '<div/>', {
            class: "comment",
            html: message
        } ).appendTo( $description );

        //console.log( "$description", $description.html() ) ;

        return $description.html();
    }

    function title_with_comment( label, comment, commentId, extraCommentClass ) {

        //console.log("renderProcess", configuration);

        let $description = jQuery( '<div/>', {} );

        let commentClasses = "comment";
        if ( extraCommentClass ) {
            commentClasses = `comment ${ extraCommentClass }`;
        }

        jQuery( '<label/>', {
            class: "summary",
            html: label
        } ).appendTo( $description );

        jQuery( '<div/>', {
            class: commentClasses,
            id: commentId,
            html: comment
        } ).appendTo( $description );

        if ( commentId == "k8EventTree" ) {
            let $maxEvents = jQuery( '<div/>', {
                class: "comment",
                text: "max:"
            } ).appendTo( $description );

            jQuery( '<input/>', {
                id: "max-events",
                value: 1000
            } )
                .appendTo( $maxEvents );
        }

        //console.log( "$description", $description.html() ) ;

        return $description.html();
    }
}