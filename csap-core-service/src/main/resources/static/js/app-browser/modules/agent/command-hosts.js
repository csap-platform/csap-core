define( [ "browser/utils" ], function ( utils ) {

    console.log( "Module loaded" ) ;

    const $scriptsPanel = $( "#agent-tab-content #agent-tab-script" ) ;
    const $hostButtonTarget = $( "#hostButtonTarget", $scriptsPanel ) ;
    const $hostSelectButton = $( "#hostSelection", $scriptsPanel ) ;

    let configurationReport ;

    return {

        initialize: function ( XconfigurationReport ) {

            configurationReport = XconfigurationReport


            $hostButtonTarget.empty() ;
            $hostSelectButton.appendTo( $hostButtonTarget ) ;

            $( '#hostSelectButton' ).click( show_dialog ) ;


        },

        show: function (  ) {

            show_dialog() ;
        },

        getSelected: function (  ) {

            return getSelected() ;
        },

    } ;


    function show_dialog() {

        if ( !alertify.hostsDialog ) {
            isNewDialog = true ;
            alertify.dialog( 'hostsDialog', dialog_factory, false, 'alert' ) ;
        }

        let targetWidth = Math.round( $( window ).outerWidth( true ) - 100 ) ;
        let maxHeight = Math.round( $( window ).outerHeight( true ) - 100 ) ;


        let dialog = alertify.hostsDialog().show() ;


//		console.log( "parent inner", $( "#hostDialogContainer" ).parent().innerHeight(),
//				"parent height" + $("#hostDialogContainer").parent().height(),
//				"parent scroll height" + $("#hostDialogContainer").parent()[0].scrollHeight,
//				"inner", $("#hostDialogContainer").innerHeight(),
//				"height" + $( "#hostDialogContainer" ).height() );
        let targetHeight = Math.round( $( "#hostDialogContainer" ).parent()[0].scrollHeight + 150 ) ;
        if ( targetHeight > maxHeight ) {
            targetHeight = maxHeight ;
        }
        console.log( "targetHeight:" + targetHeight ) ;
        dialog.resizeTo( targetWidth, targetHeight ) ;


        return false ; // prevent link

    }


    function dialog_factory() {

        add_hosts_to_dialog() ;

        $( "#serviceHostFilter input" ).change( function () {
            add_hosts_to_dialog() ;
        } )
        return {
            build: function () {
                this.setContent( this.setContent( $( "#hostDialogContainer" ).show()[0] ) ) ;
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
                        title: "Select Host(s) for commands",
                        resizable: true,
                        movable: false,
                        autoReset: false,
                        maximizable: false
                    }
                } ;
            }

        } ;
    }


    function add_hosts_to_dialog() {

//   
        for ( let packageName in configurationReport.clusterHostsMap ) {

//            let $optGroup = jQuery( '<optgroup/>', {
//                label: packageName
//            } ) ;
//            $( "#selectHostByCluster" ).append( $optGroup ) ;

            let clusterHosts = configurationReport.clusterHostsMap[ packageName ] ;
            for ( let clusterName in clusterHosts ) {

                if ( clusterHosts[clusterName].length > 0 ) {
                    let $cluster = jQuery( '<option/>', {
                        value: JSON.stringify( clusterHosts[clusterName], "," ),
                        text: packageName + ": " + clusterName
                    } ) ;
                    $( "#selectHostByCluster" ).append( $cluster ) ;
                }
            }
        }

        $( "#selectHostByCluster" ).sortSelect() ;
        $( "#selectHostByCluster" ).selectmenu( {
            width: "30em",
            change: function () {

                let selectHostsAsString = $( "#selectHostByCluster" ).val() ;
                console.log( `selectHostByCluster(): ${ selectHostsAsString }` ) ;
                let clusterHosts = JSON.parse( selectHostsAsString ) ;

                $( "#selectHostByCluster" ).val( "none" ) ;
                $( "#selectHostByCluster" ).selectmenu( "refresh" ) ;

                for ( let i = 0 ; i < clusterHosts.length ; i++ ) {
                    let host = clusterHosts[ i ] ;
                    let id = host + "Check" ;
                    console.log( "selecting: " + id ) ;
                    $( "#" + id ).prop( "checked", true ).trigger( "change" ) ;
                }
            }
        } ) ;

        $( "#hostDisplay" ).empty() ;

        let hostsToInclude = configurationReport.allHosts ;
        if ( configurationReport.serviceHosts == null || configurationReport.serviceHosts.length == 0 ) {
            $( "#serviceHostFilter " ).empty() ;
        }
        if ( $( "#serviceHostFilter input" ).is( ":checked" ) ) {
            hostsToInclude = configurationReport.serviceHosts ;
        }

        for ( let i = 0 ; i < hostsToInclude.length ; i++ ) {

            let host = hostsToInclude[i] ;
            let id = host + "Check" ;

            let $checkDiv = jQuery( '<div/>', { class: "hostCustom", title: "Include host in command" } ) ;
            $( "#hostDisplay" ).append( $checkDiv )

            let $hostInput = jQuery( '<input/>', {
                class: "hostCheckbox",
                id: id,
                value: host,
                type: "checkbox"
            } ).appendTo( $checkDiv ) ;

            if ( utils.getHostName().contains( host ) ) {
                $hostInput.prop( "checked", true ) ;
                $hostInput.prop( 'disabled', true ) ;
                $checkDiv.attr( "title", "Script host cannot be deselected. Switch to another host if necessary" ) ;
            }

            jQuery( '<label/>', { class: "csap hostLabel", text: host, for : id } ).appendTo( $checkDiv ) ;


        }



        $( "input.hostCheckbox:disabled", $scriptsPanel ).parent() 
                .css( "background-color", "gray")
                .css( "color", "white");

        $( "input.hostCheckbox", $scriptsPanel ).change( function () {
            
            if ( $(this).is(":disabled") ) {
                
                $(this).prop( "checked", true ) ;
                return ;
            }
            
            let $dialogPanel = $(this).closest("#hostDisplay") ;
            let highlightColor = "" ;

            if ( $( this ).is( ":checked" ) ) {
                highlightColor = "var(--nav-hover-background)" ;
            }

            // $(".hostLabel", $("input.instanceCheck").parent()).css("background-color", $(".ajs-dialog").css("background-color") ) ;
            $( ".hostLabel", $( this ).parent() ).css( "background-color", highlightColor ) ;

            let numHosts = $( "input:checked", $dialogPanel ).length ;
            $( "#hostSelectCount", $scriptsPanel ).text( numHosts ) ;
            if ( numHosts > 1 ) {
                $( "#separateOutput", $scriptsPanel ).prop( 'checked', false ) ;
            }

        } ) ;

        // Dialog Event binding
        $( '#hostUnCheckAll', $scriptsPanel ).click( function () {

            $( 'input',  $(this).parent().parent() ).prop( "checked", false ).trigger( "change" ) ;
            $( `input#${ utils.getHostName() }Check`, $(this).parent().parent() ).prop( "checked", true ).trigger( "change" ) ;
            return false ; // prevents link
        } ) ;

        $( '#hostCheckAll', $scriptsPanel ).click( function () {
            console.log( `checking all in ${ $(this).parent().parent().attr("id")  } `) ;
            $( 'input', $(this).parent().parent() ).prop( "checked", true ).trigger( "change" ) ;
            return false;
        } ) ;

    }




    function getSelected() {
        let hostsArray = $( 'input.hostCheckbox:checked' ).map( function () {
            return this.value ;
        } ).get() ;

        // default to current host
        if ( hostsArray.length == 0 ) {
            hostsArray = [ utils.getHostName() ] ;
        }


        console.log( "Hosts Selected: " + JSON.stringify( hostsArray ) ) ;

        return hostsArray ;
    }







} ) ;