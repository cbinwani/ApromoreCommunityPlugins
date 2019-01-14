/*
 * Copyright © 2009-2016 The Apromore Initiative.
 *
 * This file is part of "Apromore".
 *
 * "Apromore" is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * "Apromore" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/lgpl-3.0.html>.
 */

package org.apromore.service.logvisualizer.impl;

import com.raffaeleconforti.log.util.LogImporter;
import org.apromore.plugin.DefaultParameterAwarePlugin;
import org.apromore.service.logvisualizer.LogVisualizerService;
import org.deckfour.xes.classification.XEventAndClassifier;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventLifeTransClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.eclipse.collections.api.iterator.MutableIntIterator;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.LongList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.api.tuple.primitive.IntIntPair;
import org.eclipse.collections.api.tuple.primitive.ObjectIntPair;
import org.eclipse.collections.impl.bimap.mutable.HashBiMap;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.eclipse.collections.impl.tuple.Tuples;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.processmining.contexts.uitopia.UIContext;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagramImpl;
import org.processmining.models.graphbased.directed.bpmn.BPMNEdge;
import org.processmining.models.graphbased.directed.bpmn.BPMNNode;
import org.processmining.models.graphbased.directed.bpmn.elements.Gateway;
import org.processmining.models.graphbased.directed.bpmn.elements.Event;
import org.processmining.plugins.bpmn.BpmnDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * Created by Raffaele Conforti on 01/12/2016.
 */
@Service
public class LogVisualizerServiceImpl extends DefaultParameterAwarePlugin implements LogVisualizerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogVisualizerServiceImpl.class);

    private XEventClassifier full_classifier = new XEventAndClassifier(new XEventNameClassifier(), new XEventLifeTransClassifier());
    private XEventClassifier name_classifier = new XEventNameClassifier();

    private XTimeExtension xte = XTimeExtension.instance();
    private XConceptExtension xce = XConceptExtension.instance();
    private XLifecycleExtension xle = XLifecycleExtension.instance();

    private XFactory factory = new XFactoryNaiveImpl();

    private DecimalFormat decimalFormat = new DecimalFormat("#.0");
    private boolean contain_start_events = false;

    private HashBiMap<String, Integer> simplified_names;

    private MutableList<IntIntPair> sorted_activity_frequency;
    private IntIntHashMap real_activity_frequency;

    private IntIntHashMap activity_frequency;
    private IntIntHashMap activity_max_frequency;
    private IntIntHashMap activity_min_frequency;

    private ObjectIntHashMap<Arc> arcs_frequency;
    private ObjectIntHashMap<Arc> arcs_max_frequency;
    private ObjectIntHashMap<Arc> arcs_min_frequency;

    private Map<Arc, LongArrayList> arcs_duration_set;

    private MutableList<ObjectIntPair<Arc>> sorted_arcs_frequency;

    private IntHashSet retained_activities;
    private Set<Arc> retained_arcs;

    private String start = "start";
    private String complete = "complete";
    private String plusStart = "+start";
    private String plusComplete = "+complete";
    private String start_name = "|>";
    private String end_name = "[]";
    private int start_int = 1;
    private int end_int = 2;

    public final static boolean FREQUENCY = true;
    public final static boolean DURATION = false;

    public final static int TOTAL = 0;
    public final static int MEAN = 1;
    public final static int MEDIAN = 2;
    public final static int MAX = 3;
    public final static int MIN = 4;

    private final String START = "#C1C9B0";
    private final String END = "#C0A3A1";

    private final String EDGE_START_COLOR_FREQUENCY = "#646464";

    private final ColorGradient activity_frequency_gradient = new ColorGradient(new Color(241, 238, 246), new Color(4, 90, 141));
    private final ColorGradient activity_duration_gradient = new ColorGradient(new Color(254,240,217), new Color(179, 0, 0));
    private final ColorGradient arc_frequency_gradient = new ColorGradient(new Color(100, 100, 100), new Color(41, 41, 41));
    private final ColorGradient arc_duration_gradient = new ColorGradient(new Color(100, 100, 100), new Color(139, 0, 0));

