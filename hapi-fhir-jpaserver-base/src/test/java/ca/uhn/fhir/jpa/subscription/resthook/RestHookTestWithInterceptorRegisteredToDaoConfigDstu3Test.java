
package ca.uhn.fhir.jpa.subscription.resthook;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.provider.dstu3.BaseResourceProviderDstu3Test;
import ca.uhn.fhir.jpa.subscription.LatchedService;
import ca.uhn.fhir.jpa.subscription.ObservationListener;
import ca.uhn.fhir.jpa.subscription.SubscriptionActivatingInterceptor;
import ca.uhn.fhir.jpa.subscription.SubscriptionTestUtil;
import ca.uhn.fhir.jpa.subscription.module.subscriber.SubscriptionMatchingSubscriber;
import ca.uhn.fhir.jpa.testutil.RandomServerPortProvider;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Subscription;
import org.junit.*;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;

/**
 * Test the rest-hook subscriptions
 */
public class RestHookTestWithInterceptorRegisteredToDaoConfigDstu3Test extends BaseResourceProviderDstu3Test {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(RestHookTestWithInterceptorRegisteredToDaoConfigDstu3Test.class);

	private static int ourListenerPort;
	private static RestfulServer ourListenerRestServer;
	private static Server ourListenerServer;
	private static String ourListenerServerBase;
	private static ObservationListener ourObservationListener;

	@Autowired
	private SubscriptionTestUtil mySubscriptionTestUtil;
	@Autowired
	SubscriptionMatchingSubscriber mySubscriptionMatchingSubscriber;

	private LatchedService mySubscriptionActivatingInterceptorLatch = new LatchedService("SubscriptionActivatingInterceptor");
	private LatchedService mySubscriptionsMatchedLatch = new LatchedService("Subscriptions Matched");
	private int mySubscriptionsMatched;

	@Override
	protected boolean shouldLogClient() {
		return false;
	}

	@After
	public void afterUnregisterRestHookListener() {
		myDaoConfig.setAllowMultipleDelete(true);
		ourLog.info("Deleting all subscriptions");
		ourClient.delete().resourceConditionalByUrl("Subscription?status=active").execute();
		ourLog.info("Done deleting all subscriptions");
		myDaoConfig.setAllowMultipleDelete(new DaoConfig().isAllowMultipleDelete());

		mySubscriptionMatchingSubscriber.removeMatchedSubscriptionsCallbackForUnitTest();
		mySubscriptionTestUtil.unregisterSubscriptionInterceptor();
		SubscriptionActivatingInterceptor.setWaitForSubscriptionActivationSynchronouslyForUnitTest(false);
	}

	@Before
	public void beforeRegisterRestHookListener() {
		mySubscriptionTestUtil.registerRestHookInterceptor();
		mySubscriptionMatchingSubscriber.setMatchedSubscriptionsCallbackForUnitTest(count -> {mySubscriptionsMatchedLatch.countdown(); mySubscriptionsMatched = count;});
	}

	@Before
	public void beforeReset() {
		ourObservationListener.clear();
		mySubscriptionTestUtil.addChannelInterceptor(mySubscriptionActivatingInterceptorLatch);
		SubscriptionActivatingInterceptor.setWaitForSubscriptionActivationSynchronouslyForUnitTest(true);
	}

	private Subscription createSubscription(String criteria, String payload, String endpoint) throws InterruptedException {
		Subscription subscription = new Subscription();
		subscription.setReason("Monitor new neonatal function (note, age will be determined by the monitor)");
		subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
		subscription.setCriteria(criteria);

		Subscription.SubscriptionChannelComponent channel = new Subscription.SubscriptionChannelComponent();
		channel.setType(Subscription.SubscriptionChannelType.RESTHOOK);
		channel.setPayload(payload);
		channel.setEndpoint(endpoint);
		subscription.setChannel(channel);

		mySubscriptionActivatingInterceptorLatch.setExpectedCount(1);
		MethodOutcome methodOutcome = ourClient.create().resource(subscription).execute();
		mySubscriptionActivatingInterceptorLatch.awaitExpectedWithTimeout(5);
		subscription.setId(methodOutcome.getId().getIdPart());

		return subscription;
	}

