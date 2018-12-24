package ca.uhn.fhir.jpa.subscription.module.subscriber;

import ca.uhn.fhir.jpa.subscription.module.standalone.BaseBlockingQueueSubscribableChannelDstu3Test;
import ca.uhn.fhir.rest.api.Constants;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Tests copied from jpa.subscription.resthook.RestHookTestDstu3Test
 */
public class SubscriptionMatchingSubscriberTest extends BaseBlockingQueueSubscribableChannelDstu3Test {
	private static final Logger ourLog = LoggerFactory.getLogger(SubscriptionMatchingSubscriberTest.class);

	@Test
	public void testRestHookSubscriptionApplicationFhirJson() throws Exception {
		String payload = "application/fhir+json";

		String code = "1000000050";
		String criteria1 = "Observation?code=SNOMED-CT|" + code + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + code + "111&_format=xml";

		createSubscription(criteria1, payload, ourListenerServerBase);
		createSubscription(criteria2, payload, ourListenerServerBase);

		CountDownLatch latch = ourObservationListener.newCountDownLatch(1);

		sendObservation(code, "SNOMED-CT");

		assertTrue(latch.await(5L, TimeUnit.SECONDS));

		assertEquals(Constants.CT_FHIR_JSON_NEW, ourObservationListener.getContentType(0));
	}

	@Test
	public void testRestHookSubscriptionApplicationXmlJson() throws Exception {
		String payload = "application/fhir+xml";

		String code = "1000000050";
		String criteria1 = "Observation?code=SNOMED-CT|" + code + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + code + "111&_format=xml";

		createSubscription(criteria1, payload, ourListenerServerBase);
		createSubscription(criteria2, payload, ourListenerServerBase);

		CountDownLatch latch = ourObservationListener.newCountDownLatch(1);

		sendObservation(code, "SNOMED-CT");

		assertTrue(latch.await(5L, TimeUnit.SECONDS));
		assertEquals(Constants.CT_FHIR_XML_NEW, ourObservationListener.getContentType(0));
	}

	@Test
	public void testRestHookSubscriptionWithoutPayload() throws Exception {
		String payload = "";

		String code = "1000000050";
		String criteria1 = "Observation?code=SNOMED-CT|" + code;
		String criteria2 = "Observation?code=SNOMED-CT|" + code + "111";

		createSubscription(criteria1, payload, ourListenerServerBase);
		createSubscription(criteria2, payload, ourListenerServerBase);

		sendObservation(code, "SNOMED-CT");

		ourObservationListener.waitForCreatedSize(0);
		ourObservationListener.waitForUpdatedSize(0);
	}
}
