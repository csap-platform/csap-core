
import _dom from "../../utils/dom-utils.js";



const aboutHost = about_host();

export default aboutHost


function about_host() {

    _dom.logHead( "Module loaded" );

    return {
        show: function ( host ) {
            show( host );
        }
    };

    function show( host ) {
        var paramObject = {
            hostName: host
        };

        var rootUrl = baseUrl;
        try {
            rootUrl = contextUrl;
        } catch ( e ) {
            console.log( "Using baseurl" );
        }

        $.getJSON(
            rootUrl + "os/hostOverview", paramObject )
            .done( function ( response ) {
                buildAbout( response, host );
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Retrieving service instances", errorThrown );
            } );
    }

    function buildAboutLine( label, value ) {
        var $line = jQuery( '<div/>', { class: "aboutline" } );

        jQuery( '<span/>', { class: "label", text: label + ":" } ).appendTo( $line );
        jQuery( '<span/>', { text: value } )
            .css( "font-weight", "bold" )
            .css( "color", "black" )
            .css( "white-space", "pre" )
            .appendTo( $line );

        return $line;
    }

    function buildAbout( loadJson, host ) {
        var $wrap = jQuery( '<div/>', { class: "aWrap" } );

        var $about = jQuery( '<div/>', { class: "info about-host-info" } ).appendTo( $wrap );
        $about.append( buildAboutLine( "Host", host ) );
        $about.append( buildAboutLine( "Version", loadJson.redhat ) );
        $about.append( buildAboutLine( "Uptime", loadJson.uptime ) );
        $about.append( buildAboutLine( "uname", loadJson.uname ) );

        var $dfTable = jQuery( '<table/>', { class: "simple" } ).appendTo( $about );

        var dfLines = loadJson.df.split( "\n" );
        for ( var i = 0; i < dfLines.length; i++ ) {

            var $tableSection = $dfTable;
            var type = '<td/>';
            if ( i == 0 ) {
                $tableSection = jQuery( '<thead/>', {} ).appendTo( $dfTable );
                type = '<th/>';
            }

            var $row = jQuery( '<tr/>', { class: "" } ).appendTo( $tableSection );

            var fields = dfLines[ i ].trim().split( /\s+/ );

            if ( fields.length >= 5 ) {
                for ( var j = 0; j < fields.length; j++ ) {
                    jQuery( type, { text: fields[ j ] } ).appendTo( $row );
                }
            }
        }

        //$about.append( buildAboutLine( "Disk", "df Output\n" + loadJson.df ) ) ;

        alertify.alert( "About " + host, $wrap.html() );

        $( ".alertify" ).css( "width", "800px" );
        $( ".alertify" ).css( "margin-left", "-400px" );
        $( ".awrap" ).css( "text-align", "justify" );
        $( ".awrap" ).css( "white-space", "pre-wrap" );
        $( 'body' ).css( 'cursor', 'default' );

    }
}
