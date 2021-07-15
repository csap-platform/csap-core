define( [ ], function () {

    console.log( "Module loaded" ) ;

    let hackLengthForTesting = -1 ;

    return {
        //
        processValidationResults: function ( parsedJson ) {
            return processValidationResults( parsedJson ) ;
        }
    }

    function processValidationResults( parsedJson ) {

        // Clear previous errors from text area
        $( ".errorIcon" ).removeClass( "errorIcon" ) ;

        let resultsContainer = jQuery( '<div/>', {
            id: "parseResults"
        } ).css( "font-size", "1.2em" ) ;

        let resultsClass = "status-green" ;
        let resultsText = "Parsing Successful" ;


        if ( !parsedJson.success ) {
            resultsClass = "status-red" ;
            resultsText = "Parsing Failed" ;
        }

        jQuery( '<span/>', {
			class: resultsClass,
            text: resultsText
        } ).css( "height", "1.2em" ).appendTo( resultsContainer ) ;


        resultsContainer.append( "<br>" ) ;

        if ( parsedJson.errors && parsedJson.errors.length > 0 ) {

            let errorsObj = jQuery( '<div/>', {
                class: "warning"
            } ).text( "Errors: " ).css( {
                "overflow-y": "auto"
            } ) ;
            let listJQ = jQuery( '<ol/>', {
                class: "error"
            } ) ;
            for ( let i = 0 ; i < parsedJson.errors.length ; i++ ) {

                // 2 scenarios: a parsing error with a line number, and a semantic
                // error with just contents
                $( ".textWarning" ).html( "Found some Errors<br> Run validator to view" ).show() ;
                let error = parsedJson.errors[i] ;
                let errorMessage = parsedJson.errors[i] ;
                if ( error.line ) {
                    console.log( "Found error: " + error.line ) ;
                    errorMessage = '<span style="font-weight: bold"> Line: ' + error.line + "</span> Message: <br>"
                            + error.message ;
                    // $(".line" + error.line).addClass("errorIcon");
                    $( '.lineno:contains("' + error.line + '")' ).addClass( "errorIcon" ) ;
                    $( ".errorIcon" ).qtip( {
                        content: {
                            title: "Error Information",
                            text: errorMessage
                        }
                    } ) ;
                } else {
                    errorMessage = JSON.stringify( error, null, "\t" ) ;
                    errorMessage = errorMessage.replace( "__ERROR", "Error" )
                }
                ;

                jQuery( '<li/>', {
                    class: "error"
                } ).html( errorMessage ).appendTo( listJQ ) ;

            }
            listJQ.appendTo( errorsObj ) ;
            errorsObj.appendTo( resultsContainer ) ;
        } else {
            if ( parsedJson.warnings && parsedJson.warnings.length > 0 ) {

                let errorsObj = jQuery( '<div/>', {
                    class: "warning"
                } ).text( "Warnings: " ) ;
                let listJQ = jQuery( '<ol/>', {
                    class: "error"
                } ) ;
                for ( let i = 0 ; i < parsedJson.warnings.length ; i++ ) {
                    $( ".textWarning" ).html( "Found some Warnings<br> Run validator to view" ).show() ;
                    let noteItem = parsedJson.warnings[i] ;
                    noteItem = noteItem.replace( "__WARN:", "" ) ;
                    jQuery( '<li/>', {
                        class: "error"
                    } ).html( noteItem ).appendTo( listJQ ) ;
                }
                listJQ.appendTo( errorsObj ) ;
                errorsObj.appendTo( resultsContainer ) ;
            }
        }

        resultsContainer.append( "<br>" ) ;

        return resultsContainer ;
    }

} ) ;
