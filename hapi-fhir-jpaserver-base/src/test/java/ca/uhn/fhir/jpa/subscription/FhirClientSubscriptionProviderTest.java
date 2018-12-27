package ca.uhn.fhir.jpa.subscription;

import ca.uhn.fhir.jpa.subscription.module.cache.ISubscriptionProvider;
import ca.uhn.fhir.jpa.subscription.module.cache.SubscriptionLoader;
import ca.uhn.fhir.jpa.subscription.module.standalone.FhirClientSubscriptionProvider;
import ca.uhn.fhir.rest.api.Constants;
import org.hl7.fhir.dstu3.model.Subscription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class FhirClientSubscriptionProviderTest extends BaseSubscriptionsR4Test {
	@Autowired
	SubscriptionLoader mySubscriptionLoader;
	@Autowired
	ISubscriptionProvider origSubscriptionProvider;
	@Autowired
	AutowireCapableBeanFactory autowireCapableBeanFactory;

	@Before
	public void useFhirClientSubscriptionProvider() {
		FhirClientSubscriptionProvider subscriptionProvider = new FhirClientSubscriptionProvider(ourClient);
		// This bean is only available in the standalone subscription context, so we have to autowire it manually.
		autowireCapableBeanFactory.autowireBean(subscriptionProvider);
		mySubscriptionLoader.setSubscriptionProviderForUnitTest(subscriptionProvider);
	}

	@After
	public void revert() {
		mySubscriptionLoader.setSubscriptionProviderForUnitTest(origSubscriptionProvider);
	}

	private String myCode = "1000000050";

	@Test
	public void testSubscriptionLoaderFhirClient() throws Exception {
		String payload = Constants.CT_FHIR_JSON_NEW;

		String criteria1 = "Observation?code=SNOMED-CT|" + myCode + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + myCode + "111&_format=xml";


		List<Subscription> subs = new ArrayList<>();
		createSubscription(criteria1, payload);
		createSubscription(criteria2, payload);
		waitForActivatedSubscriptionCount(2);
		ourObservationListener.setExpectedCount(1);
		sendObservation(myCode, "SNOMED-CT");

		ourObservationListener.awaitExpected();
		assertEquals(Constants.CT_FHIR_JSON_NEW, ourObservationListener.getContentType(0));
	}
}
