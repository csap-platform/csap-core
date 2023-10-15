

// import "../../../../webjars/jquery/3.6.0/jquery.min.js";
// import "../utils/legacy-globals.js";

// jquery lib, and defaults, and alertify
import "../libs/csap-modules.js";

import _dom from "./dom-utils.js";


_dom.logHead( "Module loaded" );



const loc = window.location;
//let myBaseUrl = loc.protocol + "//" + loc.hostname + (loc.port? ":"+loc.port : "") + "/" ;
let csapUserApiUrl = "https://" + loc.hostname + ":8013/api/application/";
let companyDir = "https://mysites.yourcompany.net/personal/"

const $userNamesButton = $( "#test-get-user-names" );

_dom.onReady( function () {

    if ( $userNamesButton.length > 0 ) {
        setTimeout( () => {

            _dom.logSection( `loading test driver` );

            testSetup();
        }, 500 )
    }


} );

export default function CsapUser() {

    // Hooks for testing off of the lb urls
    //	if ( document.URL.indexOf( "yourcompany.com/" ) == -1 )
    //		apiUrl = "https://csap-secure.yourcompany.com/admin/api/application/";
    //	if ( document.URL.indexOf( "CsAgent:8011" ) != -1 )
    //		apiUrl = "/CsAgent/api/application/";

    //let apiBase=getParameterByName("apiBase") ;

    let currentUrl = document.URL;
    
    if ( getParameterByName( "apiUrl" ) != "" ) {
        
        csapUserApiUrl = getParameterByName( "apiUrl" );
        _dom.logArrow( `updated apiUrl: ${ csapUserApiUrl }` );

    } else {
        if ( currentUrl.includes( "csap-admin" ) ) {
            let adminBase = currentUrl.split( "csap-admin" )[ 0 ];
            csapUserApiUrl = `${ adminBase }csap-admin/api/application/`;
            console.log( `csapUserApiUrl: '${ csapUserApiUrl }' ` );

        } else if ( currentUrl.includes( "8011" ) ) {
            let adminBase = currentUrl.split( "8011" )[ 0 ];
            csapUserApiUrl = `${ adminBase }8011/api/application/`;
            console.log( `csapUserApiUrl: '${ csapUserApiUrl }' ` );
        }
    }


    if ( getParameterByName( "dir" ) != "" ) {
        companyDir = getParameterByName( "dir" );
    }


    console.log( `csapUserApiUrl: '${ csapUserApiUrl }' \n current url '${ currentUrl }' ` );




    let that = this;   // this is temp

    this.getUserNames = function ( useridArray, callBackFunction ) {

        let requestParms = {
            "userid": useridArray
        };

        _dom.logHead( ` getting user names from ${ csapUserApiUrl + "userNames" } ` );

        $.getJSON(
            csapUserApiUrl + "userNames",
            requestParms )

            .done( function ( userJson ) {
                getUserNamesSuccess( userJson, callBackFunction );
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Service names", errorThrown );
            } );
    };


    this.getUserNamesSuccess = getUserNamesSuccess;
    function getUserNamesSuccess( userJson, callBackFunction ) {
        //console.debug("CsapUser.getUserNamesSuccess: " + JSON.stringify(userJson ));

        // Invoke callback
        callBackFunction( userJson );

    }



    this.getUserInfo = function ( userid, callBackFunction ) {

        let requestParms = {};

        $.getJSON(
            csapUserApiUrl + "access/" + userid,
            requestParms )

            .done( function ( userJson ) {
                getUserInfoSuccess( userJson, callBackFunction );
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {

                handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown );
            } );
    };

    this.getParameterByName = getParameterByName;
    function getParameterByName( name ) {
        name = name.replace( /[\[]/, "\\\[" ).replace( /[\]]/, "\\\]" );
        let regexS = "[\\?&]" + name + "=([^&#]*)",
            regex = new RegExp( regexS ),
            results = regex.exec( window.location.href );
        if ( results == null ) {
            return "";
        } else {
            return decodeURIComponent( results[ 1 ].replace( /\+/g, " " ) );
        }
    }

    this.getUserInfoSuccess = getUserInfoSuccess;
    function getUserInfoSuccess( userJson, callBackFunction ) {
        console.debug( "CsapUser.getUserInfoSuccess: " );

        // Invoke callback

        if ( callBackFunction != null ) {
            console.log( "Invoking callback" );
            callBackFunction( userJson );
            return;
        }

        if ( typeof alertify != 'undefined' ) {

            // construct element
            let containerJQ = jQuery( '<div/>', {} );

            getUserContainer( userJson.data, true ).appendTo( containerJQ );

            alertify.alert( containerJQ.html() );
            $( ".alertify-inner" ).css( "text-align", "left" );
            $( ".alertify" ).css( "width", "600px" );
            $( ".alertify" ).css( "margin-left", "-300px" );

        } else
            alert( JSON.stringify( userJson ) );

    }

    function getUserDirLink( userJson ) {

        console.log( "getUserDirLink() : looking in mail2" );
        let link = null;
        if ( userJson.mail ) {
            let key = userJson.mail.split( "@" )[ 0 ];
            key = key.replace( "\.", "_" );
            console.log( "key: " + key );
            link = companyDir + key;
        }

        return link;
    }

    function getUserContainer( userJson, isIncludeLinks ) {
        // construct element

        let section = jQuery( '<section/>', { id: "csapUser" } );

        if ( !userJson ) {

            section.append( "Verify directory configuration - no information for for user" );
            return section;
        }

        let title = userJson.title;
        if ( title == null ) {
            title = ""
        }


        jQuery( '<header/>', { style: "margin-bottom: 0.5em" } )
            .html( userJson.name
                + ' <span style="display:inline-block; padding-left: 3em">' +
                title + '</span>' )
            .appendTo( section );

        section.append( "<br>" );
        jQuery( '<span/>', { class: "cLabel" } ).text( "userid: " ).appendTo( section );
        jQuery( '<span/>', { class: "value" } ).text( userJson.userid ).appendTo( section );

        jQuery( '<span/>', { class: "cLabel" } ).text( "Email: " ).appendTo( section );
        jQuery( '<span/>', { class: "value" } ).text( userJson.email ).appendTo( section );

        section.append( "<br>" );

        jQuery( '<span/>', { class: "cLabel" } ).text( "Phone: " ).appendTo( section );
        jQuery( '<span/>', { class: "value" } ).text( userJson.telephoneNumber ).appendTo( section );

        jQuery( '<span/>', { class: "cLabel" } ).text( "Manager: " ).appendTo( section );
        jQuery( '<span/>', { class: "value" } ).text( userJson.manager ).appendTo( section );
        section.append( "<br><br>" );


        jQuery( '<span/>', { class: "cLabel" } ).text( "Roles: " ).appendTo( section );

        let roles = jQuery( '<span/>', { class: "value" } ).css( "width", "auto" );
        for ( let role of userJson[ "csap-roles" ] ) {
            //console.log(`role: ${ role }`) ;
            roles.append( jQuery( '<span/>', { text: role } ).css( "padding-right", "2em" ) );
        }

        section.append( roles );
        section.append( "<br>" );
        if ( true ) {

        } else {
            if ( isIncludeLinks ) {
                section.append( "<br>" );
                jQuery( '<span/>', { class: "cLabel" } ).text( "Link: " ).appendTo( section );
                let userLookupLink = getUserDirLink( userJson );
                jQuery( '<span/>', { class: "value" } ).append(
                    jQuery( '<a/>', {
                        class: 'simple',
                        href: userLookupLink,
                        text: userJson.userid,
                        target: '_blank',
                    } ).css( "display", "inline" ) ).appendTo( section );

                jQuery( '<span/>', { class: "cLabel" } ).text( "Manager: " ).appendTo( section );

                let managerLookupLink = companyDir + userJson.manager;
                managerLookupLink = managerLookupLink.replace( " ", "_" ); // leverage user
                jQuery( '<a/>', {
                    class: 'simple',
                    href: managerLookupLink,
                    text: userJson.manager,
                    target: '_blank',
                } ).css( "display", "inline" ).appendTo( section );


            } else {

                section.append( "<br>" );
                jQuery( '<div/>', {
                    style: "font-style: italic;font-weight: bold;margin-top:0.5em"
                } )
                    .text( "Click on userid to launch company directory" )
                    .appendTo( section );
            }
        }

        section.append( "<br>" );

        let location = userJson.address;
        if ( location && location.country ) {
            jQuery( '<div/>', { class: "info" } ).text( JSON.stringify( location, null, "\n" ) ).appendTo( section );
        }

        // jQuery('<div/>', {	class:"note"}).text( JSON.stringify(userJson ) ).appendTo(containerJQ) ;

        return section;
    }

    let hoverTimer = 0;
    this.onHover = onHover;
    function onHover( selectorJQ, delay ) {

        // unregister previous events
        selectorJQ.off();

        //

        selectorJQ.each( function ( index, value ) {
            // alert( index + ": " + value );
            // $(this).text("peter") ;

            let $userInfo = $( this );
            let userid = $userInfo.text();

            if ( userid != "System" && userid != "csap-agent" ) {
                if ( $( "a", $userInfo ).length == 0 ) {
                    // add link
                    let $directoryLink = jQuery( '<a/>', {
                        class: 'simple launch_' + userid,
                        href: "#launchDir",
                        text: userid,
                        target: '_blank',
                    } ).css( "padding-right", "1em" )

                    $userInfo.html( $directoryLink );


                    $directoryLink.click( function () {
                        let linkTarget = $( this ).attr( "href" );
                        console.log( "checking link target: ", linkTarget );
                        if ( linkTarget.indexOf( "#launchDir" ) != -1 ) {
                            alertify.notify( "wait for pop up and then click again" );
                            return false;
                        } else {
                            console.log( "Launching with: ", linkTarget );
                            CsapCommon.openWindowSafely( linkTarget, "directoryLookup" );
                        }
                        return false;
                    } )

                    $userInfo.hover(
                        function () {
                            hoverTimer = setTimeout( function () {
                                $_lastUserInfo = $userInfo;
                                that.getUserInfo( userid, showPopUp );
                            }, delay );
                        },
                        function () {
                            clearTimeout( hoverTimer );
                            $( ".csapUserPopup" ).remove();
                            //useridCellJQ.text( useridCellJQ.text().toLowerCase() )  ;
                        } );
                }
            }
        } );
    }

    let $_lastUserInfo = null;
    function showPopUp( userJson ) {
        $( ".launch_" + userJson.userid )
            .attr( "href",
                getUserDirLink( userJson ) );

        //console.info( "CsapUser.onHover.showPopUp: ", $_lastUserInfo.offset().top, userJson );
        //console.log(lastCell_JQ.text() ) ;
        let $userInfoPanel = jQuery( '<div/>', {
            style: "position: absolute; width: 35em; top: 10px",
            class: "csapUserPopup"
        } );
        getUserContainer( userJson.data, false ).appendTo( $userInfoPanel );
        $userInfoPanel.hide();
        $( "body" ).append( $userInfoPanel );
        //$userInfoPanel.position( { my: "left bottom", at: "right top", of: $_lastUserInfo } );
        let panelTop = Math.round( ( $_lastUserInfo.offset().top ) - 200 );
        let panelLeft = Math.round( $_lastUserInfo.offset().left + 100 );
        //console.log("panelTop: " + panelTop + " panelLeft: " + panelLeft)
        $userInfoPanel.offset(
            {
                top: panelTop,
                left: panelLeft
            } );
        //$userInfoPanel.offset( { top: 10 , left: 500}  );
        $userInfoPanel.show();

    }
    ;


    this.handleConnectionError = handleConnectionError;
    function handleConnectionError( command, errorThrown ) {
        let message = "Failed command: " + command;
        message += "\n\n Server Message:" + errorThrown;

        console.log( message );
    }

    // $.ajaxSetup( {
    //     cache: false,
    //     traditional: true
    //             // traditional is critical for servlet based param processing. 
    //             //		timeout : deployTimeout, never timeout
    // } ) ;





}

