//check authentication
package activitystreamer.authenticate;

import activitystreamer.server.Connection;
import activitystreamer.util.Settings;
import org.json.simple.JSONObject;

public class ServerAuth {
    private Connection connection;
    private JSONObject msg;

    public ServerAuth(Connection con, JSONObject msg) {
        this.msg = msg;
        this.connection = con;
    }


    public Boolean parseMsg(JSONObject msg) {
        if (msg.containsKey("secret") && !msg.get("secret").equals("")) {
//            System.out.println("1");
            if (msg.get("secret").equals(Settings.getSecret())) {
                //if secret is right, keep connection
                return false;
            } else {
//                System.out.println("2");
                JSONObject newCommand = new JSONObject();
                newCommand.put("command", "AUTHENTICATION_FAIL"); //if wrong secret, close connection
                newCommand.put("info", "the supplied secret is incorrect: " + msg.get("secret"));
                String newJson = newCommand.toJSONString();
                connection.writeMsg(newJson);
                return true;
            }
        } else {
//            System.out.println("3");
            JSONObject newCommand = new JSONObject();   //lacking of secret command
            newCommand.put("command", "INVALID_MESSAGE");
            newCommand.put("info", "the received message did not contain a secret");
            String newJson = newCommand.toJSONString();
            connection.writeMsg(newJson);
            return true;
        }
    }


}