//    public static void main(String[] args) {
//        List<List<Integer>> sl = new ArrayList<>();
//        for(int i = 1; i < 10001; i++) {
//            List<Integer> array = new ArrayList<>();
//            for (int j = 1; j < 10001; j++) {
//                array.add(j);
//            }
//            sl.add(array);
//        }
//
//        double[] res = new double[3];
//        for(int k = 0; k < 20; k++) {
//            List<List<Integer>> nsl1 = new ArrayList<>();
//            List<List<Integer>> nsl2 = new ArrayList<>();
//            List<List<Integer>> nsl3 = Collections.synchronizedList(new ArrayList<>());
//
//            long start1 = System.currentTimeMillis();
//            for (int i = 0; i < sl.size(); i++) {
//                List<Integer> array = new ArrayList<>();
//                for (int j = 0; j < sl.get(i).size(); j++) {
//                    if (sl.get(i).get(j) % 2 == 0) {
//                        array.add(sl.get(i).get(j));
//                    }
//                }
//                nsl1.add(array);
//            }
//            long end1 = System.currentTimeMillis();
//
//            long start2 = System.currentTimeMillis();
//            sl.stream().forEach(l -> nsl2.add(l.stream().filter(i -> i % 2 == 0).collect(Collectors.toList())));
//            long end2 = System.currentTimeMillis();
//
//            long start3 = System.currentTimeMillis();
//            sl.parallelStream().forEach(l -> nsl3.add(l.parallelStream().filter(i -> i % 2 == 0).collect(Collectors.toList())));
//            long end3 = System.currentTimeMillis();
//
//            if(k > 9) {
//                res[0] = (end1 - start1);
//                res[1] = (end2 - start2);
//                res[2] = (end3 - start3);
//            }
//        }
//
//        System.out.println(res[0] / 10 + " " + res[1] / 10 + " " + res[2] / 10);
//
////        System.out.println(nsl1);
////        System.out.println(nsl2);
////        System.out.println(nsl3);
//    }

    public static void main(String[] args) {
        LogVisualizerServiceImpl l = new LogVisualizerServiceImpl();
        XLog log = null;
        try {
//            log = ImportEventLog.importFromFile(new XFactoryNaiveImpl(), "/Volumes/Data/IdeaProjects/ApromoreCodeServerNew/Compare-Logic/src/test/resources/CAUSCONC-1/bpLog3.xes");
            log = LogImporter.importFromFile(new XFactoryNaiveImpl(), "/Volumes/MobileData/Logs/Demonstration examples/Discover Process Maps/Purchasing Example.xes.gz");
//            log = LogImporter.importFromFile(new XFactoryNaiveImpl(), "/Users/conforti/Downloads/BPIC13_i.xes.gz");
//            log = LogImporter.importFromFile(new XFactoryNaiveImpl(), "/Volumes/MobileData/Logs/Demonstration examples/Discover Process Model/Synthetic Log with Subprocesses.xes.gz");
//            log = LogImporter.importFromFile(new XFactoryNaiveImpl(), "/Volumes/MobileData/Logs/4TU Logs - Noise Filtered/BPI2017 - Loan Application (NoiseFiltered).xes.gz");
        } catch (Exception e) {
            e.printStackTrace();
        }
        XLog flog = l.generateFilteredFittedLog(log, new HashSet<>(), new HashSet<>(), 0.5, 0.9);
        System.out.println();
//        JSONArray s = l.generateJSONArrayFromLog(log, 0, 100, FREQUENCY, MEAN);
//        JSONArray s1 = l.generateJSONArrayFromLog(log, 0.4, 0, true);
//        System.out.println(s);
//        System.out.println(s1);
//        System.out.println(l.visualizeLog(log, 0.30, 1));
//        l.generateDOTFromLog(log, 0.0, 0.36);
    }

    @Override
    public String visualizeLog(XLog log, double activities, double arcs) {
        try {
            BPMNDiagram bpmnDiagram = generateDiagramFromLog(log, activities, arcs, FREQUENCY, MEAN);

            UIContext context = new UIContext();
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIPluginContext uiPluginContext = context.getMainPluginContext();
            BpmnDefinitions.BpmnDefinitionsBuilder definitionsBuilder = new BpmnDefinitions.BpmnDefinitionsBuilder(uiPluginContext, bpmnDiagram);
            BpmnDefinitions definitions = new BpmnDefinitions("definitions", definitionsBuilder);

            String sb = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n " +
                    "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"\n " +
                    "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"\n " +
                    "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"\n " +
                    "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n " +
                    "targetNamespace=\"http://www.omg.org/bpmn20\"\n " +
                    "xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\">") +
                    definitions.exportElements() +
                    "</definitions>";

            return sb;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public JSONArray generateJSONArrayFromLog(XLog log, double activities, double arcs, boolean frequency_vs_duration, int avg_vs_min_vs_max) {
        try {
            BPMNDiagram bpmnDiagram = generateDiagramFromLog(log, activities, arcs, frequency_vs_duration, avg_vs_min_vs_max);
            return generateJSONFromBPMN(bpmnDiagram, frequency_vs_duration, avg_vs_min_vs_max);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public BPMNDiagram generateBPMNFromLog(XLog log, double activities, double arcs, boolean frequency_vs_duration, int avg_vs_min_vs_max) {
        try {
            BPMNDiagram bpmnDiagram = generateDiagramFromLog(log, activities, arcs, frequency_vs_duration, avg_vs_min_vs_max);
            return bpmnDiagram;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public XLog generateFilteredLog(XLog log, Set<String> manually_removed_activities, double activities, double arcs) {
        initializeDatastructures();
        log = removeUnrequiredEvents(log);
        List<IntList> simplified_log = simplifyLog(log);
        List<LongList> simplified_times_log = simplifyTimesLog(log);

        IntHashSet manually_removed_activities_int = getActivitiesCode(manually_removed_activities);
        List<IntList> filtered_log = filterLog(simplified_log, simplified_times_log, activities, manually_removed_activities_int);

        XFactoryNaiveImpl factory = new XFactoryNaiveImpl();
        XLog filtered_xlog = factory.createLog(log.getAttributes());
        for(int trace = 0; trace < filtered_log.size(); trace++) {
            XTrace filtered_xtrace = factory.createTrace(log.get(trace).getAttributes());
            IntList filtered_trace = filtered_log.get(trace);
            int unfiltered_event = 0;
            for(int event = 1; event < filtered_trace.size() - 1; event++) {
                while(!full_classifier.getClassIdentity(log.get(trace).get(unfiltered_event)).equals(getEventFullName(filtered_trace.get(event)))) {
                    unfiltered_event++;
                }

                if(manually_removed_activities.contains(name_classifier.getClassIdentity(log.get(trace).get(unfiltered_event)))) continue;

                filtered_xtrace.add(log.get(trace).get(unfiltered_event));
                unfiltered_event++;

            }
            if(filtered_xtrace.size() > 0) {
                filtered_xlog.add(filtered_xtrace);
            }
        }

        return filtered_xlog;
    }

    private IntHashSet getActivitiesCode(Set<String> manually_removed_activities) {
        IntHashSet manually_removed_activities_int = new IntHashSet();
        for(String activity : manually_removed_activities) {
            String activity_start = activity + plusStart;
            String activity_complete = activity + plusComplete;

            if(getEventNumber(activity_start) != null) manually_removed_activities_int.add(getEventNumber(activity_start));
            if(getEventNumber(activity_complete) != null) manually_removed_activities_int.add(getEventNumber(activity_complete));
        }
        return manually_removed_activities_int;
    }

    @Override
    public XLog generateFilteredFittedLog(XLog log, Set<String> manually_removed_activities, Set<String> manually_removed_arcs, double activities, double arcs) {
        initializeDatastructures();
        log = removeUnrequiredEvents(log);
        List<IntList> simplified_log = simplifyLog(log);
        List<LongList> simplified_times_log = simplifyTimesLog(log);
        IntHashSet manually_removed_activities_int = getActivitiesCode(manually_removed_activities);
        List<IntList> filtered_log = filterLog(simplified_log, simplified_times_log, activities, manually_removed_activities_int);
        Set<Arc> maintained_arcs = selectArcs(arcs);

        for(String string_arc : manually_removed_arcs) {
            String source = string_arc.substring(0, string_arc.indexOf(" (~) "));
            String target = string_arc.substring(string_arc.indexOf(" (~) ") + 5);

            String source_start = source + plusStart;
            String source_complete = source + plusComplete;
            Set<Integer> sources = new HashSet<>();
            if(getEventNumber(source_start) != null) sources.add(getEventNumber(source_start));
            if(getEventNumber(source_complete) != null) sources.add(getEventNumber(source_complete));

            String target_start = target + plusStart;
            String target_complete = target + plusComplete;
            Set<Integer> targets = new HashSet<>();
            if(getEventNumber(target_start) != null) targets.add(getEventNumber(target_start));
            if(getEventNumber(target_complete) != null) targets.add(getEventNumber(target_complete));

            for(int s : sources) {
                for(int t : targets) {
                    maintained_arcs.remove(new Arc(s, t));
                }
            }
        }

        XLog original_log = generateFilteredLog(log, manually_removed_activities, activities, arcs);

        XFactoryNaiveImpl factory = new XFactoryNaiveImpl();
        XLog filtered_xlog = factory.createLog(log.getAttributes());

        LogFitter logFitter = new LogFitter(simplified_names, factory);
        for(int trace = 0; trace < filtered_log.size(); trace++) {
            XTrace filtered_xtrace = logFitter.fitTrace(original_log.get(trace), filtered_log.get(trace), maintained_arcs);
            if(filtered_xtrace != null) {
                filtered_xlog.add(filtered_xtrace);
            }
        }

        return filtered_xlog;
    }

    private BPMNDiagram generateDiagramFromLog(XLog log, double activities, double arcs, boolean frequency_vs_duration, int avg_vs_min_vs_max) {
        initializeDatastructures();
        log = removeUnrequiredEvents(log);
        List<IntList> simplified_log = simplifyLog(log);
        List<LongList> simplified_times_log = simplifyTimesLog(log);
        filterLog(simplified_log, simplified_times_log, activities, new IntHashSet());
        retained_arcs = selectArcs(arcs);

        BPMNDiagram bpmnDiagram = new BPMNDiagramImpl("");
        IntObjectHashMap<BPMNNode> map = new IntObjectHashMap();
        for(int i : retained_activities.toArray()) {
            BPMNNode node = bpmnDiagram.addActivity(simplified_names.inverse().get(i), false, false, false, false, false);
            map.put(i, node);
        }

        for(Arc arc : retained_arcs) {
            BPMNNode source = map.get(arc.getSource());
            BPMNNode target = map.get(arc.getTarget());
            if(frequency_vs_duration == FREQUENCY) {
                if(avg_vs_min_vs_max == TOTAL) {
                    bpmnDiagram.addFlow(source, target, "[" + arcs_frequency.get(arc) + "]");
                }else if(avg_vs_min_vs_max == MAX) {
                    bpmnDiagram.addFlow(source, target, "[" + arcs_max_frequency.get(arc) + "]");
                }else if(avg_vs_min_vs_max == MIN) {
                    bpmnDiagram.addFlow(source, target, "[" + arcs_min_frequency.get(arc) + "]");
                }
            }else {
                if(avg_vs_min_vs_max == TOTAL) {
                    bpmnDiagram.addFlow(source, target, "[" + getArcTotalDuration(arc) + "]");
                }else if(avg_vs_min_vs_max == MEAN) {
                    bpmnDiagram.addFlow(source, target, "[" + getArcMeanDuration(arc) + "]");
                }else if(avg_vs_min_vs_max == MEDIAN) {
                    bpmnDiagram.addFlow(source, target, "[" + getArcMedianDuration(arc) + "]");
                }else if(avg_vs_min_vs_max == MAX) {
                    bpmnDiagram.addFlow(source, target, "[" + getArcMaxDuration(arc) + "]");
                }else if(avg_vs_min_vs_max == MIN) {
                    bpmnDiagram.addFlow(source, target, "[" + getArcMinDuration(arc) + "]");
                }
            }
        }
        return collapseStartCompleteActivities(bpmnDiagram);
    }

    private BPMNDiagram collapseStartCompleteActivities(BPMNDiagram bpmnDiagram) {
        BPMNDiagram diagram = new BPMNDiagramImpl("");

        Map<String, BPMNNode> nodes_map = new HashMap<>();
        for(BPMNNode node : bpmnDiagram.getNodes()) {
            String collapsed_name = getCollapsedEvent(node.getLabel());
            if(!nodes_map.containsKey(collapsed_name)) {
                BPMNNode collapsed_node = diagram.addActivity(collapsed_name, false, false, false, false, false);
                nodes_map.put(collapsed_name, collapsed_node);
            }
        }

        Set<Pair<String, String>> edges = new HashSet<>();
        for(BPMNEdge<? extends BPMNNode, ? extends BPMNNode> edge : bpmnDiagram.getEdges()) {
            String source_name = edge.getSource().getLabel();
            String target_name = edge.getTarget().getLabel();

            String collapsed_source_name = getCollapsedEvent(source_name);
            String collapsed_target_name = getCollapsedEvent(target_name);

            BPMNNode source = nodes_map.get(collapsed_source_name);
            BPMNNode target = nodes_map.get(collapsed_target_name);

            Pair pair = Tuples.pair(collapsed_source_name, collapsed_target_name);
            if(!collapsed_source_name.equals(collapsed_target_name) || isSingleTypeEvent(getEventNumber(source_name)) || isCompleteEvent(source_name)) {
                if(!edges.contains(pair)) {
                    diagram.addFlow(source, target, edge.getLabel());
                    edges.add(pair);
                }
            }
        }
        return diagram;
    }

    private void initializeDatastructures() {
        simplified_names = new HashBiMap<>();
        real_activity_frequency = new IntIntHashMap();

        activity_frequency = new IntIntHashMap();
        activity_max_frequency = new IntIntHashMap();
        activity_min_frequency = new IntIntHashMap();

        arcs_frequency = new ObjectIntHashMap<>();
        arcs_max_frequency = new ObjectIntHashMap<>();
        arcs_min_frequency = new ObjectIntHashMap<>();

        arcs_duration_set = new UnifiedMap<>();
    }

    private XLog removeUnrequiredEvents(XLog log) {
        boolean contain = false;
        for(XTrace trace : log) {
            Iterator<XEvent> iterator = trace.iterator();
            while (iterator.hasNext()) {
                XEvent event = iterator.next();
                String name = full_classifier.getClassIdentity(event);
                if(name.contains("+") && (isCompleteEvent(name))) {
                    contain = true;
                }
            }
        }

        if(contain) {
            for (XTrace trace : log) {
                Iterator<XEvent> iterator = trace.iterator();
                while (iterator.hasNext()) {
                    XEvent event = iterator.next();
                    String name = full_classifier.getClassIdentity(event);
                    if (name.contains("+") && !(isStartEvent(name) || isCompleteEvent(name))) {
                        iterator.remove();
                    }
                }
            }
        }else {
            XConceptExtension xce = XConceptExtension.instance();
            XLifecycleExtension xle = XLifecycleExtension.instance();
            for (XTrace trace : log) {
                Iterator<XEvent> iterator = trace.iterator();
                while (iterator.hasNext()) {
                    XEvent event = iterator.next();
                    String name = full_classifier.getClassIdentity(event);
                    xce.assignName(event, name.replace("+", "-"));
                    xle.assignTransition(event, complete);
                }
            }
        }
        return log;
    }

    private List<IntList> simplifyLog(XLog log) {
        List<IntList> simplified_log = new ArrayList<>();

        simplified_names.put(start_name, start_int);
        simplified_names.put(end_name, end_int);

        for(XTrace trace : log) {
            IntArrayList simplified_trace = new IntArrayList(trace.size());

            updateActivityFrequency(start_int, 1);

            simplified_trace.add(start_int);

            IntIntHashMap eventsCount = new IntIntHashMap();
            for(XEvent event : trace) {
                String name = full_classifier.getClassIdentity(event);
                if(name.contains("+")) {
                    String prename = name.substring(0, name.indexOf("+"));
                    String postname = name.substring(name.indexOf("+"));
                    name = prename + postname.toLowerCase();
                }
                if(isStartEvent(name)) contain_start_events = true;

                Integer simplified_event;
                if((simplified_event = getEventNumber(name)) == null) {
                    simplified_event = simplified_names.size() + 1;
                    simplified_names.put(name, simplified_event);
                }

                real_activity_frequency.addToValue(simplified_event, 1);

                eventsCount.addToValue(simplified_event, 1);
                simplified_trace.add(simplified_event);
            }

            for(int event : eventsCount.keySet().toArray()) {
                updateActivityFrequency(event, eventsCount.get(event));
            }

            updateActivityFrequency(end_int, 1);
            simplified_trace.add(end_int);

            simplified_log.add(simplified_trace);
        }

        sorted_activity_frequency = activity_frequency.keyValuesView().toList();
        sorted_activity_frequency.sort(new Comparator<IntIntPair>() {
            @Override
            public int compare(IntIntPair o1, IntIntPair o2) {
                return Integer.compare(o2.getTwo(), o1.getTwo());
            }
        });

        return simplified_log;
    }

    private void updateActivityFrequency(int activity, int frequency) {
        activity_frequency.addToValue(activity, frequency);

        Integer value = Math.max(frequency, activity_max_frequency.get(activity));
        activity_max_frequency.put(activity, value);

        value = Math.min(frequency, activity_min_frequency.get(activity));
        value = (value == 0 ? frequency : value);
        activity_min_frequency.put(activity, value);
    }

    private void updateArcFrequency(Arc arc, int frequency) {
        arcs_frequency.addToValue(arc, frequency);

        Integer value = Math.max(frequency, arcs_max_frequency.get(arc));
        arcs_max_frequency.put(arc, value);

        value = Math.min(frequency, arcs_min_frequency.get(arc));
        value = (value == 0 ? frequency : value);
        arcs_min_frequency.put(arc, value);
    }

    private List<LongList> simplifyTimesLog(XLog log) {
        List<LongList> simplified_times_log = new ArrayList<>();

        for(XTrace trace : log) {
            LongArrayList simplified_times_trace = new LongArrayList(trace.size());

            for(int i = 0; i < trace.size(); i++) {
                XEvent event = trace.get(i);
                Long time = xte.extractTimestamp(event).getTime();
                if(i == 0) {
                    simplified_times_trace.add(time);
                }
                if(i == trace.size() - 1) {
                    simplified_times_trace.add(time);
                }
                simplified_times_trace.add(time);
            }

            simplified_times_log.add(simplified_times_trace);
        }

        return simplified_times_log;
    }

    private List<IntList> filterLog(List<IntList> log, List<LongList> times_log, double activities, IntHashSet manually_removed_activities) {
        retained_activities = selectActivities(activities);
        retained_activities.removeAll(manually_removed_activities);
        List<IntList> filtered_log = new ArrayList<>(log.size());

        for(int t = 0; t < log.size(); t++) {
            IntList trace = log.get(t);
            LongList time_trace = times_log.get(t);

            IntArrayList filtered_trace = new IntArrayList();
            LongArrayList filtered_time_trace = new LongArrayList();
            for(int i = 0; i < trace.size(); i++) {
                if(retained_activities.contains(trace.get(i))) {
                    filtered_trace.add(trace.get(i));
                    filtered_time_trace.add(time_trace.get(i));
                }
            }
            filtered_log.add(filtered_trace);

            IntHashSet not_reached = new IntHashSet();
            IntHashSet not_reaching = new IntHashSet();
            for(int i = 0; i < filtered_trace.size(); i++) {
                if(i != 0) not_reached.add(filtered_trace.get(i));
                if(i != filtered_trace.size() - 1) not_reaching.add(filtered_trace.get(i));
            }

            ObjectIntHashMap<Arc> arcsCount = new ObjectIntHashMap<>();
            for(int i = 0; i < filtered_trace.size() - 1; i++) {
                for(int j = i + 1; j < filtered_trace.size(); j++) {
                    if (isAcceptableTarget(filtered_trace, filtered_trace.get(i), filtered_trace.get(j))) {
                        createArc(arcsCount, not_reached, not_reaching, filtered_trace.get(i), filtered_trace.get(j), filtered_time_trace.get(j) - filtered_time_trace.get(i));
                        break;
                    }
                }
            }

            for(Arc arc : arcsCount.keySet().toArray(new Arc[arcsCount.size()])) {
                updateArcFrequency(arc, arcsCount.get(arc));
            }
        }

        sorted_arcs_frequency = arcs_frequency.keyValuesView().toList();
        sorted_arcs_frequency.sort(new Comparator<ObjectIntPair<Arc>>() {
            @Override
            public int compare(ObjectIntPair<Arc> o1, ObjectIntPair<Arc> o2) {
                return Integer.compare(o2.getTwo(), o1.getTwo());
            }
        });

        return filtered_log;
    }

    private void createArc(ObjectIntHashMap<Arc> arcsCount, IntHashSet not_reached, IntHashSet not_reaching, int source, int target, long duration) {
        Arc arc = new Arc(source, target);
        arcsCount.addToValue(arc, 1);
        updateArcDuration(arc, duration);
        not_reaching.remove(source);
        not_reached.remove(target);
    }

    private void updateArcDuration(Arc arc, long duration) {
        LongArrayList durations = arcs_duration_set.get(arc);
        if(durations == null) {
            durations = new LongArrayList();
            arcs_duration_set.put(arc, durations);
        }
        durations.add(duration);
    }

    private boolean isAcceptableTarget(IntArrayList trace, int source_event, int target_event) {
        if(source_event == 1 && target_event == 2) return false;

        String source_name = getEventFullName(source_event);
        String target_name = getEventFullName(target_event);

        if(source_event == 1) return (isStartEvent(target_name) || isSingleTypeEvent(target_event) || isSingleTypeEvent(trace, target_event));
        if(target_event == 2) return (isCompleteEvent(source_name) || isSingleTypeEvent(source_event) || isSingleTypeEvent(trace, source_event));

        if(isStartEvent(source_name)) {
            String expected_target_name = getCompleteEvent(source_name);
            if (!isSingleTypeEvent(source_event) && !isSingleTypeEvent(trace, source_event)) {
                return getEventNumber(expected_target_name) == target_event;
            }else {
                return isStartEvent(target_name) || isSingleTypeEvent(target_event) || isSingleTypeEvent(trace, target_event);
            }
        }else if(isCompleteEvent(source_name)) {
            return (isStartEvent(target_name) || isSingleTypeEvent(target_event) || isSingleTypeEvent(trace, target_event));
        }
        return false;
    }

    private IntHashSet selectActivities(double activities) {
        IntHashSet retained_activities = new IntHashSet();
        retained_activities.add(start_int);
        retained_activities.add(end_int);

        double threshold = 0.0;
        if(real_activity_frequency.size() > 0) threshold = Math.log10(real_activity_frequency.max()) * activities;

        for(int i = 0; i < sorted_activity_frequency.size(); i++) {
            double current = Math.log10(sorted_activity_frequency.get(i).getTwo());
            if(current >= threshold) {
                retained_activities.add(sorted_activity_frequency.get(i).getOne());
            }
        }

        if(contain_start_events) {
            MutableIntIterator iterator = retained_activities.intIterator();
            while (iterator.hasNext()) {
                int i = iterator.next();
                String name = getEventFullName(i);
                String name_to_check = "";

                if (isStartEvent(name)) name_to_check = getCompleteEvent(name);
                else if (isCompleteEvent(name)) name_to_check = getStartEvent(name);

                if (!isSingleTypeEvent(getEventNumber(name)) && !retained_activities.contains(getEventNumber(name_to_check))) {
                    iterator.remove();
                }
            }
        }
        return retained_activities;
    }

    private Set<Arc> selectArcs(double arcs) {
        Set<Arc> retained_arcs = new HashSet();

        double threshold = 0.0;
        if(arcs_frequency.size() > 0) threshold = Math.log10(arcs_frequency.max()) * arcs;

        retained_arcs.addAll(arcs_frequency.keySet());

        for(int i = sorted_arcs_frequency.size() - 1; i >= 0; i--) {
            double current = Math.log10(sorted_arcs_frequency.get(i).getTwo());
            Arc arc = sorted_arcs_frequency.get(i).getOne();
            if(current < threshold) {
                if(retained_arcs.contains(arc)) {
                    retained_arcs.remove(arc);
                    if (!reachable(arc.getTarget(), retained_arcs) || !reaching(arc.getSource(), retained_arcs)) {
                        retained_arcs.add(arc);
                    }
                }
            }else {
                return retained_arcs;
            }
        }

        return retained_arcs;
    }

    private boolean reachable(int node, Set<Arc> retained_arcs) {
        if(node == 1) return true;

        IntHashSet visited = new IntHashSet();
        IntArrayList reached = new IntArrayList();
        reached.add(1);

        while (reached.size() > 0) {
            int current = reached.removeAtIndex(0);
            for (Arc arc : retained_arcs) {
                if(arc.getSource() == current) {
                    if(arc.getTarget() == node) {
                        return true;
                    }else if(!visited.contains(arc.getTarget())) {
                        visited.add(arc.getTarget());
                        reached.add(arc.getTarget());
                    }
                }
            }
        }

        return false;
    }

    private boolean reaching(int node, Set<Arc> retained_arcs) {
        if(node == 2) return true;

        IntHashSet visited = new IntHashSet();
        IntArrayList reached = new IntArrayList();
        reached.add(2);

        while (reached.size() > 0) {
            int current = reached.removeAtIndex(0);
            for (Arc arc : retained_arcs) {
                if(arc.getTarget() == current) {
                    if(arc.getSource() == node) {
                        return true;
                    }else if(!visited.contains(arc.getSource())) {
                        visited.add(arc.getSource());
                        reached.add(arc.getSource());
                    }
                }
            }
        }

        return false;
    }

    private JSONArray generateJSONFromBPMN(BPMNDiagram bpmnDiagram, boolean frequency_vs_duration, int avg_vs_max_vs_min) throws JSONException {
        JSONArray graph = new JSONArray();
        Map<BPMNNode, Integer> mapping = new HashMap<>();
        int i = 1;
        int start_node = -1;
        int end_node = -1;

        int string_length = 0;
        for(BPMNNode node : getNodes(bpmnDiagram)) {
            string_length = Math.max(string_length, node.getLabel().replace("'", "").length());
        }
        String width = (int) (string_length * 3.5)+ "px";
        String textwidth = ((int) (string_length * 3.5) - 10) + "px";

        for(BPMNNode node : getNodes(bpmnDiagram)) {
            JSONObject jsonOneNode = new JSONObject();
            mapping.put(node, i);
            jsonOneNode.put("id", i);
            if(node.getLabel().equals(start_name)) {
                start_node = i;
                jsonOneNode.put("name", "");
                jsonOneNode.put("shape", "ellipse");
                jsonOneNode.put("color", START);
                jsonOneNode.put("width", "15px");
                jsonOneNode.put("height", "15px");
                jsonOneNode.put("textwidth", textwidth);
            }else if(node.getLabel().equals(end_name)) {
                end_node = i;
                jsonOneNode.put("name", "");
                jsonOneNode.put("shape", "ellipse");
                jsonOneNode.put("color", END);
                jsonOneNode.put("width", "15px");
                jsonOneNode.put("height", "15px");
                jsonOneNode.put("textwidth", textwidth);
            }else {
                if(frequency_vs_duration == FREQUENCY) jsonOneNode.put("name", node.getLabel().replace("'", "") + "\\n\\n" + getEventFrequency(false, avg_vs_max_vs_min, node.getLabel()));
                else jsonOneNode.put("name", node.getLabel().replace("'", "") + "\\n\\n" + convertMilliseconds("" + getEventDuration(avg_vs_max_vs_min, node.getLabel())));

                jsonOneNode.put("shape", "roundrectangle");

                if(frequency_vs_duration == FREQUENCY) jsonOneNode.put("color", getFrequencyColor(avg_vs_max_vs_min, node, bpmnDiagram.getNodes()));
                else jsonOneNode.put("color", getDurationColor(avg_vs_max_vs_min, node, bpmnDiagram.getNodes()));
                jsonOneNode.put("width", width);
                jsonOneNode.put("textwidth", textwidth);
                jsonOneNode.put("height", "30px");
            }
            JSONObject jsonDataNode = new JSONObject();
            jsonDataNode.put("data", jsonOneNode);
            graph.put(jsonDataNode);
            i++;
        }

        double maxWeight = 0.0;
        for(BPMNEdge<? extends BPMNNode, ? extends BPMNNode> edge : getEdges(bpmnDiagram)) {
            String number = edge.getLabel();
            if (number.contains("[")) {
                number = number.substring(1, number.length() - 1);
            } else {
                number = "0";
            }
            maxWeight = Math.max(maxWeight, Double.parseDouble(number));
        }

        for(BPMNEdge<? extends BPMNNode, ? extends BPMNNode> edge : getEdges(bpmnDiagram)) {
            int source = mapping.get(edge.getSource());
            int target = mapping.get(edge.getTarget());
            String number = edge.getLabel();
            if(number.contains("[")) {
                number = number.substring(1, number.length() - 1);
            }else {
                number = "1";
            }

            JSONObject jsonOneLink = new JSONObject();
            jsonOneLink.put("source", source);
            jsonOneLink.put("target", target);

            if(source == start_node) jsonOneLink.put("style", "dashed");
            else if(target == end_node) jsonOneLink.put("style", "dashed");
            else jsonOneLink.put("style", "solid");

            BigDecimal bd = new BigDecimal((Double.parseDouble(number) * 100.0 / maxWeight));
            bd = bd.setScale(2, RoundingMode.HALF_UP);

            if (frequency_vs_duration == DURATION && (source == start_node || target == end_node)) {
                jsonOneLink.put("strength", 0);
                jsonOneLink.put("label", "");
                jsonOneLink.put("color", EDGE_START_COLOR_FREQUENCY);
            }else {
                jsonOneLink.put("strength", bd.doubleValue());
                if(frequency_vs_duration == FREQUENCY) {
                    jsonOneLink.put("label", number);
                    jsonOneLink.put("color", "#" + Integer.toHexString(arc_frequency_gradient.generateColor(bd.doubleValue() / 100).getRGB()).substring(2));
                }else {
                    jsonOneLink.put("label", convertMilliseconds(number));
                    jsonOneLink.put("color", "#" + Integer.toHexString(arc_duration_gradient.generateColor(bd.doubleValue() / 100).getRGB()).substring(2));
                }
            }

            JSONObject jsonDataLink = new JSONObject();
            jsonDataLink.put("data", jsonOneLink);
            graph.put(jsonDataLink);
        }

        return graph;
    }

    public BPMNDiagram insertBPMNGateways(BPMNDiagram bpmnDiagram) {
        BPMNDiagram gatewayDiagram = new BPMNDiagramImpl(bpmnDiagram.getLabel());

        Map<BPMNNode, BPMNNode> incoming = new HashMap<>();
        Map<BPMNNode, BPMNNode> outgoing = new HashMap<>();

        for(BPMNNode node : bpmnDiagram.getNodes()) {
            BPMNNode node1;
            if(node.getLabel().equals(start_name)) {
                node1 = gatewayDiagram.addEvent(start_name, Event.EventType.START, Event.EventTrigger.NONE, Event.EventUse.CATCH, false, null);
            }else if(node.getLabel().equals(end_name)) {
                node1 = gatewayDiagram.addEvent(end_name, Event.EventType.END, Event.EventTrigger.NONE, Event.EventUse.THROW, false, null);
            }else {
                node1 = gatewayDiagram.addActivity(node.getLabel(), false, false, false, false, false);
            }

            if(bpmnDiagram.getInEdges(node).size() > 1) {
                Gateway join = gatewayDiagram.addGateway("", Gateway.GatewayType.DATABASED);
                gatewayDiagram.addFlow(join, node1, "");
                incoming.put(node, join);
            }else {
                incoming.put(node, node1);
            }

            if(bpmnDiagram.getOutEdges(node).size() > 1) {
                Gateway split = gatewayDiagram.addGateway("", Gateway.GatewayType.DATABASED);
                gatewayDiagram.addFlow(node1, split, "");
                outgoing.put(node, split);
            }else {
                outgoing.put(node, node1);
            }
        }

        for(BPMNEdge<? extends BPMNNode, ? extends BPMNNode> edge : bpmnDiagram.getEdges()) {
            gatewayDiagram.addFlow(outgoing.get(edge.getSource()), incoming.get(edge.getTarget()), "");
        }

        return gatewayDiagram;
    }

    private String convertMilliseconds(String number) {
        Double milliseconds = Double.parseDouble(number);
        Double seconds = milliseconds / 1000.0;
        Double minutes = seconds / 60.0;
        Double hours = minutes / 60.0;
        Double days = hours / 24.0;
        Double weeks = days / 7.0;
        Double months = days / 30.0;
        Double years = days / 365.0;

        if(years > 1) {
            return decimalFormat.format(years) + " yrs";
        }else if(months > 1) {
            return decimalFormat.format(months) + " mths";
        }else if(weeks > 1) {
            return decimalFormat.format(weeks) + " wks";
        }else if(days > 1) {
            return decimalFormat.format(days) + " d";
        }else if(hours > 1) {
            return decimalFormat.format(hours) + " hrs";
        }else if(minutes > 1) {
            return decimalFormat.format(minutes) + " mins";
        }else if(seconds > 1) {
            return decimalFormat.format(seconds) + " secs";
        }else if(milliseconds > 1){
            return decimalFormat.format(milliseconds) + " millis";
        }else {
            return "instant";
        }
    }

    private BPMNNode[] getNodes(BPMNDiagram bpmnDiagram) {
        Set<BPMNNode> nodes = bpmnDiagram.getNodes();
        BPMNNode[] array_nodes = nodes.toArray(new BPMNNode[nodes.size()]);
        Arrays.sort(array_nodes, new Comparator<BPMNNode>() {
            @Override
            public int compare(BPMNNode o1, BPMNNode o2) {
                return o1.getLabel().compareTo(o2.getLabel());
            }
        });
        return array_nodes;
    }

    private BPMNEdge<? extends BPMNNode, ? extends BPMNNode>[] getEdges(BPMNDiagram bpmnDiagram) {
        Set<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>> edges = bpmnDiagram.getEdges();
        BPMNEdge<? extends BPMNNode, ? extends BPMNNode>[] array_edges = edges.toArray(new BPMNEdge[edges.size()]);
        Arrays.sort(array_edges, new Comparator<BPMNEdge<? extends BPMNNode, ? extends BPMNNode>>() {
            @Override
            public int compare(BPMNEdge<? extends BPMNNode, ? extends BPMNNode> o1, BPMNEdge<? extends BPMNNode, ? extends BPMNNode> o2) {
                if(o1.getSource().getLabel().equals(o2.getSource().getLabel())) {
                    return o1.getTarget().getLabel().compareTo(o2.getTarget().getLabel());
                }
                return o1.getSource().getLabel().compareTo(o2.getSource().getLabel());
            }
        });
        return array_edges;
    }

    private int getEventFrequency(boolean min, int avg_vs_max_vs_min, String event) {
        if(getEventNumber(event) == null) {
            String start_event = event + plusStart;
            String complete_event = event + plusComplete;
            if(getEventNumber(start_event) != null && getEventNumber(complete_event) != null) {
                if(min) {
                    return Math.min(getEventFrequency(min, avg_vs_max_vs_min, start_event), getEventFrequency(min, avg_vs_max_vs_min, complete_event));
                }else {
                    return Math.max(getEventFrequency(min, avg_vs_max_vs_min, start_event), getEventFrequency(min, avg_vs_max_vs_min, complete_event));
                }
            }else if(getEventNumber(start_event) != null) {
                return getEventFrequency(min, avg_vs_max_vs_min, start_event);
            }else {
                return getEventFrequency(min, avg_vs_max_vs_min, complete_event);
            }
        }else {
            if(avg_vs_max_vs_min == TOTAL) return activity_frequency.get(getEventNumber(event));
            else if(avg_vs_max_vs_min == MAX) return activity_max_frequency.get(getEventNumber(event));
            else return activity_min_frequency.get(getEventNumber(event));
        }
    }

    private long getEventDuration(int avg_vs_max_vs_min, String event) {
        if(getEventNumber(event) == null) {
            String start_event = event + plusStart;
            String complete_event = event + plusComplete;
            Integer start_event_number = getEventNumber(start_event);
            Integer complete_event_number = getEventNumber(complete_event);
            if(start_event_number != null && complete_event_number != null) {
                if(avg_vs_max_vs_min == MEAN) return getArcMeanDuration(new Arc(start_event_number, complete_event_number));
                else if(avg_vs_max_vs_min == MAX) return getArcMaxDuration(new Arc(start_event_number, complete_event_number));
                else return getArcMinDuration(new Arc(start_event_number, complete_event_number));
            }else return 0;
        }else return 0;
    }

    private long getArcTotalDuration(Arc arc) {
        LongArrayList durations = arcs_duration_set.get(arc);
        return durations.sum();
    }

    private long getArcMeanDuration(Arc arc) {
        LongArrayList durations = arcs_duration_set.get(arc);
        return durations.sum() / durations.size();
    }

    private long getArcMedianDuration(Arc arc) {
        LongArrayList durations = arcs_duration_set.get(arc);
        durations = durations.sortThis();
        if(durations.size() % 2 == 0) {
            return (durations.get(durations.size() / 2) + durations.get(durations.size() / 2 - 1)) / 2;
        }else {
            return durations.get(durations.size() / 2);
        }
    }

    private long getArcMaxDuration(Arc arc) {
        LongArrayList durations = arcs_duration_set.get(arc);
        return durations.max();
    }

    private long getArcMinDuration(Arc arc) {
        LongArrayList durations = arcs_duration_set.get(arc);
        return durations.min();
    }

    private String getFrequencyColor(int avg_vs_max_vs_min, BPMNNode node, Set<BPMNNode> nodes) {
        int max = 0;
        for(BPMNNode n : nodes) {
            max = Math.max(max, getEventFrequency(true, avg_vs_max_vs_min, n.getLabel()));
        }
        double node_frequency = getEventFrequency(true, avg_vs_max_vs_min, node.getLabel());
        return "#" + Integer.toHexString(activity_frequency_gradient.generateColor(node_frequency / max).getRGB()).substring(2);
    }

    private String getDurationColor(int avg_vs_max_vs_min, BPMNNode node, Set<BPMNNode> nodes) {
        long max = 0;
        for(BPMNNode n : nodes) {
            max = Math.max(max, getEventDuration(avg_vs_max_vs_min, n.getLabel()));
        }
        if(max == 0) return "#FEF0D9";
        double node_duration = getEventDuration(avg_vs_max_vs_min, node.getLabel());
        return "#" + Integer.toHexString(activity_duration_gradient.generateColor(node_duration / max).getRGB()).substring(2);
    }

    private String getEventFullName(int event) {
        return simplified_names.inverse().get(event);
    }

    private Integer getEventNumber(String event) {
        return simplified_names.get(event);
    }
    
    private boolean isSingleTypeEvent(int event) {
        String name = getEventFullName(event);
        if(isStartEvent(name) && getEventNumber(getCompleteEvent(name)) != null) return false;
        if(isCompleteEvent(name) && getEventNumber(getStartEvent(name)) != null) return false;
        return true;
    }

    private boolean isSingleTypeEvent(IntArrayList trace, int event) {
        String name = getEventFullName(event);
        String collapsed_name = getCollapsedEvent(name);

        for(int i = 0; i < trace.size(); i++) {
            if(collapsed_name.equals(getCollapsedEvent(getEventFullName(trace.get(i)))) && !name.equals(getEventFullName(trace.get(i)))) {
                return false;
            }
        }

        return true;
    }

    private boolean isStartEvent(String name) {
        return name.toLowerCase().endsWith(plusStart);
    }

    private boolean isCompleteEvent(String name) {
        return name.toLowerCase().endsWith(plusComplete);
    }

    private String getStartEvent(String name) {
        return name.substring(0, name.length() - 8) + start;
    }

    private String getCompleteEvent(String name) {
        return name.substring(0, name.length() - 5) + complete;
    }

    private String getCollapsedEvent(String name) {
        if(isStartEvent(name)) return name.substring(0, name.length() - 6);
        if(isCompleteEvent(name)) return name.substring(0, name.length() - 9);
        return name;
    }

}
