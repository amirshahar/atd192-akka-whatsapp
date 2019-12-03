import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.remote.WireFormats;

import javax.swing.plaf.synth.SynthDesktopIconUI;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

public class Manager extends AbstractActor {
    private HashMap<String, ActorRef> connectedUsers;
    private HashMap<String, ActorRef> groups;
    private Map<Client.CommandType, Function<User.Command, String>> commMap = new HashMap<>();
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    static public class Actions implements Serializable {
        Dictionary<String,Object> payload;
        Client.CommandType commType;
        Client.MassageType msgType;
        public Actions() {
            this.payload = new Hashtable<>();
        }

        public Actions(Client.CommandType commType,Client.MassageType msgType) {
            this();
            this.commType = commType;
            this.msgType = msgType;
        }

        public void addMember(String key, Object val){
            payload.put(key,val);
        }
        public void setPayload(Dictionary<String,Object> payload) {
            this.payload = payload;
        }
        private void removeMember(String key){
            payload.remove(key);
        }
        public void replaceValue(String key, Object val) {
            this.payload.remove(key);
            this.payload.put(key,val);
        }
    }

    static public Props props(){
        return Props.create(Manager.class, () -> new Manager());
    }

    public void commandMapping() {
        //Mapping Command to commandHandler
        commMap.put(Client.CommandType.connect, command -> connectionHandler(command));
        commMap.put(Client.CommandType.disconnect, command -> disconnectionHandler(command));
        commMap.put(Client.CommandType.text, command -> U2UMsgHandler(command));
        commMap.put(Client.CommandType.sendText, command -> U2GMsgHandler(command));

        /*
        commMap.put(Client.CommandType.text, command -> U2UMsgHandler(command));
        commMap.put(Client.CommandType.sendText, command -> U2GMsgHandler(command));
        commMap.put(Client.CommandType.file, command ->
            (command.payload.get("msgType").equals(Client.MassageType.user)) ?
                    U2UMsgHandler(command)
                    : U2GMsgHandler(command)
        );

        commMap.put(Client.CommandType.text,(command ->
                (command.payload.get("msgType").equals(Client.MassageType.user)) ?
                        U2UMsgHandler(command)
                        : U2GMsgHandler(command)
        ));
*/

        commMap.put(Client.CommandType.create,command -> createGroup(command));
        commMap.put(Client.CommandType.invite, command -> userGroup(command));
        commMap.put(Client.CommandType.remove, command -> userGroup(command));
        commMap.put(Client.CommandType.mute, command -> userGroup(command));
        commMap.put(Client.CommandType.leave, command -> userLeaveGroup(command));
        commMap.put(Client.CommandType.unmute, command -> userGroup(command));
        commMap.put(Client.CommandType.coadmin, command -> userGroup(command));

    }


    private String createGroup(User.Command command) {
        if (this.groups.containsKey((String)command.payload.get("groupName"))) {
            System.out.println("Debug!!");
            Actions action = new Actions((Client.CommandType)command.payload.get("commType"),
                        (Client.MassageType)command.payload.get("msgType"));
            action.addMember("msg",String.format("Group %s already exists.",
                                  (String) command.payload.get("groupName")));
            getSender().tell(action,getSelf());
            return "";
        }
//        System.out.println((command.command).toString());
        ActorRef newGroupActor = getContext().actorOf(
                Group.props((String)command.payload.get("groupName"),
                            (String)command.payload.get("username")),
                (String)command.payload.get("groupName"));
        this.groups.put((String)command.payload.get("groupName"), newGroupActor);
        newGroupActor.forward(command, getContext());
        return "";
    }

    private boolean groupIsExists(User.Command command, Actions action){
        if (!this.groups.containsKey((String)command.payload.get("groupName"))) {
            action.addMember("msg",String.format("%s group isn't exists.",
                                                 (String) command.payload.get("groupName")));
            action.addMember("success", false);
            getSender().tell(action,getSelf());
            return false;
        }
        return true;
    }
    private boolean userIsExists(User.Command command, Actions action){
        if(!this.connectedUsers.containsKey((String)command.payload.get("username"))){
            //System.out.println("InviteUser Manager Debug - Enter To Second If");
            action.addMember("msg",String.format("%s isn't exists.",
                                                 (String) command.payload.get("username")));
            action.addMember("success", false);
            getSender().tell(action,getSelf());
            return false;
        }
        return true;
    }
    private String userLeaveGroup(User.Command command) {
        //System.out.println("InviteUser Manager Debug");
        Actions action = new Actions((Client.CommandType) command.payload.get("commType"),
                                     (Client.MassageType) command.payload.get("msgType"));
        if(groupIsExists(command,action))
            if(userIsExists(command,action))
                command.payload.put("targetRef", connectedUsers.get((String) command.payload.get("username")));
                this.groups.get((String) command.payload.get("groupName")).tell(command, ActorRef.noSender());
        return "";
    }
    private String userGroupR(User.Command command, Runnable res) {
        Actions action = new Actions((Client.CommandType)command.payload.get("commType"),
                                     (Client.MassageType)command.payload.get("msgType"));
        res.run();
        return "";
    }
    private String userGroup(User.Command command) {
        //System.out.println("InviteUser Manager Debug");
        Actions action = new Actions((Client.CommandType)command.payload.get("commType"),
                                     (Client.MassageType)command.payload.get("msgType"));
        if(groupIsExists(command,action))
            if(userIsExists(command,action)) {
                command.payload.put("targetRef", connectedUsers.get((String) command.payload.get("targetUsername")));
                command.payload.put("userSourceRef", connectedUsers.get((String) command.payload.get("username")));
                this.groups.get((String) command.payload.get("groupName")).tell(command, ActorRef.noSender());
                /*
                action.addMember("success", true);
                action.addMember("groupName", (String) command.payload.get("groupName"));
                action.addMember("targetUsername", (String) command.payload.get("targetUsername"));
                groups.get((String)command.payload.get("groupName")).forward(command, getContext());
                forwardMessageToGroup((String)command.payload.get("groupName"),action);
                 */
            }
        if(((Client.CommandType)command.payload.get("commType")).equals(Client.CommandType.coadmin)) {
            if (command.payload.get("commTypeSub").equals("add"))
                action.addMember("msg", String.format("%s added to be coAdmin the %s group",
                                                      (String) command.payload.get("targetUsername"),
                                                      (String) command.payload.get("groupName")));
            else
                action.addMember("msg", String.format("%s no longer coAdmin the %s group",
                                                      (String) command.payload.get("targetUsername"),
                                                      (String) command.payload.get("groupName")));
        }
        action.addMember("success", true);
        if(action.payload.get("msg") != "" && action.payload.get("msg") != null)
            getSender().tell(action,ActorRef.noSender());
        return "";
    }

