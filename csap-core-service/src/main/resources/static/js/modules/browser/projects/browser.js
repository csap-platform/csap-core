// define( [ "browser/utils", "editor/json-forms", "ace/ace", "jsYaml/js-yaml" ], function ( utils, jsonForms, aceEditor, jsYaml ) {




import _dom from "../../utils/dom-utils.js";

import _net from "../../utils/net-utils.js";
import _dialogs from "../../utils/dialog-utils.js";

import loadTreeComponents from "../../libs/fancy-tree.js"

import utils from "../utils.js"

import jsonForms from "../../editor/json-forms.js"


import { aceEditor, jsYaml } from "../../libs/file-editor.js"


export const projectBrowser = project_browser();

export default projectBrowser


function project_browser() {

    _dom.logHead( "Module loaded" );



    let __packageToFileMap;
    const _panelId = "#jsonFileBrowser";


    let _max_tree_load_wait = 0;
    let $jsonBrowser = $( _panelId );
    let $definition = null;
    let _definition = null;
    let _isTreeUpdated = false;


    let _projectJsonEditor, _projectJsonDialog, _lastJsonEdited;
    const $projectJsonPre = $( "#project-json-editor-container" );
    const $jsonSave = $( "#pje-save", $projectJsonPre.parent() );
    const $jsonModeCheckbox = $( "#pje-json-mode", $projectJsonPre.parent() )

    let _last_opened_folder;

    let _aceTimer;

    let _lastPathSelected = null;

    let onChangeFunction;

    console.log( "Module loaded" );

    let clusterKeys = new Array( "multiVmPartition", "singleVmPartition", "multiVm", "version" );


    let _initComplete = false;


    return {

        addOnChangeFunction: function ( theFunction ) {
            onChangeFunction = theFunction;
        },

        show: function ( definition, $definitionTextArea ) {
            _definition = definition;
            $definition = $definitionTextArea;
            return show();
        },

        updatePackageMap: function ( packageMap ) {
            __packageToFileMap = packageMap;
        },

        reset: function ( definition ) {

            _max_tree_load_wait = 0;
            if ( !_initComplete )
                return;
            //https://github.com/mar10/fancytree/wiki/TutorialLoadData
            console.log( "reloading tree" );
            _definition = definition;
            openFirstItem();

        }

    }

    function showTreeUpdateWarnings() {

        if ( _isTreeUpdated ) {
            setTimeout( () => {

                alertify.alert(
                    "Release package has been updated. Operations in dialog will overwrite."
                    + "Alternately - close dialog and apply or checkin changes."
                );

            }, 1000 );
        }
    }

    function openFirstItem() {

        try {
            let tree = $jsonBrowser.fancytree( 'getTree' );
            tree.reload( tree_lazy_load() );
            //tree.getFirstChild().setExpanded( true ); // http://wwwendt.de/tech/fancytree/doc/jsdoc/FancytreeNode.html
            //tree.getFirstChild().getNextSibling().setExpanded( true );
            tree.getFirstChild().setExpanded( true );
            //		expandFolder( "projects" ) ;
            //		expandFolder( "lifecycles" ) ;
            //		expandFolder( "application" ) ;
        } catch ( e ) {
            //alertify.alert( message );
            _max_tree_load_wait++;
            if ( _max_tree_load_wait > 5 ) {
                console.log( "Max attempts exceeded wait for tree init" );
            } else {

                console.log( `Tree not loaded, attempt: ${ _max_tree_load_wait } ` );
                setTimeout( function () {
                    openFirstItem();
                }, 500 );
            }
        }
    }

    function buildPackageRoot() {

        //console.log( "event", event, "data", data );
        let children = new Array();

        let selectedFileName = __packageToFileMap[ utils.getActiveProject() ];

        children.push( {
            "title": selectedFileName,
            "name": "property folder",
            "location": jsonForms.getRoot(),
            "folder": true,
            "lazy": true,
            "icon": baseUrl + "images/text-x-script.png"
        } );
        return children
    }
    //	
    function expandFolder( location ) {

        let tree = $jsonBrowser.fancytree( 'getTree' );
        let node = tree.findFirst( function ( node ) {
            return findByPath( node.data, location );
        } );
        if ( node )
            node.setExpanded( true );
    }

    function findByPath( treeData, path ) {
        //console.log("findByPath", treeData, path) ;
        return treeData.location == path;
    }
    function show() {

        console.log( "Show tree requested" );
        async function loadTreeAndThenRunInit(  ) {

            await loadTreeComponents() ;
            init();
        }

        return loadTreeAndThenRunInit() ;

        //$jsonBrowser.show() ;

    }

    function openInText() {
        console.log( "Launching to text view", _lastPathSelected );
        $( "#tabs" ).tabs( "option", "active", 4 );
    }

    function definitionUpdated() {
        console.log( "definitionUpdated ", _definition );
        $definition.val( JSON.stringify( _definition, null, "\t" ) );
    }
    function init() {
        if ( _initComplete ) {
            return;
        }


        console.log( "Tree Initialization" );



        jsonForms.registerForChangeNotification( definitionUpdated );
        $( ".openInTextButton" ).click( openInText );

        _initComplete = true;
        console.log( `Building tree` );
        $jsonBrowser.fancytree( tree_build() );


        openFirstItem();

    }

    function tree_build() {

        let config = {
            source: tree_lazy_load(),
            keyboard: false,

            collapse: function ( event, data ) {

                let fancyTreeData = data;
                let nodeSelected = fancyTreeData.node;
                let nodeData = nodeSelected.data;
                //let versionNode = findFirst 
                console.log( "tree_build() collapse", nodeData );

                if ( nodeData.location == jsonForms.getRoot() ) {
                    $( "#jsonFileContainer" ).append( $definition );
                }

                // reload values from definition - could optimize and only reset when modified
                nodeSelected.resetLazy();

            },
            expand: function ( event, data ) {

                let fancyTreeData = data;
                let nodeSelected = fancyTreeData.node;
                let nodeData = nodeSelected.data;
                //let versionNode = findFirst 
                console.log( "tree_build() expand" );
                $( "span.spacer-description" ).parent().parent().css( "margin-top", "8px" );

                //nodeSelected.sortChildren(elementSort );
                _last_opened_folder = fancyTreeData.node;
                _lastPathSelected = nodeData.location;

            },
            activate: function ( event, data ) {

                let fancyTreeData = data;
                let nodeSelected = fancyTreeData.node;
                let nodeData = nodeSelected.data;
                let path = nodeData.location;
                $( this ).css( "border", "5px" );


                console.log( `path: ${ path } last node:`, nodeData );

                if ( nodeData.jsonEdit ) {

                    let editSnippet = _definition;
                    if ( path != jsonForms.getRoot() ) {
                        editSnippet = utils.json( path, _definition );
                    }
                    tree_render_jsonEditor( editSnippet, path );
                    nodeSelected.parent.setActive();
                }

                _lastPathSelected = nodeData.location;

            },

            renderNode: tree_render_node,

            lazyLoad: function ( ftEvent, ftData ) {
                ftData.result = tree_lazy_load( ftEvent, ftData.node );
            }
        };

        return config;
    }



    function getDefinition() {
        console.log( "getDefinition()" );
        return _definition;
    }
    function tree_render_node( ftEvent, ftData ) {

        let ftNode = ftData.node;
        console.log( `tree_render_node(): ${ ftNode.data.fieldName }`, ftNode.data );
        // note - no references to title are used or rendering is broken

        let $description = jQuery( '<div/>', {} );
        let label = ftNode.data.fieldName;
        let value = ftNode.data.fieldValue;
        let path = ftNode.data.location;
        let cssClass = "tedit";

        let isService = false;
        if ( ftNode.folder &&
            ( path == "services." + label ) ) {
            isService = true;
        }

        let isSettings = false;
        let settingsRegEx = new RegExp( "lifecycles.*settings" ); // strip out . for css
        let isCluster = false;
        let clusterRegEx = new RegExp( "lifecycles\.[a-z0-9]*\." + label, "i" ); // strip out . for css
        let lifecycle = ""
        if ( ftNode.folder &&
            ( path.match( settingsRegEx ) ) ) {
            isSettings = true;
            lifecycle = path.split( "." )[ 1 ];
        } else if ( ftNode.folder &&
            ( path.match( clusterRegEx ) ) ) {
            isCluster = true;
            lifecycle = path.split( "." )[ 1 ];
        }

        if ( ftNode.folder && path == "lifecycles." + label ) {

            value = label;
            label = "Lifecycle: ";
            cssClass = "folderDesc"

        } else if ( isService || isSettings || isCluster ) {
            label = ftNode.data.fieldName;

        } else if ( !ftNode.folder ) {
            label = ftNode.data.fieldName;

        } else {
            console.log( "path", path, "label", label );
            return;
        }

        if ( path === jsonForms.getRoot() ) {

            console.log( `rendered root: ${ path } ` );
            //if ( false ) {

            //            let rootContainerId = "rootJsonEditorForjson" ;
            //            console.log( "renderValue() - showing root json document" )
            //            jQuery( '<div/>', {
            //                id: rootContainerId,
            //                class: "treeText",
            //                spellcheck: false,
            //                "data-path": path
            //            } ).appendTo( $description ) ;
            //
            //            ftNode.setTitle( $description.html() ) ;

            // need to delay for rendering
            //            setTimeout( () => {
            //
            //                $( "#" + rootContainerId ).append( $definition ) ;
            //                tree_render_jsonEditor( $definition ) ;
            //
            //            }, 500 ) ;
            // 


        } else if ( isCluster ) {

            jQuery( '<label/>', {
                text: label
            } ).appendTo( $description );

            let $button = jQuery( '<button/>', {
                title: "Click to open cluster editor",
                class: "treeClusterButton custom",
                "data-clusterlife": lifecycle,
                "data-clustername": label,
            } ).appendTo( $description );

            ftNode.setTitle( $description.html() );

            setTimeout( () => {

                registerClusterForm(
                    $( ".treeClusterButton[data-clustername='" + label + "']",
                        $jsonBrowser ) );
            }, 500 );


        } else if ( isSettings ) {

            jQuery( '<label/>', {
                text: label
            } ).appendTo( $description );

            let $button = jQuery( '<button/>', {
                title: "Click to open settings editor",
                class: "treeSettingsButton custom",
                "data-lifecycle": lifecycle
            } ).appendTo( $description );

            ftNode.setTitle( $description.html() );

            setTimeout( () => {

                registerSettingsForm(
                    $( ".treeSettingsButton[data-lifecycle='" + lifecycle + "']",
                        $jsonBrowser ) );
            }, 500 );


        } else if ( isService ) {

            jQuery( '<label/>', {
                text: label
            } ).appendTo( $description );

            let $button = jQuery( '<button/>', {
                title: "Click to open service editor",
                class: "treeServiceButton custom",
                "data-service": label
            } ).appendTo( $description );

            ftNode.setTitle( $description.html() );

            setTimeout( () => {

                registerServiceForm(
                    $( ".treeServiceButton[data-service='" + label + "']",
                        $jsonBrowser ) );
            }, 500 );


        } else if ( isArray( value ) || isObject( value ) ) {



            console.log( `value is objecy or array: ${ path } ` );

            //if ( false ) {

            //            let editorId = "jsonEdit" + path.replace( /\./g, "_" ) ;
            //            jQuery( '<textarea/>', {
            //                id: editorId,
            //                class: "treeText fitContent",
            //                spellcheck: false,
            //                "data-path": path
            //            } ).appendTo( $description ) ;
            //
            //            ftNode.setTitle( $description.html() ) ;
            //
            //            setTimeout( () => {
            //
            //                setTimeout( () => {
            //
            //                    tree_render_jsonEditor( $( '#' + editorId ) ) ;
            //                }, 500 ) ;
            //
            //                jsonForms.configureJsonEditors(
            //                        getDefinition, "fitContent", _panelId, '#json' ) ;
            //                jsonForms.loadValues( _panelId, _definition ) ;
            //
            //            }, 500 ) ;


        } else {

            jQuery( '<label/>', {
                text: label
            } ).appendTo( $description );

            jQuery( '<span/>', {
                class: cssClass,
                "data-path": path,
                text: value
            } ).appendTo( $description );

            ftNode.setTitle( $description.html() );

            setTimeout( () => {

                registerClickEdits( $( ".tedit[data-path='" + ftNode.data.location + "']", $jsonBrowser ) );
            }, 500 );
        }


    }


    function registerClusterForm( $clusterButton ) {
        $clusterButton.click( function () {
            let lifeEdit = $clusterButton.data( "clusterlife" ).trim();
            let clusterName = $clusterButton.data( "clustername" ).trim();
            let relPkg = utils.getActiveProject();

            console.log( "\n\n registerClusterForm(): launching: " + clusterEditUrl
                + " lifeEdit: " + lifeEdit + " clusterName: " + clusterName + " project: " + relPkg );

            let params = {
                project: relPkg,
                lifeToEdit: lifeEdit,
                clusterName: clusterName
            }

            //clusterEditor.setRefresh( _refreshFunction )


            $.get( clusterEditUrl,
                params,
                clusterEditor.show,
                'html' );

            showTreeUpdateWarnings();
            return false;
        } );
    }

    function registerSettingsForm( $settingsButton ) {
        $settingsButton.click( function () {
            let lifeEdit = $settingsButton.data( "lifecycle" ).trim();

            console.log( "\n registerSettingsForm(): launching: " + settingsEditUrl + " lifeEdit: " + lifeEdit );

            let params = {
                lifeToEdit: lifeEdit
            }

            $.get( settingsEditUrl,
                params,
                settingsEdit.showSettingsDialog,
                'html' );
            showTreeUpdateWarnings();
            return false;
        } );
    }

    function registerServiceForm( $serviceButton ) {
        $serviceButton.click( function () {
            let targetService = $serviceButton.data( "service" ).trim();
            console.log( "\n registerServiceForm(): launching: "
                + serviceEditUrl + " hostName: " + hostName
                + " serviceName: " + targetService );

            // global used in service portal
            serviceName = targetService;
            let params = {
                project: utils.getActiveProject(),
                serviceName: targetService,
                hostName: "*"
            }
            $.get( serviceEditUrl,
                params,
                function ( htmlDialog ) {
                    serviceEdit.showServiceDialog( targetService, htmlDialog );
                },
                'html' );
            showTreeUpdateWarnings();
            return false;
        } );
    }

    function registerClickEdits( $clickableValue ) {

        $clickableValue.off();
        $clickableValue.click( function () {
            let content = $( this ).text();
            let path = $( this ).data( "path" );
            let id = $( this ).data( "id" );
            let type = $( this ).data( "type" );

            $( this ).off();
            $( this ).empty();
            _isTreeUpdated = true;
            let $editValueContainer = jQuery( '<input/>', {
                class: "",
                "data-id": id,
                "data-path": path,
                value: content
            } );
            $( this ).append( $editValueContainer );
            $editValueContainer.change( function () {
                //jsonForms.updateDefinition( _definition, $( this ) );
                updateDefinition( $( this ) );
            } );
        } );
    }

    function updateDefinition( $inputThatChanged ) {

        let jsonPath = $( $inputThatChanged ).data( "path" );
        console.log( `modified:  ${ jsonPath } ` );

        if ( jsonPath == jsonForms.getRoot() ) {
            _definition = JSON.parse( $inputThatChanged.val() );
            //openFirstItem() ;
        }
        jsonForms.updateDefinition( _definition, $inputThatChanged );
        //        }
        $definition.val( JSON.stringify( _definition, null, "\t" ) );
        onChangeFunction( _definition );

    }

    function tree_render_jsonEditor( editSnippet, path ) {

        console.log( `tree_render_jsonEditor path: ${ path }` );

        _lastJsonEdited = editSnippet;

        if ( !alertify.jsonDisplay ) {
            buildJsonTreeEditor()
        }

        // reset view back to default
        $projectJsonPre.data( "path", path );
        $jsonModeCheckbox.prop( "checked", false );
        $( "#pje-fold-mode", $projectJsonPre.parent() ).prop( "checked", false );
        _projectJsonEditor.getSession().setMode( "ace/mode/yaml" );
        _projectJsonEditor.getSession().setValue( dumpAndIndentAsYaml( editSnippet, path ) );

        utils.disableButtons( $jsonSave );



        _projectJsonDialog = alertify.jsonDisplay().show();

        _projectJsonEditor.focus();


    }

    function buildJsonTreeEditor() {



        let $yamlSpacing = $( "#pje-yaml-spacing", $projectJsonPre.parent() );

        _projectJsonEditor = aceEditor.edit( $projectJsonPre.attr( "id" ) );
        _projectJsonEditor.setOptions( utils.getAceDefaults( "ace/mode/yaml" ) );

        _projectJsonEditor.getSession().on( 'change', function () {
            let waitForLoadComplete = function () {
                let updatedContent = _projectJsonEditor.getSession().getValue();
                let isJson = $jsonModeCheckbox.is( ':checked' );
                console.log( `Validating editor mode - isJson: ${ isJson }` );

                if ( isJson ) {
                    try {
                        JSON.parse( updatedContent );
                        utils.enableButtons( $jsonSave, $jsonModeCheckbox );
                    } catch ( e ) {
                        console.log( "Failed to parse", e );
                        utils.disableButtons( $jsonSave, $jsonModeCheckbox );
                    }

                } else {
                    try {
                        // loadAll - would convert json to array of docs - so load only
                        const updatedYaml = jsYaml.load( updatedContent );
                        _projectJsonEditor.getSession().clearAnnotations();
                        utils.enableButtons( $jsonSave, $jsonModeCheckbox );
                    } catch ( e ) {
                        utils.markAceYamlErrors( _projectJsonEditor.getSession(), e );
                        utils.disableButtons( $jsonSave, $jsonModeCheckbox );
                    }
                }
            }

            setTimeout( waitForLoadComplete, 100 );
        } );

        let csapDialogFactory = _dialogs.dialog_factory_builder( {
            content: $( "#project-json-editor-dialog" ).show()[ 0 ],
            onresize: projectJsonEditorResize
        } );

        alertify.dialog( 'jsonDisplay', csapDialogFactory, false, 'confirm' );

        $jsonModeCheckbox.change( function () {
            let isJson = $jsonModeCheckbox.is( ':checked' );
            console.log( `Switching editor mode - isJson: ${ isJson }` );

            let latestContent = _projectJsonEditor.getSession().getValue();

            console.log( latestContent )
            let savePath = $projectJsonPre.data( "path" );

            if ( isJson ) {
                utils.disableButtons( $yamlSpacing );

                try {
                    const updatedYaml = jsYaml.load( latestContent );
                    //                    _projectJsonEditor.getSession().setValue( editContent ) ;
                    _projectJsonEditor.getSession().setValue( JSON.stringify( updatedYaml, null, 2 ) );
                    _projectJsonEditor.getSession().setMode( "ace/mode/json" );

                } catch ( e ) {
                    alertify.csapWarning( `${ e.message }`, "Json Processing Failed" );
                    console.log( `yaml load failer`, e );
                }
            } else {
                //                console.log( "updatedContent", updatedContent ) ;
                utils.enableButtons( $yamlSpacing );
                let updatedAsYaml = dumpAndIndentAsYaml(
                    JSON.parse( latestContent ),
                    savePath );

                _projectJsonEditor.getSession().setValue( updatedAsYaml );
                _projectJsonEditor.getSession().setMode( "ace/mode/yaml" );
            }
        } );

        $( "#pje-fold-mode", $projectJsonPre.parent() ).change( function () {
            if ( $( this ).is( ':checked' ) ) {
                _projectJsonEditor.getSession().foldAll( 2 );
            } else {
                _projectJsonEditor.getSession().unfold();
            }
        } );

        $yamlSpacing.change( function () {
            if ( !$jsonModeCheckbox.is( ':checked' ) ) {
                let savePath = $projectJsonPre.data( "path" );
                _projectJsonEditor.getSession().setValue( dumpAndIndentAsYaml( _lastJsonEdited, savePath ) );
            }
        } );

        $jsonSave.click( function () {

            let savePath = $projectJsonPre.data( "path" );

            console.log( `json editor save clicked,  path: ${ savePath }` );
            let updatedContent = _projectJsonEditor.getSession().getValue();

            if ( !$jsonModeCheckbox.is( ':checked' ) ) {
                const updatedYaml = jsYaml.load( updatedContent );
                updatedContent = JSON.stringify( updatedYaml, "\n", "\t" );
            }


            console.log( "Apply Editor Changes: Merging :", updatedContent );
            let $tempText = jQuery( '<textarea/>', {
                id: "temp-text",
                class: "treeText fitContent",
                spellcheck: false,
                "data-path": savePath,
                "data-json": true
            } );
            $tempText.val( updatedContent );
            updateDefinition( $tempText );

            let parentNode = _last_opened_folder;
            setTimeout( () => {
                parentNode.setExpanded( false );
                setTimeout( () => {
                    parentNode.setExpanded( true );
                }, 500 );
            }, 1000 );

            //                }
            _projectJsonDialog.close();
        } );
    }


    function projectJsonEditorResize( dialogWidth, dialogHeight ) {

        setTimeout( function () {



            let maxWidth = dialogWidth - 10;
            $projectJsonPre.css( "width", maxWidth );
            $projectJsonPre.css( "margin-left", "5px" );
            $projectJsonPre.css( "margin-top", "5px" );

            let maxHeight = dialogHeight
                - Math.round( $( "div.flex-container", $projectJsonPre.parent() ).outerHeight( true ) )
                - 20;
            $projectJsonPre.css( "height", maxHeight );

            console.log( `jsonDisplayEditorResize() launched/resizing yaml editor` );
            _projectJsonEditor.resize();


        }, 500 );

    }

    function dumpAndIndentAsYaml( editSnippet, path ) {

        console.log( `dumpAndIndentAsYaml: ${ path } ` )

        let options = {
            indent: 2
            //            forceQuotes: true supported 4.x
        }
        let yamlText = jsYaml.dump( editSnippet, options );

        if ( $( "#pje-yaml-spacing", $projectJsonPre.parent() ).is( ':checked' ) ) {

            let spaceTopLevel = path.startsWith( "service-templates." )
                || path.startsWith( "project" );

            yamlText = utils.yamlSpaces(
                yamlText,
                [ "environments", "project", "service-templates" ],
                spaceTopLevel );
        }

        return yamlText;
    }

    function tree_lazy_load( ftEvent, ftNode ) {


        let parentLocation = "";
        let subTreeDefinition = _definition;

        if ( ftNode ) {
            if ( ftNode.data.location != jsonForms.getRoot() ) {
                parentLocation = ftNode.data.location;
                subTreeDefinition = jsonForms.getValue( parentLocation, _definition );
                parentLocation += ".";
            }
        } else {
            return buildPackageRoot();
        }

        let ftNodeData = ftNode.data;

        let ftNodeTree = new Array();

        // generate fields in sorted order
        let sortedFields = sortElements( subTreeDefinition, parentLocation );

        console.log( `tree_lazy_load() - parentLocation: ${ parentLocation } sortedFields: `, sortedFields );


        if ( ftNodeData ) {
            console.log( "Adding editor entry", ftNodeData.location );

            let description = buildTitle( "edit", "" );
            if ( ftNodeData.location && ftNodeData.location == jsonForms.getRoot() ) {
                description = "<span class='rootEditing spacer-description' style='font-style: italic'>edit</span>";
            }
            ftNodeTree.push( {
                "title": description,
                "folder": false,
                "extraClasses": "ft_jsonedit",
                // data fields for rendering
                "jsonEdit": true,
                "fieldName": "...",
                "fieldValue": subTreeDefinition,
                "location": ftNodeData.location,
                "sortByField": "zzzzzz"
            } );

        }

        for ( let fieldName of sortedFields ) {
            let fieldLocation = parentLocation + fieldName;
            let fieldTree = jsonForms.getValue( fieldLocation, _definition );
            console.log( "fieldLocation", fieldLocation, "fieldTree", fieldTree );

            let isFolder = true;
            let isLazy = true;
            let fieldValue = "";
            if ( ( typeof fieldTree ) != "object" ) {
                isFolder = false;
                isLazy = false;
                fieldValue = fieldTree;
            }
            let title = fieldName;

            let extraClasses = "";

            let clusterType = "";
            if ( fieldTree.type && fieldTree[ "template-references" ] ) {
                clusterType = fieldTree.type;
            }

            if ( fieldName == "project" ) {
                extraClasses = "ft_folder";
                let desc = `project: ${ fieldTree[ "project-version" ] }, api: ${ fieldTree[ "api-version" ] }`;
                title = buildTitle( fieldName, desc );

            } else if ( fieldName == "service-templates" || fieldName == "template-references" ) {
                extraClasses = "ft_os";
                let count = Object.keys( fieldTree ).length;
                title = buildTitle( fieldName, `${ count } service templates` );

            } else if ( fieldName == "environments" || parentLocation == "environments." ) {
                extraClasses = "ft_environment";
                if ( fieldName == "defaults" ) {
                    extraClasses = "ft_settings";
                }
                let count = Object.keys( fieldTree ).length;
                let desc = "environments";
                let importDesc = "";
                if ( parentLocation == "environments." ) {
                    desc = "clusters";
                    // console.log(`fieldName: ${fieldName}`, subTreeDefinition[fieldName])
                    if ( subTreeDefinition && subTreeDefinition[ fieldName ] ) {
                        let settings = ( subTreeDefinition[ fieldName ] ).settings;
                        if ( settings ) {
                            count--;
                            if ( settings.imports ) {
                                importDesc = `<span>import: ${ settings.imports }</span>`;
                            }
                        }
                    }

                }
                title = buildTitle( fieldName, `${ count } ${ desc } ${ importDesc }` );

            } else if ( fieldTree.isMessaging ) {
                extraClasses = "ft_jms";

            } else if ( fieldTree.isDataStore ) {
                extraClasses = "ft_db";

            } else if ( fieldTree.server ) {
                extraClasses = getServerIconStyle( fieldTree );

            } else if ( clusterType == "kubernetes-provider" ) {
                extraClasses = "ft_kubelet";
                let desc = ""
                if ( fieldTree[ "masters" ] ) {
                    let masterCount = Object.keys( fieldTree[ "masters" ] ).length;
                    desc += ` ${ masterCount } kubernetes masters, `;
                }
                let serviceCount = Object.keys( fieldTree[ "template-references" ] ).length;
                desc += ` ${ serviceCount } services`;
                title = buildTitle( fieldName, desc );

            } else if ( clusterType == "kubernetes" ) {
                extraClasses = "ft_kubernetes";
                let desc = ""
                if ( fieldTree[ "template-references" ] ) {
                    let serviceCount = Object.keys( fieldTree[ "template-references" ] ).length;
                    desc += ` ${ serviceCount } kubernetes services`;
                }
                title = buildTitle( fieldName, desc );

            } else if ( fieldTree.hosts ) {
                extraClasses = "ft_cluster";
                let hostCount = Object.keys( fieldTree.hosts ).length;
                let desc = `${ hostCount } hosts `;
                if ( fieldTree[ "template-references" ] ) {
                    let serviceCount = Object.keys( fieldTree[ "template-references" ] ).length;
                    desc += ` ${ serviceCount } services`;
                }
                title = buildTitle( fieldName, desc );

            } else if ( fieldName == "application" || fieldName == "project" ) {
                extraClasses = "ft_application";

            } else if ( fieldName == "settings" ) {
                extraClasses = "ft_settings";
                title = buildTitle( fieldName, fieldTree[ "loadbalancer-url" ] );

            } else if ( fieldName == "hosts" ) {
                extraClasses = "ft_hosts";

            } else if ( fieldName == "parameters" ) {
                extraClasses = "ft_settings";

            } else if ( fieldName.toLowerCase().contains( "metrics" ) ) {
                extraClasses = "ft_performance";

            } else if ( fieldName.toLowerCase().contains( "monitor" ) ) {
                extraClasses = "ft_monitor";

            } else if ( !isFolder ) {
                extraClasses = "default";
            }


            ftNodeTree.push( {
                "title": title,
                "folder": isFolder,
                "lazy": isLazy,
                "extraClasses": extraClasses,
                // data fields for rendering
                "fieldName": fieldName,
                "fieldValue": fieldValue,
                "location": fieldLocation
            } );
        }


        return ftNodeTree
    }

    function buildTitle( name, description ) {

        let $title = jQuery( '<div/>', {} );

        jQuery( '<label/>', {
            class: "spacer-name",
            html: name
        } ).appendTo( $title );

        jQuery( '<span/>', {
            class: "spacer-description",
            html: description
        } ).appendTo( $title );

        return $title.html();
    }

    function sortElements( subTree, parentLocation ) {

        console.log( `sortElements() parentLocation: '${ parentLocation }'` );

        let serviceAttributes = new RegExp( "service-templates\..*\..*" ); // strip out . for css

        if ( ( parentLocation === 'project.' )
            || parentLocation.match( serviceAttributes ) ) {
            console.log( "sortElements() skipping sort" );
            return Object.keys( subTree );
        }

        let sortedFields = new Array();
        if ( parentLocation == "" ) {
            // root tree - hard code what is shown
            let defaultFields = [ "application", "project", "environments", "service-templates" ];
            for ( let rootField of defaultFields ) {
                if ( _definition[ rootField ] != null ) {
                    sortedFields.push( rootField );
                }
            }
            return sortedFields;

        }

        // generate fields in sorted order
        for ( let field in subTree ) {

            let fieldLocation = parentLocation + field;
            let fieldTree = jsonForms.getValue( fieldLocation, _definition );
            let sortValue = field;
            if ( !isString( fieldTree ) ) {
                sortValue = "zz" + field;
            }
            if ( field == "settings" ) {
                sortValue = "aaSettings"
            } else if ( field == "defaults" ) {
                sortValue = "aadefaults"
            } else if ( field == "parameters" ) {
                sortValue = "aa" + field
            } else if ( field == "server" ) {
                sortValue = "aaa" + field
            }
            sortedFields.push( { field: field, sortByField: sortValue } );
        }

        sortedFields = sortedFields.sort( fieldSort );
        // console.log("sortedFields", sortedFields) ;

        return sortedFields.map( function ( a ) {
            return a.field
        } );
    }

    function fieldSort( a, b ) {
        //console.log("sorting: " ,a.sortByField ) 
        let x = a.sortByField.toLowerCase(), y = b.sortByField.toLowerCase();
        return x === y ? 0 : x > y ? 1 : -1;
    }
    ;

    function getServerIconStyle( serviceDefinition ) {

        let server = serviceDefinition.server;
        let style = server;

        if ( server.contains( "tomcat" ) ) {
            style = "tomcatEol";
            if ( server.contains( "tomcat8-5" )
                || server.contains( "tomcat9" ) ) {
                style = "tomcatLatest";
            }
        } else if ( server == "csap-api" ) {
            style = "csap_api";
            if ( serviceDefinition.processFilter == "none" ) {
                style = "script";
            }
        } else if ( server == "csap-api" ) {
            style = "csap_api";
            if ( serviceDefinition.processFilter == "none" ) {
                style = "script";
            }
        }

        let metaData = serviceDefinition.metaData;
        if ( metaData ) {
            if ( metaData.contains( "isDataStore" ) ) {
                style = "db";
            }
            if ( metaData.contains( "isJms" ) ) {
                style = "jms";
            }
        }

        return "ft_" + style;
    }

    function isObject( o ) {
        return Object.prototype.toString.call( o ) == '[object Object]';
    }
    function isArray( o ) {
        return Object.prototype.toString.call( o ) == '[object Array]';
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

}