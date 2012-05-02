import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.common.util.concurrent.SettableFuture;
import com.aerofs.proto.*;
import com.aerofs.proto.AB.*;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.ArrayList;

public class TestAddressBook
{
    static class Client implements AddressBookServiceStub.AddressBookServiceStubCallbacks
    {
        Server server;

        public Client(Server s)
        {
            server = s;
        }

        @Override
        public ListenableFuture<byte[]> doRPC(byte[] data)
        {
            return server.processRequest(data);
        }

        @Override
        public Throwable decodeError(ErrorReply error)
        {
            return new IllegalArgumentException(error.getErrorMessage());
        }

    }

    static class Server implements AddressBookService
    {
        AddressBookServiceReactor reactor;

        public Server()
        {
            reactor = new AddressBookServiceReactor(this);
        }

        @Override
        public ErrorReply encodeError(Throwable error)
        {
            return ErrorReply.newBuilder().setErrorMessage(error.getMessage()).build();
        }

        @Override
        public ListenableFuture<AddPersonReply> addPerson(Person person, String someValue)
        {
            SettableFuture<AddPersonReply> future = SettableFuture.create();

            // Fail if person name is empty
            if (person.getName().length() == 0) {
                // Normaly we would have used future.setException() here,
                // but for the purpose of testing we also want to make sure we catch thrown exceptions
                throw new IllegalArgumentException("can't add a person with an empty name");
            }

            AddPersonReply reply = AddPersonReply.newBuilder().setId(1234).build();
            future.set(reply);

            return future;
        }

        @Override
        public ListenableFuture<AddPeopleReply> addPeople(List<Person> people, List<String> testValues) throws Exception
        {
            AddPeopleReply.Builder reply = AddPeopleReply.newBuilder();

            if (people != null) {
                for (Person person : people) {
                    reply.addLengthName(person.getName().length());
                }
            }

            SettableFuture<AddPeopleReply> future = SettableFuture.create();
            future.set(reply.build());
            return future;
        }

        private ListenableFuture<byte[]> processRequest(byte[] data)
        {
            // we just received this byte array from the client.
            // React and send back the reply to the client
            return reactor.react(data);
        }
    }

    public static void main(String[] args) throws Exception
    {
        Server server = new Server();
        Client client = new Client(server);

        testAddingAPerson(client);
        testInvalidRequest(client);
        testRepeatedParams(client);
        testBlockingStub(client);
    }

    private static void testAddingAPerson(Client client) throws Exception
    {
        AddressBookServiceStub stub = new AddressBookServiceStub(client);

        Person person = Person.newBuilder()
                    .setName("Joe Foo")
                    .setEmail("joe@foo.com")
                    .build();

        stub.addPerson(person, "hello").get();
    }

    private static void testInvalidRequest(Client client) throws Exception
    {
        AddressBookServiceStub stub = new AddressBookServiceStub(client);
        try {
            // Try adding an empty person
            stub.addPerson(Person.newBuilder().setName("").build(), null).get();

            // we should not get to this point
            throw new RuntimeException("test failed - an expected error wasn't reported.");

        } catch (ExecutionException e) {
            String expected = "java.lang.IllegalArgumentException: can't add a person with an empty name";
            if (e.getMessage().equals(expected)) {
                System.out.println("Expected error: " + e.getMessage());
            } else {
                System.out.println("Unexpected error. Was expecting: \"" + expected + "\"");
                throw e;
            }
        }
    }

    private static void testRepeatedParams(Client client) throws Exception
    {
        ArrayList<Person> people = new ArrayList<Person>();
        people.add(Person.newBuilder().setName("John").build());
        people.add(Person.newBuilder().setName("Antonio").build());

        AddressBookServiceStub stub = new AddressBookServiceStub(client);
        AddPeopleReply reply = stub.addPeople(people, null).get();
        List<Integer> l = reply.getLengthNameList();
        assert l.size() == 2;
        assert l.get(0).intValue() == people.get(0).getName().length();
        assert l.get(1).intValue() == people.get(1).getName().length();
    }

    private static void testBlockingStub(Client client) throws Exception
    {
        AddressBookServiceBlockingStub stub = new AddressBookServiceBlockingStub(client);
        Person john = Person.newBuilder().setName("John").build();
        AddPersonReply reply = stub.addPerson(john, "test");
        assert reply.getId() == 1234;
    }
}
