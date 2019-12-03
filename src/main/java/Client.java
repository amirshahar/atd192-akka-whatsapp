import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import scala.None;
import scala.collection.parallel.mutable.ParArray;
import scala.concurrent.java8.FuturesConvertersImpl;

import javax.security.auth.callback.Callback;
import javax.swing.plaf.synth.SynthDesktopIconUI;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Client {
    enum MassageType {
        user,
        group
    }
    enum CommandType {
        connect,
        disconnect,
        text,
        file,
        create,
        delete,
        invite,
        leave,
        mute,
        sendText,
        sendFile,
        remove,
        unmute,
        coadmin
    }
    private static final int MAX_TRIES = 3;
    final ActorSystem system = ActorSystem.create("UserSystem", ConfigFactory.load("application.conf").getConfig("UserConf"));
    ActorRef userActor = null;
    private Supplier<Stream<Function<String, Matcher>>> matcher;
    private Map<CommandType, Function<Dictionary<String,Object>,User.Command>> commMap = new HashMap<>();
    private Map<MassageType, Function<User.Command,Void>> msgMap = new HashMap<>();

//    public Function<String,Object> connectRequest(){
//        return (username) -> {
//            ActorRef user = system.actorOf(User.props(username),username);
//            user.tell(new User.Command(CommandType.connect,""),ActorRef.noSender());
//            return CommandType.connect;
//        };
//    }
//    public Function<String,Object> disconnectRequest(){
//        return (username) -> {
//            System.out.println(username);
//            return CommandType.disconnect;
//        };
//    }

    public Supplier<Stream<Function<String, Matcher>>> initialPatterns() {
        final List<Pattern> rxs = new ArrayList<>();
        rxs.add(Pattern.compile("\\s*user\\s*connect\\s*[a-zA-Z0-9]+"));
        rxs.add(Pattern.compile("\\s*user\\s*disconnect\\s*"));
        rxs.add(Pattern.compile("\\s*user\\s*(text|file)\\s*[a-zA-Z0-9]+(\\s*[a-zA-Z0-9]+)+"));
        rxs.add(Pattern.compile("\\s*group\\s*send\\s*(text|file)\\s*[a-zA-Z0-9]+(\\s*[a-zA-Z0-9]+)+"));
        rxs.add(Pattern.compile("\\s*group\\s*(create|leave)\\s*[a-zA-Z0-9]+"));
        rxs.add(Pattern.compile("\\s*group\\s*user\\s*(invite|remove|unmute)\\s*[a-zA-Z0-9]+\\s*[a-zA-Z0-9]+"));
        rxs.add(Pattern.compile("\\s*group\\s*coadmin\\s*(add|remove)\\s*[a-zA-Z0-9]+\\s*[a-zA-Z0-9]+"));
        rxs.add(Pattern.compile("\\s*group\\s*user\\s*(mute)\\s*[a-zA-Z0-9]+\\s*[a-zA-Z0-9]+\\s*[0-9]+"));
        //group send text <groupname> <message>
        //group send file <groupname> <sourcefilePath>
        //group user invite <groupname> <targetusername>
        //group user remove <groupname> <targetusername>
        //group user mute <groupname> <targetusername> <timeinseconds>
        //group user unmute <groupname> <targetusername>
        //group coadmin add <groupname> <targetusername>
        //group coadmin remove <groupname> <targetusername>

        /* ~~~ the above line Cover Both cases in one Regex ~~~
        this.rxs.add(Pattern.compile("\\s*group\\s*file\\s*[a-zA-Z0-9]+\\s*[a-zA-Z0-9]+"));
        this.rxs.add(Pattern.compile("\\s*group\\s*text\\s*[a-zA-Z0-9]+\\s*[a-zA-Z0-9]+"));
         */
        return  () -> rxs.stream().map(p -> (input) -> p.matcher(input));
    }
    public void commandMapping() {
        //Mapping Command to commandHandler
        Arrays.stream(CommandType.values()).forEach(ct -> commMap.put(ct, (payload) -> new User.Command(ct, payload)));
    }
    public void massageMapping() {
        //Mapping Massage to mssageHandler
        msgMap.put(MassageType.user,(command -> {
            userActor.tell(new User.Command(command.command,command.payload), ActorRef.noSender());
            return null;}));
        msgMap.put(MassageType.group,(command -> {
            userActor.tell(new User.Command(command.command,command.payload), ActorRef.noSender());
            return null;}));
    }

    public static void main(String[] args) {
        Client client = new Client();

        client.commandMapping();
        client.massageMapping();
        client.matcher = client.initialPatterns();

        client.run(args);
    }

    public Dictionary<String,Object> generatePayload(String[] input, int commandPos){
        Dictionary<String,Object> payload = new Hashtable<>();
        //System.out.println(CommandType.valueOf(input[commandPos]));
        switch (CommandType.valueOf(input[commandPos])){
            case connect:
                payload.put("username",input[2]);
                break;
            case disconnect:
                payload.put("commType",CommandType.disconnect);
                break;
            case file:
                payload.put("target", input[2]);
                payload.put("sourcefilePath", input[3]);
                break;
            case text:
                payload.put("target", input[2]);
                payload.put("message", String.join(" ", Arrays.copyOfRange(input,
                                                                               3,
                                                                               input.length)));
                break;
            case create:
                payload.put("msgType", MassageType.group);
                payload.put("commType",CommandType.create);
                payload.put("groupName",input[2]);
                break;
            case leave:
                payload.put("msgType", MassageType.group);
                payload.put("commType",CommandType.leave);
                payload.put("groupName",input[2]);
                break;
            case sendText:
                payload.put("msgType", MassageType.group);
                payload.put("commType",CommandType.sendText);
                payload.put("groupName",input[3]);
                payload.put("msg", String.join(" ",Arrays.copyOfRange(input,
                                                                          4, input.length)));
                break;
            case invite:
                payload.put("msgType", MassageType.group);
                payload.put("commType", CommandType.invite);
                payload.put("groupName", input[3]);
                payload.put("targetUsername", input[4]);
                break;
            case remove:
                payload.put("msgType", MassageType.group);
                payload.put("commType", CommandType.remove);
                payload.put("groupName", input[3]);
                payload.put("targetUsername", input[4]);
                break;
            case mute:
                payload.put("msgType", MassageType.group);
                payload.put("commType", CommandType.mute);
                payload.put("groupName", input[3]);
                payload.put("targetUsername", input[4]);
                payload.put("timeInSeconds", input[5]);
                break;
            case unmute:
                payload.put("msgType", MassageType.group);
                payload.put("commType", CommandType.unmute);
                payload.put("groupName", input[3]);
                payload.put("targetUsername", input[4]);
                break;
            case coadmin:
                payload.put("msgType", MassageType.group);
                payload.put("commType", CommandType.coadmin);
                payload.put("commTypeSub", input[2]);
                payload.put("groupName", input[3]);
                payload.put("targetUsername", input[4]);
                break;
        }
        return payload;
    }

    public void massageHandler(String[] input){
        int countTries = 0;
        int commandPos = 1;
        if((MassageType.valueOf(input[0]) == MassageType.group) && !(input[1].equals("create") || input[1].equals("leave") || input[1].equals("coadmin")))
            commandPos += 1;
//        else if((MassageType.valueOf(input[0]) == MassageType.group) && input[1].equals("send")) {
//            commandPos += 1;
//            input[commandPos] = input[2].equals("text")? "sendText" : "sendFile";
//        }
        input = Arrays.stream(input).map(ele -> ele.equals("text")? "sendText": ele).toArray(String[]::new);
        while(true) {
            try {
                msgMap.get(MassageType.valueOf(input[0]))
                        .compose(commMap.get(CommandType.valueOf(input[commandPos])))
                        .apply(generatePayload(input, commandPos));
                break;
            } catch (Exception e) {
                if(++countTries < MAX_TRIES) {
                    System.out.println(String.format("Tries number %d",countTries));
                    userActor = CommandType.connect.toString().equals(input[1]) ?
                            system.actorOf(User.props(), input[2]) : null;
                }else
                    throw e;
            }
        }
    }

    public void run(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Welcome to Akka-Whatsapp");
        while(true){
            try {
                String input = sc.nextLine();
                List<Matcher> matchers = matcher.get().map(ele -> ele.apply(input)).collect(Collectors.toList());
                matchers.forEach(match -> {
                    if(match.matches())
                        massageHandler(match.group().split(" "));
                });
            }
            catch (IllegalArgumentException e){
                System.out.println(e.getMessage());
            }
            catch (Exception e){
                System.out.println(e);
            }
        }
    }
}
