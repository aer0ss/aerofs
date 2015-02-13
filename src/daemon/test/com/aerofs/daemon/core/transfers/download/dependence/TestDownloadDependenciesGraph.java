/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.transfers.download.dependence;

import com.aerofs.daemon.core.transfers.download.dependence.DownloadDependenciesGraph.ExDownloadDeadlock;
import com.aerofs.lib.id.CID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.ids.UniqueID;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestDownloadDependenciesGraph extends AbstractTest
{
    private final DownloadDependenciesGraph _dldg = new DownloadDependenciesGraph();

    private final SIndex _sidx = new SIndex(1);

    private SOCID [] _s = new SOCID[5];

    @Before
    public void setup()
    {
        for (int i = 0; i < _s.length; i++) {
            _s[i] = new SOCID(_sidx, new OID(UniqueID.generate()), CID.META);
        }
    }

    @Test
    public void shouldFindAPathInDiamondShapedGraph()
            throws Exception
    {
        //          0
        //        /   \
        //       1     2
        //        \   /
        //          3
        _dldg.addEdge_(_s[0], _s[1]);
        _dldg.addEdge_(_s[0], _s[2]);
        _dldg.addEdge_(_s[1], _s[3]);
        _dldg.addEdge_(_s[2], _s[3]);

        // Two correct possible outputs
        List<DependencyEdge> correctPathL, correctPathR;
        correctPathL = ImmutableList.of(new DependencyEdge(_s[0], _s[1]),
                new DependencyEdge(_s[1], _s[3]));
        correctPathR = ImmutableList.of(new DependencyEdge(_s[0], _s[2]),
                new DependencyEdge(_s[2], _s[3]));

        List<DependencyEdge> path = _dldg.pathBetween_(_s[0], _s[3]);

        assertTrue(path.equals(correctPathL) || path.equals(correctPathR));
    }

    @Test
    public void shouldFindAPathWhenDeadPathOnLeft()
            throws Exception
    {
        //          0
        //        / |
        //       1  2  3   (N.B. the max of _s[1] and _s[2] will be connected to _s[4])
        //          |
        //          4
        for (int i = 1; i <= 2; i++) _dldg.addEdge_(_s[0], _s[i]);
        SOCID max = (_s[1].compareTo(_s[2]) > 0) ? _s[1] : _s[2];
        _dldg.addEdge_(max, _s[4]);

        List<DependencyEdge> correctPath = ImmutableList.of(
                new DependencyEdge(_s[0], max), new DependencyEdge(max, _s[4]));

        assertEquals(correctPath, _dldg.pathBetween_(_s[0], _s[4]));
        assertEquals(Lists.newLinkedList(), _dldg.pathBetween_(_s[3], _s[4]));
    }

    @Test
    public void shouldThrowWithCycleWhenAddingEdgesInACycle()
            throws Exception
    {
        //   0 -> 1 -> 2 -> 3
        List<DependencyEdge> correctCycle = Lists.newLinkedList();
        for (int i = 0; i < 3; i++) {
            correctCycle.add(new DependencyEdge(_s[i], _s[i+1]));
            _dldg.addEdge_(_s[i], _s[i+1]);
        }

        // Wrap around from 3 -> 0
        List<DependencyEdge> cycle = null;
        try {
            correctCycle.add(new DependencyEdge(_s[3], _s[0]));
            _dldg.addEdge_(_s[3], _s[0]);
        } catch (ExDownloadDeadlock e) {
            cycle = e._cycle;
        }

        assertEquals(correctCycle, cycle);
    }

}
