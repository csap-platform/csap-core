
console.log( "loaded" ) ;

define( [ ], function () {
    console.log( "Module loaded: model" ) ;


    let replaceText = "" ;
    let _editor ;

    return {
        appInit: function ( optionalAceEditor ) {

            if ( optionalAceEditor ) {
                _editor = optionalAceEditor ;
            }
            return appInit() ;
        }
    }


    // note the public method
    function appInit() {
        console.log( "Init" ) ;


        $( '#encodeButton' ).click( function () {

            if ( _editor ) {
                $( "#contents" ).val( _editor.getValue() ) ;
            }
            getDataEncoded() ;
            return false ; // prevents link
        } ) ;
        $( '#decodeButton' ).click( function () {
            if ( _editor ) {
                $( "#contents" ).val( _editor.getValue() ) ;
            }
            getDataDecoded() ;
            return false ; // prevents link
        } ) ;

        $( '#replaceButton' ).hide() ;
        $( '#replaceButton' ).click( function () {
            console.log( `Updating contents` ) ;
            let orig = $( "#contents" ).val() ;
            $( "#contents" ).val( replaceText ) ;
            
            if ( _editor ) {
                _editor.getSession().setValue( replaceText );
            }
            
            replaceText = orig ;
            return false ; // prevents link
        } ) ;

    }
    ;

    function getDataDecoded() {

        $( 'body' ).css( 'cursor', 'wait' ) ;
        $.post( baseUrl + "definition/properties/decode", {
            propertyFileContents: $( "#contents" ).val(),
            customToken: $( "#customToken" ).val()

        } ).done( function ( loadJson ) {
            getDataSuccess( loadJson ) ;

        } ).fail( function ( jqXHR, textStatus, errorThrown ) {
            alertify.alert( "Failed Operation: " + jqXHR.statusText, "Only super users can decode" ) ;
        }, 'json' ) ;
    }

    function getDataEncoded() {

        $( 'body' ).css( 'cursor', 'wait' ) ;
        $.post( baseUrl + "definition/properties/encode", {
            propertyFileContents: $( "#contents" ).val(),
            customToken: $( "#customToken" ).val()
        } )

                .done( function ( loadJson ) {
                    getDataSuccess( loadJson ) ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    // handleConnectionError("Getting Items in DB", errorThrown);
                    alertify.alert( "Failed Operation: " + jqXHR.statusText, "Only admins can generate encoded values" ) ;
                }, 'json' ) ;
    }


    function getDataSuccess( dataJson ) {

        alertify.notify( "Number of lines processed:"
                + ( dataJson.converted.length + dataJson.ignored.length ) ) ;

        let table = $( "#ajaxResults table" ).clone() ;

        for ( let i = 0 ; i < dataJson.converted.length ; i++ ) {

            let recordObj = dataJson.converted[i] ;

            let encValue = recordObj.encrypted ;
            let wrappedValue = recordObj.encrypted ;
            if ( recordObj.decrypted != null ) {
                encValue = recordObj.decrypted ;
            } else {
                if ( encValue.indexOf( "ENC(" ) == -1 ) {
                    wrappedValue = "ENC(" + recordObj.encrypted + ")" ;
                }
            }
            let trContent = '<td style="" class="resp">'
                    + recordObj.key
                    + '</td><td style="" class="resp">'
                    + recordObj.original
                    + '</td><td  style="" class="resp">'
                    + encValue + '</td><td  style="" class="resp">'
                    + wrappedValue + '</td>' ;

            let tr = $( '<tr />', {
                'class': "peter",
                'style': "height: auto;",
                html: trContent
            } ) ;
            $( "tbody", table ).append( tr ) ;
        }

        let message = "Number of lines converted: " + dataJson.converted.length
                + ", of total: " + ( dataJson.converted.length + dataJson.ignored.length ) + "<br><br>" ;
        if ( dataJson.converted.length == 0 ) {
            let trContent = '<td style="padding: 2px;text-align: left">-</td><td style="padding: 2px;text-align: left">No Data Found</td>' ;
            let tr = $( '<tr />', {
                'class': "peter",
                html: trContent
            } ) ;
            $( "tbody", table ).append( tr ) ;
        }

        replaceText = dataJson.updatedContent ;
        $( '#replaceButton' ).show() ;

        // alertify.alert(message + table.clone().wrap('<p>').parent().html());

        let resultsDialog = alertify.alert( message + table.clone().wrap( '<p>' ).parent().html() ) ;

        let targetWidth = $( window ).outerWidth( true ) - 100 ;
        let targetHeight = $( window ).outerHeight( true ) - 100 ;

        resultsDialog.setting( {
            title: "Encryption/Decryption Results",
            resizable: true,
            movable: false,
            width: targetWidth,
            'label': "Close after reviewing output",
            'onok': function () { }


        } ).resizeTo( targetWidth, targetHeight ) ;



        $( 'body' ).css( 'cursor', 'default' ) ;
    }






} ) ;
