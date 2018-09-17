package activitystreamer.server;

import activitystreamer.login.Login;
import activitystreamer.util.Settings;
import activitystreamer.activityMsg.ActivityBroadcast;
import activitystreamer.activityMsg.ActivityMessage;
import activitystreamer.authenticate.ServerAuth;
import activitystreamer.register.LockAllowed;
import activitystreamer.register.LockDenied;
import activitystreamer.register.LockRequest;
import activitystreamer.register.Register;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Control extends Thread {
    private static final Logger log = LogManager.getLogger();
    protected static Control control = null;
    private static ArrayList<Connection> connections;
    private static JSONObject loadMap = new JSONObject();//record the load of each server in the system
    private static boolean term = false;
    private static Listener listener;
    private static JSONArray allowNums = new JSONArray();
    private static String backupHost =null;
    private static int backupPort =0;

    //curr_regis is used to mark the client that send the register request,
    //String is his username, Connection is the client
    private static Map<String, Connection> curr_regis = new HashMap<String, Connection>();
    //userinfo is used to store the users' information in the local storage(memory)
    private static JSONArray userinfo = new JSONArray();
    //Login is used to store the users that have already logged in
    private static ArrayList<Connection> clients = new ArrayList<Connection>();
    private static JSONArray actMsg = new JSONArray();

    private static Map<Connection, String> loginclients = new HashMap<Connection, String>();
    private static ArrayList<String> loginUserName = new ArrayList<String>();


    public Control() {
        // initialize the connections array
        connections = new ArrayList<Connection>();
        initiateConnection();
        // start a listener
        try {
            listener = new Listener();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);

        }
        start();

    }

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
        }
        return control;
    }

    //userInfo
    public static void setBackupHost(String host) {
        backupHost = host;
    }
    public static void setBackupPort(int port) {
        backupPort = port;
    }

    public static String setBackupHost() {
        return backupHost;
    }
    public static int setBackupPort() {
        return backupPort;
    }

    public static JSONArray getActMsg(){
        return actMsg;
    }

    public static void setActMsg(JSONArray j) {
        actMsg = j;
    }

    public static void addClients(Connection c) {
        clients.add(c);
    }

    public static void removeClients(Connection c) {
        clients.remove(c);
    }
    public static ArrayList<String> getLoginUserName(){
        return loginUserName;
    }

    public static void addLoginUserName(String name) {
        loginUserName.add(name);
    }

    public static void removeLoginUserName(String name) {
        loginUserName.remove(name);
    }

    public static Map<Connection, String> getLoginClients() {
        return loginclients;
    }

    public static void addLoginClients( Connection c,String s) {
        loginclients.put(c, s);
    }

    public static void removeLoginClients(Connection c) {
        loginclients.remove(c);
    }

    public static JSONArray getUserInfo() {
        return userinfo;
    }

    public static void setUserInfo(JSONArray a) {
        userinfo = a;
    }

    //allowNums
    public static JSONArray getAllowNums() {
        return allowNums;
    }

    public static void setAllowNums(JSONArray a) {
        allowNums = a;
    }

    //currRegis
    public static Map<String, Connection> getCurrRegis() {
        return curr_regis;
    }

    public static void setCurrRegis(Map<String, Connection> y) {
        curr_regis = y;
    }

    public static JSONObject getLoadMap() {
        return loadMap;
    }

    public static ArrayList getClients() {
        return clients;
    }

    public static void setClients(Connection c) {
        clients.add(c);// TODO Auto-generated method stub

    }

    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteHostname() != null) {
            try {
                Connection newCon = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
                //TODO implement (finished)
                newCon.setSever();
                newCon.setOut();
                JSONObject newCommand = new JSONObject(); //send authentication req to remote server
                newCommand.put("command", "AUTHENTICATE");
                newCommand.put("secret", Settings.getSecret());
                String newJson = newCommand.toJSONString();
                newCon.writeMsg(newJson);


            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }

    }

    /*
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    public synchronized boolean process(Connection con, String msg) {
        //TODO (continued)
        JSONParser parser = new JSONParser(); //check parse
        try {
            parser.parse(msg);
        } catch (ParseException e) {
            JSONObject newCommand = new JSONObject();
            newCommand.put("command", "INVALID_MESSAGE");
            newCommand.put("info", "JSON parse error while parsing message");
            String newJson = newCommand.toJSONString();
            return con.writeMsg(newJson);
        }
//
        JSONObject command = (JSONObject) JSONValue.parse(msg);
        if(command.get("command").equals("AUTHENTICATE")){
            con.setNewCon(true);
            ServerAuth auth = new ServerAuth(con,command);
            con.setSever();
            return auth.parseMsg(command);

        }else if(command.get("command").equals("BACKUP")){
            if(!command.get("host").toString().equals(Settings.getLocalHostname())||
                    Integer.parseInt(command.get("port").toString())!= Settings.getLocalPort()){
                setBackupHost(command.get("host").toString());
                setBackupPort(Integer.parseInt(command.get("port").toString()));
            }
//            System.out.println(backupHost + " " + backupPort);
            return false;
        }else if (command.get("command").equals("AUTHENTICATION_FAIL")) {
            log.info(command.get("info"));
            return true;
        } else if (command.get("command").equals("INVALID_MESSAGE")) {
            log.info(command.get("info"));
            return true;
        } else if (command.get("command").equals("SERVER_ANNOUNCE")) {

            String id = command.get("id").toString();
            //send to all servers except the one that msg is sent from
            for (Connection load : connections) {
                if (!connections.contains(con)&&!con.getServer()) {
                    String newJson = command.toJSONString();
                    load.writeMsg(newJson);
                }
            }
            // if ID exists in list, upgrade it, or put it in list
            if (loadMap.containsKey(id)) {
                JSONObject body = (JSONObject) loadMap.get(id);
                body.put("hostname", command.get("hostname"));
                body.put("port", command.get("port"));
                body.put("load", command.get("load"));
            } else { //else add its load and server info to the list
                JSONObject newCommand = new JSONObject();
                newCommand.put("hostname", command.get("hostname"));
                newCommand.put("port", command.get("port"));
                newCommand.put("load", command.get("load"));
                loadMap.put(id, newCommand);
            }
            return false;
        } else if (command.get("command").equals("REGISTER")) {
            log.info("REGISTER come in");
            Register regist = new Register(con);
            return regist.handleRegister(command);
        } else if (command.get("command").equals("LOCK_REQUEST")) {
            log.info("LOCK_REQUEST  ----------------------");
            LockRequest lockRequest = new LockRequest(con);
            return lockRequest.handleLockRequest(con, command);
        } else if (command.get("command").equals("LOCK_DENIED")) {
            LockDenied denied = new LockDenied(con);
            return denied.handleLockDenied(command);
        } else if (command.get("command").equals("LOCK_ALLOWED")) {

            LockAllowed allowed = new LockAllowed(con);
            return allowed.handleLockAllowed(command);

        } else if (command.get("command").equals("LOGIN")) {
            System.out.println("login request");
            System.out.println(command);
            Login login = new Login(con);
            if (!login.parseMsg(command)) {
                JSONObject rdr = filter(); //check the load
                if (rdr.containsKey("command")) {
                    //if there is load is exceeded, close connection send redirection.
                    String newJson = rdr.toJSONString();
                    con.writeMsg(newJson);
//                    System.out.println(newJson);
                    return true;
                } else {
                    Control.addClients(con);
                    Control.addLoginClients(con,command.get("username").toString());
                    Control.addLoginUserName(command.get("username").toString());
//                    System.out.println(loginclients.get(con));
                    for(Map.Entry<Connection, String> entry: loginclients.entrySet()) {
//                        System.out.println(entry.getValue());
                    }
                    JSONObject userCommand = new JSONObject();
                    userCommand.put("command", "USER_LOGIN");
                    userCommand.put("username", command.get("username"));
                    String newJson1 = userCommand.toJSONString();
                    for (Connection c : connections) {
                        if(c.getServer()) {
                            c.writeMsg(newJson1);
                        }
                    }
                    for(int i=0;i<loginUserName.size();i++) {
                        System.out.print(loginUserName.get(i));
                        System.out.println("----------");
                    }
                    //if there are messages that are not sent to the client
//                     messageID
//                     message
//                     clientExpected
                    JSONArray userStory = new JSONArray();
                    userStory = getActMsg();
                    //each message has a username list
                    ArrayList<String> username = new ArrayList<String>();
                    JSONObject onestory = new JSONObject();
                    for(int i =0; i<userStory.size();i++) {// pass through each message
                        onestory = (JSONObject) userStory.get(i);
                        username =(ArrayList<String>) onestory.get("clientsExpected");
                        ArrayList<String> usernameCopy = new ArrayList<String>(username);
                        System.out.println("ONESTORY");
                        System.out.println(onestory);
                        System.out.println("USERNAME");
                        System.out.println(username);
                        for(String s:usernameCopy) {
                            if(s.equals(command.get("username"))) {
                                JSONObject actmsg = new JSONObject();
                                String message = (String) onestory.get("message");
                                try {
                                    JSONObject M = (JSONObject) (new JSONParser().parse(message));
                                    actmsg.put("activity", M.get("activity"));
                                }catch(Exception e) {}
//                    			 String newJson2 = actmsg.toJSONString();
                                actmsg.put("command", "ACTIVITY_BROADCAST");

                                String newJson2 = actmsg.toJSONString();
//                                System.out.println("NEWJSON2");
//                                System.out.println(newJson2);
                                con.writeMsg(newJson2);
                                username.remove(s);
                                JSONObject newuserinfo = new JSONObject();
                                newuserinfo.put("command", "ACTIVITY_BROADCAST");
                                newuserinfo.put("messageID", onestory.get("messageID"));
                                newuserinfo.put("clientsExpected", username);
                                String newJSON = newuserinfo.toJSONString();
                                for(Connection c:connections) {
                                    if(c.getServer()) {
                                        c.writeMsg(newJSON);
                                    }else {

                                    }
                                }
                            }
                        }
                    }
                    return false;
                }
            } else {
                return true;
            }
        } else if (command.get("command").equals("ACTIVITY_BROADCAST")) {
            ActivityBroadcast actBC = new ActivityBroadcast(con);
            return actBC.handleActivityBC(command);
        } else if (command.get("command").equals("ACTIVITY_MESSAGE")) {
            ActivityMessage actMsg = new ActivityMessage(con);
            return actMsg.handleActivityMsg(command);
        } else if (command.get("command").equals("LOGOUT")) {
            System.out.println("logout");
            System.out.println(msg);
            JSONObject newCommand = new JSONObject();
            newCommand.put("command", "REMOVE_LOGINUSER");
            newCommand.put("username", loginclients.get(con));
            String newJson = newCommand.toJSONString();
//            System.out.println(newJson);
            for(Connection c :connections) {
                if(c.getServer()) {
                    c.writeMsg(newJson);
                }
            }
            removeLoginUserName(loginclients.get(con));
            removeLoginClients(con);
            removeClients(con);
            for(String s:loginUserName) {
                System.out.println(s);
            }
            return true;
        }else if(command.get("command").equals("USER_INFO")){
            ArrayList<String> loginInfo = (ArrayList<String>)command.get("lgArray");
            for (String a : loginInfo){
                if(!loginUserName.contains(a)){
                    loginUserName.add(a);
                }
            }
            String newob = command.get("jsArray").toString();
            JSONParser parser1 = new JSONParser();
            try {
                JSONArray newInfo = (JSONArray) parser1.parse(newob);
                    for (Object a : newInfo){
                        if(!userinfo.contains(a)){
                            userinfo.add(a);
                        }
                    }

            }catch (ParseException e2){}
//            System.out.println(userinfo);

            return false;
        } else if (command.get("command").equals("USER_LOGIN")) {
//            System.out.println(msg);
            //add client username into its own list
            addLoginUserName(command.get("username").toString());
            //then broadcast
            for(Connection c:connections) {
                if(c.getServer()&&!c.equals(con)) {
                    c.writeMsg(msg);
                }
            }
            for(int i=0;i<loginUserName.size();i++) {
                System.out.print(loginUserName.get(i));
                System.out.println("----------");
            }
            return false;
        }else if (command.get("command").equals("REMOVE_LOGINUSER")){
//            System.out.println(msg);
            removeLoginUserName(command.get("username").toString());
            for(Connection c: connections) {
                if(c.getServer()&&!c.equals(con)) {
                    c.writeMsg(msg);
                }
            }
            for(String s:loginUserName) {
                System.out.println(s);
            }
            return false;
        }else {            //可在IF,ELSE之间增加ELSE IF 语句， 判断登陆， 注册，等情况。
            return true;
        }
    }

//    public void updateLoad() {
//
//    }

    /*
     * Get local load
     */
    public int calcuLoad() {
        int count = 0;
        for (Connection con : connections) {
            if (!con.getServer()) {
                count += 1;
            }
        }
        return count;
    }

    /*
     * get the server load list if any server has 2 less load than local sever
     */
    public JSONObject filter() {
        int localLoad = calcuLoad();

        JSONObject rdtInfo = new JSONObject();
        for (Object key : loadMap.keySet()) {
//            System.out.println(loadMap);
            JSONObject body = (JSONObject) loadMap.get(key.toString());
            if (Integer.parseInt(body.get("load").toString()) < localLoad - 1) {
                try {
                    Connection newCon = outgoingConnection(new Socket(body.get("hostname").toString(), Integer.parseInt(body.get("port").toString())));
//                    System.out.println("success"+body.get("port").toString());
                    newCon.setTestS();
                    rdtInfo.put("command", "REDIRECT");
                    rdtInfo.put("hostname", body.get("hostname").toString());
                    rdtInfo.put("port", body.get("port").toString());
//                    newCon.closeCon();
                    connections.remove(newCon);
                    System.out.println("set redirect:" +rdtInfo);
                    return rdtInfo;
                }catch(IOException e){
                    System.out.println("exception");
                }
//                rdtInfo.put("command", "REDIRECT");
//                rdtInfo.put("hostname", body.get("hostname").toString());
//                rdtInfo.put("port", body.get("port").toString());

//                return rdtInfo;
            }
        }
        return rdtInfo;

    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed2(Connection con) {
        connections.remove(con);
    }
    public synchronized void connectionClosed(Connection con) {
        if (!term) {
            //if the crashed connection is outcomming, connect to backup server
            if(con.getConType()&&(con.getServer())&&(!backupHost.equals("unknow"))){
//                System.out.println(backupHost);
                try {
//                    System.out.println("it is not parent node");
                    Settings.setRemoteHostname(backupHost);//send authentication req to remote server
                    Settings.setRemotePort(backupPort);//send authentication req to remote server
                    Connection newCon = outgoingConnection(new Socket(backupHost, backupPort));
                    //TODO implement (finished)
                    newCon.setSever();
                    newCon.setOut();
                    JSONObject newCommand1 = new JSONObject();
                    newCommand1.put("command", "AUTHENTICATE");
                    newCommand1.put("secret", Settings.getSecret());
                    String newJson1 = newCommand1.toJSONString();
                    newCon.writeMsg(newJson1);

                } catch (IOException e1) {
                    log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e1);
                    System.exit(-1);
                }
                con.closeCon();
                connections.remove(con);
            }else if(con.getServer()){
                con.closeCon();
                connections.remove(con);
            }
        }
        connections.remove(con);
        ;
    }

    /*
     * A new incoming connection has been established, and a reference is returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);
        return c;

    }

    /*
     * A new outgoing connection has been established, and a reference is returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {
        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        connections.add(c);
//        System.out.println("new connection added");
        return c;

    }

    @Override
    public void run() {
        log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            /*
             * modified 2018/05/23
             */

            //check if there is any new connected server every 5 sec
            //if there is, send the activity broadcast messages in
            //the actMsg one by one, and set the newCon to false
            //if there is not, let it be.
            for(Connection server : connections) {
                if(server.getServer()&&server.getNewCon()) {
                    for(Object o : actMsg) {
                        JSONObject jo = (JSONObject) o;
                        JSONObject updateMsg = new JSONObject(jo);
                        updateMsg.put("command", "ACTIVITY_BROADCAST");
                        String updatemsg = updateMsg.toJSONString();
                        server.writeMsg(updatemsg);
                    }
                    server.setNewCon(false);
                }
            }
            //add user info
            JSONObject newUserInfo = new JSONObject();
            String userInfoJS = userinfo.toJSONString();
            System.out.println("userinfo"+userinfo);
            System.out.println("logininfo"+loginUserName);
            newUserInfo.put("command","USER_INFO");
            newUserInfo.put("jsArray",userInfoJS);
            newUserInfo.put("lgArray",loginUserName);
            String uiString = newUserInfo.toJSONString();
            for (Connection con : connections) {
                if (con.getServer()) {
                    con.writeMsg(uiString);
                }
            }
            // do something with 5 second intervals in between
            //do SERVER ANNOUNCE every 5 sec
            JSONObject newCommand = new JSONObject();
            newCommand.put("command", "SERVER_ANNOUNCE");
            newCommand.put("id", Settings.getLocalHostname() + ":" + Settings.getLocalPort());
            newCommand.put("load", calcuLoad());
            newCommand.put("hostname", Settings.getLocalHostname());
            newCommand.put("port", Settings.getLocalPort());
            String newJson = newCommand.toJSONString();
            //realtime clients balanced to each server
            JSONObject rdr = filter(); //check the load
            if (rdr.containsKey("command")) {
                //if there is load is exceeded, close connection send redirection.
                String baclaceInfo = rdr.toJSONString();
                for(Connection client:connections){
                    if (!client.getServer()) {
                        client.writeMsg(baclaceInfo);
                        client.closeCon();
                        return;
                    }
                }

            }

            JSONObject aliveMsg = new JSONObject();
            aliveMsg.put("command", "BACKUP");
            if(Settings.getRemoteHostname() == null){
                aliveMsg.put("host", "unknow");
            }else{
                aliveMsg.put("host", Settings.getRemoteHostname());
            }
            aliveMsg.put("port", Settings.getRemotePort());
            String Msg1 = aliveMsg.toJSONString();

            //TODO SERVER_ANNOUNCE
            for (Connection con : connections) {
                if (con.getServer()) {
                    con.writeMsg(newJson);
                    con.writeMsg(Msg1);
                    con.writeMsg(uiString);
                }
            }
