package ca.uhn.fhir.jpa.subscription;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import com.google.common.collect.Lists;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static ca.uhn.fhir.jpa.dao.BaseJpaTest.waitForSize;

public class ObservationListener<T extends IBaseResource> implements IResourceProvider {
	private static final Logger ourLog = LoggerFactory.getLogger(ObservationListener.class);

	private CountDownLatch myCountdownLatch;

	private final List<String> myContentTypes = Collections.synchronizedList(new ArrayList<>());
	private final List<String> myHeaders = Collections.synchronizedList(new ArrayList<>());
	private final List<T> myCreatedObservations = Collections.synchronizedList(Lists.newArrayList());
	private final List<T> myUpdatedObservations = Collections.synchronizedList(Lists.newArrayList());

	private Class<T> type;

	// Hack to get generic type
	public ObservationListener(Class<T> type) {
		this.type = type;
	}

	@Create
	public MethodOutcome create(@ResourceParam T theObservation, HttpServletRequest theRequest) {
		ourLog.info("Received Listener Create");
		myContentTypes.add(theRequest.getHeader(Constants.HEADER_CONTENT_TYPE).replaceAll(";.*", ""));
		myCreatedObservations.add(theObservation);
		extractHeaders(theRequest);
		return new MethodOutcome(new IdDt("Observation/1"), true);
	}

	private void extractHeaders(HttpServletRequest theRequest) {
		Enumeration<String> headerNamesEnum = theRequest.getHeaderNames();
		while (headerNamesEnum.hasMoreElements()) {
			String nextName = headerNamesEnum.nextElement();
			Enumeration<String> valueEnum = theRequest.getHeaders(nextName);
			while (valueEnum.hasMoreElements()) {
				String nextValue = valueEnum.nextElement();
				myHeaders.add(nextName + ": " + nextValue);
			}
		}
	}

	@Override
	public Class<T> getResourceType() {
		return type;
	}

	@Update
	public MethodOutcome update(@ResourceParam T theObservation, HttpServletRequest theRequest) {
		ourLog.info("Received Listener Update");
		myUpdatedObservations.add(theObservation);
		myContentTypes.add(theRequest.getHeader(Constants.HEADER_CONTENT_TYPE).replaceAll(";.*", ""));
		extractHeaders(theRequest);
		countdown();
		return new MethodOutcome(new IdDt("Observation/1"), false);
	}

	public void clear() {
		myCreatedObservations.clear();
		myUpdatedObservations.clear();
		myContentTypes.clear();
		myHeaders.clear();
	}

	public void waitForCreatedSize(int size) {
		waitForSize(size, myCreatedObservations);
	}
	public void waitForUpdatedSize(int size) {
		waitForSize(size, myUpdatedObservations);
	}

	public String getContentType(int index) {
		return myContentTypes.get(index);
	}

	public T getUpdatedObservation(int index) {
		return myUpdatedObservations.get(index);
	}

	public int size() {
		return myUpdatedObservations.size();
	}

	private void countdown() {
		if (myCountdownLatch != null) {
			myCountdownLatch.countDown();
		}
	}

	public CountDownLatch newCountDownLatch(int count) {
		myCountdownLatch = new CountDownLatch(count);
		return myCountdownLatch;
	}

	public boolean headersHasItem(String theItem) {
		return myHeaders.contains(theItem);
	}
}
