package activitystreamer.login;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class Login {
    private Connection connection;

    public Login(Connection con) {

        connection = con;

    }

    public Boolean parseMsg(JSONObject msg) {
        if (msg.containsKey("username") && msg.get("username").equals("anonymous")) {//username anonymous
            JSONObject newCommand = new JSONObject();
            newCommand.put("command", "LOGIN_SUCCESS");
            newCommand.put("info", "logged in as anonymous");
            String newJson = newCommand.toJSONString();
            connection.writeMsg(newJson);
            Control.setClients(connection);
            return false;
        } else if (msg.containsKey("username") && msg.containsKey("secret")) {//not anonymous
            int exist = 0;
            JSONArray jsonArray = Control.getUserInfo();
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject object = (JSONObject) jsonArray.get(i);
                String name = object.get("username").toString();
                String secret = (String) object.get("secret").toString();
                if (msg.get("username").equals(name)) {
                    if (msg.get("secret").equals(secret)) {//log in success
                        exist = 1;
                        break;
                    } else {//secret is wrong
                        JSONObject newCommand = new JSONObject();
                        newCommand.put("command", "LOGIN_FAILED");
                        newCommand.put("info", "attempt to ligin with wrong secret");
                        String newJson = newCommand.toJSONString();
                        connection.writeMsg(newJson);
                        return true;
                    }
                }
            }
            if (exist == 0) {//username did not register

                JSONObject newCommand = new JSONObject();
                newCommand.put("command", "LOGIN_FAILED");
                newCommand.put("info", "the username " + msg.get("username") + " is not found");
                String newJson = newCommand.toJSONString();
                connection.writeMsg(newJson);
                return true;
            } else {//login success
                System.out.println("success");
                Control.setClients(connection);
                JSONObject newCommand = new JSONObject();
                newCommand.put("command", "LOGIN_SUCCESS");
                newCommand.put("info", "logged in as user " + msg.get("username"));
                String newJson = newCommand.toJSONString();
                connection.writeMsg(newJson);
                return false;
            }
        } else {//INVALID MESSAGE
            JSONObject newCommand = new JSONObject();
            newCommand.put("command", "INVALID_MESSAGE");
            newCommand.put("info", "the received message did not contain a command");
            String newJson = newCommand.toJSONString();
            connection.writeMsg(newJson);
            return true;
        }


    }
}
