package activitystreamer.register;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Register {
    private Connection con;

    public Register(Connection c) {
        this.con = c;
    }

    public boolean handleRegister(JSONObject msg) {
        JSONArray userInfo = new JSONArray();
        userInfo = Control.getUserInfo();
        List<Connection> loggedIn = new ArrayList<Connection>();
        loggedIn = Control.getClients();

        if (msg.containsKey("username") && msg.containsKey("secret")) {
            String newName = (String) msg.get("username");
            String newSecret = (String) msg.get("secret");
            //second, test if receiving a REGISTER message from a client
            //that has already logged in on this **connection
            if (judge_already_login(loggedIn)) {

                return already_login();
            }

            //if it is in the local storage, failed
            for (int i = 0; i < userInfo.size(); i++) {
                JSONObject userA = (JSONObject) userInfo.get(i);
                String usernameA = (String) userA.get("username");
                //String secretA = (String)userA.get("secret");
                if (newName.equals(usernameA)) {
                    return register_failed(newName, con);
                }
            }
            //make a note of all the servers except the current
            //that is handling the REGISTER
            JSONObject allowCount = new JSONObject();
            allowCount.put("username", newName);
            allowCount.put("secret", newSecret);
            allowCount.put("number", "0");
            Control.getAllowNums().add(allowCount);
            //broadcast lock_request to all other servers that linked to it
            JSONObject lock_request = new JSONObject();
            lock_request.put("command", "LOCK_REQUEST");
            lock_request.put("username", newName);
            lock_request.put("secret", newSecret);
            String lock_Request = lock_request.toJSONString();
            ArrayList<Connection> Connections = Control.getInstance().getConnections();
            //if there is only one server in the system, there is no need to send lock_request
            int sizeOfServerCon = 0;
            for (Connection c : Connections) {
                if (c.getServer()) {
                    sizeOfServerCon++;
                }
            }
            if (sizeOfServerCon == 0) {
                userInfo = record_newuser(userInfo, newName, newSecret);
                register_success(newName, con);
            } else {
                for (Connection c : Connections) {
                    if (c.getServer()) {
                        c.writeMsg(lock_Request);
                    }
                }
            }

            //mark the current server that has REGISTER
            Map<String, Connection> curr_regis = Control.getCurrRegis();
            JSONObject newUser = new JSONObject();
            newUser.put("username", newName);
            newUser.put("secret", newSecret);
            curr_regis.put(newUser.toJSONString(), con);
            return false;
        } else {
            return miss_component();
        }
    }

    public boolean judge_already_login(List<Connection> loggedIn) {
        if (loggedIn == null) return false;
        for (Connection c : loggedIn) {
            if (c.equals(con))
                return true;
        }
        return false;
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

    public boolean miss_component() {
        JSONObject miss = new JSONObject();
        miss.put("command", "INVALID_MESSAGE");
        miss.put("info", "the received message did not contain a username/secret or both");
        String miss_Info = miss.toJSONString();
        System.out.println(miss_Info);
        con.writeMsg(miss_Info);
        return true;
    }

    public boolean already_login() {
        JSONObject login = new JSONObject();
        login.put("command", "INVALID_MESSAGE");
        login.put("info", "the client has already logged in");
        String login_Info = login.toJSONString();
        System.out.println(login_Info);
        con.writeMsg(login_Info);
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
}
