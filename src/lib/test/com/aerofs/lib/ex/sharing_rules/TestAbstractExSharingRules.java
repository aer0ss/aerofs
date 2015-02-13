/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex.sharing_rules;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.sharing_rules.AbstractExSharingRules.DetailedDescription;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class TestAbstractExSharingRules
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
        ImmutableMap<UserID, FullName> map = ImmutableMap.of(
                UserID.fromInternal("foo@bar.baz"), new FullName("Foo", "Bar"));

        AbstractExSharingRules e = new TestExSharedFolderRules(map);

        // _do not_ change the format of this string, it is parsed by javascript code in
        // shared_folders.mako
        String expectedJsonString =
                "{\"foo@bar.baz\":{\"first_name\":\"Foo\",\"last_name\":\"Bar\"}}";

        assertThat(BaseUtil.utf2string(e.getDataNullable()), containsString(expectedJsonString));
    }

    @Test
    public void shouldRoundTripCorrectly() throws Exception
    {
        ImmutableMap<UserID, FullName> map = ImmutableMap.of(
                UserID.fromInternal("foo@bar.baz"), new FullName("Foo", "Bar"),
                UserID.fromInternal("al@capone.biz"), new FullName("Al", "Capone"),
                UserID.fromInternal("asdf@gmail.com"), new FullName("A", "F"));

        AbstractExSharingRules in = new TestExSharedFolderRules(map);

        PBException pb = PBException.newBuilder()
                .setType(in.getWireType())
                .setData(ByteString.copyFrom(in.getDataNullable()))
                .build();

        assertEquals(map, new TestExSharedFolderRules(pb).decodedExceptionData(
                DetailedDescription.class).users);
    }

    /**
     * this class is used to test the constructors and public methods of an abstract class
     */
    private static class TestExSharedFolderRules extends AbstractExSharingRules
    {
        private static final long serialVersionUID = 0L;

        public TestExSharedFolderRules(ImmutableMap<UserID, FullName> map)
        {
            super(new DetailedDescription(DetailedDescription.Type.WARNING_EXTERNAL_SHARING, map));
        }

        public TestExSharedFolderRules(PBException pb)
        {
            super(pb, DetailedDescription.class);
        }

        @Override
        public Type getWireType()
        {
            return Type.SHARING_RULES_ERROR;
        }
    }
}
