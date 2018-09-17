package activitystreamer.activityMsg;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;

public class ActivityMessage {
	private Connection con;

	public ActivityMessage(Connection c) {
		this.con = c;
	}

	public boolean handleActivityMsg(JSONObject msg) {
//		Map<String,Connection> allLoginClients = Control.getAllLogin();
//		Set<String> usernames = allLoginClients.keySet();
		ArrayList<String> allLoginUsers = Control.getLoginUserName();
		JSONArray actMsg = Control.getActMsg();
		Map<Connection,String> CurrLogin = Control.getLoginClients();
		ArrayList<String> usernames = new ArrayList<String>(allLoginUsers);

		if(msg.containsKey("username")&&msg.containsKey("secret")&&msg.containsKey("activity")) {
			System.out.println("ACTIVITY_MESSAGE RECEIVED");
			JSONObject updateMsg = new JSONObject();
			String originmsg = msg.toJSONString();
			//have to delete clients that connect to this server
			ArrayList<Connection> cons = Control.getInstance().getConnections();
			for (Connection client : CurrLogin.keySet()) {
				for(String key : allLoginUsers) {
					if(CurrLogin.get(client).equals(key)) {
						usernames.remove(key);
					}
				}
				JSONObject activity = new JSONObject();
				activity.put("command", "ACTIVITY_BROADCAST");
				JSONObject act = (JSONObject) msg.get("activity");
				act.put("authenticated_user", msg.get("username"));
				activity.put("activity", act);
				String act1 = activity.toJSONString();
				client.writeMsg(act1);
				System.out.println("message has send to "+CurrLogin.get(client));
			}
			//let the message send time be the unique ID
			updateMsg.put("command", "ACTIVITY_BROADCAST");
			DateFormat dateFormat = new SimpleDateFormat("yyyy/mm/dd HH:mm:ss");
			Date date = new Date();
			updateMsg.put("messageID", dateFormat.format(date));

			JSONObject activity2 = new JSONObject();
			activity2.put("command", "ACTIVITY_BROADCAST");
			JSONObject act2 = (JSONObject) msg.get("activity");
			act2.put("authenticated_user", msg.get("username"));
			activity2.put("activity", act2);
			String act3 = activity2.toJSONString();

			updateMsg.put("message", act3);
			updateMsg.put("clientsExpected", usernames);
			String updatemsg = updateMsg.toJSONString();
			//broadcast updatemsg to servers
			for (Connection Con : cons) {
				if(Con.getServer()) {
					Con.writeMsg(updatemsg);
				}
			}
			//store this activity message to local storage
			JSONObject newMsg = new JSONObject();
			newMsg.put("messageID", updateMsg.get("messageID"));
			newMsg.put("message", updateMsg.get("message"));
			newMsg.put("clientsExpected", updateMsg.get("clientsExpected"));
			actMsg.add(newMsg);
			System.out.println("----------MESSAGE CACHE----------");
			System.out.println(Control.getActMsg());
			return false;
		}else {
			return miss_component();
		}
	}

	public boolean miss_component() {
		JSONObject miss = new JSONObject();
		miss.put("command", "INVALID_MESSAGE");
		miss.put("info", "the received message did not contain a username/secret/activity");
		String miss_Info = miss.toJSONString();
		System.out.println(miss_Info);
		con.writeMsg(miss_Info);
		return true;
	}
}
