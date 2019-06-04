import akka.actor.ActorSystem;

import java.io.IOException;

public class Server {
    private final String nameManagerActor = "managingActor";
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("whatsapp-akka");
        try {
            system.actorOf(Manager.props(),"managingActor");
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