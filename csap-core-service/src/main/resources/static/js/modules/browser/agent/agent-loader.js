
import _dom from "../../utils/dom-utils.js";



const agent = agent_loader();

export default agent


function agent_loader() {

    _dom.logHead( "Module loaded" );


    // let agent ;


    return {
        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            _dom.logHead( "loading agent views" );

            // let loadedAgent;

            //let agent ; 
            async function afterLoading( resolve, reject ) {
                const { agent } = await import( './agent.js' );

                _dom.logArrow( "agent loaded - going to delay" );

                return agent.show( $menuContent, forceHostRefresh, menuPath )
                    // .then( () => resolve( "loaded message" ) ) ; 

            }

            let $menuLoaded = afterLoading()  ;

            return $menuLoaded;

        }
    };

}