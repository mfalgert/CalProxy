package se.kth.calproxy.proxy;

import com.google.api.client.util.DateTime;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class CalProxy implements SipListener{
    private SipProvider sipProvider;
    private String proxyName;
    private String proxyIp;
    private int proxyPort;
    private String protocol;
    private String fileName;
    HashMap<String, ClientTransaction> cts;
    HashMap<String, ProxyUser> proxyUsers;
    HashSet<String> proxyMessages;

    public static void main(String args[]) throws Exception{
        ProtocolObjects.init("CalProxy", false);
        CalProxy calProxy = new CalProxy("CalProxy", InetAddress.getLocalHost().getHostAddress(), 5060, "udp", "src/main/users.txt");
        calProxy.sipProvider.addSipListener(calProxy);
    }

    public CalProxy(String proxyName, String proxyIp, int proxyPort, String protocol, String fileName){
        this.proxyName = proxyName;
        this.proxyIp = proxyIp;
        this.proxyPort = proxyPort;
        this.protocol = protocol;
        this.fileName = fileName;
        proxyUsers = getUsers();
        cts = new HashMap<String, ClientTransaction>();
        proxyMessages = new HashSet<String>();

        try{
            ListeningPoint listeningPoint = ProtocolObjects.sipStack.createListeningPoint(proxyIp, proxyPort, protocol);
            sipProvider = ProtocolObjects.sipStack.createSipProvider(listeningPoint);
        }
        catch(Exception e){
            e.printStackTrace();
        }

        System.out.println("Proxy now running...");
        System.out.println("====================");
    }

    public HashMap<String, ProxyUser> getUsers(){
        HashMap<String, ProxyUser> users = new HashMap<String, ProxyUser>();
        try{
            FileReader fr = new FileReader(fileName);
            BufferedReader br = new BufferedReader(fr);
            String line, sipUser, email, calId;
            String[] parsedLine;

            while((line = br.readLine()) != null) {
                parsedLine = line.split(";");
                sipUser = parsedLine[0];
                email = parsedLine[1];
                calId = parsedLine[2];
                ProxyUser user = new ProxyUser(sipUser, email, calId);
                users.put(sipUser, user);
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }

        return users;
    }

    @Override
    public void processRequest(RequestEvent requestEvent){
        try{
            Request request = requestEvent.getRequest();
            SipProvider sipProvider = (SipProvider) requestEvent.getSource();
            ListeningPoint lp = sipProvider.getListeningPoint(protocol);
            String host = lp.getIPAddress();
            int port = lp.getPort();
            ViaHeader requestVia = (ViaHeader) request.getHeader(ViaHeader.NAME);
            String key = requestVia.getHost() + ":" + requestVia.getPort();

            FromHeader requestFrom = (FromHeader) request.getHeader("From");
            ToHeader requestTo = (ToHeader) request.getHeader("To");
            String requestFromHost = requestFrom.getAddress().toString().split("@")[1].split(">")[0].split(";")[0];
            String requestToHost = requestTo.getAddress().toString().split("@")[1].split(">")[0].split(";")[0];
            String requestFromName = requestFrom.toString().split("@")[0].split("<")[1];
            if(requestFromName.contains(":")) requestFromName = requestFromName.split(":")[1];
            String requestToName = requestTo.toString().split("@")[0].split("<")[1];
            if(requestToName.contains(":")) requestToName = requestToName.split(":")[1];
            String requestFromAddress = requestFrom.toString().split("<")[1].split(">")[0];
            String requestToAddress = requestTo.toString().split("<")[1].split(">")[0];

            System.out.println("REQUEST (type / from / to): " + request.getMethod() + " / " + requestFromHost + " / " + requestToHost);

            ViaHeader via = ProtocolObjects.headerFactory.createViaHeader(host, port, protocol, null);
            via.setRPort();

            if(request.getMethod().equals(Request.INVITE)){
                Response trying = ProtocolObjects.messageFactory.createResponse(100, request);
                ServerTransaction st = requestEvent.getServerTransaction();
                if(st == null){
                    st = sipProvider.getNewServerTransaction(request);
                }
                st.sendResponse(trying);

                //CHECK GOOGLE CALENDAR

                boolean block = false;
                String msg = null, blockedUsers = null, allowedUsers = null;

                // If user is not registered as a Calendar proxy user
                if(proxyUsers.get(requestToAddress) == null){
                    block = false;
                }
                // Check if busy
                else{
                    try{
                        List<String> currentEvents = proxyUsers.get(requestToAddress).getGoogleCalendar().getCalendarEvents(new DateTime(System.currentTimeMillis()));

                        if(!currentEvents.isEmpty()){
                            String event = currentEvents.get(0);
                            if(event == null || !event.toLowerCase().contains("#start") || !event.toLowerCase().contains("#end")){
                                block = false;
                            }else{
                                try{
                                    String[] rule = event.toLowerCase().split("#start\n")[1].split("#end")[0].split("\n");
                                    for (String row : rule){
                                        if(row.toLowerCase().contains("block:")){
                                            try{
                                                blockedUsers = row.toLowerCase().split("block:")[1].trim();
                                            }catch (Exception e){
                                                blockedUsers = "";
                                            }
                                        }else if(row.toLowerCase().contains("allow:")){
                                            try{
                                                allowedUsers = row.toLowerCase().split("allow:")[1].trim();
                                            }catch (Exception e){
                                                allowedUsers = "";
                                            }
                                        }else if(row.toLowerCase().contains("message:")){
                                            try{
                                                msg = row.toLowerCase().split("message:")[1].trim();
                                                System.out.println("message1:" + msg);
                                            }catch (Exception e){
                                                msg = null;
                                            }
                                        }
                                    }

                                    if(blockedUsers.toLowerCase().contains("all")){
                                        if(allowedUsers.toLowerCase().contains(requestFromAddress)){
                                            block = false;
                                        }else{
                                            block = true;
                                        }
                                    }else if(blockedUsers.toLowerCase().contains(requestFromAddress)){
                                        if(allowedUsers.toLowerCase().contains(requestFromAddress)){
                                            block = false;
                                        }else{
                                            block = true;
                                        }
                                    }

                                }catch (Exception e){
                                    block = false;
                                    System.out.println("Could not parse rule");
                                }
                            }
                        }
                    }catch (IOException e){
                        System.out.println("Could not retrieve google calendar for user: " + requestToAddress);
                    }
                }

                System.out.println("blocked: " + block);

                if(block){
                    Response decline = ProtocolObjects.messageFactory.createResponse(603, request);
                    st.sendResponse(decline);

                    // send the block message to caller
                    if(msg != null && !msg.isEmpty() && msg.length() > 0){
                        FromHeader msgFrom = ProtocolObjects.headerFactory.createFromHeader(requestTo.getAddress(), "12345");
                        ToHeader msgTo = ProtocolObjects.headerFactory.createToHeader(requestFrom.getAddress(), null);
                        CallIdHeader msgCallId = ProtocolObjects.headerFactory.createCallIdHeader(sipProvider.getNewCallId().getCallId().split("@")[0]);
                        proxyMessages.add(msgCallId.getCallId());
                        CSeqHeader msgCSeq = ProtocolObjects.headerFactory.createCSeqHeader(20L, Request.MESSAGE);
                        MaxForwardsHeader msgMaxForwards = ProtocolObjects.headerFactory.createMaxForwardsHeader(70);
                        ViaHeader msgVia = ProtocolObjects.headerFactory.createViaHeader(requestToHost, proxyPort, protocol, null);
                        msgVia.setRPort();
                        SipURI msgSipUriTo = ProtocolObjects.addressFactory.createSipURI(requestToName, requestToHost);
                        msgSipUriTo.setPort(proxyPort);
                        SipURI msgSipUriFrom = ProtocolObjects.addressFactory.createSipURI(requestFromName, requestFromHost);
                        msgSipUriFrom.setPort(requestVia.getPort());
                        Address msgAddress = ProtocolObjects.addressFactory.createAddress(requestToName, msgSipUriTo);
                        ContactHeader msgContact = ProtocolObjects.headerFactory.createContactHeader(msgAddress);
                        ContentTypeHeader msgContentType = ProtocolObjects.headerFactory.createContentTypeHeader("text", "plain");
                        ArrayList<ViaHeader> msgVias = new ArrayList<ViaHeader>();
                        msgVias.add(msgVia);
                        Request message = ProtocolObjects.messageFactory.createRequest(msgSipUriFrom, Request.MESSAGE, msgCallId, msgCSeq, msgFrom, msgTo, msgVias, msgMaxForwards);
                        message.setHeader(msgContact);
                        message.setContent(msg, msgContentType);
                        ClientTransaction msgCt = sipProvider.getNewClientTransaction(message);
                        msgCt.sendRequest();
                    }
                }

                else{
                    Request newRequest = (Request) request.clone();
                    newRequest.addFirst(via);
                    SipURI sipURI = ProtocolObjects.addressFactory.createSipURI(proxyName, proxyIp);
                    sipURI.setPort(proxyPort);
                    sipURI.setLrParam();
                    Address address = ProtocolObjects.addressFactory.createAddress(proxyName, sipURI);
                    RecordRouteHeader recordRoute = ProtocolObjects.headerFactory.createRecordRouteHeader(address);
                    newRequest.addFirst(recordRoute);
                    newRequest.removeFirst(RouteHeader.NAME);
                    ClientTransaction ct = sipProvider.getNewClientTransaction(newRequest);
                    cts.put(key, ct);
                    ct.setApplicationData(st);
                    ct.sendRequest();
                }
            }
            else if(request.getMethod().equals(Request.CANCEL)){
                Response response = ProtocolObjects.messageFactory.createResponse(200, request);
                ServerTransaction st = requestEvent.getServerTransaction();
                if(st == null){
                    st = sipProvider.getNewServerTransaction(request);
                }
                st.sendResponse(response);

                Request cancel = cts.get(key).createCancel();
                ClientTransaction cancelCt = sipProvider.getNewClientTransaction(cancel);
                cancelCt.sendRequest();
            }
            else if(request.getMethod().equals(Request.REGISTER)){
                Response response = ProtocolObjects.messageFactory.createResponse(200, request);
                ServerTransaction st = requestEvent.getServerTransaction();
                if(st == null){
                    st = sipProvider.getNewServerTransaction(request);
                }
                st.sendResponse(response);
            }
            else{
                CSeqHeader cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
                if(request.getMethod().equals(Request.ACK)){
                    if(cseq.getMethod().equals(Request.CANCEL)){
                        return;
                    }
                }

                Request newRequest = (Request) request.clone();
                newRequest.addFirst(via);
                newRequest.removeFirst(RouteHeader.NAME);
                sipProvider.sendRequest(newRequest);
            }
        }
        catch(Exception e){
            e.printStackTrace();
            System.out.println("Exception: " + e.toString());
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent){
        try{
            Response response = responseEvent.getResponse();
            SipProvider sipProvider = (SipProvider) responseEvent.getSource();
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

            FromHeader responseFrom = (FromHeader) response.getHeader("From");
            ToHeader responseTo = (ToHeader) response.getHeader("To");
            String responseFromHost = responseFrom.getAddress().toString().split("@")[1].split(">")[0].split(";")[0];
            String responseToHost = responseTo.getAddress().toString().split("@")[1].split(">")[0].split(";")[0];
            String responseFromName = responseFrom.toString().split("@")[0].split(":")[1].split("<")[1];
            String responseToName = responseTo.toString().split("@")[0].split(":")[1].split("<")[1];
            System.out.println("RESPONSE (type / from / to): " + response.getReasonPhrase() + " / " + responseFromHost + " / " + responseToHost);

            if(response.getStatusCode() == 100){
                return;
            }

            if(cseq.getMethod().equals(Request.INVITE) || cseq.getMethod().equals(Request.CANCEL)){
                ClientTransaction ct = responseEvent.getClientTransaction();
                if(ct != null){
                    if(cseq.getMethod().equals(Request.CANCEL) && response.getStatusCode() != 487){
                        return;
                    }

                    ServerTransaction st = (ServerTransaction) ct.getApplicationData();
                    Response newResponse = (Response) response.clone();
                    newResponse.removeFirst(ViaHeader.NAME);
                    st.sendResponse(newResponse);
                }
                else{
                    Response newResponse = (Response) response.clone();
                    newResponse.removeFirst(ViaHeader.NAME);
                    sipProvider.sendResponse(newResponse);
                }
            }
            else{
                if(cseq.getMethod().equals(Request.MESSAGE)){
                    CallIdHeader callId = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
                    if(proxyMessages.remove(callId.getCallId())){
                        return;
                    }
                }

                Response newResponse = (Response) response.clone();
                newResponse.removeFirst(ViaHeader.NAME);
                sipProvider.sendResponse(newResponse);
            }
        }
        catch(Exception e){
            System.out.println("Exception: " + e.toString());
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent){
        //System.out.println("Timeout occured");
    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent){
        //System.out.println("IOException occured");
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent){
        if(!transactionTerminatedEvent.isServerTransaction()){
            //System.out.println("TransactionTerminated occured: CLIENT");
            ClientTransaction ct = transactionTerminatedEvent.getClientTransaction();
            System.out.println("Terminated event: " + ct.getRequest().getMethod());
            //TODO only if cancel/invite ?
            for(String key : cts.keySet()){
                if(cts.get(key).equals(ct)){
                    cts.remove(key);
                }
            }
        }
        else{
            //System.out.println("TransactionTerminated occured: SERVER");
        }
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent){
        //System.out.println("DialogTerminated occured");
    }
}
