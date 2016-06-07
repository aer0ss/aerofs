package com.aerofs.sp.server.integration;

import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.User;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestSP_SetDeviceName extends AbstractSPTest
{
    private static final String UTF8MB4_DEVICE_NAME = "\uD83D\uDCA9";

    private Device device;
    private User user;

    @Before
    public void setup() throws Exception
    {
        sqlTrans.begin();
        user = saveUser();
        device = factDevice.create(new DID(UniqueID.generate())).save(user, "", "", UTF8MB4_DEVICE_NAME);
        sqlTrans.commit();
    }

    @Test
    public void shouldSupportUtf8mb4DeviceName() throws Exception
    {
        sqlTrans.begin();
        assertEquals(device.getName(), ddb.getName(device.id()));
        sqlTrans.commit();
    }
}
