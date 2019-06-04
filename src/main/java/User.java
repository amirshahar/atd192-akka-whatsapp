import akka.actor.AbstractActor;
//import akka.actor.ActorRef;
import akka.actor.Props;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class User extends AbstractActor {
    /* User Class vars declarations */
    //private final ActorRef managingActor;

    private String username;
    private Map<Client.CommandType, Function<Command,String>> commMap = new HashMap<>();

    static public Props props(){
        return Props.create(User.class, () -> new User());
    }

    //Commands Claas
    static public class Command {
        public final Client.CommandType command;
        public String payload;

        public Command(Client.CommandType command, String payload) {
            this.command = command;
            this.payload = payload;
        }
    }

    public User(){
        commandMapping();
    }

    private String connectionHnadler(String payload){
        this.username = payload;
        //Todo
        //  Server is online? if not send "server is offline"
        //  Notify the Manager
        //  Make sure that is unique <username>, if not send "<username> is in used"
        //  upun success send "success msg"
        return "connection msg";
    }

    private String disconnectionHnadler(String payload){
        //Todo
        //  Notify the Manager
        //  gracefully leaves all groups
        //  if <username> is admin of a group, the groups needs to be dismantled
        //      meaning remove any group member and update them
        //  all information of <username> in the managing server will be purged.
        //  Send Success msg
        this.username = null;
        return "msg";
    }

    public void commandMapping() {
        //Mapping Command to commandHandler
        commMap.put(Client.CommandType.connect,(command) -> connectionHnadler(command.payload));
        commMap.put(Client.CommandType.disconnect,(command) -> disconnectionHnadler(command.payload));
    }
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Command.class, command-> {
                    String r = commMap.get(command.command).apply(command);
                    System.out.println(r);
                    //managingActor.tell(command,getSelf());
                })
                .build();
    }
}