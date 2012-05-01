import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.ArrayList;

public class TestAddressBook
{
    static class Client implements AB.AddressBookServiceStub.AddressBookServiceStubCallbacks
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
        public Throwable decodeError(AB.ErrorReply error)
        {
            return new IllegalArgumentException(error.getErrorMessage());
        }

    }

    static class Server implements AB.AddressBookService
    {
        AB.AddressBookServiceReactor reactor;

        public Server()
        {
            reactor = new AB.AddressBookServiceReactor(this);
        }

        @Override
        public AB.ErrorReply encodeError(Throwable error)
        {
            return AB.ErrorReply.newBuilder().setErrorMessage(error.getMessage()).build();
        }

        @Override
        public ListenableFuture<AB.AddPersonReply> addPerson(AB.Person person, String someValue)
        {
            SettableFuture<AB.AddPersonReply> future = SettableFuture.create();

            // Fail if person name is empty
            if (person.getName().length() == 0) {
                // Normaly we would have used future.setException() here,
                // but for the purpose of testing we also want to make sure we catch thrown exceptions
                throw new IllegalArgumentException("can't add a person with an empty name");
            }

            AB.AddPersonReply reply = AB.AddPersonReply.newBuilder().setId(1234).build();
            future.set(reply);

            return future;
        }

        @Override
        public ListenableFuture<AB.AddPeopleReply> addPeople(List<AB.Person> people, List<String> testValues) throws Exception
        {
            AB.AddPeopleReply.Builder reply = AB.AddPeopleReply.newBuilder();

            if (people != null) {
                for (AB.Person person : people) {
                    reply.addLengthName(person.getName().length());
                }
            }

            SettableFuture<AB.AddPeopleReply> future = SettableFuture.create();
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
        AB.AddressBookServiceStub stub = new AB.AddressBookServiceStub(client);

        // Test 1: add a person
        AB.Person person = AB.Person.newBuilder()
                    .setName("Joe Foo")
                    .setEmail("joe@foo.com")
                    .build();

        stub.addPerson(person, "hello").get();

        // Test 2. Invalid request
        try {
            // Try adding an empty person
            stub.addPerson(AB.Person.newBuilder().setName("").build(), null).get();

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

        // Test 3: repeated parameters
        ArrayList<AB.Person> people = new ArrayList<AB.Person>();
        people.add(AB.Person.newBuilder().setName("John").build());
        people.add(AB.Person.newBuilder().setName("Antonio").build());

        AB.AddPeopleReply reply = stub.addPeople(people, null).get();
        List<Integer> l = reply.getLengthNameList();
        assert l.size() == 2;
        assert l.get(0).intValue() == people.get(0).getName().length();
        assert l.get(1).intValue() == people.get(1).getName().length();

    }
}
