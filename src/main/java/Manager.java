import akka.actor.AbstractActor;
import akka.actor.Props;

public class Manager extends AbstractActor {
    static public Props props(){
        return Props.create(Manager.class, () -> new Manager());
    }


    //Connect
    //Delete (Disconnect)
    //Creating a Group
    //Deleting a Group
    //Managing group user list: broadcasting to all members
    //Managing group user list: user invited to a group
    //Managing group user list: user removed from a group
    //Managing group user list: active users

    public Manager(){}
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(User.Command.class, command ->{
                    if(command.command.equals("connect"))
                        System.out.println("Yes");
                    else
                        System.out.println("No");
                })
                .build();
    }
}
