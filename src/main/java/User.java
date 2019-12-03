import akka.actor.AbstractActor;
//import akka.actor.ActorRef;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.util.Timeout;

import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


public class User extends AbstractActor {
    /* User Class vars declarations */
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    final static Timeout timeout_time = new Timeout(Duration.create(2, TimeUnit.SECONDS));
    private ActorSelection managingActor =
            getContext().actorSelection("akka://ManagingServer@127.0.0.1:2552/user/ManagingServerActor");
    private Map<Client.CommandType, Function<Command, String>> commMap = new HashMap<>();
    private Map<Client.CommandType, Function<Manager.Actions, String>> actionMap = new HashMap<>();


    private String username;
    private HashMap<String, Dictionary<String, Object>>  messages;
    private HashMap<String, Dictionary<String, Object>> files;

    private static final Timeout TimeOut = new Timeout(Duration.create(1, TimeUnit.SECONDS));

    static public Props props(){
        return Props.create(User.class, () -> new User());
    }

    //Commands Claas
    static public class Command implements Serializable {
        public final Client.CommandType command;
        public Dictionary<String, Object> payload;

        public Command(Client.CommandType command, Dictionary<String,Object> payload) {
            this.command = command;
            this.payload = payload;
        }


    }

    public User(){
        commandMapping();
        actionMapping();
        messages = new HashMap<>();
        files = new HashMap<>();
    }

    //invite user: /group user invite <groupname> <targetusername>
    private String receiveGroupInvite(Manager.Actions action) {
        //System.out.println("Arrived Invite");
        Dictionary<String, Object> payload = action.payload;
        if(!(boolean)payload.get("success"))
            System.out.println((String)payload.get("msg"));
        else {
            this.messages.put((String) payload.get("username"), payload);
            System.out.println(String.format("!Welcome to %s! to our %s group",
                                             (String) payload.get("targetUsername"),
                                             (String) payload.get("groupName")));
        }
        return "";
    }
    private String receiveGroupRemove(Manager.Actions action) {
        Dictionary<String, Object> payload = action.payload;
        this.messages.put((String) payload.get("username"), payload);
        System.out.println(String.format("You have been removed from  %s by %s",
                                         (String)payload.get("groupName"),
                                         (String)payload.get("username")));
        return "";
    }
    private String receiveGroupTxt(Manager.Actions action) {
        Dictionary<String, Object> payload = action.payload;
        this.messages.put((String) payload.get("username"),payload);
        System.out.println(
                String.format("[%s][%s][%s]<%s>",
                              ((Date)payload.get("time")).toString(),
                              (String) payload.get("groupName"),
                              (String) payload.get("username"),
                              (String) payload.get("message")));
        return "";
    }

    private String receiveTxt(Manager.Actions action) {
        Dictionary<String, Object> payload = action.payload;
        this.messages.put((String) payload.get("username"),payload);
        System.out.println(
                String.format("%s: [%s][%s][<source>]<%s>",
                              this.username,
                              ((Date)payload.get("time")).toString(),
                              (String) payload.get("username"),
                              (String) payload.get("message")));
        return "";
    }

    private String receiveFile(Manager.Actions action) {

                Dictionary<String, Object> payload = action.payload;
        this.messages.put((String) payload.get("username"),payload);
        System.out.println(
                String.format("[%s][%s][<source>] File received: <%s>",
                              this.username,
                              ((Date)payload.get("time")).toString(),
                              (String) payload.get("username"),
                              (String) payload.get("targetfilepath")));
        return "";
    }
    private String receiveResMsg(Manager.Actions action){
        System.out.println(action.payload.get("msg"));
        return "";
    }

    private String U2UFileMsgHandler(Dictionary<String, Object> payload) {
        payload.put("msgType", Client.MassageType.user);
        Future<Object> future = Patterns.ask(managingActor,
                                             new Command(Client.CommandType.file, payload),
                                             timeout_time);
        try {
            Manager.Actions actionResult =
                    (Manager.Actions) Await.result(future, timeout_time.duration());
            Dictionary<String, Object> result = actionResult.payload;
            if (!(boolean)result.get("success"))
                throw new Exception(String.format("%s user isn't exists.",
                                                  payload.get("username")));
            else {
                actionResult = new Manager.Actions(Client.CommandType.text,Client.MassageType.user);
                actionResult.addMember("username",this.username);
                actionResult.addMember("time",new Date());
                actionResult.addMember("targetfilepath",(String)payload.get("sourcefilePath"));
                ((ActorRef) result.get("actor_ref"))
                        .tell(actionResult, getSelf());
            }
            return "";
        }
        catch (Exception exp){
            System.out.println(exp.getMessage());
        }
        return "";
    }

