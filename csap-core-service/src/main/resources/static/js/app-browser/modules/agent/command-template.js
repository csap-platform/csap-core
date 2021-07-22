define( [ "browser/utils" ], function ( utils ) {

    console.log( "Module loaded" ) ;

 	const $scriptsPanel = $( "#agent-tab-content #agent-tab-script" ) ;

    let _command_editor ;
    let configurationReport ; 

    let CSAP_ENV_FILE = "/opt/csap/csap-platform/bin/csap-environment.sh" ;
    let pidParam = "no-pid-param" ;
    let serviceNameParam = "no-service-param" ;
    let searchTextParam ="no-search-text-param" ;



    const commonScriptHeader = '#!/bin/bash\n\n'
            + '# helper functions \n'
            + 'source ' + CSAP_ENV_FILE + ' ; \n\n' ;

    let _foldFunction ;

// |(^\s+)|(\s+$)
    //let trimRegEx = new RegExp( "\\t\\s+|^\\s+", "g" ) ;



    return {

        initialize: function ( XconfigurationReport, aceEditor, foldFunction ) {
            configurationReport = XconfigurationReport ;

             init( aceEditor, foldFunction ) ;
        },

        show: function (  ) {

            show_dialog() ;
        },

        load: function ( template ) {
            return loadTemplate( template ) ;
        },

    }

    function init( aceEditor, foldFunction ) {

        _command_editor = aceEditor ;
        _foldFunction = foldFunction ;

        if ( utils.getPageParameters().has( "pid" ) ) {
            pidParam = utils.getPageParameters().get( "pid" ) ;
        }
        if ( utils.getPageParameters().has( "serviceName" ) ) {
            serviceNameParam = utils.getPageParameters().get( "serviceName" ) ;
        }
        if ( utils.getPageParameters().has( "searchText" ) ) {
            searchTextParam = utils.getPageParameters().get( "searchText" ) ;
        }
        
        
        console.log( `serviceNameParam: ${ serviceNameParam }, pidParam: ${ pidParam }` ) ;

        $( '#show-templates-button' ).click( function (  ) {
            show_dialog( _command_editor ) ;
            return false ;
        } ) ;
    }
    ;




    function show_dialog() {


        if ( !alertify.osTemplates ) {
            isNewDialog = true ;
            alertify.dialog( 'osTemplates', templateDialogFactory, false, 'alert' ) ;
        }

        let targetWidth = $( window ).outerWidth( true ) - 100 ;
        let targetHeight = $( window ).outerHeight( true ) - 100 ;

        let dialog = alertify.osTemplates().show() ;

        setTimeout( () => {
            $( "#command-table-filter input" ).css( "background-color", "yellow" ).focus()
        }, 500 ) ;

        dialog.resizeTo( targetWidth, targetHeight )

    }

    function templateDialogFactory() {
        
        console.log( `building template dialog`) ;


        let $templateTable = $( "#templatePrompt tbody" ) ;

        $templateTable.empty() ;

        for ( let scriptDefinition of configurationReport.templateArray ) {
            let nameColumn = jQuery( '<td/>', { text: scriptDefinition.command } ) ;
            let sourceColumn = jQuery( '<td/>', { text: scriptDefinition.source } ) ;
            let descColumn = jQuery( '<td/>', { text: scriptDefinition.description } ) ;
            
            
            let templateRow = jQuery( '<tr/>', {
                "data-source": scriptDefinition.source
            } )
                    .append( nameColumn )
                    .append( sourceColumn )
                    .append( descColumn ) ;

            $templateTable.append( templateRow )
        }

        $( "tr", $templateTable ).click( function () {
            loadTemplate( $( this ).data( "source" ) ) ;
            $( ".ajs-footer button" ).click() ;

        } ) ;


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

        $( "#script-source-name",$scriptsPanel ).text( templateName ) ;

        $( "#scriptName",$scriptsPanel ).val( utils.getCsapUser() + "-" + templateName ) ;
        $( "#scriptName",$scriptsPanel ).trigger("change") ;


        $.get( utils.getOsUrl( ) + "/command/template/" + templateName,
                function ( templateContents ) {
                    templateTextLoader( templateContents, templateName )
                },
                'text'
                ) ;

    }

    function templateTextLoader( templateContents, templateName ) {
        let regexp = new RegExp( '_file_', 'g' ) ;
        let location = configurationReport.defaultLocation ;
        templateContents = templateContents.replace( regexp, location ) ;


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
        templateContents = templateContents.replace( regexp, searchTextParam ) ;



        let editorContents = templateContents ;
        if ( templateName.endsWith( ".sh" ) ) {
            editorContents = commonScriptHeader + templateContents ;
        }
        $( "#scriptText" ).val( editorContents ) ;

        _command_editor.getSession().setValue( $( "#scriptText" ).val(), -1 ) ;

        if ( _foldFunction ) {
            setTimeout( function () {
                _foldFunction() ;
            }, 500 )

        }
    }

} ) ;