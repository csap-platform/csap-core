import _dom from "../utils/dom-utils.js";

const templates = command_templates();

export default templates


function command_templates() {

    _dom.logHead( "Module loaded" );


    let _command_editor ;



    let commonScriptHeader = '#!/bin/bash\n\n'
            + '# helper functions \n'
            + 'source ' + common_scripts + ' ; \n\n' ;

    let _foldFunction ;

// |(^\s+)|(\s+$)
    //let trimRegEx = new RegExp( "\\t\\s+|^\\s+", "g" ) ;



    return {

        initialize: function ( aceEditor, foldFunction ) {

            _command_editor = aceEditor ;
            _foldFunction = foldFunction ;

            $( '#show-templates-button' ).click( function (  ) {
                show_dialog( _command_editor ) ;
                return false ;
            } ) ;
        },

        show: function (  ) {

            show_dialog() ;
        },

        load: function ( template ) {
            loadTemplate( template ) ;
        },

    }




    function show_dialog() {

        if ( !alertify.osTemplates ) {
            alertify.dialog( 'osTemplates', templateDialogFactory, false, 'alert' ) ;
        }

        let targetWidth = $( window ).outerWidth( true ) - 100 ;
        let targetHeight = $( window ).outerHeight( true ) - 100 ;

        $( "#templateTable tbody" ).css( "height", targetHeight - 200 )

        let dialog = alertify.osTemplates().show() ;

        setTimeout( () => {
            $( "#command-table-filter input" ).css( "background-color", "yellow" ).focus()
        }, 500 ) ;

        dialog.resizeTo( targetWidth, targetHeight )

    }

    function templateDialogFactory() {


        let templateTable = $( "#templatePrompt tbody" ) ;

        templateTable.empty() ;

        for ( let i = 0 ; i < templateArray.length ; i++ ) {
            let currTemplate = templateArray[i] ;
            let nameColumn = jQuery( '<td/>', { text: currTemplate.command } ) ;
            let sourceColumn = jQuery( '<td/>', { text: currTemplate.source } ) ;
            let descColumn = jQuery( '<td/>', { text: currTemplate.description } ) ;
            let templateRow = jQuery( '<tr/>', {
                id: "row" + $( this ).attr( "id" ),
                "data-source": currTemplate.source,
                class: "templateRow"
            } )
                    .append( nameColumn )
                    .append( sourceColumn )
                    .append( descColumn ) ;

            templateTable.append( templateRow )
        }

        templateTable.append( $( "#projectScriptTemplates tbody" ).html() )

        $( ".templateRow" ).click( function () {
            loadTemplate( $( this ).data( "source" ) ) ;
            $( ".ajs-footer button" ).click() ;

        } ) ;


        $( ".fileRow" ).click( function () {
            let fullPath = definitionFolder + "/scripts/" + $( this ).data( "template" ) ;

            let inputMap = {
                fromFolder: fullPath,
                hostName: hostName,
                command: "script"
            } ;
            postAndRemove( "_self", "command", inputMap ) ;

            $( ".scriptSelectionClose" ).click() ;
        } ) ;

//	$( ".runtimeSelect" ).val( $( this ).val() );




        return {
            build: function () {
                this.setContent( this.setContent( $( "#templatePrompt" ).show()[0] ) ) ;
                this.setting( {
                    'onok': function ( closeEvent ) {
                        // console.log( "Submitting Request: "+ JSON.stringify( closeEvent ) );

                    }
                } ) ;
            },
            setup: function () {
                return {
                    buttons: [
                        {
                            text: "Close",
                            className: alertify.defaults.theme.cancel + " scriptSelectionClose",
                            key: 27 // escape key
                        } ],
                    options: {
                        title: "OS Scripts Selection",
                        resizable: true,
                        movable: false,
                        autoReset: false,
                        maximizable: false
                    }
                } ;
            }

        } ;
    }

    function loadTemplate( templateName ) {


        console.log( "templateName", templateName ) ;

        $( "#script-source-name" ).text( templateName ) ;

        $( "#scriptName" ).val( personName + "-" + templateName ) ;


        $.get( "command/template/" + templateName,
                function( templateContents ) {
                    templateTextLoader(templateContents, templateName)
                },
                'text'
                ) ;

    }

    function templateTextLoader( templateContents, templateName ) {
        let regexp = new RegExp( '_file_', 'g' ) ;
        templateContents = templateContents.replace( regexp, defaultLocation ) ;


        if ( pidParam != "" ) {
            regexp = new RegExp( '_pid_', 'g' ) ;
            templateContents = templateContents.replace( regexp, pidParam ) ;
        }

        if ( serviceNameParam != "" ) {
            regexp = new RegExp( '_serviceName_', 'g' ) ;
            templateContents = templateContents.replace( regexp, serviceNameParam ) ;
        }


        if ( serviceNameParam != "" ) {
            regexp = new RegExp( '_serviceName_', 'g' ) ;
            templateContents = templateContents.replace( regexp, serviceNameParam ) ;
        }


        regexp = new RegExp( '_searchText_', 'g' ) ;
        templateContents = templateContents.replace( regexp, searchText ) ;



        let editorContents = commonScriptHeader +  templateContents ;
        if ( templateName.endsWith(".py") ) {
            editorContents = templateContents ;
        }
        $( "#scriptText" ).val( editorContents ) ;

        _command_editor.getSession().setValue( $( "#scriptText" ).val() ) ;

        if ( _foldFunction ) {
            setTimeout( function () {
                _foldFunction() ;
            }, 200 )

        }
    }

}