    private String U2UTxtMsgHandler(Dictionary<String, Object> payload){
        payload.put("msgType", Client.MassageType.user);
        Future<Object> future = Patterns.ask(managingActor,
                                             new Command(Client.CommandType.text, payload),
                                             timeout_time);
        try {
            Manager.Actions actionResult =
                    (Manager.Actions) Await.result(future, timeout_time.duration());
            Dictionary<String, Object> result = actionResult.payload;
            if (!(boolean)result.get("success"))
                throw new Exception(String.format("%s user isn't exists.",
                                                         payload.get("username")));
            else {
                actionResult = new Manager.Actions(Client.CommandType.text,Client.MassageType.user);
                actionResult.addMember("username",this.username);
                actionResult.addMember("time",new Date());
                actionResult.addMember("message",(String)payload.get("message"));
                ((ActorRef) result.get("actor_ref"))
                        .tell(actionResult, getSelf());
            }
            return "";
        }
        catch (Exception exp) {
            System.out.println(exp);
         return "";
        }
    }

    private String connectionHandler(Dictionary<String, Object> payload){
        payload.put("user_ref",getSelf());
        Future<Object> future = Patterns.ask(managingActor,
                                             new Command(Client.CommandType.connect, payload),
                                             timeout_time);
        try {
            Object result = Await.result(future,timeout_time.duration());
            this.username = (String)payload.get("username");
            System.out.println(result.toString());
            return "";
        }
        catch (Exception exp){
            System.out.println(exp.getMessage());
            System.out.println("Server is Offline!");
            return "";
        }
    }
    private String disconnectionHandler(Dictionary<String, Object> payload) {
        try {
            payload.put("username",this.username);
            Future<Object> future = Patterns.ask(managingActor,
                                             new Command(Client.CommandType.disconnect, payload),
                                             timeout_time);

            Object resualt = Await.result(future, timeout_time.duration());
            this.username = null;
            System.out.println(resualt.toString());
            return "";
        } catch (Exception exp) {
            System.out.println(exp.getMessage());
            System.out.println("Server is Offline!");
            return "";
        }
        //Todo
        //  Notify the Manager
        //  gracefully leaves all groups
        //  if <username> is admin of a group, the groups needs to be dismantled
        //      meaning remove any group member and update them
        //  all information of <username> in the managing server will be purged.
        //  Send Success msg
    }

    //GroupCommands Handlers
    /*
    private String commandGroupd(Dictionary<String, Object> payload){
        payload.put("username",this.username);
        managingActor.tell(new Command((Client.CommandType)payload.get("commType"),
                                       payload),getSelf());
        return "";
    }
    */
    private String commandGroup(Dictionary<String, Object> payload) {
        //System.out.println(payload.get("commType"));
        payload.put("msgType", Client.MassageType.group);
        payload.put("username",this.username);
        payload.put("time",new Date());
        managingActor.tell(new Command((Client.CommandType)payload.get("commType"),
                                       payload),getSelf());
        return "";
    }

    private void commandMapping() {
        //Mapping Command to commandHandler
        commMap.put(Client.CommandType.connect, command -> connectionHandler(command.payload));
        commMap.put(Client.CommandType.disconnect, command -> disconnectionHandler(command.payload));
        commMap.put(Client.CommandType.text, command -> U2UTxtMsgHandler(command.payload));
        commMap.put(Client.CommandType.file, command -> U2UFileMsgHandler(command.payload));
        commMap.put(Client.CommandType.create, command -> commandGroup(command.payload));
        commMap.put(Client.CommandType.sendText, command -> commandGroup(command.payload));
        commMap.put(Client.CommandType.invite, command -> commandGroup(command.payload));
        commMap.put(Client.CommandType.leave, command -> commandGroup(command.payload));
        commMap.put(Client.CommandType.remove, command -> commandGroup(command.payload));
        commMap.put(Client.CommandType.mute, command -> commandGroup(command.payload));
        commMap.put(Client.CommandType.unmute, command -> commandGroup(command.payload));
        commMap.put(Client.CommandType.coadmin, command -> commandGroup(command.payload));
    }

    private void actionMapping() {
        actionMap.put(Client.CommandType.text, action -> receiveTxt(action));
        actionMap.put(Client.CommandType.file, action -> receiveFile(action));
        actionMap.put(Client.CommandType.create, action -> receiveResMsg(action));
        actionMap.put(Client.CommandType.delete, action -> receiveResMsg(action));
        actionMap.put(Client.CommandType.sendText, action -> receiveGroupTxt(action));
        actionMap.put(Client.CommandType.invite, action -> receiveResMsg(action));
        actionMap.put(Client.CommandType.remove, action -> receiveGroupRemove(action));

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Command.class, command -> {
                    commMap.get(command.command).apply(command);
                    //managingActor.tell(command,getSelf());
                })
                .match(Manager.Actions.class, action -> receiveResMsg(action))//actionMap.get(action.commType).apply(action);
                .build();
    }
}