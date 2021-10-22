define( [ ], function () {

    console.log( "Module loaded" ) ;
    let hackLengthForTesting = -1 ;

    return {
        //
        processValidationResults: function ( parsedJson ) {
            return processValidationResults( parsedJson ) ;
        }
    }

    function processValidationResults( validationReport ) {

        // Clear previous errors from text area
        $( ".errorIcon" ).removeClass( "errorIcon" ) ;

        let resultsContainer = jQuery( '<div/>', {
            id: "parseResults"
        } ).css( "font-size", "1.2em" ) ;

        let resultsClass = "status-green" ;
        let resultsText = "Parsing Successful" ;


        if ( !validationReport.success ) {
            resultsClass = "status-red" ;
            resultsText = "Parsing Failed" ;
        }

        jQuery( '<span/>', {
            class: resultsClass,
            text: resultsText
        } ).css( "height", "1.2em" ).appendTo( resultsContainer ) ;


        resultsContainer.append( "<br>" ) ;

        if ( validationReport.errors && validationReport.errors.length > 0 ) {

            let errorsObj = jQuery( '<div/>', {
                class: "warning"
            } ).text( "Errors: " ).css( {
                "overflow-y": "auto"
            } ) ;
            let listJQ = jQuery( '<ol/>', {
                class: "error"
            } ) ;
            for ( let i = 0 ; i < validationReport.errors.length ; i++ ) {

                // 2 scenarios: a parsing error with a line number, and a semantic
                // error with just contents
                $( ".textWarning" ).html( "Found some Errors<br> Run validator to view" ).show() ;
                let error = validationReport.errors[i] ;
                let errorMessage = validationReport.errors[i] ;
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
            
            if ( validationReport.warnings && validationReport.warnings.length > 0 ) {

                let $warnings = jQuery( '<div/>', {
                    class: "warning",
                    text: "Warnings"
                } ) ;

                let $warningsList = jQuery( '<ol/>', { class: "csap-list" } ) ;

                for ( let warning of  validationReport.warnings ) {
                    $( ".textWarning" ).html( "Found some Warnings<br> Run validator to view" ).show() ;
                    let noteItem = warning ;
                    noteItem = noteItem.replace( "__WARN:", "" ) ;
                    jQuery( '<li/>', {
                        html: noteItem
                    } ).appendTo( $warningsList ) ;
                }
                $warningsList.appendTo( $warnings ) ;
                $warnings.appendTo( resultsContainer ) ;
            }
//            if ( validationReport.warnings && validationReport.warnings.length > 0 ) {
//
//                let errorsObj = jQuery( '<div/>', {
//                    class: "warning"
//                } ).text( "Warnings: " ) ;
//                let listJQ = jQuery( '<ol/>', {
//                    class: "error"
//                } ) ;
//                for ( let i = 0 ; i < validationReport.warnings.length ; i++ ) {
//                    $( ".textWarning" ).html( "Found some Warnings<br> Run validator to view" ).show() ;
//                    let noteItem = validationReport.warnings[i] ;
//                    noteItem = noteItem.replace( "__WARN:", "" ) ;
//                    jQuery( '<li/>', {
//                        class: "error"
//                    } ).html( noteItem ).appendTo( listJQ ) ;
//                }
//                listJQ.appendTo( errorsObj ) ;
//                errorsObj.appendTo( resultsContainer ) ;
//            }
        }

        resultsContainer.append( "<br>" ) ;

        return resultsContainer ;
    }

} ) ;
