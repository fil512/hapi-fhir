package ca.uhn.fhir.jpa.subscription.module.standalone;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import org.hl7.fhir.dstu3.model.Subscription;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SubscriptionLoaderFhirClientTest extends BaseBlockingQueueSubscribableChannelDstu3Test {
	private String myCode = "1000000050";

	@Before
	public void loadSubscriptions() {
		String payload = "application/fhir+json";

		String criteria1 = "Observation?code=SNOMED-CT|" + myCode + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + myCode + "111&_format=xml";


		List<Subscription> subs = new ArrayList<>();
		subs.add(newSubscription(criteria1, payload, ourListenerServerBase));
		subs.add(newSubscription(criteria2, payload, ourListenerServerBase));

		IBundleProvider bundle = new SimpleBundleProvider(new ArrayList<>(subs), "uuid");
		initSubscriptionLoader(bundle);
	}

	@Test
	public void testSubscriptionLoaderFhirClient() throws Exception {
		sendObservation(myCode, "SNOMED-CT");

		ourObservationListener.waitForCreatedSize(0);
		ourObservationListener.waitForUpdatedSize(1);
		assertEquals(Constants.CT_FHIR_JSON_NEW, ourObservationListener.getContentType(0));
	}
}