//            System.out.println("size: "+connections.size());
//            ArrayList<Connection> connections2 = new ArrayList<Connection>(connections);
//            for (Connection con : connections2) {
//                if (con.getServer()) {
//                    try{
//                        con.getSocket().sendUrgentData(1);
//                    }catch (IOException e){
//                        System.out.println("exception");
//                        if(con.getConType()&&(con.getServer())&&(!backupHost.equals("unknow"))){
//                            System.out.println(backupHost);
//                            try {
////                                System.out.println("it is not parent node");
//                                Settings.setRemoteHostname(backupHost);//send authentication req to remote server
//                                Settings.setRemotePort(backupPort);//send authentication req to remote server
//                                Connection newCon = outgoingConnection(new Socket(backupHost, backupPort));
//                                System.out.println("outgoing?");
//                                System.out.println("new connection: "+newCon);
//                                //TODO implement (finished)
//                                newCon.setSever();
//                                newCon.setOut();
//                                JSONObject newCommand1 = new JSONObject();
//                                newCommand1.put("command", "AUTHENTICATE");
//                                newCommand1.put("secret", Settings.getSecret());
//                                String newJson1 = newCommand1.toJSONString();
//                                newCon.writeMsg(newJson1);
//
//                            } catch (IOException e1) {
//                                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e1);
//                                System.exit(-1);
//                            }
//                            con.closeCon();
//                            connections.remove(con);
//                        }else if(con.getServer()){
//                            con.closeCon();
//                            connections.remove(con);
//                        }
//                        con.closeCon();
//                        connections.remove(con);
//
//
//                    }
//
//                }
//            }
            ArrayList<Connection> connections2 = new ArrayList<Connection>(connections);
            for (Connection con : connections2) {
                if(con.getTestS()){
                    connections.remove(con);
                }
            }

            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
//				log.debug("doing activity");
                term = doActivity();
            }


        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
    }

    public boolean doActivity() {
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }


}
