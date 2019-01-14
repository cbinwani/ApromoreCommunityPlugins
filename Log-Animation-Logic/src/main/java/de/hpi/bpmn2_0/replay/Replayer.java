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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.hpi.bpmn2_0.replay;

import de.hpi.bpmn2_0.model.Definitions;
import de.hpi.bpmn2_0.model.FlowNode;
import de.hpi.bpmn2_0.model.connector.SequenceFlow;
import de.hpi.bpmn2_0.backtracking2.Backtracking;
import de.hpi.bpmn2_0.backtracking2.Node;
import de.hpi.bpmn2_0.backtracking2.StateElementStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
/**
 *
 * @author Administrator
 */
public class Replayer {
    private ReplayParams params;
    private BPMNDiagramHelper helper;
    private boolean isValidProcess = true;
    private String processCheckMessage = "";
    private String[] colors = {"blue","orange","red","green","margenta"};
    private Map<String, ReplayResult> replayResultMap = new HashMap(); // mapping from trace key to replay result
    private Map<String,Integer> eventNameKeyMap = new HashMap();
    private Map<XTrace,String> traceKeyMap = new HashMap();
    private static final Logger LOGGER = Logger.getLogger(Replayer.class.getCanonicalName());
    
    class ReplayResult {
    	Node leafNode;
    	long runtime;
    	
    	public ReplayResult(Node node, long runtime) {
    		this.leafNode = node;
    		this.runtime = runtime;
    	}
    }
    
