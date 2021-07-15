// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
    
    waitSeconds: 30,
    
    paths: {
        jsYaml: BASE_URL + "webjars/js-yaml/3.14.0/dist",
        ace: BASE_URL + "webjars/ace-builds/1.4.11/src",
        misc: "../../misc/modules"
    }
} ) ;


// "ace/ext-modelist" "../../../testMode"
require( ["ace/ace", "ace/ext-modelist", "misc/property-encoder"], function ( aceEditor, aceModeListLoader, csapEncoder ) {

    console.log( "\n\n ************** _main: loaded x*** \n\n" ) ;

    let _showRootMessage = true ;

    let aceEditorId = "ace-editor" ;

    let _resizeTimer ;

    let _editor ;

    $( document ).ready( function () {
        
        initialize() ;
    } ) ;

    this.initialize = initialize ;
    function initialize () {

        setTimeout( editorInit, 100 ) ;
        //editorInit() ;
        if ( FE_SETTINGS.rootFile != null ) {
            $( "#chownUserid" ).val( "root" ) ;
            alertify.alert( "Restriced Access Warning", "The host operating system is restricting access to selected file. Options:" +
                    "<br/> - use csap shell to change permisssions; or " +
                    "<br/> - use csap editor to save modifications - carefully review prior to saving" +
                    '<br/><div class="options"> User is set to ROOT.' +
                    "<br/>Changing to non-root is permitted - but note some OS files are required to be owned by root.</div>" ) ;
        }
    }

    function determineAceExtension ( theFile ) {
        let modelist = aceEditor.require( "ace/ext/modelist" ) ;
        let testFileForExtension = theFile ;

        if ( theFile.endsWith( ".jmx" ) ) {
            testFileForExtension = "assumingJmxIsXml.xml" ;
        } else if ( theFile.endsWith( ".jsonnet" ) ) {
            testFileForExtension = "assumingJmxIsXml.json5" ;
        }


        let fileMode = modelist.getModeForPath( testFileForExtension ).mode ;
        console.log( "testFileForExtension : ", testFileForExtension, "fileMode: ", fileMode ) ;
        
        return fileMode ;
    }

    function editorInit () {


        CsapCommon.configureCsapAlertify() ;

        _editor = aceEditor.edit( aceEditorId ) ;

//        let resize_function = function () {
//            let displayHeight = $( window ).outerHeight( true ) - $( "header" ).outerHeight( true ) ;
//            $( "#" + aceEditorId ).css( "height", displayHeight ) ;
//            //_editor.resize() ;
//        } ;
//        resize_function() ;
//        $( window ).resize( function () {
//            clearTimeout( _resizeTimer ) ;
//            _resizeTimer = setTimeout( resize_function, 200 ) ;
//        } ) ;

        $( "#" + aceEditorId ).show() ;

        _editor.setOptions( {
            mode: determineAceExtension( FE_SETTINGS.fromFolder),
            theme: "ace/theme/textmate",
            newLineMode: "unix",
            tabSize: 2,
            fontSize: "11pt",
            useSoftTabs: true,
            wrap: true
        } ) ;

        console.log( "initializing for property encoder" ) ;
        $( "#contents" ).val( _editor.getValue() ) ;

        $( "#save-file-button" ).fadeTo( "fast", 0.3 ) ;
        _editor.getSession().on( 'change', function () {
            $( "#contents" ).val( _editor.getValue() ) ;
            $( "#save-file-button" ).fadeTo( "medium", 1 ) ;
            console.log( "content updated" ) ;
            //document.getElementById("output").innerHTML=editor.getValue();
        } ) ;

        $( "#ace-theme-select" ).change( function () {
            _editor.setTheme( $( this ).val() ) ;
        } ) ;

        let $aceFoldCheckbox = $( "#ace-fold-checkbox" ) ;
        $aceFoldCheckbox.change( function () {
            if ( $( this ).is( ':checked' ) ) {
                _editor.getSession().foldAll( 2 ) ;
            } else {
                //_yamlEditor.getSession().unfoldAll( 2 ) ;
                _editor.getSession().unfold( ) ;
            }
        } )

        $( '#ace-wrap-checkbox' ).change( function () {
            if ( $( "#ace-wrap-checkbox" ).prop( 'checked' ) ) {
                _editor.session.setUseWrapMode( true ) ;

            } else {
                _editor.session.setUseWrapMode( false ) ;
            }
        } ) ;


        $( "#save-file-button" ).click( function () {
            $( "#contents" ).val( _editor.getValue() ) ;
            if ( $( "#chownUserid" ).val() == "root" ) {

                let newItemDialog = alertify.confirm( "Validate your content carefully<br><br>In case of errors, submitting root level requests require cases to be opened to recover VM." ) ;

                newItemDialog.setting( {
                    title: 'Caution: Root user speciied',
                    'labels': {
                        ok: 'Execute',
                        cancel: 'Cancel Request'
                    },
                    'onok': function () {
                        alertify.success( "Submitting Request" ) ;
                        $( "#editForm" ).submit() ;

                    },
                    'oncancel': function () {
                        alertify.warning( "Cancelled Request" ) ;
                    }

                } ) ;

            } else {
                $( "#editForm" ).submit() ;
            }
        } ) ;

        $( '#sync-file-button' ).click( function () {

            let trimmedCommand = "sync" ;
            console.log( "Invoking: " + trimmedCommand ) ;
            // fileCommand(trimmedCommand) ;
            let inputMap = {
                fromFolder: FE_SETTINGS.fromFolder,
                serviceName: FE_SETTINGS.serviceName,
                hostName: CSAP_HOST_NAME,
                command: trimmedCommand
            } ;
            postAndRemove( "sync" + FE_SETTINGS.fromFolder, commandScreen, inputMap ) ;

            return false ; // prevents link
        } ) ;


        if ( hasResults ) {
            console.log( "Found results" ) ;
//            let message = "" ;
//            for ( let i = 0 ; i < saveResult.length ; i++ ) {
//                message += saveResult[i] + "<br><br>" ;
//            }

            let $message = jQuery( '<div/>', { } ) ;
            
            jQuery( '<div/>', {
                class: "settings",
                text: saveResult["plain-text"]
            } )
                    .css("font-size", "12pt")
                    .css("white-space", "pre-wrap")
                    .css("margin-top", "2em")
                    .css("margin-bottom", "2em")
                    .css("font-weight", "bold")
                    .appendTo( $message ) ;
            alertify.alert( "Save results", $message.html() ) ;
        }

        let message = "Note: Use of editor should be restricted to non-prod hosts during development.<br><br>" +
                "All configuration files should adhere to SCM practices by using SVN, GIT, etc. for production deployments" ;
        //	alertify.csapWarning(message) ;
        
        
        csapEncoder.appInit( _editor ) ;

    }


} ) ;