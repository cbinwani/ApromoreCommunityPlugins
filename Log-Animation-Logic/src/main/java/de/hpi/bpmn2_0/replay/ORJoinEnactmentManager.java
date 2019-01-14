/*
 * Copyright © 2009-2018 The Apromore Initiative.
 *
 * This file is part of "Apromore".
 *
 * "Apromore" is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * "Apromore" is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/lgpl-3.0.html>.
 */

package de.hpi.bpmn2_0.replay;

import de.hpi.bpmn2_0.backtracking2.State;
import de.hpi.bpmn2_0.model.FlowNode;
import de.hpi.bpmn2_0.model.connector.SequenceFlow;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
* Manage enabledness of all OR-join gateways in the model
* Contain a mapping from an OR-join to a dedicated ORJoinEnabledChecker
*/
public class ORJoinEnactmentManager {
    private final static Map<FlowNode,ORJoinEnabledChecker> nodeToCheckerMap = new HashMap<>();
    
    public static void init(BPMNDiagramHelper helper) {
        reset();
        for (FlowNode orJoin : helper.getAllORJoins()) {
            nodeToCheckerMap.put(orJoin, new ORJoinEnabledChecker(orJoin,helper));
        }
    }
    
    //Assume that it has been initialized
    public static void update(Map<SequenceFlow,Integer> edgeTokenChanges) {
        for (ORJoinEnabledChecker checker : nodeToCheckerMap.values()) {
            checker.update(edgeTokenChanges);
        }
    }
    
    public static boolean isEnabled(FlowNode orJoinNode) {
        return nodeToCheckerMap.get(orJoinNode).query();
    }
    
    public static boolean isEnabled(FlowNode orJoinNode, Set<SequenceFlow> processMarking) {
        return nodeToCheckerMap.get(orJoinNode).query(processMarking);
    }
    
    /**
     * Get set of sequence flows with tokens that an OR join node is waiting for
     * Assume that the check enabledness (isEnabled method) has already been called on this node and this marking
     * @param orJoinNode
     * @param processMarking
     * @return set of sequence flows or empty set if the OR node is enabled
     */
    public static Set<SequenceFlow> getSequencesWithTokenBeingWaitedFor(FlowNode orJoinNode, Set<SequenceFlow> processMarking) {
        return nodeToCheckerMap.get(orJoinNode).getSequenceFlowsInRed(processMarking);
    }
    
    private static void reset() {
        for (ORJoinEnabledChecker checker : nodeToCheckerMap.values()) {
            checker.reset();
        }
    }
}