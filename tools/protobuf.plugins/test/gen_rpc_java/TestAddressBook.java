import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.common.util.concurrent.SettableFuture;

public class TestAddressBook
{
	final AB.AddressBookService service = new AddressBookServiceImpl();
	final AB.AddressBookServiceReactor reactor = new AB.AddressBookServiceReactor(service);

	class AddressBookServiceImpl implements AB.AddressBookService
	{
		public ListenableFuture<AB.AddPersonReply> addPerson(AB.Person person, String someValue)
		{
			SettableFuture<AB.AddPersonReply> future = SettableFuture.create();

			// TODO: Assert something about the person passed as param
			AB.AddPersonReply reply = AB.AddPersonReply.newBuilder().setId(1234).build();
			future.set(reply);

			return future;
		}
	}

	public static void main(String[] args) throws InvalidProtocolBufferException
	{
		TestAddressBook test = new TestAddressBook();

		// Create a RPC call to add a new person. This would normally be done on the client side
		AB.AddPersonCall call = AB.AddPersonCall.newBuilder()
			.setPerson(AB.Person.newBuilder()
					.setName("Joe Foo")
					.setEmail("joe@foo.com")
					.build())
			.setSomeValue("hello")
			.build();

		com.aerofs.proto.RpcService.Payload payload = com.aerofs.proto.RpcService.Payload.newBuilder()
			.setType(AB.AddressBookServiceReactor.ServiceRpcTypes.ADD_PERSON.ordinal())
			.setPayloadData(call.toByteString())
			.build();

		test.onReceived(payload.toByteArray());

	}

	private void onReceived(byte[] data) throws InvalidProtocolBufferException
	{
		// Now, on the server side, we just received this byte array from the client.
		// React and send back the reply to the client 
		ListenableFuture<byte[]> future = reactor.react(data);
		future.addListener(new Runnable() {
	            @Override
	            public void run()
	            {
					//... send the byte array to the client
				}
			},
			MoreExecutors.sameThreadExecutor()
		);
	}
}
