package se.kth.calproxy.proxy;

import java.util.Properties;

import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipStack;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;

public class ProtocolObjects{
    static AddressFactory addressFactory;
    static MessageFactory messageFactory;
    static HeaderFactory headerFactory;
    static SipStack sipStack;
    static int logLevel = 32;
    static String logFileDirectory = "";

    static void init(String stackname, boolean autoDialog){
        SipFactory sipFactory;
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", stackname);
        properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", logFileDirectory + stackname + "debuglog.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG", logFileDirectory + stackname + "log.txt");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", (autoDialog? "on": "off"));
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", Integer.toString(logLevel));

        try {
            sipStack = sipFactory.createSipStack(properties);
            System.out.println("createSipStack " + sipStack);
        }
        catch(Exception e){
            e.printStackTrace();
            System.err.println(e.getMessage());
            throw new RuntimeException("Stack failed to initialize");
        }

        try{
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
        }
        catch(SipException ex){
            ex.printStackTrace();
            throw new RuntimeException ( ex);
        }
    }

    public static void destroy(){
        sipStack.stop();
    }

    public static void start() throws Exception{
        sipStack.start();
    }
}