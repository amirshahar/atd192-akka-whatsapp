import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.ActorRefRoutee;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Router;

import java.io.Serializable;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

public class Group extends AbstractActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private Map<Client.CommandType, Function<User.Command, String>> commMap = new HashMap<>();

    Router router;
    private String groupName;
    private String admin;
    private List<String> users;
    private List<String> coAdmins;
    private List<String> mutedUsers;

    public Group (String name, String admin) {
        commandMapping();
        this.groupName = name;
        this.admin = admin;
        this.users = new ArrayList<String>();
        this.coAdmins = new ArrayList<String>();
        this.mutedUsers = new ArrayList<String>();
        this.router = new Router(new BroadcastRoutingLogic());
    }

    static public Props props(String name, String admin) {
        return Props.create(Group.class, () -> new Group(name, admin));
    }

    private void addUserToGroup(String username, ActorRef actor) {
        this.users.add(username);
        this.router = this.router.addRoutee(new ActorRefRoutee(actor));
    }

    private String createGroup(User.Command command) {
        Manager.Actions action = new Manager.Actions((Client.CommandType)command.payload.get("commType"),
                                                     (Client.MassageType)command.payload.get("msgType"));
        String msg;
//        if (!command.payload.get("username").toString().equals(this.admin))
//            msg = String.format("%s you're not admin",
//                                    (String)command.payload.get("username"));
//        if(!command.payload.get("groupName").toString().equals(this.groupName))
//            msg = String.format("%s is not the name of group",
//                                (String)command.payload.get("groupName"));
//        if(mutedUsers.contains((String) command.payload.get("username")))
//            msg = String.format("%s you're member in %s group but you in muted mode!",
//                                (String)command.payload.get("username"),
//                                (String)command.payload.get("groupName"));
//        if((!command.payload.get("username").toString().equals(this.admin)) && (!coAdmins.contains((String) command.payload.get("username"))))
//            msg = String.format("%s you're member in %s group but you're not CoAdmin!",
//                                (String)command.payload.get("username"),
//                                (String)command.payload.get("groupName"));
        authorizationCheck(command);
        addUserToGroup(this.admin, getSender());
        msg = String.format("Group %s created successfully.", this.groupName);
        action.addMember("msg",msg);
        this.router.route(action, getSelf());
        return "";
    }

    private String deleteGroup(User.Command command){
        return "";
    }

    private boolean userIsMember(User.Command command, Manager.Actions action){
        if (!users.contains((String)command.payload.get("username")))
        {
            action.addMember("success",false);
            action.addMember("msg",String.format("username is not member in %s group",this.groupName));
            getSender().tell(action,ActorRef.noSender());
            return false;
        }
        return true;
    }
    private boolean userIsMuted(User.Command command, Manager.Actions action){
        if (!users.contains((String)command.payload.get("username")))
        {
            action.addMember("success",false);
            action.addMember("msg",String.format("%s is muted mode in %s group",
                                                 (String)command.payload.get("username"),
                                                 this.groupName));
            getSender().tell(action,ActorRef.noSender());
            return false;
        }
        return true;
    }

    private String groupUserLeave(User.Command command) {
        Manager.Actions action = new Manager.Actions(Client.CommandType.leave,Client.MassageType.group);
        action.setPayload(command.payload);
        if(userIsMember(command,action)) {
            this.users.remove((String) command.payload.get("username"));

            //user name is admin co-admin, group exist, user exist ???
            this.router = this.router.removeRoutee((ActorRef)command.payload.get("targetRef"));
            action.addMember("success", true);
            action.addMember("msg",
                             String.format("%s leave the %s group",
                                           (String) command.payload.get("username"),
                                           (String) command.payload.get("groupName")));
            this.router.route(action, ActorRef.noSender());
            Manager.Actions actionSelf = new Manager.Actions(Client.CommandType.leave,Client.MassageType.group);
            actionSelf.addMember("msg",String.format("you are just leave the %s group",
                                                 (String) command.payload.get("groupName")));
            ((ActorRef) command.payload.get("targetRef")).tell(actionSelf,ActorRef.noSender());
            //((ActorRef)command.payload.get("targetRef")).tell(action, getSelf());
        }
        return "";
    }
    private String groupUserUnMute(User.Command command) {
        Manager.Actions action = new Manager.Actions(Client.CommandType.unmute,Client.MassageType.group);
        action.setPayload(command.payload);
        if(userIsMember(command,action))
            if(userIsMuted(command,action)){
                this.mutedUsers.remove((String)command.payload.get("targetUsername"));
                action.addMember("success", true);
                action.addMember("msg",
                                 String.format("You have been unmuted in %s by %s",
                                               command.payload.get("groupName"),
                                               command.payload.get("username")
                                 ));
                //this.router.route(action, ActorRef.noSender());
                //Manager.Actions actionSelf = new Manager.Actions(Client.CommandType.leave,Client.MassageType.group);
                //actionSelf.addMember("msg",String.format("you are just leave the %s group",(String) command.payload.get("groupName")));
                ((ActorRef) command.payload.get("targetRef")).tell(action,ActorRef.noSender());
                //((ActorRef)command.payload.get("targetRef")).tell(action, getSelf());
            }
        return "";
    }
    private String groupUserCoAdmin(User.Command command) {
        Manager.Actions action = new Manager.Actions(Client.CommandType.coadmin,Client.MassageType.group);
        String msg;
        action.setPayload(command.payload);
        if(userIsMember(command,action)) {
            action.addMember("success", true);
            if (command.payload.get("commTypeSub").equals("add")) {
                msg = String.format("You have added as CoAdmin the %s group",
                                               command.payload.get("groupName")
                                 );
                //this.router.route(action, ActorRef.noSender());
                //Manager.Actions actionSelf = new Manager.Actions(Client.CommandType.leave,Client.MassageType.group);
                //actionSelf.addMember("msg",String.format("you are just leave the %s group",(String) command.payload.get("groupName")));
                this.coAdmins.add((String) command.payload.get("targetUsername"));
                //((ActorRef)command.payload.get("targetRef")).tell(action, getSelf());
            }
            else {
                msg =  String.format("You have been removed as CoAdmin the %s group",
                                     command.payload.get("groupName")
                                    );
                this.coAdmins.remove((String) command.payload.get("targetUsername"));
            }
            action.addMember("msg",msg);
            ((ActorRef) command.payload.get("targetRef")).tell(action, ActorRef.noSender());
        }
        return "";
    }



    private String groupUserMute(User.Command command) {
        Manager.Actions action = new Manager.Actions(Client.CommandType.mute,Client.MassageType.group);
        action.setPayload(command.payload);
        if(userIsMember(command,action)) {
            action.addMember("success", true);
            action.addMember("msg",
                             String.format("You have been muted for %s seconds",
                                           command.payload.get("timeInSeconds")
                                           ));
            //this.router.route(action, ActorRef.noSender());
            //Manager.Actions actionSelf = new Manager.Actions(Client.CommandType.leave,Client.MassageType.group);
            //actionSelf.addMember("msg",String.format("you are just leave the %s group",(String) command.payload.get("groupName")));
            this.mutedUsers.add((String) command.payload.get("targetUsername"));
            this.getContext().getSystem().scheduler().
                    scheduleOnce(Duration.ofMillis(Integer.parseInt((String)command.payload.get("timeInSeconds")) * 1000),
                                 getSelf(), new User.Command(Client.CommandType.unmute,command.payload),
                                 this.getContext().getSystem().dispatcher(), getSender());

            ((ActorRef) command.payload.get("targetRef")).tell(action,ActorRef.noSender());
            //((ActorRef)command.payload.get("targetRef")).tell(action, getSelf());
        }
        return "";
    }

    private String authorizationCheck(User.Command command) {
        String msg = "";
        if(!command.payload.get("groupName").toString().equals(this.groupName))
            msg = String.format("%s is not the name of group",
                                (String)command.payload.get("groupName"));
        if(mutedUsers.contains((String) command.payload.get("username")))
            msg = String.format("%s you're member in %s group but you in muted mode!",
                                (String)command.payload.get("username"),
                                (String)command.payload.get("groupName"));
        if (!command.payload.get("username").toString().equals(this.admin) && !coAdmins.contains(command.payload.get("username").toString()))
            msg = String.format("%s you're not admin and not coAdmin",
                                (String)command.payload.get("username"));
        if((!command.payload.get("username").toString().equals(this.admin)) && (!coAdmins.contains((String) command.payload.get("username"))))
            msg = String.format("%s you're member in %s group but you're not CoAdmin!",
                                (String)command.payload.get("username"),
                                (String)command.payload.get("groupName"));
        return msg;
    }


    private String groupUserInvite(User.Command command) {
        Manager.Actions action = new Manager.Actions(Client.CommandType.invite,Client.MassageType.group);
        action.setPayload(command.payload);
        String msg;
        if((msg = authorizationCheck(command)) != ""){
            action.addMember("success",false);
            action.addMember("msg",msg);
            ((ActorRef)command.payload.get("userSourceRef")).tell(action,ActorRef.noSender());
            return "";
        }

        //user name is admin co-admin, group exist, user exist ???
        this.users.add((String)command.payload.get("targetUsername"));
        this.router = this.router.addRoutee(new ActorRefRoutee((ActorRef)command.payload.get("targetRef")));
        action.addMember("success",true);
        action.addMember("msg",
                         String.format("Welcome! %s joint the %s group",
                                       (String) command.payload.get("targetUsername"),
                                       (String) command.payload.get("groupName")));
        this.router.route(action,ActorRef.noSender());
        //((ActorRef)command.payload.get("targetRef")).tell(action, getSelf());
        return "";
    }
    private String groupUserDisconnected(User.Command command) {
        System.out.println("Debug - groupUserDisconnected Arrived!");
        Manager.Actions action = new Manager.Actions(Client.CommandType.disconnect,Client.MassageType.group);
        action.setPayload(command.payload);
        if(userIsMember(command,action)) {
            users.remove((String) command.payload.get("username"));
            this.router = this.router.removeRoutee((ActorRef) command.payload.get("actorRef"));
            this.router.route(action,ActorRef.noSender());
        }
        return "";
    }
    private String groupUserRemove(User.Command command) {
        Manager.Actions action = new Manager.Actions(Client.CommandType.remove,Client.MassageType.group);
        action.setPayload(command.payload);
        String msg;
        if(userIsMember(command,action)) {
            this.users.remove((String) command.payload.get("targetUsername"));
            this.router = this.router.removeRoutee((ActorRef) command.payload.get("actorRef"));
        }
        return "";
    }

    private String U2GMsg(User.Command command) {
        Dictionary<String, Object> payload = command.payload;
        Manager.Actions action = new Manager.Actions(Client.CommandType.leave,Client.MassageType.group);
        action.setPayload(command.payload);
        //action.replaceValue("commType", Client.CommandType.text);
        if(userIsMember(command,action))
            if(!mutedUsers.contains((String) payload.get("username"))) {
            action.addMember("success",true);
            //action.replaceValue("msg",(String)action.payload.get("message"));
            action.replaceValue("msg",String.format("[%s][%s][%s]<%s>",
                                                    ((Date)payload.get("time")).toString(),
                                                    (String) payload.get("groupName"),
                                                    (String) payload.get("username"),
                                                    (String) payload.get("msg")));
            this.router.route(action,ActorRef.noSender());
            //this.router.route(action,(ActorRef)command.payload.get("actorRef"));
            return "";
        }
        action.addMember("success",false);
        getSender().tell(action,ActorRef.noSender());
        return "";
    }

    public void commandMapping() {
        commMap.put(Client.CommandType.create, command -> createGroup(command));
        commMap.put(Client.CommandType.delete, command -> deleteGroup(command));
        commMap.put(Client.CommandType.invite, command -> groupUserInvite(command));
        commMap.put(Client.CommandType.leave, command -> groupUserLeave(command));
        commMap.put(Client.CommandType.remove, command -> groupUserRemove(command));
        commMap.put(Client.CommandType.disconnect, command -> groupUserDisconnected(command));
        commMap.put(Client.CommandType.sendText, command -> U2GMsg(command));
        commMap.put(Client.CommandType.mute, command -> groupUserMute(command));
        commMap.put(Client.CommandType.unmute, command -> groupUserUnMute(command));
        commMap.put(Client.CommandType.coadmin, command -> groupUserCoAdmin(command));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(User.Command.class, command -> {
                    commMap.get(command.command).apply(command);
                })
                .build();
    }
}