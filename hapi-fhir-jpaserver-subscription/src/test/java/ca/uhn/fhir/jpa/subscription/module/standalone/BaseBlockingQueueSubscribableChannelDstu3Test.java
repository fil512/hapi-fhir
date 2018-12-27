package ca.uhn.fhir.jpa.subscription.module.standalone;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.subscription.module.*;
import ca.uhn.fhir.jpa.subscription.module.cache.SubscriptionChannelFactory;
import ca.uhn.fhir.jpa.subscription.module.cache.SubscriptionRegistry;
import ca.uhn.fhir.jpa.subscription.module.subscriber.ResourceModifiedJsonMessage;
import ca.uhn.fhir.jpa.subscription.module.subscriber.SubscriptionMatchingSubscriberTest;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.util.PortUtil;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class BaseBlockingQueueSubscribableChannelDstu3Test extends BaseSubscriptionDstu3Test {
	private static final Logger ourLog = LoggerFactory.getLogger(SubscriptionMatchingSubscriberTest.class);

	@Autowired
	FhirContext myFhirContext;
	@Autowired
	StandaloneSubscriptionMessageHandler myStandaloneSubscriptionMessageHandler;
	@Autowired
	SubscriptionChannelFactory mySubscriptionChannelFactory;
	@Autowired
	SubscriptionRegistry mySubscriptionRegistry;

	private static int ourListenerPort;
	private static RestfulServer ourListenerRestServer;
	private static Server ourListenerServer;
	protected static String ourListenerServerBase;
	protected static ObservationListener ourObservationListener;
	private static SubscribableChannel ourSubscribableChannel;
	private static AtomicLong ourIdCounter = new AtomicLong(0);
	private List<IIdType> mySubscriptionIds = Collections.synchronizedList(new ArrayList<>());
	private LatchedService myStandaloneSubscriptionMessageHandlerLatch = new LatchedService();

	@After
	public void afterUnregisterRestHookListener() {
		mySubscriptionIds.clear();
	}

	@Before
	public void beforeReset() {
		ourObservationListener.clear();
		mySubscriptionRegistry.clearForUnitTest();
		if (ourSubscribableChannel == null) {
			ourSubscribableChannel = mySubscriptionChannelFactory.newMatchingChannel("test");
			ourSubscribableChannel.subscribe(myStandaloneSubscriptionMessageHandler);
		}
		ExecutorChannelInterceptor interceptor = new ExecutorChannelInterceptor() {
			@Override
			public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
				myStandaloneSubscriptionMessageHandlerLatch.countdown();
			}
		};

		((LinkedBlockingQueueSubscribableChannel) ourSubscribableChannel).setInterceptorForUnitTest(interceptor);
	}

	public <T extends IBaseResource> T sendResource(T theResource) throws InterruptedException {
		ResourceModifiedMessage msg = new ResourceModifiedMessage(myFhirContext, theResource, ResourceModifiedMessage.OperationTypeEnum.CREATE);
		ResourceModifiedJsonMessage message = new ResourceModifiedJsonMessage(msg);
		myStandaloneSubscriptionMessageHandlerLatch.setExpectedCount(1);
		ourSubscribableChannel.send(message);
		myStandaloneSubscriptionMessageHandlerLatch.awaitExpectedWithTimeout(5);
		return theResource;
	}

	protected Subscription createSubscription(String theCriteria, String thePayload, String theEndpoint) throws InterruptedException {
		Subscription subscription = newSubscription(theCriteria, thePayload, theEndpoint);
		return sendResource(subscription);
	}

	protected Subscription newSubscription(String theCriteria, String thePayload, String theEndpoint) {
		Subscription subscription = new Subscription();
		subscription.setReason("Monitor new neonatal function (note, age will be determined by the monitor)");
		subscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
		subscription.setCriteria(theCriteria);
		IdType id = new IdType("Subscription", ourIdCounter.incrementAndGet());
		subscription.setId(id);

		Subscription.SubscriptionChannelComponent channel = new Subscription.SubscriptionChannelComponent();
		channel.setType(Subscription.SubscriptionChannelType.RESTHOOK);
		channel.setPayload(thePayload);
		channel.setEndpoint(theEndpoint);
		subscription.setChannel(channel);
		return subscription;
	}

	protected Observation sendObservation(String code, String system) throws InterruptedException {
		Observation observation = new Observation();
		IdType id = new IdType("Observation", ourIdCounter.incrementAndGet());
		observation.setId(id);

		CodeableConcept codeableConcept = new CodeableConcept();
		observation.setCode(codeableConcept);
		Coding coding = codeableConcept.addCoding();
		coding.setCode(code);
		coding.setSystem(system);

		observation.setStatus(Observation.ObservationStatus.FINAL);

		return sendResource(observation);
	}


	@BeforeClass
	public static void startListenerServer() throws Exception {
		ourListenerPort = PortUtil.findFreePort();
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