    private void forwardMessageToGroup(String groupName, Object message) {
        groups.get(groupName).forward(message, getContext());
    }

    private String U2GMsgHandler(User.Command command) {
        //System.out.println("~~~~!!!!!~~~~");
        //String msg;
        Actions action = new Actions();
        action.setPayload(command.payload);
        action.replaceValue("commType", Client.CommandType.text);
        //action.addMember("time",new Date());
        /*
        System.out.println(command.payload.get("commType"));
        if (!this.groups.containsKey((String)command.payload.get("groupName"))){
            msg = String.format("%s isn't exists.", ((String)command.payload.get("groupName")));
            action.removeMember("msg");
            action.addMember("msg",msg);
            action.addMember("success",false);
        }
        else {
            msg = String.format("%s is exists and ActorRef sent.", ((String)command.payload.get("target")));
            action.removeMember("msg");
            action.addMember("msg",msg);
            action.addMember("success",true);
            action.addMember("actor_ref",groups.get((String)command.payload.get("groupName")));
        }
        */
        if(groupIsExists(command,action)) {
            //System.out.println("!!!");
            this.groups.get((String) command.payload.get("groupName")).forward(command, getContext());
            //action.addMember(("actorRef"), getSender());
        }return "";
    }

    private String connectionHandler(User.Command command) {
        String msg;
        if (this.connectedUsers.containsKey((String)command.payload.get("username")))
            msg = String.format("%s already exists.", ((String)command.payload.get("username")));
        else {
            this.connectedUsers.put((String)command.payload.get("username"), (ActorRef)command.payload.get("user_ref"));
            msg = String.format("%s connected successfully.", ((String)command.payload.get("username")));
        }
        getSender().tell(msg,getSender());
        return msg;
    }

    private String disconnectionHandler(User.Command command) {
        String msg;
        if (!this.connectedUsers.containsKey((String) command.payload.get("username"))) {
            msg = String.format("%s isn't exists.", ((String) command.payload.get("username")));
        }
        else {
            msg = String.format("%s disconnected successfully.", ((String) command.payload.get("username")));
            System.out.println("Debug - disconnectionHandler Arrived in MANAGER!");
            System.out.println(command.payload.get("commType"));
            command.payload.put("msg", msg);
            command.payload.put("actorRef", connectedUsers.get((String) command.payload.get("username")));
            this.connectedUsers.remove(command.payload.get("username"));
            groups.values().forEach(groupRef -> groupRef.forward(command, getContext()));
        }
        getSender().tell(msg, getSender());
        return "";
    }

    private String U2UMsgHandler(User.Command command) {
        String msg;
        Actions action = new Actions();
        if (!this.connectedUsers.containsKey((String)command.payload.get("target"))){
            msg = String.format("%s isn't exists.", ((String)command.payload.get("target")));
            action.addMember("success",false);
        }
        else {
            msg = String.format("%s is exists and ActorRef sent.", ((String)command.payload.get("target")));
            action.addMember("success",true);
            action.addMember("actor_ref",connectedUsers.get((String)command.payload.get("target")));
        }
        action.addMember("msg",msg);
        getSender().tell(action,getSelf());
        return msg;
    }

    //Connect
    //Delete (Disconnect)
    //Creating a Group
    //Deleting a Group
    //Managing group user list: broadcasting to all members
    //Managing group user list: user invited to a group
    //Managing group user list: user removed from a group
    //Managing group user list: active users

    public Manager(){
        this.connectedUsers = new HashMap<>();
        this.groups = new HashMap<>();
        commandMapping();
    }

    private String receiveResMsg(Manager.Actions action){
        Dictionary<String, Object> payload = action.payload;
        if(!(boolean)payload.get("success")) {
            connectedUsers.get((String)payload.get("username")).tell(action,ActorRef.noSender());
        }
        else {
            forwardMessageToGroup((String)payload.get("groupName"),action);
        }
        return "";
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(User.Command.class, command -> commMap.get(command.command).apply(command))
                .match(Actions.class, action -> receiveResMsg(action))
                .build();
    }
}