	private Observation sendObservation(String code, String system) throws InterruptedException {
		Observation observation = new Observation();
		CodeableConcept codeableConcept = new CodeableConcept();
		observation.setCode(codeableConcept);
		Coding coding = codeableConcept.addCoding();
		coding.setCode(code);
		coding.setSystem(system);

		observation.setStatus(Observation.ObservationStatus.FINAL);

		MethodOutcome methodOutcome = ourClient.create().resource(observation).execute();

		String observationId = methodOutcome.getId().getIdPart();
		observation.setId(observationId);

		return observation;
	}

	@Test
	public void testRestHookSubscriptionJson() throws Exception {
		String payload = "application/json";

		String code = "1000000050";
		String criteria1 = "Observation?code=SNOMED-CT|" + code + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + code + "111&_format=xml";

		Subscription subscription1 = createSubscription(criteria1, payload, ourListenerServerBase);
		Subscription subscription2 = createSubscription(criteria2, payload, ourListenerServerBase);

		ourObservationListener.setExpectedCount(1);

		Observation observation1 = sendObservation(code, "SNOMED-CT");

		// Should see 1 subscription notification
		ourObservationListener.awaitExpected();
		
		Subscription subscriptionTemp = ourClient.read(Subscription.class, subscription2.getId());
		Assert.assertNotNull(subscriptionTemp);

		subscriptionTemp.setCriteria(criteria1);
		ourClient.update().resource(subscriptionTemp).withId(subscriptionTemp.getIdElement()).execute();

		ourObservationListener.setExpectedCount(2);
		Observation observation2 = sendObservation(code, "SNOMED-CT");

		// Should see two subscription notifications
		ourObservationListener.awaitExpected();

		mySubscriptionActivatingInterceptorLatch.setExpectedCount(1);
		ourClient.delete().resourceById(new IdDt("Subscription", subscription2.getId())).execute();
		mySubscriptionActivatingInterceptorLatch.awaitExpectedWithTimeout(5);

		ourObservationListener.setExpectedCount(1);
		Observation observationTemp3 = sendObservation(code, "SNOMED-CT");

		// Should see only one subscription notification
		ourObservationListener.awaitExpected();

		Observation observation3 = ourClient.read(Observation.class, observationTemp3.getId());
		CodeableConcept codeableConcept = new CodeableConcept();
		observation3.setCode(codeableConcept);
		Coding coding = codeableConcept.addCoding();
		coding.setCode(code + "111");
		coding.setSystem("SNOMED-CT");

		mySubscriptionsMatchedLatch.setExpectedCount(1);
		ourClient.update().resource(observation3).withId(observation3.getIdElement()).execute();
		mySubscriptionsMatchedLatch.awaitExpected();
		// Should see no subscription notification
		assertEquals(0, mySubscriptionsMatched);

		Observation observation3a = ourClient.read(Observation.class, observationTemp3.getId());

		CodeableConcept codeableConcept1 = new CodeableConcept();
		observation3a.setCode(codeableConcept1);
		Coding coding1 = codeableConcept1.addCoding();
		coding1.setCode(code);
		coding1.setSystem("SNOMED-CT");

		ourObservationListener.setExpectedCount(1);

		ourClient.update().resource(observation3a).withId(observation3a.getIdElement()).execute();

		// Should see only one subscription notification
		ourObservationListener.awaitExpected();
		Assert.assertFalse(subscription1.getId().equals(subscription2.getId()));
		Assert.assertFalse(observation1.getId().isEmpty());
		Assert.assertFalse(observation2.getId().isEmpty());
	}

