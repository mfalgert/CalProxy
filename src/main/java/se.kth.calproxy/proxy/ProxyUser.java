package se.kth.calproxy.proxy;

import se.kth.calproxy.google.GoogleCalendar;

import java.io.IOException;

public class ProxyUser {
    private String sipUser;
    private String email;
    private String calId;
    private GoogleCalendar googleCalendar;

    public ProxyUser(String sipUser, String email, String calId) throws IOException{
        this.sipUser = sipUser;
        this.email = email;
        this.calId = calId;
        this.googleCalendar = new GoogleCalendar(email, calId);
    }

    public String getSipUser(){
        return sipUser;
    }

    public String getEmail(){
        return email;
    }

    public String getCalId(){
        return calId;
    }

    public void setSipUser(String sipUser){
        this.sipUser = sipUser;
    }

    public void setEmail(String email){
        this.email = email;
    }

    public void setCalId(String calId){
        this.calId = calId;
    }

    public GoogleCalendar getGoogleCalendar(){
        return googleCalendar;
    }

    public void setGoogleCalendar(GoogleCalendar googleCalendar){
        this.googleCalendar = googleCalendar;
    }
}
