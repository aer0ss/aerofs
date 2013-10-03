/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.shared_folder_rules;

import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Arrays;

import static com.aerofs.lib.ex.shared_folder_rules.AbstractExSharedFolderRules.SERIALIZATION_ENCODING;

public class TestAbstractExSharedFolderRules
{
    /**
     * This test is intended to guard against accidental change in serialization format.
     *
     * The serialized json is parsed by javascript in shared_folders.mako, hence we need to ensure
     * each entry is serialized as expected so javascript can parse the serialized data.
     *
     * We only test the single entry case because if we have multiple entries, the order of the
     * entries in the serialized map is up to the serializer and cannot be easily covered (we have
     * to check for every possible permutation).
     *
     * Also the primary focus is to guard against accidental changes in the serialization of a
     * single entry, so this test case suffices for the time being.
     */
    @Test
    public void shouldSerializeSingleEntryCorrectly() throws Exception
    {
        UserID userID = UserID.fromInternal("foo@bar.baz");
        FullName fullName = new FullName("Foo", "Bar");
        ImmutableMap<UserID, FullName> map = ImmutableMap.of(userID, fullName);

        AbstractExSharedFolderRules e = new TestExSharedFolderRules(map);
        byte[] data = e.getDataNullable();

        // _do not_ change the format of this string, it is parsed by javascript code in
        // shared_folders.mako
        String expectedJsonString =
                "{\"foo@bar.baz\":{\"first_name\":\"Foo\",\"last_name\":\"Bar\"}}";
        byte[] expected = expectedJsonString.getBytes(Charset.forName(SERIALIZATION_ENCODING));

        assert Arrays.equals(data, expected);
    }

    @Test
    public void shouldDeserializeCorrectly() throws Exception
    {
        String serializedJsonString =
                "{\"foo@bar.baz\":{\"first_name\":\"Foo\",\"last_name\":\"Bar\"}," +
                "\"al@capone.biz\":{\"first_name\":\"Al\",\"last_name\":\"Capone\"}," +
                "\"asdf@gmail.com\":{\"first_name\":\"A\",\"last_name\":\"F\"}}";
        byte[] data = serializedJsonString.getBytes(Charset.forName(SERIALIZATION_ENCODING));

        PBException e = PBException.newBuilder()
                .setType(Type.INTERNAL_ERROR)
                .setData(ByteString.copyFrom(data))
                .build();
        AbstractExSharedFolderRules ex = new TestExSharedFolderRules(e);

        ImmutableMap<UserID, FullName> map = ex.getExternalUsers();

        assert map.size() == 3;

        verifyEntry(map, "foo@bar.baz", "Foo", "Bar");
        verifyEntry(map, "al@capone.biz", "Al", "Capone");
        verifyEntry(map, "asdf@gmail.com", "A", "F");
    }

    private void verifyEntry(ImmutableMap<UserID, FullName> map, String email,
            String firstName, String lastName)
    {
        UserID userID = UserID.fromInternal(email);

        assert map.containsKey(userID);

        FullName fullName = map.get(userID);

        assert fullName._first.equals(firstName) && fullName._last.equals(lastName);
    }

    /**
     * this class is used to test the constructors and public methods of an abstract class
     */
    private static class TestExSharedFolderRules extends AbstractExSharedFolderRules
    {
        private static final long serialVersionUID = 0L;

        public TestExSharedFolderRules(ImmutableMap<UserID, FullName> map)
        {
            super(map);
        }

        public TestExSharedFolderRules(PBException pb)
        {
            super(pb);
        }

        @Override
        public Type getWireType()
        {
            return Type.INTERNAL_ERROR;
        }
    }
}
