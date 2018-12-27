package ca.uhn.fhir.jpa.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.*;

// FIXME KHS reconcile with LatchedService in subscription module
public class LatchedService {
	private static final Logger ourLog = LoggerFactory.getLogger(LatchedService.class);

	private CountDownLatch myCountdownLatch;
	private AtomicReference<AssertionError> myFailure;
	private String myName;

	public LatchedService(String theName) {
		myName = theName;
	}

	public void countdown() {
		try {
			if (myCountdownLatch != null) {
				assertTrue(myName + " latch triggered unexpectedly.  Did you set the expectedCount too low?", myCountdownLatch.getCount() > 0);
				myCountdownLatch.countDown();
			}
		} catch (AssertionError e) {
			myFailure.set(e);
		}
	}

	public void setExpectedCount(int count) {
		myFailure = new AtomicReference<>();
		myCountdownLatch = new CountDownLatch(count);
	}

	public void awaitExpected() throws InterruptedException {
		// FIXME KHS 5
		awaitExpectedWithTimeout(95);
	}

	public void awaitExpectedWithTimeout(int timeoutSecond) throws InterruptedException {
		assertTrue("Timed out while waiting for "+myName+" latch to be triggered", myCountdownLatch.await(timeoutSecond, TimeUnit.SECONDS));

		if (myFailure.get() != null) {
			throw myFailure.get();
		}
	}

	// FIXME KHS trip on matcher completing with no sends
//	public void expectNothing() throws InterruptedException {
//		expectNothing(5);
//	}
//
//	public void expectNothing(int timeoutSecond) throws InterruptedException {
//		assertFalse(myCountdownLatch.await(timeoutSecond, TimeUnit.SECONDS));
//	}
}
