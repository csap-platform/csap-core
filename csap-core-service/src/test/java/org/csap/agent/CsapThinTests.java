package org.csap.agent ;

import org.springframework.test.context.ActiveProfiles ;

@ActiveProfiles ( CsapBareTest.PROFILE_JUNIT )
public abstract class CsapThinTests extends CsapThinNoProfile {
}