    public Replayer(Definitions bpmnDefinition, ReplayParams params) {
        this.params = params;
        this.isValidProcess = true;
        try {
            helper = new BPMNDiagramHelper();
            helper.checkModel(bpmnDefinition);
            ORJoinEnactmentManager.init(helper);
        } catch (Exception ex) {
            isValidProcess = false;
            processCheckMessage = ex.getMessage();
            Logger.getLogger(Replayer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public ReplayParams getReplayParams() {
        return this.params;
    }
    
    public boolean isValidProcess() {
        return isValidProcess;
    }
    
    public String getProcessCheckingMsg() {
        return processCheckMessage;
    }
    
    public AnimationLog replay(XLog log, String color) {
        AnimationLog animationLog = new AnimationLog(log);
        animationLog.setColor(color /*this.getLogColor()*/);
        animationLog.setName(log.getAttributes().get("concept:name").toString());
        
        int eventCount = 0;
        eventNameKeyMap.clear();
        traceKeyMap.clear();
        
        //-------------------------------------------
        // Create trace keys to avoid replaying the same
        // trace which has been already replayed
        // A trace key is a shortened form of the full trace
        //-------------------------------------------
        for (XTrace trace : log) {
        	String traceKey = "";
        	for (XEvent event : trace) {
        		int eventKey = 0; 
        		String eventName = event.getAttributes().get("concept:name").toString();
        		if (!eventNameKeyMap.containsKey(eventName)) {
        			eventNameKeyMap.put(eventName, eventCount);
        			eventKey = eventCount;
        			eventCount++;
        		}
        		else {
        			eventKey = eventNameKeyMap.get(eventName);
        		}
        		traceKey += eventKey + ".";
        	}
        	traceKeyMap.put(trace, traceKey);
        }
        
        //-------------------------------------------
        // Replay every trace in the log
        // Note that the replay includes timing computation for the replayed trace
        //-------------------------------------------
        replayResultMap.clear();
        
        for (XTrace trace : log) {
        	ReplayResult repResult;
        	String traceKey = traceKeyMap.get(trace);
			if (replayResultMap.containsKey(traceKey)) {
			    repResult = replayResultMap.get(traceKey);
			}
			else {
				repResult = this.replayTrace(trace);
				replayResultMap.put(traceKey, repResult);
			}
        	 
			ReplayTrace replayTrace = this.createReplayTrace(trace, repResult);
            if (!replayTrace.isEmpty()) {
//                LOGGER.info("Trace " + replayTrace.getId() + ": " + replayTrace.getBacktrackingNode().getPathString());
                replayTrace.calcTiming();
                animationLog.add(trace, replayTrace);
            }
            else {
                animationLog.addUnreplayTrace(trace);
//                LOGGER.info("Trace " + replayTrace.getId() + ": No path found!");
            }                    
        }
        long algoRuntime = animationLog.getAlgoRuntime();
                
        //------------------------------------------------
        // Compute exact trace fitness 
        //------------------------------------------------
        long startMinMMTime = System.currentTimeMillis();
        double minBoundMoveCostOnModel=0;
        double traceFitness=0;
        if (params.isExactTraceFitnessCalculation()) {
            minBoundMoveCostOnModel = this.getMinBoundMoveCostOnModel();
            animationLog.setMinBoundMoveOnModel(minBoundMoveCostOnModel);
            traceFitness = animationLog.getTraceFitness(minBoundMoveCostOnModel);
        }
        long endMinMMTime = System.currentTimeMillis();
        animationLog.setExactTraceFitnessFormulaTime(endMinMMTime - startMinMMTime + algoRuntime);
        
        //------------------------------------------------
        // Compute approximate trace fitness 
        //------------------------------------------------        
        long startApproxMinMMTime = System.currentTimeMillis();
        double approxTraceFitness = animationLog.getApproxTraceFitness();
        long endApproxMinMMTime = System.currentTimeMillis();
        animationLog.setApproxTraceFitnessFormulaTime(endApproxMinMMTime - startApproxMinMMTime + algoRuntime);
        
//        if (!animationLog.isEmpty()) {       
//            LOGGER.info("REPLAY TRACES WITH FITNESS AND REPLAY PATH");
//            LOGGER.info("TraceID, ExactFitness, ApproxFitness, Reliable, AlgoTime(ms), Replay Path");
//            double approxMMCost = animationLog.getApproxMinMoveModelCost();
//            for (ReplayTrace trace : animationLog.getTraces()) {
//                LOGGER.info(trace.getId() + "," +
//                            trace.getTraceFitness(minBoundMoveCostOnModel) + "," +
//                            trace.getTraceFitness(approxMMCost) + "," +
//                            trace.isReliable() + "," +
//                            trace.getAlgoRuntime() + "," +
//                            trace.getBacktrackingNode().getPathString());
//            }
//            LOGGER.info("LOG " + animationLog.getName() + ". Traces replayed:" + animationLog.getTraces().size() +
//                        ". Exact Trace Fitness:" + traceFitness +
//                        ". Approx. Trace Fitness:" + approxTraceFitness +
//                        ". minBoundMoveCostOnModel:" + animationLog.getMinBoundMoveOnModel() +
//                        ". approxMMCost:" + animationLog.getApproxMinMoveModelCost() +
//                        ". totalAlgoTime:" + animationLog.getAlgoRuntime() +
//                        ". exactTraceFitnessFormulaTime:" + animationLog.getExactTraceFitnessFormulaTime() +
//                        ". approxTraceFitnessFormulaTime:" + animationLog.getApproxTraceFitnessFormulaTime() +
//                        ". StartDate:" + animationLog.getStartDate().toString() +
//                        ". EndDate:" + animationLog.getEndDate() +
//                        ". Color:" + animationLog.getColor());
//        } else {
//            LOGGER.info("LOG " + animationLog.getName() + ": no traces have been replayed");
//        }

        return animationLog;
    }
    
    /*
    * Replay using backtracking algorithm
    * Return replay trace or empty if backtracking returns no result
    * Side effect: the input trace can be preprocessed (activities not in the model are removed)
    * Assume: encodedTraces has been created for the log.
    * The Start event will be assigned a timestamp 20 seconds before the first trace event
    * The End event will be assigned a timestamp 60 seconds after the last trace event
    */
    public ReplayResult replayTrace(XTrace trace) {
        Node leafNode; //contains the found backtracking leaf node
        long start = 0;
        long end = 0;
    
        //--------------------------------------------
        //Remove event names not found in process model or 
        //events with lifecycle:transition = "start" (not yet processed this case)
        //--------------------------------------------
        Iterator<XEvent> iterator = trace.iterator();
        while (iterator.hasNext()) {
            XEvent event = iterator.next();
            if (!helper.getActivityNames().contains(LogUtility.getConceptName(event)) ||
                 LogUtility.getLifecycleTransition(event).toLowerCase().equals("start")) {
                iterator.remove();
            }
        }

        //--------------------------------------------
        // Replay trace with backtracking algorithm and measure time
        //--------------------------------------------
        Backtracking backtrack = new Backtracking(this.params, trace, helper);
        start = System.currentTimeMillis();
        leafNode = backtrack.explore();    
        end = System.currentTimeMillis();
        backtrack.clear();
        backtrack = null;

        return new ReplayResult(leafNode, end-start);
    }
    
    /*
     * Create the sequence of nodes, each node representing a replay step
     */
    public ReplayTrace createReplayTrace(XTrace trace, ReplayResult repResult) {
    	Node leafNode = repResult.leafNode;
    	long runtime = repResult.runtime;
    	
        //---------------------------------
        // Reverse the path to start from the root node
        //---------------------------------
        Stack<Node> stack = new Stack<>();
        Node node = leafNode;
        if (leafNode.getMatchCount() > 0) { //only get trace if at least one activity matched
            while (node != null) {
                stack.push(node);
                node = node.getParent();
            }
        }
        
        //---------------------------------------------
        // Create replay trace from the selected token play path
        // Use the model node and markings in every backtracking node's state 
        // to identify the connection between nodes. The state's model node is
        // the activity or gateway taken and the markings contains
        // all tokens (process state) after the element is taken.
        //---------------------------------------------
        ReplayTrace replayTrace = new ReplayTrace(new XTrace2(trace), this, leafNode, runtime);
        
        if (!stack.isEmpty()) {
            FlowNode modelNode=null;
            TraceNode traceNode;
            Set<SequenceFlow> previousMarkings=null;
            Set<SequenceFlow> moves;
            
            //---------------------------------------------
            // Traverse backtracking result from the beginning
            //---------------------------------------------
            while (!stack.empty()) {
                node = (Node)stack.pop();
                modelNode = node.getState().getElement();
                
                //Skip EVENT_SKIPPED nodes: no need to show them on the replay
                if (node.getState().getElementStatus() == StateElementStatus.EVENT_SKIPPED) {
                    continue;
                }
                
                //Set up starting node
                if (replayTrace.getStart() == null) {
                    traceNode = new TraceNode(modelNode);
                    if (node.getState().getElementStatus() == StateElementStatus.STARTEVENT) {
                        traceNode.setStart((new DateTime(LogUtility.getTimestamp(trace.get(0)))).minusSeconds(
                                                            params.getStartEventToFirstEventDuration()));
                    }   
                    replayTrace.setStart(traceNode);
                    replayTrace.addToReplayedList(modelNode);
                    previousMarkings = node.getState().getMarkings();
                    continue;
                }
                
                //---------------------------------------
                //Add normal node to the replay trace
                //---------------------------------------
                traceNode = new TraceNode(modelNode);
                
                //Connect this node to its incoming nodes based on the replay markings
                //The difference between current node's markings and previous node's 
                //are tokens to be moved
                moves = (Set)((HashSet)previousMarkings).clone();
                moves.removeAll(node.getState().getMarkings()); //moves contains tokens consumed                
                for (SequenceFlow takenFlow : moves) {
                    replayTrace.add((FlowNode)takenFlow.getSourceRef(), traceNode); 
                }
                
                //Set timestamp
                if (traceNode.isActivity()) {
                    if (node.getState().getElementStatus() == StateElementStatus.ACTIVITY_MATCHED) {
                        traceNode.setActivityMatched(true);
                        traceNode.setStart(new DateTime(LogUtility.getTimestamp(trace.get(node.getState().getTraceIndex()-1))));
                    } else {
                        traceNode.setActivitySkipped(true);
                    }
                }           
                
                replayTrace.addToReplayedList(modelNode);                
                previousMarkings = node.getState().getMarkings();
            }
            
            //----------------------------------------------
            // Set timing for end event if it is connected to the last node in the replay trace
            // modelNode: point to the last node of the replay trace
            //----------------------------------------------
            if (helper.getTargets(modelNode).contains(helper.getEndEvent())) {
                //traceNode = replayTrace.getMarkingsMap().get(helper.getEndEvent());
                traceNode = new TraceNode(helper.getEndEvent());
                traceNode.setStart((new DateTime(LogUtility.getTimestamp(trace.get(trace.size()-1)))).plusSeconds(
                                                                params.getLastEventToEndEventDuration()));
                replayTrace.add(modelNode, traceNode);
                replayTrace.addToReplayedList(helper.getEndEvent());
            }
        }
        
        return replayTrace;
    }
    
    
    /**
     * Select the least cost move on model from start to end
     * @return min cost or 0 if no path found due to unsound model
     */    
    public double getMinBoundMoveCostOnModel() {
        Backtracking backtrack = new Backtracking(this.params, helper);
        Node selectedNode = backtrack.exploreShortestPath();
        double minMMCost = 0;
        if (selectedNode != null) {
            Node node = selectedNode;
            while (node != null) {
                if (node.getState().getElementStatus() == StateElementStatus.ACTIVITY_SKIPPED) {
                    minMMCost += this.params.getActivitySkipCost();
                }
                node = node.getParent();
            }
        }
        return minMMCost;
    }
    
    /*
    * When current node is fired
    *   - Each of its active incoming sequences flow will reduce one token
    *   - Each of its outgoing sequences will have one new token
    */
    /*
    private void updateTokenChangeForEdges(FlowNode current, FlowNode previous, Collection<FlowNode> targets) {
        int currentCount;
        
        //Incoming sequence flows
        if (helper.getAllMerges().contains(current)) {  //XOR join: remove one token on the active incoming flow
            for (SequenceFlow flow : current.getIncomingSequenceFlows()) {
                if (previous == (FlowNode)flow.getSourceRef()) {
                    currentCount = this.edgeToTokenChangeMap.get(flow);
                    currentCount--;
                    this.edgeToTokenChangeMap.put(flow, currentCount);
                }
            }
        } 
        else if (helper.getAllJoins().contains(current)) { //AND join: remove one token on every incoming flow
            for (SequenceFlow flow : current.getIncomingSequenceFlows()) {
                currentCount = this.edgeToTokenChangeMap.get(flow);
                currentCount--;
                this.edgeToTokenChangeMap.put(flow, currentCount);
            }
        }
        else if (helper.getAllORJoins().contains(current)) { //OR join: remove one token on every active incoming flow (has token)
            for (SequenceFlow flow : current.getIncomingSequenceFlows()) {
                currentCount = this.edgeToTokenChangeMap.get(flow);
                if (currentCount > 0) {
                    currentCount--;
                    this.edgeToTokenChangeMap.put(flow, currentCount);
                }
            }
        }
        else { // Activity node, XOR split, AND split, OR split: remove one token on the only incoming sequence flow
            for (SequenceFlow flow : current.getIncomingSequenceFlows()) {
                currentCount = this.edgeToTokenChangeMap.get(flow);
                currentCount--;
                this.edgeToTokenChangeMap.put(flow, currentCount);
            }
        }
        
        
        //Outgoing sequence flows
        //XOR split: add one token on the selected outgoing flow
        //AND or OR split: add one token on every outgoing flow
        //Others: add one token on the only outgoing flow
        for (SequenceFlow flow : current.getOutgoingSequenceFlows()) {
            if (targets.contains((FlowNode)flow.getTargetRef())) {
                currentCount = this.edgeToTokenChangeMap.get(flow);
                currentCount++;
                this.edgeToTokenChangeMap.put(flow, currentCount);
            }
        }            
        
        //Call to update all OR-join state
        ORJoinEnactmentManager.update(edgeToTokenChangeMap);
    }
    */
    /*
    private String getLogColor() {
        curColorIndex++;
        if (curColorIndex < this.colors.length) {
            return this.colors[curColorIndex];
        } else {
            return "black"; //black is used to indicate out of color index
        }
    }
    */
}
