package activitystreamer.client;

import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import javax.swing.JOptionPane;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class ClientSkeleton extends Thread {
    private static final Logger log = LogManager.getLogger();
    private static ClientSkeleton clientSolution;
    private TextFrame textFrame;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private PrintWriter outwriter;
    private boolean term = false;


    public ClientSkeleton() {

        try {
            if (term == false) {
                socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                inreader = new BufferedReader(new InputStreamReader(in));
                outwriter = new PrintWriter(out, true);
                JSONObject newCommand = new JSONObject(); //
                if (Settings.getUsername().equals("") || Settings.getUsername().equals("anonymous")) {
                    Settings.setUsername("anonymous");
                    System.out.println("anonymous");
                    newCommand.put("command", "LOGIN");
                    newCommand.put("username", "anonymous");
                    newCommand.put("secret", Settings.getSecret());
                    String newJson = newCommand.toJSONString();
                    outwriter.println(newJson);
                    outwriter.flush();
                } else if (Settings.getSecret().equals("") || Settings.getSecret().equals("")) {
                    String nextSecret =Settings.nextSecret();
                    newCommand.put("command", "REGISTER");
                    newCommand.put("username", Settings.getUsername());
                    newCommand.put("secret", nextSecret);
                    String newJson = newCommand.toJSONString();
                    outwriter.println(newJson);
                    outwriter.flush();
                    Settings.setSecret(nextSecret);
                    JOptionPane.showMessageDialog(null,"Your secret is "+nextSecret);
                    System.out.println("Your secret is "+nextSecret);
                } else {
                    newCommand.put("command", "LOGIN");
                    newCommand.put("username", Settings.getUsername());
                    newCommand.put("secret", Settings.getSecret());
                    String newJson = newCommand.toJSONString();
                    outwriter.println(newJson);
                    outwriter.flush();
                }


            }
        } catch (UnknownHostException e) {

        } catch (IOException e) {

        }
        textFrame = new TextFrame();
        start();
    }

    public static ClientSkeleton getInstance() {
        if (clientSolution == null) {
            clientSolution = new ClientSkeleton();
        }
        return clientSolution;
    }

    @SuppressWarnings("unchecked")
    public void sendActivityObject(JSONObject activityObj) {
        JSONObject newCommand = new JSONObject();
        newCommand.put("command", "ACTIVITY_MESSAGE");
        newCommand.put("username", Settings.getUsername());
        newCommand.put("secret", Settings.getSecret());
        newCommand.put("activity", activityObj);
        String newJson = newCommand.toJSONString();

        outwriter.println(newJson);
        outwriter.flush();

    }


    public void disconnect() {
        System.out.println("disconnect");
        try {
            term = true;
            inreader.close();
            out.close();
            System.exit(0);
        } catch (IOException e) {
        }

    }

    public void logout() {
        System.out.println("logout");
        JSONObject newCommand = new JSONObject();
        newCommand.put("command", "LOGOUT");
        String newJson = newCommand.toJSONString();
        outwriter.println(newJson);
        outwriter.flush();
        disconnect();

    }


    public void run() {
        try {
            String data;
            while (!term && (data = inreader.readLine()) != null) {
                term = ClientSkeleton.getInstance().process(this, data);
            }
            log.debug("connection closed to " + Settings.socketAddress(socket));
            ClientSkeleton.getInstance().disconnect();
            in.close();
        } catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            ClientSkeleton.getInstance().disconnect();
        }
    }

    public synchronized boolean process(ClientSkeleton con, String msg) {
        JSONObject command = (JSONObject) JSONValue.parse(msg);
        if (command.get("command").equals("REGISTER_SUCCESS")) {
            System.out.println("REGISTER_SUCCESS");
            JSONObject newCommand = new JSONObject();
            newCommand.put("command", "LOGIN");
            newCommand.put("username", Settings.getUsername());
            newCommand.put("secret", Settings.getSecret());
            String newJson = newCommand.toJSONString();
            outwriter.println(newJson);
            outwriter.flush();
            return false;

        } else if (command.get("command").equals("INVALID_MESSAGE")) {
            log.info(command.get("info"));
            return true;
        } else if (command.get("command").equals("LOGIN_SUCCESS")) {
            log.info("LOGIN_SUCCESS" + command.get("info"));
            return false;
        } else if (command.get("command").equals("REDIRECT")) {
            System.out.println("COMMAND"+command);
            log.info("REDIRECT" + msg);
            String hostname = command.get("hostname").toString();
            int port = Integer.parseInt(command.get("port").toString());
            Settings.setRemoteHostname(hostname);
            Settings.setRemotePort(port);

            try {
                socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                inreader = new BufferedReader(new InputStreamReader(in));
                outwriter = new PrintWriter(out, true);
            } catch (UnknownHostException e) {

            } catch (IOException e) {

            }
            JSONObject newCommand = new JSONObject();
            newCommand.put("command", "LOGIN");
            newCommand.put("username", Settings.getUsername());
            newCommand.put("secret", Settings.getSecret());
            String newJson = newCommand.toJSONString();
            outwriter.println(newJson);
            outwriter.flush();
            return false;
        } else if (command.get("command").equals("LOGIN_FAILED")) {
            System.out.println("LOGIN_FAILED");
            log.info(command.get("info"));
            return true;
        } else if (command.get("command").equals("ACTIVITY_BROADCAST")) {
            log.info("ACTIVITY_BROADCAST " + command.toJSONString());
            textFrame.setOutputText(command);
            return false;
        } else if (command.get("command").equals("REGISTER_FAILED")) {
            log.info("REGISTER_FAILED " + command.get("info"));
            textFrame.setOutputText(command);
            return true;
        } else if (command.get("command").equals("ALIVE_MSG")) {
            log.info("AVLIVE_MSG");
            return false;
        }else {
            System.out.println("COMMAND"+command);
            log.info(command.get("info").toString());
            textFrame.setOutputText((JSONObject) command.get("info"));
            return true;
        }

    }
}
