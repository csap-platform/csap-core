define( [ ], function () {
    console.log( "module loaded" ) ;


    let agent ;


    return {
        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            console.log( `loading agent` ) ;


            if ( agent ) {
                return agent.show( $menuContent, forceHostRefresh, menuPath ) ;
            }


            let $menuLoaded = new $.Deferred() ;
            require( [ "agent/agent" ], function ( theAgent ) {

                agent = theAgent ;
                $.when( agent.show( $menuContent, forceHostRefresh, menuPath ) ).then( function () {
                    $menuLoaded.resolve() ;
                } ) ;
            } ) ;

            return $menuLoaded ;

        }
    } ;

} ) ;