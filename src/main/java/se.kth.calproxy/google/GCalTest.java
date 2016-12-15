package se.kth.calproxy.google;

import com.google.api.client.util.DateTime;

import java.io.IOException;
import java.util.List;

/**
 * Created by Robert Norgren Erneborg on 2015-10-03.
 */
public class GCalTest {

	public static final String USER_EMAIL2 = "robert.erneborg@gmail.com";
	public static final String CALENDAR_ID = "primary";
	public static final String USER_EMAIL = "sip.calendar.proxy@gmail.com";
//	public static final String CALENDAR_ID2 = "ua0mniid949gu8nij32m0nur5c2fgdgr@import.calendar.se.kth.calproxy.web.google.com";

	public static void main (String[] args){

		DateTime now = new DateTime(System.currentTimeMillis());
		try{
			GoogleCalendar gcal = new GoogleCalendar(USER_EMAIL, CALENDAR_ID);
			GoogleCalendar gcal2 = new GoogleCalendar(USER_EMAIL2, CALENDAR_ID);
			List<String> events = gcal.getCalendarEvents(now);
			List<String> events2 = gcal2.getCalendarEvents(now);
			events = gcal.getCalendarEvents(now);

			for (String event : events)
				System.out.println(event);
			for (String event : events2)
				System.out.println(event);

		}catch (IOException e){
			e.printStackTrace();
		}

	}
}
