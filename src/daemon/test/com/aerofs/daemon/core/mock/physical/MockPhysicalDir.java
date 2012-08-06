package com.aerofs.daemon.core.mock.physical;

/**
 * This class mocks PhysicalFiles and wires the PhysicalFile factory accordingly. Usage:
 *
 *  MockPhysicalDir root =
 *          new MockPhysicalDir("root",
 *              new MockPhysicalFile("f1"),
 *              new MockPhysicalDir("d2",
 *                  new MockPhysicalFile("f2.1"),
 *                  new MockPhysicalDir("d2.2")
 *              )
 *          );
 *
 *     PhysicalFile.Factory factFile = mock(PhysicalFile.Factory.class);
 *     root.mock(fact);
 *     PhysicalObjectsPrinter.printRecursively(fact.create("root"));
 *
 * This will prints:
 *
 *     root/
 *     root/f1
 *     root/d2/
 *     root/d2/f2.1
 *     root/d2/d2.2/
 *
 * Note that directories with be printed with a trailing slash.
 *
 */
public class MockPhysicalDir extends AbstractMockPhysicalObject
{
    public MockPhysicalDir(String name, AbstractMockPhysicalObject ... children)
    {
        super(name, children);
        assert children != null;
    }
}
