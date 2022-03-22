import _dom from "../utils/dom-utils.js";

const hostSelection = command_hosts();

export default hostSelection


function command_hosts() {

    _dom.logHead( "Module loaded" );

    return {
        
        initialize: function(  ) {
            
            $( "#hostSelection" ).appendTo( $( "#hostButtonTarget" ) ) ;

            $( '#hostSelectButton' ).click( show_dialog ) ;


        }, 

        show: function (  ) {
            
            show_dialog () ;
        }, 
        
        getSelected: function (  ) {
            
            return getSelected () ;
        }, 
        

        
    } ;
    
    
    function show_dialog () {

        if ( !alertify.hostsDialog ) {
            alertify.dialog( 'hostsDialog', dialog_factory, false, 'alert' ) ;
        }

        var targetWidth = Math.round( $( window ).outerWidth( true ) - 100 ) ;
        var maxHeight = Math.round( $( window ).outerHeight( true ) - 100 ) ;


        var dialog = alertify.hostsDialog().show() ;


//		console.log( "parent inner", $( "#hostDialogContainer" ).parent().innerHeight(),
//				"parent height" + $("#hostDialogContainer").parent().height(),
//				"parent scroll height" + $("#hostDialogContainer").parent()[0].scrollHeight,
//				"inner", $("#hostDialogContainer").innerHeight(),
//				"height" + $( "#hostDialogContainer" ).height() );
        var targetHeight = Math.round( $( "#hostDialogContainer" ).parent()[0].scrollHeight + 150 ) ;
        if ( targetHeight > maxHeight ) {
            targetHeight = maxHeight ;
        }
        console.log( "targetHeight:" + targetHeight ) ;
        dialog.resizeTo( targetWidth, targetHeight ) ;


        return false ; // prevent link

    }


    function dialog_factory () {

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
                        }],
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


    function add_hosts_to_dialog () {


//        for ( let clusterName in clusterHostsMap ) {
//            if ( clusterName == "all" )
//                continue ;
//            var optionItem = jQuery( '<option/>', {
//                value: clusterName,
//                text: clusterName
//            } ) ;
//            $( "#selectHostByCluster" ).append( optionItem ) ;
//        }
        
        for ( let packageName in clusterHostsMap ) {

//            var $optGroup = jQuery( '<optgroup/>', {
//                label: packageName
//            } ) ;
//            $( "#selectHostByCluster" ).append( $optGroup ) ;
            
            let clusterHosts = clusterHostsMap[ packageName ] ;
            for ( let clusterName in clusterHosts ) {
                
                if ( clusterHosts[clusterName].length > 0 ) {
                    let $cluster = jQuery( '<option/>', {
                        value: JSON.stringify( clusterHosts[clusterName], ","),
                        text: packageName + ": " + clusterName
                    } );
                     $( "#selectHostByCluster" ).append( $cluster ) ;
                 }
            }
        }

        $( "#selectHostByCluster" ).sortSelect() ;
        $( "#selectHostByCluster" ).selectmenu( {
            width: "30em",
            change: function () {

                let selectHostsAsString = $( "#selectHostByCluster" ).val() ;
                console.log(`selectHostByCluster(): ${ selectHostsAsString }`) ;
                let clusterHosts = JSON.parse( selectHostsAsString ) ;
                
                $( "#selectHostByCluster" ).val( "none" ) ;
                $( "#selectHostByCluster" ).selectmenu( "refresh" ) ;
                
                for ( var i = 0 ; i < clusterHosts.length ; i++ ) {
                    let host = clusterHosts[ i ] ;
                    let id = host + "Check" ;
                    console.log( "selecting: " + id ) ;
                    $( "#" + id ).prop( "checked", true ).trigger( "change" ) ;
                }
            }
        } ) ;

        $( "#hostDisplay" ).empty() ;

        var hostsToInclude = allHostsArray ;
        if ( serviceHostsArray == null || serviceHostsArray.length == 0 ) {
            $( "#serviceHostFilter " ).empty() ;
        }
        if ( $( "#serviceHostFilter input" ).is( ":checked" ) ) {
            hostsToInclude = serviceHostsArray ;
        }

        for ( var i = 0 ; i < hostsToInclude.length ; i++ ) {

            var host = hostsToInclude[i] ;
            var id = host + "Check" ;

            var $checkDiv = jQuery( '<div/>', {class: "hostCustom", title: "Include host in command"} ) ;
            $( "#hostDisplay" ).append( $checkDiv )

            var $hostInput = jQuery( '<input/>', {
                class: "hostCheckbox",
                id: id,
                value: host,
                type: "checkbox"
            } ).appendTo( $checkDiv ) ;

            if ( hostName.contains( host ) ) {
                $hostInput.prop( "checked", true ) ;
                $hostInput.prop( 'disabled', true ) ;
                $checkDiv.attr( "title", "Script host cannot be deselected. Switch to another host if necessary" ) ;
            }

            jQuery( '<label/>', {class: "hostLabel", text: host, for : id} ).appendTo( $checkDiv ) ;


        }



        $( ".hostLabel", $( "input.hostCheckbox:disabled" ).parent() )
                .css( "background-color", "gray")
                .css( "color", "white");

        $( "input.hostCheckbox" ).change( function () {
            
            if ( $(this).is(":disabled") ) {
                $(this).prop( "checked", true ) ;
                return ;
            }
            
            var highlightColor = "" ;

            if ( $( this ).is( ":checked" ) )
                highlightColor = "var(--panel-header-bg)" ;

            // $(".hostLabel", $("input.instanceCheck").parent()).css("background-color", $(".ajs-dialog").css("background-color") ) ;
            $( ".hostLabel", $( this ).parent() ).css(  "background-color", highlightColor ) ;

            var numHosts = $( "#hostDisplay input:checked" ).length ;
            $( "#hostSelectCount" ).text( numHosts ) ;
            if ( numHosts > 1 ) {
                $( "#separateOutput" ).prop( 'checked', false ) ;
            }

        } ) ;

        // Dialog Event binding
        $( '#hostUnCheckAll' ).click( function () {

            $( 'input', "#hostDisplay" ).prop( "checked", false ).trigger( "change" ) ;
            return false ; // prevents link
        } ) ;

        $( '#hostCheckAll' ).click( function () {
            $( 'input', "#hostDisplay" ).prop( "checked", true ).trigger( "change" ) ;
        } ) ;

    }




    function getSelected () {
        var hostsArray = $( 'input.hostCheckbox:checked' ).map( function () {
            return this.value ;
        } ).get() ;

        // default to current host
        if ( hostsArray.length == 0 )
            hostsArray = [hostName] ;


        console.log( "Hosts Selected: " , hostsArray  ) ;

        return hostsArray ;
    }
    
    





}