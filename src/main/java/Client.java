import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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
        file
    }
    private static final int MAX_TRIES = 3;
    final ActorSystem system = ActorSystem.create("whatsapp-akka");
    ActorRef userActor = null;
    private Supplier<Stream<Function<String, Matcher>>> matcher;
    private Map<CommandType, Function<String,User.Command>> commMap = new HashMap<>();
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
        rxs.add(Pattern.compile("\\s*group\\s*(text|file)\\s*[a-zA-Z0-9]+\\s*[a-zA-Z0-9]+"));
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
        //Mapping Massage to massageHandler
        msgMap.put(MassageType.user,(command -> {
            userActor.tell(command,ActorRef.noSender());
            return null;}));
    }

    public static void main(String[] args) {
        Client client = new Client();

        client.commandMapping();
        client.massageMapping();
        client.matcher = client.initialPatterns();

        client.run(args);
    }

    public void massageHandler(String[] input){
        int countTries = 0;
        while(true) {
            try {
                msgMap.get(MassageType.valueOf(input[0]))
                        .compose(commMap.get(CommandType.valueOf(input[1])))
                        .apply(input[2]);
                break;
            } catch (Exception e) {
                if(++countTries < MAX_TRIES) {
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
                //Function<String, Matcher>
                String input = sc.nextLine();
                massageHandler(matcher.get().map(ele -> ele.apply(input))
                        .filter(pre -> pre.matches()).findFirst().get().group().split("\\s+"));
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
