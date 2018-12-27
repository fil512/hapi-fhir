package ca.uhn.fhir.jpa.subscription.module.subscriber;

import ca.uhn.fhir.jpa.subscription.module.standalone.BaseBlockingQueueSubscribableChannelDstu3Test;
import ca.uhn.fhir.rest.api.Constants;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests copied from jpa.subscription.resthook.RestHookTestDstu3Test
 */
public class SubscriptionCheckingSubscriberTest extends BaseBlockingQueueSubscribableChannelDstu3Test {
	private static final Logger ourLog = LoggerFactory.getLogger(SubscriptionCheckingSubscriberTest.class);

	@Test
	public void testRestHookSubscriptionApplicationFhirJson() throws Exception {
		String payload = Constants.CT_FHIR_JSON_NEW;

		String code = "1000000050";
		String criteria1 = "Observation?code=SNOMED-CT|" + code + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + code + "111&_format=xml";

		createSubscription(criteria1, payload, ourListenerServerBase);
		createSubscription(criteria2, payload, ourListenerServerBase);

		ourObservationListener.setExpectedCount(1);

		sendObservation(code, "SNOMED-CT");

		ourObservationListener.awaitExpected();

		assertEquals(Constants.CT_FHIR_JSON_NEW, ourObservationListener.getContentType(0));
	}

	@Test
	public void testRestHookSubscriptionApplicationXmlJson() throws Exception {
		String payload = Constants.CT_FHIR_XML_NEW;

		String code = "1000000050";
		String criteria1 = "Observation?code=SNOMED-CT|" + code + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + code + "111&_format=xml";

		createSubscription(criteria1, payload, ourListenerServerBase);
		createSubscription(criteria2, payload, ourListenerServerBase);

		ourObservationListener.setExpectedCount(1);
		sendObservation(code, "SNOMED-CT");
		ourObservationListener.awaitExpectedWithTimeout(5);

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

		ourObservationListener.setExpectedCount(1);
		sendObservation(code, "SNOMED-CT");
		ourObservationListener.expectNothing();
	}
}
