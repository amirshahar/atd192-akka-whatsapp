import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;

public class Server {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("ManagingServer", ConfigFactory.load("application.conf").getConfig("ManagingServerConf"));
        try {
            system.actorOf(Manager.props(),"ManagingServerActor");
            System.out.println("~~~ Server Ready! ~~~");
            System.out.println(">>> Press ENTER to terminate Server <<<");
            System.in.read();
        }
        catch (IOException ioe){
        }
        finally {
            system.terminate();
        }
    }
}