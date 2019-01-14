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

package org.apromore.plugin.portal.logvisualizer;

// Java 2 Standard Edition
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

// Third party packages
import org.apromore.model.*;
import org.apromore.plugin.portal.PortalContext;
import org.apromore.plugin.portal.loganimation.ElementLayout;
import org.apromore.plugin.portal.loganimation.LayoutGenerator;
import org.apromore.plugin.portal.loganimation.LogAnimationPluginInterface;
import org.apromore.service.CanoniserService;
import org.apromore.service.EventLogService;
import org.apromore.service.ProcessService;
import org.apromore.service.helper.UserInterfaceHelper;
import org.apromore.service.logvisualizer.LogVisualizerService;
import org.apromore.service.logvisualizer.impl.LogVisualizerServiceImpl;
import org.deckfour.xes.model.XLog;
import org.json.JSONArray;
import org.processmining.contexts.uitopia.UIContext;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.bpmn.elements.Activity;
import org.processmining.models.graphbased.directed.bpmn.elements.Flow;
import org.processmining.plugins.bpmn.BpmnDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;
import org.zkoss.zul.Window;

import javax.swing.*;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

// Local packages

public class DiscoverProcessMapController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoverProcessMapController.class.getCanonicalName());

    private PortalContext portalContext;
    private LogVisualizerService logVisualizerService;
    private EventLogService eventLogService;

    private ProcessService processService;
    private CanoniserService canoniserService;
    private UserInterfaceHelper userInterfaceHelper;

    private LogAnimationPluginInterface logAnimationPlugin;

    private Window slidersWindow;

    private Textbox activitiesText;
    private Textbox arcsText;
    private Slider activities;
    private Slider arcs;

    private Combobutton frequency;
    private Menuitem absolute_frequency;
    private Menuitem max_frequency;
    private Menuitem min_frequency;

    private Combobutton duration;
    private Menuitem total_duration;
    private Menuitem median_duration;
    private Menuitem mean_duration;
    private Menuitem max_duration;
    private Menuitem min_duration;

    private Button animate;
    private Menuitem exportFitted;
    private Menuitem exportUnfitted;

    private int arcs_value = 0;
    private int activities_value = 100;

    private boolean frequency_VS_duration;
    private int total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.TOTAL;

    private boolean visualized = false;
    private XLog log;
    private LogSummaryType logSummary;

    public DiscoverProcessMapController(PortalContext context, EventLogService eventLogService, LogVisualizerService logVisualizerService, ProcessService processService, CanoniserService canoniserService, UserInterfaceHelper userInterfaceHelper, LogAnimationPluginInterface logAnimationPlugin, boolean frequency_VS_duration) {

        this.portalContext          = context;
        this.logVisualizerService   = logVisualizerService;
        this.processService         = processService;
        this.canoniserService       = canoniserService;
        this.userInterfaceHelper    = userInterfaceHelper;
        this.logAnimationPlugin     = logAnimationPlugin;
        this.frequency_VS_duration  = frequency_VS_duration;
        this.eventLogService        = eventLogService;

        Map<SummaryType, List<VersionSummaryType>> elements = context.getSelection().getSelectedProcessModelVersions();
        if (elements.size() != 1) {
            Messagebox.show("Please, select exactly one log.", "Wrong Log Selection", Messagebox.OK, Messagebox.INFORMATION);
            return;
        }
        SummaryType summary = elements.keySet().iterator().next();
        if (!(summary instanceof LogSummaryType)) {
            Messagebox.show("Please, select exactly one log.", "Wrong Log Selection", Messagebox.OK, Messagebox.INFORMATION);
            return;
        }
        logSummary = (LogSummaryType) summary;
        log = eventLogService.getXLog(logSummary.getId());

        try {
            this.slidersWindow = (Window) portalContext.getUI().createComponent(getClass().getClassLoader(), "zul/logvisualizer.zul", null, null);

            this.activities = (Slider) slidersWindow.getFellow("slider1");
            this.arcs = (Slider) slidersWindow.getFellow("slider2");
            this.activitiesText = (Textbox) slidersWindow.getFellow("textbox1");
            this.arcsText = (Textbox) slidersWindow.getFellow("textbox2");

            this.frequency = (Combobutton) slidersWindow.getFellow("frequency");
            this.absolute_frequency = (Menuitem) slidersWindow.getFellow("absolute_frequency");
            this.max_frequency = (Menuitem) slidersWindow.getFellow("max_frequency");
            this.min_frequency = (Menuitem) slidersWindow.getFellow("min_frequency");

            this.duration = (Combobutton) slidersWindow.getFellow("duration");
            this.total_duration = (Menuitem) slidersWindow.getFellow("total_duration");
            this.median_duration = (Menuitem) slidersWindow.getFellow("median_duration");
            this.mean_duration = (Menuitem) slidersWindow.getFellow("mean_duration");
            this.max_duration = (Menuitem) slidersWindow.getFellow("max_duration");
            this.min_duration = (Menuitem) slidersWindow.getFellow("min_duration");

            this.animate = (Button) slidersWindow.getFellow("animate");
            this.exportFitted = (Menuitem) slidersWindow.getFellow("exportFitted");
            this.exportUnfitted = (Menuitem) slidersWindow.getFellow("exportUnfitted");

            this.activities.addEventListener("onScroll", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    activitiesText.setText("" + activities.getCurpos());
                    setArcAndActivityRatios();
                }
            });

            this.arcs.addEventListener("onScroll", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    arcsText.setText("" + arcs.getCurpos());
                    setArcAndActivityRatios();
                }
            });

            this.activitiesText.addEventListener("onChange", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    int i = Integer.parseInt(activitiesText.getValue());
                    if(i < 0) i = 0;
                    else if(i > 100) i = 100;
                    activitiesText.setText("" + i);
                    activities.setCurpos(i);
                    setArcAndActivityRatios();
                }
            });
            this.activitiesText.addEventListener("onMouseOver", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    int i = Integer.parseInt(activitiesText.getValue());
                    if(i < 0) i = 0;
                    else if(i > 100) i = 100;
                    activitiesText.setText("" + i);
                    activities.setCurpos(i);
                    setArcAndActivityRatios();
                }
            });

            this.arcsText.addEventListener("onChange", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    int i = Integer.parseInt(arcsText.getValue());
                    if(i < 0) i = 0;
                    else if(i > 100) i = 100;
                    arcsText.setText("" + i);
                    arcs.setCurpos(i);
                    setArcAndActivityRatios();
                }
            });
            this.arcsText.addEventListener("onMouseOver", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    int i = Integer.parseInt(arcsText.getValue());
                    if(i < 0) i = 0;
                    else if(i > 100) i = 100;
                    arcsText.setText("" + i);
                    arcs.setCurpos(i);
                    setArcAndActivityRatios();
                }
            });

            this.frequency.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeFrequency();
                }
            });
            this.absolute_frequency.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeFrequency();
                }
            });
            this.max_frequency.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeFrequency();
                }
            });
            this.min_frequency.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeFrequency();
                }
            });

            this.duration.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeDuration();
                }
            });
            this.total_duration.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeDuration();
                }
            });
            this.median_duration.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeDuration();
                }
            });
            this.mean_duration.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeDuration();
                }
            });
            this.max_duration.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeDuration();
                }
            });
            this.min_duration.addEventListener("onClick", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    visualizeDuration();
                }
            });

            this.animate.addEventListener("onAnimate", new EventListener<Event>() {
                @Override
                public void onEvent(Event event) throws Exception {
                    activities_value = activities.getCurpos();
                    arcs_value = arcs.getCurpos();
                    BPMNDiagram diagram = logVisualizerService.generateBPMNFromLog(log, 1 - activities.getCurposInDouble() / 100, 1 - arcs.getCurposInDouble() / 100, frequency_VS_duration, total_VS_median_VS_mean_VS_max_VS_min);

                    Set<String> manually_removed_activities = new HashSet<>();
                    String layout = event.getData().toString();
                    Map<String, ElementLayout> layoutMap = LayoutGenerator.generateLayout(layout);
                    Set<Activity> setActivities = new HashSet<>(diagram.getActivities());
                    for(Activity activity : setActivities) {
                        if(activity.getLabel().contains("|>")) continue;
                        if(activity.getLabel().contains("[]")) continue;
                        if(!layoutMap.containsKey(activity.getLabel())) {
                            diagram.removeActivity(activity);
                            manually_removed_activities.add(activity.getLabel());
                        }
                    }
                    for(Activity a : diagram.getActivities()) {
                        for(Activity b : diagram.getActivities()) {
                            String n = a.getLabel() + " (~) " + b.getLabel();
                            if(layoutMap.containsKey(n)) {
                                Set<Flow> flows = new HashSet<>(diagram.getFlows());
                                boolean found = false;
                                for(Flow flow : flows) {
                                    if(flow.getSource().equals(a) && flow.getTarget().equals(b)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if(!found) diagram.addFlow(a, b, "");
                            }
                        }
                    }
                    diagram = logVisualizerService.insertBPMNGateways(diagram);

                    UIContext context = new UIContext();
                    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                    UIPluginContext uiPluginContext = context.getMainPluginContext();
                    BpmnDefinitions.BpmnDefinitionsBuilder definitionsBuilder = new BpmnDefinitions.BpmnDefinitionsBuilder(uiPluginContext, diagram);
                    BpmnDefinitions definitions = new BpmnDefinitions("definitions", definitionsBuilder);

                    StringBuilder sb = new StringBuilder();
                    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n " +
                            "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"\n " +
                            "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"\n " +
                            "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"\n " +
                            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n " +
                            "targetNamespace=\"http://www.omg.org/bpmn20\"\n " +
                            "xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\">");

                    sb.append(definitions.exportElements());
                    sb.append("</definitions>");
                    String model = sb.toString();

                    System.out.println(model.replaceAll("\n",""));
                    System.out.println(layout);

                    XLog filtered = logVisualizerService.generateFilteredLog(log, manually_removed_activities, 1 - activities.getCurposInDouble() / 100, 1 - arcs.getCurposInDouble() / 100);
                    logAnimationPlugin.execute(portalContext, model, layout, filtered, false);
                }
            });

            this.exportFitted.addEventListener("onExport", new EventListener<Event>() {
                @Override
                public void onEvent(Event event) throws Exception {
                    activities_value = activities.getCurpos();
                    arcs_value = arcs.getCurpos();

                    BPMNDiagram diagram = logVisualizerService.generateBPMNFromLog(log, 1 - activities.getCurposInDouble() / 100, 1 - arcs.getCurposInDouble() / 100, frequency_VS_duration, total_VS_median_VS_mean_VS_max_VS_min);
                    Set<String> manually_removed_activities = new HashSet<>();
                    String layout = event.getData().toString();
                    Map<String, ElementLayout> layoutMap = LayoutGenerator.generateLayout(layout);
                    Set<Activity> setActivities = new HashSet<>(diagram.getActivities());
                    Set<String> manually_removed_arcs = new HashSet<>();
                    for(Activity activity : setActivities) {
                        if(activity.getLabel().contains("|>")) continue;
                        if(activity.getLabel().contains("[]")) continue;
                        if(!layoutMap.containsKey(activity.getLabel())) {
                            diagram.removeActivity(activity);
                            manually_removed_activities.add(activity.getLabel());
                        }
                    }
                    for(Flow flow : diagram.getFlows()) {
                        if(!layoutMap.containsKey(flow.getSource().getLabel() + " (~) " + flow.getTarget().getLabel())) {
                            manually_removed_arcs.add(flow.getSource().getLabel() + " (~) " + flow.getTarget().getLabel());
                        }
                    }

                    XLog filtered_log = logVisualizerService.generateFilteredFittedLog(log, manually_removed_activities, manually_removed_arcs, 1 - activities.getCurposInDouble() / 100, 1 - arcs.getCurposInDouble() / 100);
                    saveLog(filtered_log);
                }
            });

            this.exportUnfitted.addEventListener("onExport", new EventListener<Event>() {
                @Override
                public void onEvent(Event event) throws Exception {
                    activities_value = activities.getCurpos();
                    arcs_value = arcs.getCurpos();

                    BPMNDiagram diagram = logVisualizerService.generateBPMNFromLog(log, 1 - activities.getCurposInDouble() / 100, 1 - arcs.getCurposInDouble() / 100, frequency_VS_duration, total_VS_median_VS_mean_VS_max_VS_min);
                    Set<String> manually_removed_activities = new HashSet<>();
                    String layout = event.getData().toString();
                    Map<String, ElementLayout> layoutMap = LayoutGenerator.generateLayout(layout);
                    for(Activity activity : diagram.getActivities()) {
                        if(!layoutMap.containsKey(activity.getLabel())) {
                            manually_removed_activities.add(activity.getLabel());
                        }
                    }

                    XLog filtered_log = logVisualizerService.generateFilteredLog(log, manually_removed_activities, 1 - activities.getCurposInDouble() / 100, 1 - arcs.getCurposInDouble() / 100);
                    saveLog(filtered_log);
                }
            });

            this.slidersWindow.addEventListener("onMouseOver", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    setArcAndActivityRatios();
                }
            });
            this.slidersWindow.addEventListener("onLoaded", new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    setArcAndActivityRatios();
                }
            });
            this.slidersWindow.doModal();

        } catch (IOException e) {
            context.getMessageHandler().displayError("Could not load component ", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void visualizeFrequency() throws InterruptedException {
        frequency_VS_duration = LogVisualizerServiceImpl.FREQUENCY;
        if (absolute_frequency.isChecked()) {
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.TOTAL;
        } else if (max_frequency.isChecked()) {
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.MAX;
        } else {
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.MIN;
        }
        visualized = false;
        setArcAndActivityRatios();
    }


    private void visualizeDuration() throws InterruptedException {
        frequency_VS_duration = LogVisualizerServiceImpl.DURATION;
        if(total_duration.isChecked()){
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.TOTAL;
        }else if(median_duration.isChecked()){
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.MEDIAN;
        }else if(mean_duration.isChecked()){
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.MEAN;
        }else if(max_duration.isChecked()) {
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.MAX;
        }else {
            total_VS_median_VS_mean_VS_max_VS_min = LogVisualizerServiceImpl.MIN;
        }
        visualized = false;
        setArcAndActivityRatios();
    }

    public void setArcAndActivityRatios() throws InterruptedException {
        try {
            if(activities_value != activities.getCurpos() || arcs_value != arcs.getCurpos() || !visualized) {
                visualized = true;
                activities_value = activities.getCurpos();
                arcs_value = arcs.getCurpos();

                JSONArray array = logVisualizerService.generateJSONArrayFromLog(log, 1 - activities.getCurposInDouble() / 100, 1 - arcs.getCurposInDouble() / 100, frequency_VS_duration, total_VS_median_VS_mean_VS_max_VS_min);

                String jsonString = array.toString();
                String javascript = "load('" + jsonString + "');";
                Clients.evalJavaScript("reset()");
                Clients.evalJavaScript(javascript);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveLog(XLog filtered_log) {
        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            eventLogService.exportToStream(outputStream, filtered_log);

            int folderId = portalContext.getCurrentFolder() == null ? 0 : portalContext.getCurrentFolder().getId();

            eventLogService.importLog(portalContext.getCurrentUser().getUsername(), folderId,
                    logSummary.getName() + "_filtered", new ByteArrayInputStream(outputStream.toByteArray()), "xes.gz",
                    logSummary.getDomain(), DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar()).toString(),
                    logSummary.isMakePublic());

            portalContext.refreshContent();
        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
