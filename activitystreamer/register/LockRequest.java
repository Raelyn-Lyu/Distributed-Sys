package activitystreamer.register;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;

public class LockRequest {
    private Connection con;
    //private JSONArray userInfo;


    public LockRequest(Connection con) {
        this.con = con;
    }

    public boolean handleLockRequest(Connection con, JSONObject msg) {
        if (msg.containsKey("username") && msg.containsKey("secret")) {
            String newName = (String) msg.get("username");
            String newSecret = (String) msg.get("secret");
            JSONArray userInfo = new JSONArray();
            userInfo = Control.getUserInfo();
            //Broadcast from a server to all other servers (between servers only)
            //to indicate that a client is trying to register a username with a
            //given sercret.
            JSONObject lock_request = new JSONObject();
            lock_request.put("command", "LOCK_REQUEST");
            lock_request.put("username", newName);
            lock_request.put("secret", newSecret);
            String lock_Request = lock_request.toJSONString();
            broadcastLOCKREQUEST(lock_Request);
            //Broadcast a LOCK_DENIED to all other servers (between servers only)
            //if the username is already known to the server with a different secret.
            for (int i = 0; i < userInfo.size(); i++) {
                JSONObject userA = (JSONObject) userInfo.get(i);
                String usernameA = (String) userA.get("username");
                String secretA = (String) userA.get("secret");
                if ((newName.equals(usernameA)) && (!newSecret.equals(secretA))) {
                    broadcastDENIED(newName, newSecret);
                    return false;
                }
            }
            //Broadcast a LOCK_ALLOWED to all other servers (between servers only)
            //if the username is not already known to the server. The server will
            //record this username and secret pair in its local storage.
            userInfo = record_newuser(userInfo, newName, newSecret);
            broadcastALLOWED(newName, newSecret);
            return false;
        } else {
            return miss_component();
        }
    }

    public boolean miss_component() {
        JSONObject miss = new JSONObject();
        miss.put("command", "INVALID_MESSAGE");
        miss.put("info", "the received message did not contain a username/secret or both");
        String miss_Info = miss.toJSONString();
        System.out.println(miss_Info);
        con.writeMsg(miss_Info);
        return true;
    }

    public void broadcastDENIED(String username, String secret) {
        JSONObject lock_denied = new JSONObject();
        lock_denied.put("command", "LOCK_DENIED");
        lock_denied.put("username", username);
        lock_denied.put("secret", secret);
        String lock_Denied = lock_denied.toJSONString();
        broadcast(lock_Denied);
    }

    public void broadcastALLOWED(String username, String secret) {
        JSONObject lock_allowed = new JSONObject();
        lock_allowed.put("command", "LOCK_ALLOWED");
        lock_allowed.put("username", username);
        lock_allowed.put("secret", secret);
        String lock_Allowed = lock_allowed.toJSONString();
        broadcast(lock_Allowed);
    }

    public void broadcast(String msg) {
        ArrayList<Connection> otherServers = Control.getInstance().getConnections();
        //if the server does not connect to other servers except the one it receives
        //messages from, then it directly write msg back to that one.
        int sizeOfServerCon = 0;
        for (Connection serverCon : otherServers) {
            if (serverCon.getServer()) {
                sizeOfServerCon++;
            }
        }

        if (sizeOfServerCon == 1) {
            con.writeMsg(msg);
        } else {
            for (Connection serverCon : otherServers) {
                if (!con.equals(serverCon) && serverCon.getServer())
                    serverCon.writeMsg(msg);
            }
        }
    }

    public void broadcastLOCKREQUEST(String msg) {
        ArrayList<Connection> otherServers = Control.getInstance().getConnections();
        for (Connection serverCon : otherServers) {
            if (!con.equals(serverCon) && serverCon.getServer())
                serverCon.writeMsg(msg);
        }
    }


    public JSONArray record_newuser(JSONArray info, String username, String secret) {
        JSONObject user = new JSONObject();
        user.put("username", username);
        user.put("secret", secret);
        info.add(user);
        return info;
    }

}


