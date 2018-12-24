package ca.uhn.fhir.jpa.subscription;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.provider.r4.BaseResourceProviderR4Test;
import ca.uhn.fhir.jpa.subscription.module.LinkedBlockingQueueSubscribableChannel;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.util.BundleUtil;
import ca.uhn.fhir.util.PortUtil;
import com.google.common.collect.Lists;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.listener.SingleQueryCountHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.junit.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Ignore
public abstract class BaseSubscriptionsR4Test extends BaseResourceProviderR4Test {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(BaseSubscriptionsR4Test.class);

	protected static ObservationListener<Observation> ourObservationListener;
	private static int ourListenerPort;
	private static RestfulServer ourListenerRestServer;
	private static Server ourListenerServer;

	private static SingleQueryCountHolder ourCountHolder;

	@Autowired
	private SingleQueryCountHolder myCountHolder;
	@Autowired
	protected SubscriptionTestUtil mySubscriptionTestUtil;
	@Autowired
	protected SubscriptionMatcherInterceptor mySubscriptionMatcherInterceptor;

	protected CountingInterceptor myCountingInterceptor;

	protected static String ourListenerServerBase;

	protected List<IIdType> mySubscriptionIds = Collections.synchronizedList(new ArrayList<>());


	@After
	public void afterUnregisterRestHookListener() {
		SubscriptionMatcherInterceptor.setForcePayloadEncodeAndDecodeForUnitTests(false);

		for (IIdType next : mySubscriptionIds) {
			IIdType nextId = next.toUnqualifiedVersionless();
			ourLog.info("Deleting: {}", nextId);
			ourClient.delete().resourceById(nextId).execute();
		}
		mySubscriptionIds.clear();

		myDaoConfig.setAllowMultipleDelete(true);
		ourLog.info("Deleting all subscriptions");
		ourClient.delete().resourceConditionalByUrl("Subscription?status=active").execute();
		ourClient.delete().resourceConditionalByUrl("Observation?code:missing=false").execute();
		ourLog.info("Done deleting all subscriptions");
		myDaoConfig.setAllowMultipleDelete(new DaoConfig().isAllowMultipleDelete());

		mySubscriptionTestUtil.unregisterSubscriptionInterceptor();
	}

	@Before
	public void beforeRegisterRestHookListener() {
		mySubscriptionTestUtil.registerRestHookInterceptor();
	}

	@Before
	public void beforeReset() throws Exception {
		if (ourObservationListener != null) {
			ourObservationListener.clear();
		}

		// Delete all Subscriptions
		Bundle allSubscriptions = ourClient.search().forResource(Subscription.class).returnBundle(Bundle.class).execute();
		for (IBaseResource next : BundleUtil.toListOfResources(myFhirCtx, allSubscriptions)) {
			ourClient.delete().resource(next).execute();
		}
		waitForActivatedSubscriptionCount(0);

		LinkedBlockingQueueSubscribableChannel processingChannel = mySubscriptionMatcherInterceptor.getProcessingChannelForUnitTest();
		processingChannel.clearInterceptorsForUnitTest();
		myCountingInterceptor = new CountingInterceptor();
		processingChannel.addInterceptorForUnitTest(myCountingInterceptor);
	}


	protected Subscription createSubscription(String theCriteria, String thePayload) throws InterruptedException {
		Subscription subscription = newSubscription(theCriteria, thePayload);

		MethodOutcome methodOutcome = ourClient.create().resource(subscription).execute();
		subscription.setId(methodOutcome.getId().getIdPart());
		mySubscriptionIds.add(methodOutcome.getId());

		return subscription;
	}

	protected Subscription newSubscription(String theCriteria, String thePayload) {
		Subscription subscription = new Subscription();
		subscription.setReason("Monitor new neonatal function (note, age will be determined by the monitor)");
		subscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
		subscription.setCriteria(theCriteria);

		Subscription.SubscriptionChannelComponent channel = subscription.getChannel();
		channel.setType(Subscription.SubscriptionChannelType.RESTHOOK);
		channel.setPayload(thePayload);
		channel.setEndpoint(ourListenerServerBase);
		return subscription;
	}


	protected void waitForQueueToDrain() throws InterruptedException {
		mySubscriptionTestUtil.waitForQueueToDrain();
	}

	@PostConstruct
	public void initializeOurCountHolder() {
		ourCountHolder = myCountHolder;
	}


	protected Observation sendObservation(String code, String system) {
		Observation observation = new Observation();
		CodeableConcept codeableConcept = new CodeableConcept();
		observation.setCode(codeableConcept);
		Coding coding = codeableConcept.addCoding();
		coding.setCode(code);
		coding.setSystem(system);

		observation.setStatus(Observation.ObservationStatus.FINAL);

		MethodOutcome methodOutcome = ourClient.create().resource(observation).execute();

		observation.setId(methodOutcome.getId());

		return observation;
	}


	@AfterClass
	public static void reportTotalSelects() {
		ourLog.info("Total database select queries: {}", getQueryCount().getSelect());
	}

	private static QueryCount getQueryCount() {
		return ourCountHolder.getQueryCountMap().get("");
	}

	@BeforeClass
	public static void startListenerServer() throws Exception {
		ourListenerPort = PortUtil.findFreePort();
		ourListenerRestServer = new RestfulServer(FhirContext.forR4());
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