function testSetup() {

    let csapUser = new CsapUser();

    console.log( "Registering event listeners" );

    $userNamesButton.click(
        function () {
            csapUser.getUserNames( $( "#userids" ).val()
                .split( "," ), sampleCallback );
            return false; // prevents link
        } );

    $( "#getUserInfo" ).click(
        function () {
            csapUser.getUserInfo( $( "#userid" ).val()
                .trim(), sampleCallback );
            return false; // prevents link
        } );

    $( "#getUserInfoHtml" ).click(
        function () {
            csapUser.getUserInfo(
                $( "#useridHtml" ).val().trim(),
                null );
            return false; // prevents link
        } );

    $( "#updateUserCol" )
        .click(
            function () {
                var delay = 500;
                csapUser
                    .onHover(
                        $( "table tbody tr td:nth-child(2)" ),
                        delay );
                return false; // prevents link
            } );

    $( "button" ).hover( function () {
        $( this ).css( "text-decoration", "underline" );
        $( this ).css( 'cursor', 'pointer' );
    }, function () {
        $( this ).css( "text-decoration", "none" );
        $( this ).css( 'cursor', 'default' );
    } );


    function sampleCallback( resultsJson ) {
        console.log( "sampleCallback: " + JSON.stringify( resultsJson ) );
        $( "#api-results-panel" ).empty();
        $.each( resultsJson, function ( index, value ) {
            // alert( index + ": " + value );
            $( "#api-results-panel" ).append( "userid: " + index + " name:" + value + "<br/>" )
        } );
    }


}
