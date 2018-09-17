package activitystreamer.register;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Map;

public class LockDenied {
    private Connection con;

    public LockDenied(Connection con) {
        this.con = con;
    }

    public boolean handleLockDenied(JSONObject msg) {
        if (msg.containsKey("username") && msg.containsKey("secret")) {
            String newName = (String) msg.get("username");
            String newSecret = (String) msg.get("secret");
            JSONArray userInfo = new JSONArray();
            userInfo = Control.getUserInfo();
            Map<String, Connection> curr_regis = Control.getCurrRegis();
            JSONObject regisUser = new JSONObject();
            regisUser.put("username", newName);
            regisUser.put("secret", newSecret);
            Connection regisServer = curr_regis.get(regisUser.toJSONString());

            for (int i = 0; i < userInfo.size(); i++) {
                JSONObject user = new JSONObject();
                user = (JSONObject) userInfo.get(i);
                if (newName.equals(user.get("username"))
                        && newSecret.equals(user.get("secret"))) {
                    userInfo = remove_user(userInfo, newName, newSecret);
                }
            }
            if (con.equals(regisServer)) {
                return register_failed(newName, regisServer);
            }
            broadcastDENIED(newName, newSecret);
            return false;
        } else {
            return miss_component();
        }
    }

    public JSONArray remove_user(JSONArray userInfo, String username, String secret) {
        for (int i = 0; i < userInfo.size(); i++) {
            JSONObject userA = (JSONObject) userInfo.get(i);
            String Aname = userA.get("username").toString();
            String Asecret = userA.get("secret").toString();
            if (Aname.equals(username) && Asecret.equals(secret)) {
                userInfo.remove(userA);
                break;
            }
        }
        return userInfo;
    }

    public boolean register_failed(String username, Connection c) {
        JSONObject failed = new JSONObject();
        failed.put("command", "REGISTER_FAILED");
        failed.put("info", username + " is already registered with the system");
        String failed_Info = failed.toJSONString();
        System.out.println(failed_Info);
        c.writeMsg(failed_Info);
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

    public boolean miss_component() {
        JSONObject miss = new JSONObject();
        miss.put("command", "INVALID_MESSAGE");
        miss.put("info", "the received message did not contain a username/secret or both");
        String miss_Info = miss.toJSONString();
        System.out.println(miss_Info);
        con.writeMsg(miss_Info);
        return true;
    }
}
