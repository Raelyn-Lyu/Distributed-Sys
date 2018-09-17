package activitystreamer.register;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Map;

public class LockAllowed {
    private Connection con;

    public LockAllowed(Connection c) {
        this.con = c;
    }

    public boolean handleLockAllowed(JSONObject msg) {
        if (msg.containsKey("username") && msg.containsKey("secret")) {
            String newName = (String) msg.get("username");
            String newSecret = (String) msg.get("secret");
            JSONArray allowNums = Control.getAllowNums();
            JSONArray userInfo = Control.getUserInfo();
            int serverNum = Control.getLoadMap().keySet().size();
            int count = 0;
            for (int i = 0; i < allowNums.size(); i++) {
                JSONObject allowNum = (JSONObject) allowNums.get(i);
                if (allowNum.get("username").equals(newName)
                        && allowNum.get("secret").equals(newSecret)) {
                    count = Integer.parseInt(allowNum.get("number").toString());
                    count++;
                    allowNum.put("number", count);
                    break;
                }
            }

            Map<String, Connection> curr_regis = Control.getCurrRegis();
            JSONObject regisUser = new JSONObject();
            regisUser.put("username", newName);
            regisUser.put("secret", newSecret);
            Connection regisServer = curr_regis.get(regisUser.toJSONString());

            int countAllow = 0;

            for (int i = 0; i < allowNums.size(); i++) {
                JSONObject allowNum = (JSONObject) allowNums.get(i);
                if (allowNum.get("username").equals(newName)
                        && allowNum.get("secret").equals(newSecret)) {
                    countAllow = Integer.parseInt(String.valueOf(allowNum.get("number")));
                }
            }
            if (serverNum == countAllow) {

                remove_countAllow(allowNums, newName, newSecret);
                record_newuser(userInfo, newName, newSecret);
                return register_success(newName, regisServer);
            }
            broadcastALLOWED(newName, newSecret);
            return false;
        } else {
            return miss_component();
        }
    }

    public JSONArray remove_countAllow(JSONArray allowNums, String username, String secret) {
        for (int i = 0; i < allowNums.size(); i++) {
            JSONObject allowNum = (JSONObject) allowNums.get(i);
            String name = (String) allowNum.get("username");
            String s = (String) allowNum.get("secret");
            if (name.equals(username) && s.equals(secret)) {
                allowNums.remove(allowNum);
                return allowNums;
            }
        }
        return allowNums;
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

    public JSONArray record_newuser(JSONArray info, String username, String secret) {
        JSONObject user = new JSONObject();
        user.put("username", username);
        user.put("secret", secret);
        info.add(user);
        return info;
    }

    public boolean register_success(String username, Connection c) {
        JSONObject success = new JSONObject();
        success.put("command", "REGISTER_SUCCESS");
        success.put("info", "register success for " + username);
        String success_Info = success.toJSONString();
        System.out.println(success_Info);
        c.writeMsg(success_Info);
        return false;
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
}
