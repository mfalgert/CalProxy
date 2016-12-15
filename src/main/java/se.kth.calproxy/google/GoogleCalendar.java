package se.kth.calproxy.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;

import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Robert Norgren Erneborg on 2015-10-03.
 */
public class GoogleCalendar {

	/** Application name. */
	private static final String APPLICATION_NAME =
			"Sip Calendar Proxy";

	/** Directory to store user credentials for this application. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(
			System.getProperty("user.home"), ".credentials/sip-calendar-proxy");

	/** Global instance of the {@link FileDataStoreFactory}. */
	private static FileDataStoreFactory DATA_STORE_FACTORY;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY =
			JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private static HttpTransport HTTP_TRANSPORT;

	/** Global instance of the scopes required by this quickstart. */
	private static final List<String> SCOPES =
			Arrays.asList(CalendarScopes.CALENDAR_READONLY);

	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	private Calendar calendar;
	private String userEmail;
	private String userCalendarId;

	/**
	 *
	 * @param userEmail
	 * @param userCalendarId
	 * @throws java.io.IOException
	 */
	public GoogleCalendar(String userEmail, String userCalendarId) throws IOException{
		this.userCalendarId = userCalendarId;
		this.userEmail = userEmail;
		this.calendar = getCalendarService();
	}

	/**
	 * Creates an authorized Credential object.
	 * @return an authorized Credential object.
	 * @throws java.io.IOException
	 */
	private Credential authorize(String userEmail) throws IOException {
		// Load client secrets, i.e. Oauth2 secrets.
		InputStream in =
				GoogleCalendar.class.getResourceAsStream("/client_secret.json");
		GoogleClientSecrets clientSecrets =
				GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow =
				new GoogleAuthorizationCodeFlow.Builder(
						HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
						.setDataStoreFactory(DATA_STORE_FACTORY)
						.setAccessType("offline")
						.build();
		Credential credential = new AuthorizationCodeInstalledApp(
				flow, new LocalServerReceiver()).authorize(userEmail);
		System.out.println(
				"Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
		return credential;
	}

	/**
	 * Build and return an authorized Calendar client service.
	 * @return an authorized Calendar client service
	 * @throws java.io.IOException
	 */
	private com.google.api.services.calendar.Calendar getCalendarService() throws IOException {
		Credential credential = authorize(userEmail);
		return new com.google.api.services.calendar.Calendar.Builder(
				HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName(APPLICATION_NAME)
				.build();
	}

	// todo specify what should be returned
	public List<String> getCalendarEvents(DateTime now) throws IOException{

		Events events = calendar.events().list(userCalendarId)
				.setMaxResults(10)
				.setTimeMin(now)
				.setOrderBy("startTime")
				.setSingleEvents(true)
				.execute();

		List<Event> allEvents = events.getItems();
		List<String> currentEvents = new LinkedList<String>();

		for (Event event : allEvents){
			DateTime start = event.getStart().getDateTime();
			DateTime end = event.getEnd().getDateTime();

			if (start == null) start = event.getStart().getDate();
			if (end == null) end = event.getEnd().getDate();
			// the event is live
			if (start.getValue() < now.getValue() && now.getValue() < end.getValue())
				currentEvents.add(event.getDescription());

//			System.out.printf("%s (%s)\n", event.getSummary(), start);
//			System.out.println(event.getDescription());
		}

		return currentEvents;
//		String pageToken = null;
//		do {
//			CalendarList calendarList = calendar.calendarList().list().setPageToken(pageToken).execute();
//			List<CalendarListEntry> calendars = calendarList.getItems();
//
//			for (CalendarListEntry calendarListEntry : calendars) {
//				System.out.println(calendarListEntry.getSummary());
//			}
//			pageToken = calendarList.getNextPageToken();
//		} while (pageToken != null);
	}

}