	@Test
	public void testRestHookSubscriptionXml() throws Exception {
		String payload = "application/xml";

		String code = "1000000050";
		String criteria1 = "Observation?code=SNOMED-CT|" + code + "&_format=xml";
		String criteria2 = "Observation?code=SNOMED-CT|" + code + "111&_format=xml";

		Subscription subscription1 = createSubscription(criteria1, payload, ourListenerServerBase);
		Subscription subscription2 = createSubscription(criteria2, payload, ourListenerServerBase);

		ourObservationListener.setExpectedCount(1);
		Observation observation1 = sendObservation(code, "SNOMED-CT");

		// Should see 1 subscription notification
		ourObservationListener.awaitExpected();
		
		Subscription subscriptionTemp = ourClient.read(Subscription.class, subscription2.getId());
		Assert.assertNotNull(subscriptionTemp);

		subscriptionTemp.setCriteria(criteria1);

		mySubscriptionActivatingInterceptorLatch.setExpectedCount(1);
		ourClient.update().resource(subscriptionTemp).withId(subscriptionTemp.getIdElement()).execute();
		mySubscriptionActivatingInterceptorLatch.awaitExpectedWithTimeout(5);

		ourObservationListener.setExpectedCount(2);
		Observation observation2 = sendObservation(code, "SNOMED-CT");

		// Should see two subscription notifications
		ourObservationListener.awaitExpected();

		mySubscriptionActivatingInterceptorLatch.setExpectedCount(1);
		ourClient.delete().resourceById(new IdDt("Subscription", subscription2.getId())).execute();
		mySubscriptionActivatingInterceptorLatch.awaitExpectedWithTimeout(5);

		ourObservationListener.setExpectedCount(1);

		Observation observationTemp3 = sendObservation(code, "SNOMED-CT");

		// Should see only one subscription notification
		ourObservationListener.awaitExpected();

		Observation observation3 = ourClient.read(Observation.class, observationTemp3.getId());
		CodeableConcept codeableConcept = new CodeableConcept();
		observation3.setCode(codeableConcept);
		Coding coding = codeableConcept.addCoding();
		coding.setCode(code + "111");
		coding.setSystem("SNOMED-CT");

		mySubscriptionsMatchedLatch.setExpectedCount(1);
		ourClient.update().resource(observation3).withId(observation3.getIdElement()).execute();
		mySubscriptionsMatchedLatch.awaitExpected();
		// Should see no subscription notification
		assertEquals(0, mySubscriptionsMatched);


		Observation observation3a = ourClient.read(Observation.class, observationTemp3.getId());

		CodeableConcept codeableConcept1 = new CodeableConcept();
		observation3a.setCode(codeableConcept1);
		Coding coding1 = codeableConcept1.addCoding();
		coding1.setCode(code);
		coding1.setSystem("SNOMED-CT");

		ourObservationListener.setExpectedCount(1);

		ourClient.update().resource(observation3a).withId(observation3a.getIdElement()).execute();

		// Should see only one subscription notification
		ourObservationListener.awaitExpected();

		Assert.assertFalse(subscription1.getId().equals(subscription2.getId()));
		Assert.assertFalse(observation1.getId().isEmpty());
		Assert.assertFalse(observation2.getId().isEmpty());
	}

	@BeforeClass
	public static void startListenerServer() throws Exception {
		ourListenerPort = RandomServerPortProvider.findFreePort();
		ourListenerRestServer = new RestfulServer(FhirContext.forDstu3());
		ourListenerServerBase = "http://localhost:" + ourListenerPort + "/fhir/context";

		ourObservationListener = new ObservationListener(Observation.class);
		ourListenerRestServer.setResourceProviders(ourObservationListener);

		ourListenerServer = new Server(ourListenerPort);

		ServletContextHandler proxyHandler = new ServletContextHandler();
		proxyHandler.setContextPath("/");

		ServletHolder servletHolder = new ServletHolder();
		servletHolder.setServlet(ourListenerRestServer);
		proxyHandler.addServlet(servletHolder, "/fhir/context/*");

		ourListenerServer.setHandler(proxyHandler);
		ourListenerServer.start();
	}

	@AfterClass
	public static void stopListenerServer() throws Exception {
		ourListenerServer.stop();
	}



}
