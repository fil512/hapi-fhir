package ca.uhn.fhir.jpa.subscription.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LatchedService {
	private static final Logger ourLog = LoggerFactory.getLogger(LatchedService.class);

	private CountDownLatch myCountdownLatch;
	private AtomicReference<AssertionError> myFailure;

	public void countdown() {
		try {
			assertNotNull(myCountdownLatch);
			assertThat(myCountdownLatch.getCount(), greaterThan(0L));
		} catch (AssertionError e) {
			myFailure.set(e);
		}
		ourLog.info("Counting down {}", myCountdownLatch);
		myCountdownLatch.countDown();
	}

	public void setExpectedCount(int count) {
		myFailure = new AtomicReference<>();
		myCountdownLatch = new CountDownLatch(count);
	}

	public void awaitExpected() throws InterruptedException {
		awaitExpectedWithTimeout(10);
	}

	public void awaitExpectedWithTimeout(int timeoutSecond) throws InterruptedException {
		assertTrue(myCountdownLatch.await(timeoutSecond, TimeUnit.SECONDS));

		if (myFailure.get() != null) {
			throw myFailure.get();
		}
	}

	// FIXME KHS trip on matcher completing with no sends
	public void expectNothing() throws InterruptedException {
		expectNothing(5);
	}

	public void expectNothing(int timeoutSecond) throws InterruptedException {
		assertFalse(myCountdownLatch.await(timeoutSecond, TimeUnit.SECONDS));
	}
}
