package activitystreamer.activityMsg;

import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;

public class ActivityBroadcast {
	private Connection con;
	
	public ActivityBroadcast(Connection c) {
		this.con = c;
	}
	
	public boolean exist(String s, ArrayList<String> ss) {
		for(String str : ss) {
			if(s.equals(str)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean handleActivityBC(JSONObject msg) {
		System.out.println("ACTIVITY_BROADCAST RECEIVED");
		System.out.println(msg.toJSONString());
		JSONArray actMsg = Control.getActMsg();
		JSONArray actMSG = (JSONArray) actMsg.clone();
		Map<Connection,String> CurrLogin = Control.getLoginClients();
		
		List<Connection> allCons = Control.getInstance().getConnections();
		List<Connection> servers = new ArrayList<Connection>();
		
		ArrayList<String> usernames = (ArrayList<String>) msg.get("clientsExpected");
		ArrayList<String> originUsers = new ArrayList<String>(usernames);
		String originmsg = (String) msg.get("message");
		for(Connection c : allCons) {
			if(c.getServer()) {
				servers.add(c);
			}
		}
		//if this id exists in local storage, delete the clients in the local
		//storage that the received message does not have.
		for(Object o : actMsg) {
			
			JSONObject localMsg = (JSONObject) o;
			if(msg.get("messageID").equals(localMsg.get("messageID"))) {
				System.out.println("this id exists in local storage");
				ArrayList<String> localUsernames = (ArrayList<String>) localMsg.get("clientsExpected");
				ArrayList<String> updatelocal = new ArrayList<String>(localUsernames);
				for(String s1 : localUsernames) {
					if(!exist(s1,originUsers)) {
						updatelocal.remove(s1);
					}
				}
				localMsg.put("clientsExpected", updatelocal);
				return false;
			}
		}
		//if this id does not exist in local storage, usual operation
		System.out.println("this id does not exist in local storage");
		Set<Connection> CurrLoginCopy = new HashSet<Connection>(CurrLogin.keySet());
		for(Connection client : CurrLoginCopy) {
			System.out.println("there are clients connect to this server");
			try {
				client.getSocket().sendUrgentData(1);
				//connection is not broken or client did not logout
				//send original activity message to client
				
				
				//and delete the username of this client from msg
				for(String key : originUsers) {
					if(CurrLogin.get(client).equals(key)) {
						usernames.remove(key);
						JSONObject activity = new JSONObject();
						JSONObject message = (JSONObject) (new JSONParser().parse(originmsg));
						activity.put("command", "ACTIVITY_BROADCAST");
						activity.put("activity", message.get("activity"));
						String act = activity.toJSONString();
						client.writeMsg(act);
						System.out.println("message has send to "+CurrLogin.get(client));
					}
				}
			}catch(Exception e){
//				//connection is broken or client logout
//				System.out.println("A client has offline.");
				CurrLogin.keySet().remove(client);
			}
		}
		
		//if all the clients has received this message, then delete it
		//from local storage
		if(usernames.isEmpty()) {
			System.out.println("the usernames is empty");
			for(Object o : actMsg) {
				JSONObject jo = (JSONObject) o;
				if(jo.get("messageID").equals(msg.get("messageID"))) {
					actMSG.remove(jo);
				}
			}
		}else {
			//if not, replace with the new msg
//			for(Object o : actMSG) {
//				JSONObject jo = (JSONObject) o;
//				if(jo.get("messageID").equals(msg.get("messageID"))) {
//					jo.put("clientsExpected", usernames);
//				}
//			}
			JSONObject jo = new JSONObject();
			jo.put("messageID", msg.get("messageID"));
			jo.put("message", msg.get("message"));
			jo.put("clientsExpected", usernames);
			actMSG.add(jo);
			System.out.println("the usernames is not empty");
			System.out.println(usernames);
			System.out.println(actMSG);
		}
		Control.setActMsg(actMSG);
		System.out.println("----------MESSAGE CACHE 2----------");
		System.out.println(Control.getActMsg());
		//update the activity message
		JSONObject updateMsg = new JSONObject();
		updateMsg.put("command", "ACTIVITY_BROADCAST");
		updateMsg.put("messageID", msg.get("messageID"));
		updateMsg.put("message", msg.get("message"));
		updateMsg.put("clientsExpected", usernames);
		String updatemsg = updateMsg.toJSONString();
		//if usernames are changed, new message needs to be broadcast
		//to every server this server connects to
		if(!usernames.equals(originUsers)) {
			for(Connection server : servers) {
				server.writeMsg(updatemsg);
			}
		}else {
			//if not, broadcast to servers except the incoming one
			for(Connection server : servers) {
				if(!con.equals(server)) {
					server.writeMsg(updatemsg);
				}
			}
		}
		
	return false;	
	}
}
