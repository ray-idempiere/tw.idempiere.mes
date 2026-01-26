package tw.idempiere.mes.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Column;
import org.adempiere.webui.component.Columns;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;
import org.adempiere.webui.theme.ThemeManager;
import org.adempiere.webui.util.ZKUpdateUtil;
import org.adempiere.webui.window.FDialog;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MColumn;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MQuery;
import org.compiere.model.MResource;
import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Image;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.North;
import org.zkoss.zul.Script;
import org.zkoss.zul.Separator;
import org.zkoss.zul.Space;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Vbox;

import tw.idempiere.mes.service.MESService;
import tw.idempiere.mes.util.MESBroadcastUtil;
import org.adempiere.base.event.EventManager;
import org.osgi.service.event.EventHandler;

/**
 * Production Schedule Form with Timeline
 */
public class WProductionSchedule extends ADForm implements IFormController, EventListener<Event> {

    private static final long serialVersionUID = 1L;
    private static CLogger log = CLogger.getCLogger(WProductionSchedule.class);

    // ==================== UI Constants ====================

    private static final String VERSION = "2.2.0"; // Current System Version

    // Window Dimensions
    private static final String WINDOW_WIDTH_FULL = "100%";
    private static final String WINDOW_HEIGHT_FULL = "100%";
    private static final String DIALOG_WIDTH_SCAN = "400px";
    private static final String DIALOG_HEIGHT_SCAN = "350px";

    // Font Sizes
    private static final String FONT_SIZE_XLARGE = "84px"; // Rate display
    private static final String FONT_SIZE_LARGE = "36px"; // Headers
    private static final String FONT_SIZE_MEDIUM = "32px"; // Labels
    private static final String FONT_SIZE_NORMAL = "24px"; // Regular text
    private static final String FONT_SIZE_SMALL = "20px"; // Buttons
    private static final String FONT_SIZE_INPUT = "18px"; // Input fields
    private static final String FONT_SIZE_LABEL = "16px"; // Small labels
    private static final String FONT_SIZE_INFO = "14px"; // Info text

    // Colors
    private static final String COLOR_SUCCESS = "#28a745";
    private static final String COLOR_SUCCESS_BG = "#e6ffed";
    private static final String COLOR_ERROR = "#e74c3c";
    private static final String COLOR_ERROR_BG = "#FFEEEE";
    private static final String COLOR_PRIMARY = "#007bff";
    private static final String COLOR_INFO = "#2980b9";
    private static final String COLOR_DARK = "#333";
    private static final String COLOR_MEDIUM = "#34495e";
    private static final String COLOR_LIGHT = "#7f8c8d";
    private static final String COLOR_BG_LIGHT = "#f0f0f0";
    private static final String COLOR_BG_NEUTRAL = "#f5f5f5";

    // Sizes
    private static final String SIZE_IMAGE = "250px";
    private static final String SIZE_BUTTON_HEIGHT = "50px";
    private static final String SIZE_BUTTON_HEIGHT_LARGE = "60px";
    private static final String SIZE_BUTTON_WIDTH = "200px";
    private static final String SIZE_BUTTON_WIDTH_FULL = "100%";
    private static final String SIZE_INPUT_HEIGHT = "40px";

    // Spacing
    private static final String SPACING_PADDING = "20px";
    private static final String SPACING_MARGIN = "10px";
    private static final String SPACING_GAP = "20px";

    // Event Queue Constants
    private static final String EVENT_QUEUE_NAME = "MesUpdateQueue";
    private static final String EVENT_NAME_UPDATE = "MES_UPDATE";

    // Database Table IDs
    private static final int TABLE_ID_PRODUCT = 208; // M_Product

    // Notification Durations (milliseconds)
    private static final int NOTIFY_DURATION_SHORT = 500;
    private static final int NOTIFY_DURATION_NORMAL = 1000;
    private static final int NOTIFY_DURATION_LONG = 2000;
    private static final int NOTIFY_DURATION_EXTRA = 3000;

    // Notification Positions
    private static final String NOTIFY_POS_CENTER = "middle_center";
    private static final String NOTIFY_POS_TOP_RIGHT = "top_right";

    // ==================== Fields ====================

    private Button btnRefresh;
    private Button btnToday;
    private Button btnGenerate;
    private Button btnSearch;
    private Textbox txtSearch;
    private Div timelineContainer;

    private Checkbox chkTVMode;
    private Timer autoRefreshTimer;

    // KPI Labels
    private Label lblTotal;
    private Label lblCompleted;
    private Label lblLate;

    private Listbox lstPeriod;

    private MESService service = new MESService();

    @Override
    public void onPageAttached(org.zkoss.zk.ui.Page newpage, org.zkoss.zk.ui.Page oldpage) {
        super.onPageAttached(newpage, oldpage);
        if (newpage != null) {
            newpage.getDesktop().enableServerPush(true);
        }
    }

    protected void initForm() {
        ZKUpdateUtil.setHeight(this, "100%");
        Borderlayout layout = new Borderlayout();
        // Inject CSS for flashing effect
        Html style = new Html("<style>" +
                "@keyframes flash-border { 0% { border-color: red; box-shadow: 0 0 10px red; } 50% { border-color: transparent; box-shadow: none; } 100% { border-color: red; box-shadow: 0 0 10px red; } }"
                +
                ".flash-highlight { border: 3px solid red !important; animation: flash-border 1s 10; }" +
                "</style>");
        this.appendChild(style);

        this.appendChild(layout);

        // Toolbar
        North north = new North();
        layout.appendChild(north);
        Hbox toolbar = new Hbox();
        north.appendChild(toolbar);
        toolbar.setStyle("padding: 5px; align-items: center;");

        // Project Title & Version
        Label lblTitle = new Label("Taiwan MES " + VERSION);
        lblTitle.setStyle("font-weight: bold; font-size: 18px; color: #2c3e50; margin-right: 15px; margin-left: 5px;");
        toolbar.appendChild(lblTitle);

        toolbar.appendChild(new Separator("vertical"));
        toolbar.appendChild(new Space());

        btnRefresh = new Button("Refresh");
        if (ThemeManager.isUseFontIconForImage())
            btnRefresh.setIconSclass("z-icon-Refresh");
        else
            btnRefresh.setImage(ThemeManager.getThemeResource("images/Refresh16.png"));
        btnRefresh.addEventListener(Events.ON_CLICK, this);
        toolbar.appendChild(btnRefresh);

        btnToday = new Button("Today");
        if (ThemeManager.isUseFontIconForImage())
            btnToday.setIconSclass("z-icon-Calendar");
        else
            btnToday.setImage(ThemeManager.getThemeResource("images/Calendar16.png"));
        btnToday.addEventListener(Events.ON_CLICK, this);
        toolbar.appendChild(btnToday);

        toolbar.appendChild(new Space());

        btnGenerate = new Button("Generate Orders");
        if (ThemeManager.isUseFontIconForImage())
            btnGenerate.setIconSclass("z-icon-Process");
        else
            btnGenerate.setImage(ThemeManager.getThemeResource("images/Process16.png"));
        btnGenerate.addEventListener(Events.ON_CLICK, this);
        toolbar.appendChild(btnGenerate);

        toolbar.appendChild(new Space());
        toolbar.appendChild(new Separator("vertical"));
        toolbar.appendChild(new Space());

        // Zoom Presets
        Button btnZoomWeek = new Button("Week (7d)");
        btnZoomWeek.setAttribute("zoom", "week");
        btnZoomWeek.addEventListener(Events.ON_CLICK, this);
        toolbar.appendChild(btnZoomWeek);

        Button btnZoomMonth = new Button("Month (30d)");
        btnZoomMonth.setAttribute("zoom", "month");
        btnZoomMonth.addEventListener(Events.ON_CLICK, this);
        toolbar.appendChild(btnZoomMonth);

        toolbar.appendChild(new Space());
        toolbar.appendChild(new Separator("vertical"));
        toolbar.appendChild(new Space());

        // Search Box
        Label lblSearch = new Label("Search:");
        lblSearch.setStyle("margin-left: 10px; align-self: center;");
        toolbar.appendChild(lblSearch);

        txtSearch = new Textbox();
        txtSearch.setPlaceholder("Order / Product");
        txtSearch.addEventListener(Events.ON_OK, this); // Enter key
        toolbar.appendChild(txtSearch);

        Button btnSearch = new Button("Go");
        btnSearch.addEventListener(Events.ON_CLICK, this);
        toolbar.appendChild(btnSearch);

        this.btnSearch = btnSearch; // Assign to field for event handling

        toolbar.appendChild(new Space());
        chkTVMode = new Checkbox("TV Mode (Auto-Refresh)");
        chkTVMode.addEventListener(Events.ON_CHECK, this);
        toolbar.appendChild(chkTVMode);

        // KPI Stats
        toolbar.appendChild(new Space());
        toolbar.appendChild(new Separator("vertical"));
        toolbar.appendChild(new Space());

        // Period Selector
        lstPeriod = new Listbox();
        lstPeriod.setMold("select");
        lstPeriod.appendItem("Day", "Day");
        lstPeriod.appendItem("Week", "Week");
        lstPeriod.appendItem("Month", "Month");
        lstPeriod.setSelectedIndex(0); // Default Today
        lstPeriod.addEventListener(Events.ON_SELECT, this);
        toolbar.appendChild(lstPeriod);

        toolbar.appendChild(new Space());

        lblTotal = new Label("Total: 0");
        lblTotal.setStyle("font-weight: bold; margin-right: 10px;");
        toolbar.appendChild(lblTotal);

        lblCompleted = new Label("Completed: 0");
        lblCompleted.setStyle("font-weight: bold; color: green; margin-right: 10px;");
        toolbar.appendChild(lblCompleted);

        lblLate = new Label("Late: 0");
        lblLate.setStyle("font-weight: bold; color: red;");
        toolbar.appendChild(lblLate);

        // Timer (disabled by default)
        autoRefreshTimer = new Timer();
        autoRefreshTimer.setDelay(60000); // 60s
        autoRefreshTimer.setRepeats(true);
        autoRefreshTimer.setRunning(false);
        autoRefreshTimer.addEventListener(Events.ON_TIMER, this);
        this.appendChild(autoRefreshTimer);

        // Center Timeline
        Center center = new Center();
        layout.appendChild(center);

        timelineContainer = new Div();
        timelineContainer.setId("timeline_container_" + System.currentTimeMillis());
        ZKUpdateUtil.setHeight(timelineContainer, "100%");
        ZKUpdateUtil.setWidth(timelineContainer, "100%");
        center.appendChild(timelineContainer);

        // Listen for Init event
        timelineContainer.addEventListener("onInitTimeline", this);
        timelineContainer.addEventListener("onOrderMove", this);
        timelineContainer.addEventListener("onItemContext", this);
        timelineContainer.addEventListener("onItemSelect", this);
        timelineContainer.addEventListener("onGroupClick", this);

        // Listen for Custom Context Menu Events
        this.addEventListener("onProcessSet", this);
        this.addEventListener("onNotice", this);
        this.addEventListener("onMaterialIssue", this);

        // Inject Dependencies
        injectDependencies();

        // Subscribe Timeline to real-time EventQueue updates
        subscribeTimelineToEvents();
    }

    private void injectDependencies() {
        // Direct injection to bypass path resolution issues with ~./ in plugins

        // CSS
        String cssContent = readResource("/web/styles/vis-timeline-graph2d.min.css");
        // Append Custom Styles
        cssContent += "\n.vis-item.status-completed { background-color: #28a745; border-color: #1e7e34; color: white; }"
                +
                "\n.vis-item.status-inprogress { background-color: #007bff; border-color: #0056b3; color: white; }" +
                "\n.vis-item.status-draft { background-color: #6c757d; border-color: #545b62; color: white; }" +
                "\n.vis-item.status-error { background-color: #dc3545; border-color: #bd2130; color: white; }" +
                "\n.mes-item-content { position: relative; width: 100%; height: 100%; overflow: hidden; }" +
                "\n.mes-item-text { position: relative; z-index: 2; padding: 2px; }" +
                "\n.mes-progress { position: absolute; bottom: 0; left: 0; height: 100%; background-color: rgba(255, 255, 255, 0.3); z-index: 1; }";

        if (cssContent != null && !cssContent.isEmpty()) {
            org.zkoss.zul.Style style = new org.zkoss.zul.Style();
            style.setContent(cssContent);
            this.appendChild(style);
        } else {
            log.severe("Could not read Vis.js CSS from classpath");
        }

        // JS Lib
        String jsContent = readResource("/web/js/vis-timeline-graph2d.min.js");
        if (jsContent != null && !jsContent.isEmpty()) {
            Script sc = new Script();
            sc.setContent(jsContent);
            this.appendChild(sc);
        } else {
            log.severe("Could not read Vis.js JS from classpath");
        }

        // JS Custom Logic - Embedded to ensure availability
        Script sc = new Script();
        sc.setContent(MES_TIMELINE_JS);
        this.appendChild(sc);

        // Initialize empty timeline delayed to wait for Vis load
        // Use recursive timeout to wait for Vis
        StringBuilder initJs = new StringBuilder();
        initJs.append("(function() {");
        initJs.append("  var checkVis = function(count) {");
        initJs.append(
                "    if (typeof vis !== 'undefined' && typeof window.renderOrUpdateMESTimeline !== 'undefined') {");
        initJs.append("      zk.Widget.$('$").append(timelineContainer.getId())
                .append("').fire('onInitTimeline', null, {toServer:true});");
        initJs.append("    } else if(count < 50) {"); // Wait max 50 * 100ms = 5s
        initJs.append("      setTimeout(function(){ checkVis(count + 1); }, 100);");
        initJs.append("    } else {");
        initJs.append("      console.error('Vis or MES Timeline JS not loaded');");
        initJs.append("    }");
        initJs.append("  };");
        initJs.append("  checkVis(0);");
        initJs.append("})();");
        Clients.evalJavaScript(initJs.toString());
    }

    private static final String MES_TIMELINE_JS = "window.renderOrUpdateMESTimeline = function(containerId, listenerUuid, groupsData, itemsData, options) { "
            + "    setTimeout(function() { "
            + "        var container = document.getElementById(containerId); "
            + "        if (!container) { "
            + "            console.error('renderMESTimeline: Container not found ' + containerId); "
            + "            return; "
            + "        } "
            + "        "
            + "        if (container.visTimeline) { "
            + "            console.log('Updating existing timeline...'); "
            + "            container.visTimeline.setGroups(new vis.DataSet(groupsData)); "
            + "            container.visTimeline.setItems(new vis.DataSet(itemsData)); "
            + "            container.visTimeline.setOptions(options); "
            + "            return; "
            + "        } "
            + "        "
            + "        console.log('Initializing new timeline...'); "
            + "        var groups = new vis.DataSet(groupsData); "
            + "        var items = new vis.DataSet(itemsData); "
            + "        "
            + "        options.onMove = function(item, callback) { "
            + "            var wgt = zk.Widget.$('#' + listenerUuid); "
            + "            if (wgt) { "
            + "                wgt.fire('onOrderMove', { "
            + "                    id: item.id, "
            + "                    start: item.start.toISOString(), "
            + "                    end: item.end.toISOString(), "
            + "                    group: item.group "
            + "                }, {toServer: true}); "
            + "                callback(item); "
            + "            } else { "
            + "                console.error('Widget not found: ' + listenerUuid); "
            + "                callback(null); "
            + "            } "
            + "        }; "
            + "        "
            + "        var timeline = new vis.Timeline(container, items, groups, options); "
            + "        container.visTimeline = timeline; "
            + "        window.currentVisTimeline = timeline; "
            + "        "
            + "        container.visTimeline.on('select', function (properties) { "
            + "            if (properties.items && properties.items.length > 0) { "
            + "                var wgt = zk.Widget.$('#' + listenerUuid); "
            + "                if (wgt) { "
            + "                    wgt.fire('onItemSelect', { "
            + "                        id: properties.items[0] "
            + "                    }, {toServer: true}); "
            + "                } "
            + "            } "
            + "        }); "
            + "        "
            + "        container.visTimeline.on('click', function (properties) { "
            + "            if (properties.what === 'group-label' && properties.group != null) { "
            + "                var wgt = zk.Widget.$('#' + listenerUuid); "
            + "                if (wgt) { "
            + "                    wgt.fire('onGroupClick', { "
            + "                        id: properties.group "
            + "                    }, {toServer: true}); "
            + "                } "
            + "            } "
            + "        }); "
            + "        "
            + "        container.addEventListener('contextmenu', function(e) { "
            + "            console.log('DOM contextmenu detected'); "
            + "            var props = timeline.getEventProperties(e); "
            + "            if (props && props.item) { "
            + "                e.preventDefault(); "
            + "                var wgt = zk.Widget.$(listenerUuid); "
            + "                if (wgt) { "
            + "                    wgt.fire('onItemContext', { "
            + "                        id: props.item, "
            + "                        x: e.pageX, "
            + "                        y: e.pageY "
            + "                    }, {toServer: true}); "
            + "                } "
            + "            } "
            + "        }); "
            + "        "
            + "        console.log('MES Timeline rendered on ' + containerId); "
            + "    }, 200); "
            + "};";

    private String readResource(String path) {
        StringBuilder sb = new StringBuilder();
        try (java.io.InputStream is = getClass().getResourceAsStream(path);
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            if (is == null) {
                return null;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            log.severe("Error reading resource: " + path + " - " + e.getMessage());
            return null;
        }
        return sb.toString();
    }

    @Override
    public void onEvent(Event event) throws Exception {
        if (event.getTarget() == btnRefresh) {
            refreshTimeline();
        } else if (event.getTarget() == btnToday) {
            handleZoom("today");
        } else if (event.getTarget().getAttribute("zoom") != null) {
            handleZoom((String) event.getTarget().getAttribute("zoom"));
        } else if (event.getTarget() == btnGenerate) {
            showGenerateDialog();
        } else if (event.getTarget() == btnSearch
                || (event.getTarget() == txtSearch && Events.ON_OK.equals(event.getName()))) {
            refreshTimeline();
        } else if (event.getTarget() == chkTVMode) {
            boolean tvMode = chkTVMode.isChecked();
            autoRefreshTimer.setRunning(tvMode);
            if (tvMode) {
                // Hide non-essential controls if desired, or just toast
                Clients.showNotification("TV Mode Active: Auto-refresh every 60s", "info", null, "top_center", 2000);
            }
            refreshTimeline();
        } else if (event.getTarget() == autoRefreshTimer) {
            refreshTimeline();
        } else if (event.getTarget() == lstPeriod) {
            refreshTimeline();
        } else if (event.getName().equals("onInitTimeline")) {
            refreshTimeline();
        } else if (event.getName().equals("onOrderMove")) {
            org.zkoss.json.JSONObject data = (org.zkoss.json.JSONObject) event.getData();
            int id = Integer.parseInt(data.get("id").toString());
            String startStr = data.get("start").toString();
            String endStr = data.get("end").toString();
            int resourceId = Integer.parseInt(data.get("group").toString());
            showRescheduleConfirmDialog(id, resourceId, startStr, endStr);
        } else if (event.getName().equals("onItemSelect")) {
            org.zkoss.json.JSONObject data = (org.zkoss.json.JSONObject) event.getData();
            int id = Integer.parseInt(data.get("id").toString());
            handleItemSelect(id);
        } else if (event.getName().equals("onGroupClick")) {
            org.zkoss.json.JSONObject data = (org.zkoss.json.JSONObject) event.getData();
            int id = Integer.parseInt(data.get("id").toString());
            showResourceDialog(id);
        } else if (event.getName().equals("onItemContext")) {
            org.zkoss.json.JSONObject data = (org.zkoss.json.JSONObject) event.getData();
            int id = Integer.parseInt(data.get("id").toString());
            int x = Integer.parseInt(data.get("x").toString());
            int y = Integer.parseInt(data.get("y").toString());
            showContextMenu(id, x, y);
        } else if (event.getName().equals("onProcessSet")) {
            Object[] args = (Object[]) event.getData();
            int orderId = (Integer) args[0];
            String stage = (String) args[1];
            updateOrderStage(orderId, stage);
        } else if (event.getName().equals("onMaterialIssue")) {
            int orderId = (Integer) event.getData();
            showMaterialIssueDialog(orderId);
        } else if (event.getName().equals("onNotice")) {
            int orderId = (Integer) ((org.zkoss.json.JSONObject) event.getData()).get("id");
            showNoticeDialog(orderId);
        }
    }

    private void handleZoom(String type) {
        String script = "";
        if ("today".equals(type)) {
            // Today 00:00 to 24:00
            script = "var t = new Date(); t.setHours(0,0,0,0); var e = new Date(); e.setHours(23,59,59,999); window.currentVisTimeline.setWindow(t, e);";
        } else if ("week".equals(type)) {
            // Today to +7d
            script = "var t = new Date(); t.setHours(0,0,0,0); var e = new Date(); e.setDate(t.getDate() + 7); window.currentVisTimeline.setWindow(t, e);";
        } else if ("month".equals(type)) {
            // Today to +30d
            script = "var t = new Date(); t.setHours(0,0,0,0); var e = new Date(); e.setDate(t.getDate() + 30); window.currentVisTimeline.setWindow(t, e);";
        }

        if (!script.isEmpty()) {
            Clients.evalJavaScript("if(window.currentVisTimeline) { " + script + " }");
        }
    }

    private void handleItemSelect(int orderId) {
        // User requested removal of "Packing" click logic
    }

    private void updateOrderStage(int orderId, String stage) {
        // Update Description to include Stage Info
        // Clean existing stage info if any "Stage: ..."
        String currentDesc = DB.getSQLValueString(null, "SELECT Description FROM M_Production WHERE M_Production_ID=?",
                orderId);
        if (currentDesc == null)
            currentDesc = "";

        // Simple append or replace strategy
        // Ideally we use a dedicated column, but for now Description "Stage: X"
        String newDesc = currentDesc;
        if (newDesc.contains("Stage:")) {
            newDesc = newDesc.replaceAll("Stage: [^\\n]*", "Stage: " + stage);
        } else {
            if (!newDesc.isEmpty())
                newDesc += "\n";
            newDesc += "Stage: " + stage;
        }

        DB.executeUpdate("UPDATE M_Production SET Description=? WHERE M_Production_ID=?",
                new Object[] { newDesc, orderId },
                false, null);

        // Publish EventQueue event to notify KPI Dialogs
        try {
            int productId = DB.getSQLValue(null, "SELECT M_Product_ID FROM M_Production WHERE M_Production_ID=?",
                    orderId);

            System.out.println("=== DEBUG: [Context Menu] Publishing stage change event for Order ID: " + orderId
                    + ", Stage: " + stage);

            MESBroadcastUtil.publishMESUpdate(orderId, productId, 0);

            System.out.println("=== DEBUG: [Context Menu] Event published successfully");
        } catch (Exception ex) {
            System.err.println("=== DEBUG: [Context Menu] Failed to publish event: " + ex.getMessage());
        }

        refreshTimeline();
    }

    private void updateOrderNotice(int orderId, String notice) {
        String currentDesc = DB.getSQLValueString(null, "SELECT Description FROM M_Production WHERE M_Production_ID=?",
                orderId);
        if (currentDesc == null)
            currentDesc = "";

        String newDesc = currentDesc;
        // Clean input notice to single line
        String cleanNotice = notice.replace("\n", " ").trim();

        if (newDesc.contains("Notice: ")) {
            newDesc = newDesc.replaceAll("Notice: .*", "Notice: " + cleanNotice);
        } else {
            if (!newDesc.isEmpty() && !newDesc.endsWith("\n"))
                newDesc += "\n";
            newDesc += "Notice: " + cleanNotice;
        }

        DB.executeUpdate("UPDATE M_Production SET Description=? WHERE M_Production_ID=?",
                new Object[] { newDesc, orderId },
                false, null);

        // Publish EventQueue event to notify KPI Dialogs
        try {
            int productId = DB.getSQLValue(null, "SELECT M_Product_ID FROM M_Production WHERE M_Production_ID=?",
                    orderId);

            System.out.println("=== DEBUG: [Context Menu] Publishing notice change event for Order ID: " + orderId);

            MESBroadcastUtil.publishMESUpdate(orderId, productId, 0);
        } catch (Exception ex) {
            log.warning("Failed to publish notice event: " + ex.getMessage());
        }

        refreshTimeline();
    }

    private void showNoticeDialog(final int orderId) {
        String currentDesc = DB.getSQLValueString(null, "SELECT Description FROM M_Production WHERE M_Production_ID=?",
                orderId);
        // Extract existing notice
        String notice = "";
        if (currentDesc != null && currentDesc.contains("Notice: ")) {
            int start = currentDesc.indexOf("Notice: ") + 8;
            int end = currentDesc.indexOf("\n", start);
            if (end == -1)
                end = currentDesc.length();
            notice = currentDesc.substring(start, end);
        }

        final Window win = new Window();
        win.setTitle("Update Notice (Order ID: " + orderId + ")");
        win.setPosition("center");
        win.setMode("highlighted");
        win.setWidth("400px");
        win.setHeight("250px");
        win.setBorder("normal");
        win.setClosable(true);
        win.setSizable(true);

        Vbox vbox = new Vbox();
        vbox.setWidth("100%");
        vbox.setHeight("100%");
        vbox.setPack("center");
        vbox.setAlign("center");
        win.appendChild(vbox);

        Label lbl = new Label("Enter Notice:");
        vbox.appendChild(lbl);

        final Textbox txt = new Textbox(notice);
        txt.setRows(3);
        txt.setWidth("90%");
        txt.setStyle("font-size: 16px; padding: 5px;");
        vbox.appendChild(txt);

        Button btnSave = new Button("Save");
        btnSave.setSclass("btn-primary"); // Assuming bootstrap theme or similar
        btnSave.setStyle("margin-top: 10px; font-weight: bold; padding: 5px 20px;");
        btnSave.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event e) {
                updateOrderNotice(orderId, txt.getValue());
                win.detach();
            }
        });
        vbox.appendChild(btnSave);

        this.appendChild(win);
    }

    /**
     * BOM Component data structure
     */
    private static class BOMComponent {
        int productId;
        String productName;
        String productValue;
        BigDecimal bomQty;
        BigDecimal requiredQty;
        String uom;
    }

    /**
     * Shows Material Issue - creates draft M_Movement and opens Movement Window
     */
    private void showMaterialIssueDialog(final int orderId) {
        try {
            // Get Order info
            String sql = "SELECT DocumentNo, M_Product_ID, ProductionQty FROM M_Production WHERE M_Production_ID=?";
            PreparedStatement pstmt = null;
            ResultSet rs = null;

            String documentNo;
            int productId;
            BigDecimal qtyOrdered;

            try {
                pstmt = DB.prepareStatement(sql, null);
                pstmt.setInt(1, orderId);
                rs = pstmt.executeQuery();
                if (rs.next()) {
                    documentNo = rs.getString("DocumentNo");
                    productId = rs.getInt("M_Product_ID");
                    qtyOrdered = rs.getBigDecimal("ProductionQty");
                } else {
                    Clients.showNotification("Order not found", "error", null, NOTIFY_POS_CENTER,
                            NOTIFY_DURATION_NORMAL);
                    return;
                }
            } finally {
                DB.close(rs, pstmt);
            }

            // Check if Movement already exists for this order using M_Production_ID FK
            String movementSql = "SELECT M_Movement_ID, DocStatus FROM M_Movement " +
                    "WHERE M_Production_ID=? ORDER BY Created DESC";

            int existingMovementId = -1;
            String movementDocStatus = null;

            try {
                pstmt = DB.prepareStatement(movementSql, null);
                pstmt.setInt(1, orderId);
                rs = pstmt.executeQuery();
                if (rs.next()) {
                    existingMovementId = rs.getInt("M_Movement_ID");
                    movementDocStatus = rs.getString("DocStatus");
                }
            } finally {
                DB.close(rs, pstmt);
            }

            int movementId;

            if (existingMovementId > 0) {
                // Open existing Movement
                movementId = existingMovementId;
                log.info("Opening existing Movement ID: " + movementId + " (Status: " + movementDocStatus
                        + ") for Order: " + documentNo);

                // If Movement is completed, update order stage to Cutting
                if ("CO".equals(movementDocStatus)) {
                    String currentStage = DB.getSQLValueString(null,
                            "SELECT Description FROM M_Production WHERE M_Production_ID=?", orderId);

                    if (currentStage == null || !currentStage.contains("Stage: Cutting")) {
                        updateOrderStage(orderId, "Cutting");
                        log.info("Updated order stage to Cutting (Movement already completed)");
                    }
                }
            } else {
                // Create new draft Movement
                movementId = createDraftMaterialMovement(orderId, documentNo, productId, qtyOrdered);

                if (movementId <= 0) {
                    Clients.showNotification("Failed to create Material Movement", "error", null, NOTIFY_POS_CENTER,
                            NOTIFY_DURATION_NORMAL);
                    return;
                }

                // Update Order stage to "Material Issue"
                updateOrderStage(orderId, "Material Issue");

                log.info("Created draft Movement ID: " + movementId + " for Order: " + documentNo);
            }

            // Open Movement Window
            openMovementWindow(movementId);

            Clients.showNotification("Material Issue Movement opened", "info", null, NOTIFY_POS_TOP_RIGHT,
                    NOTIFY_DURATION_NORMAL);

        } catch (Exception e) {
            log.severe("Error in showMaterialIssueDialog: " + e.getMessage());
            e.printStackTrace();
            Clients.showNotification("Error: " + e.getMessage(), "error", null, NOTIFY_POS_CENTER,
                    NOTIFY_DURATION_LONG);
        }
    }

    /**
     * Creates a draft M_Movement document with BOM components
     */
    private int createDraftMaterialMovement(int orderId, String orderDocNo, int productId, BigDecimal qtyOrdered) {
        String trxName = Trx.createTrxName("MatIssueDraft");
        Trx trx = null;

        try {
            trx = Trx.get(trxName, true);

            log.info("Creating draft Movement for Order: " + orderDocNo + ", Product: " + productId);

            // Check if draft Movement already exists for this Production
            log.info("Checking for existing draft Movement for Production ID: " + orderId);

            int existingMovementId = DB.getSQLValue(null,
                    "SELECT M_Movement_ID FROM M_Movement " +
                            "WHERE M_Production_ID=? AND DocStatus='DR' AND IsActive='Y' " +
                            "ORDER BY Created DESC",
                    orderId);

            log.info("Existing Movement ID found: " + existingMovementId);

            if (existingMovementId > 0) {
                log.warning("Draft Movement already exists for this Production: " + existingMovementId);

                // Get Movement DocumentNo for user message
                String existingDocNo = DB.getSQLValueString(null,
                        "SELECT DocumentNo FROM M_Movement WHERE M_Movement_ID=?", existingMovementId);

                Clients.showNotification(
                        "Draft Movement already exists: " + existingDocNo +
                                ". Please complete or delete it first.",
                        "warning", null, NOTIFY_POS_CENTER, NOTIFY_DURATION_LONG);
                trx.close();
                return existingMovementId; // Return existing Movement ID
            }

            log.info("No existing draft Movement found, proceeding to create new one");

            // Get Resource name from M_Production
            String resourceName = DB.getSQLValueString(null,
                    "SELECT r.Name FROM S_Resource r " +
                            "JOIN M_Production o ON r.S_Resource_ID = o.S_Resource_ID " +
                            "WHERE o.M_Production_ID = ?",
                    orderId);

            if (resourceName != null) {
                log.info("Order assigned to Resource: " + resourceName);
            }

            // Get warehouse configuration
            int warehouseId = Env.getContextAsInt(Env.getCtx(), "#M_Warehouse_ID");
            if (warehouseId <= 0) {
                warehouseId = DB.getSQLValue(null, "SELECT MIN(M_Warehouse_ID) FROM M_Warehouse WHERE IsActive='Y'");
            }

            log.info("Using Warehouse ID: " + warehouseId);

            if (warehouseId <= 0) {
                log.severe("No active warehouse found!");
                trx.rollback();
                trx.close();
                Clients.showNotification("No active warehouse found", "error", null, NOTIFY_POS_CENTER,
                        NOTIFY_DURATION_LONG);
                return -1;
            }

            // Get default locators
            int fromLocatorId = DB.getSQLValue(null,
                    "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND IsDefault='Y' ORDER BY M_Locator_ID",
                    warehouseId);

            int toLocatorId = -1;

            // Priority 1: Try to match Resource name to Locator name
            if (resourceName != null && !resourceName.isEmpty()) {
                toLocatorId = DB.getSQLValue(null,
                        "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND Value=? AND IsActive='Y'",
                        warehouseId, resourceName);

                if (toLocatorId > 0) {
                    log.info("Found matching locator for resource: " + resourceName + " -> Locator ID: " + toLocatorId);
                }
            }

            // Priority 2: Try generic production locator
            if (toLocatorId <= 0) {
                toLocatorId = DB.getSQLValue(null,
                        "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND (Value LIKE '%PROD%' OR Value LIKE '%生產%') ORDER BY M_Locator_ID",
                        warehouseId);
            }

            // If production locator not found or same as from, get any different locator
            if (toLocatorId <= 0 || toLocatorId == fromLocatorId) {
                toLocatorId = DB.getSQLValue(null,
                        "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND M_Locator_ID != ? ORDER BY M_Locator_ID",
                        warehouseId, fromLocatorId);
            }

            log.info("From Locator: " + fromLocatorId + ", To Locator: " + toLocatorId);

            if (fromLocatorId <= 0 || toLocatorId <= 0 || fromLocatorId == toLocatorId) {
                log.severe("Invalid locators! From: " + fromLocatorId + ", To: " + toLocatorId);
                trx.rollback();
                trx.close();
                Clients.showNotification("Invalid warehouse locators - need at least 2 different locators in warehouse",
                        "error", null, NOTIFY_POS_CENTER,
                        NOTIFY_DURATION_LONG);
                return -1;
            }

            // Create Movement Header
            org.compiere.model.MMovement movement = new org.compiere.model.MMovement(Env.getCtx(), 0, trxName);
            movement.setMovementDate(new Timestamp(System.currentTimeMillis()));
            movement.setDescription("Material Issue for Order: " + orderDocNo);

            // Set M_Production_ID for proper FK relationship - MUST be set before save
            movement.set_ValueOfColumn("M_Production_ID", orderId);

            log.info("Setting M_Production_ID to: " + orderId + " on Movement");

            if (!movement.save()) {
                log.severe("Failed to save M_Movement header: " + movement.getProcessMsg());
                trx.rollback();
                trx.close();
                Clients.showNotification("Failed to save Movement: " + movement.getProcessMsg(), "error", null,
                        NOTIFY_POS_CENTER, NOTIFY_DURATION_LONG);
                return -1;
            }

            // Verify M_Production_ID was saved
            int savedProductionId = movement.get_ValueAsInt("M_Production_ID");
            log.info("Movement created: " + movement.getM_Movement_ID() +
                    ", M_Production_ID saved as: " + savedProductionId);

            if (savedProductionId != orderId) {
                log.severe("M_Production_ID not saved correctly! Expected: " + orderId + ", Got: " + savedProductionId);
            }

            // Get BOM Components
            String bomSql = "SELECT bom.M_ProductBOM_ID, bom.BOMQty " +
                    "FROM M_Product_BOM bom " +
                    "WHERE bom.M_Product_ID = ? AND bom.IsActive='Y'";

            PreparedStatement pstmt = null;
            ResultSet rs = null;
            int lineCount = 0;

            try {
                pstmt = DB.prepareStatement(bomSql, trxName);
                pstmt.setInt(1, productId);
                rs = pstmt.executeQuery();

                while (rs.next()) {
                    int componentProductId = rs.getInt("M_ProductBOM_ID");
                    BigDecimal bomQty = rs.getBigDecimal("BOMQty");
                    BigDecimal requiredQty = bomQty.multiply(qtyOrdered);

                    log.info("Adding BOM line: Product=" + componentProductId + ", Qty=" + requiredQty);

                    // Create Movement Line
                    org.compiere.model.MMovementLine line = new org.compiere.model.MMovementLine(movement);
                    line.setM_Product_ID(componentProductId);
                    line.setM_Locator_ID(fromLocatorId);
                    line.setM_LocatorTo_ID(toLocatorId);
                    line.setMovementQty(requiredQty);

                    if (!line.save()) {
                        log.severe("Failed to save M_MovementLine for product: " + componentProductId);
                        trx.rollback();
                        trx.close();
                        Clients.showNotification("Failed to save movement line for product: " + componentProductId,
                                "error", null,
                                NOTIFY_POS_CENTER, NOTIFY_DURATION_LONG);
                        return -1;
                    }
                    lineCount++;
                }
            } finally {
                DB.close(rs, pstmt);
            }

            log.info("Created " + lineCount + " movement lines");

            if (lineCount == 0) {
                log.warning("No BOM components found for product: " + productId);
                Clients.showNotification("Warning: No BOM components found", "warning", null, NOTIFY_POS_CENTER,
                        NOTIFY_DURATION_NORMAL);
            }

            // Save as DRAFT (do NOT complete)
            movement.saveEx();
            trx.commit();
            trx.close();

            log.info("Draft Movement created successfully: " + movement.getM_Movement_ID());
            return movement.getM_Movement_ID();

        } catch (Exception e) {
            log.severe("Error creating draft material movement: " + e.getMessage());
            e.printStackTrace();
            if (trx != null) {
                trx.rollback();
                trx.close();
            }
            Clients.showNotification("Error: " + e.getMessage(), "error", null, NOTIFY_POS_CENTER,
                    NOTIFY_DURATION_LONG);
            return -1;
        }
    }

    /**
     * Opens the Movement Window for editing
     */
    private void openMovementWindow(int movementId) {
        try {
            // Find Movement Window
            int AD_Window_ID = DB.getSQLValue(null,
                    "SELECT AD_Window_ID FROM AD_Window WHERE Name='Material Movement' AND IsActive='Y'");

            if (AD_Window_ID <= 0) {
                // Fallback: Try to find ANY window for M_Movement table
                int AD_Table_ID = MTable.getTable_ID("M_Movement");
                AD_Window_ID = DB.getSQLValue(null,
                        "SELECT AD_Window_ID FROM AD_Tab WHERE AD_Table_ID=? AND IsActive='Y' ORDER BY IsSortTab",
                        AD_Table_ID);
            }

            if (AD_Window_ID > 0) {
                MQuery query = new MQuery("M_Movement");
                query.addRestriction("M_Movement_ID", MQuery.EQUAL, movementId);
                AEnv.zoom(AD_Window_ID, query);
            } else {
                Clients.showNotification("Movement Window not found", "error", null, NOTIFY_POS_CENTER,
                        NOTIFY_DURATION_NORMAL);
            }
        } catch (Exception e) {
            log.severe("Error opening Movement Window: " + e.getMessage());
            e.printStackTrace();
            Clients.showNotification("Error opening window: " + e.getMessage(), "error", null, NOTIFY_POS_CENTER,
                    NOTIFY_DURATION_NORMAL);
        }
    }

    /**
     * Shows Complete Order confirmation dialog
     */
    private void showCompleteOrderDialog(final int orderId) {
        // Get Order info
        String sql = "SELECT DocumentNo, M_Product_ID, ProductionQty, QtyDelivered FROM M_Production WHERE M_Production_ID=?";
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        final String documentNo;
        final BigDecimal qtyOrdered;
        final BigDecimal qtyDelivered;

        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, orderId);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                documentNo = rs.getString("DocumentNo");
                int productId = rs.getInt("M_Product_ID");
                qtyOrdered = rs.getBigDecimal("ProductionQty");
                qtyDelivered = rs.getBigDecimal("QtyDelivered") != null ? rs.getBigDecimal("QtyDelivered")
                        : BigDecimal.ZERO;
            } else {
                Clients.showNotification("Order not found", "error", null, NOTIFY_POS_CENTER, NOTIFY_DURATION_NORMAL);
                return;
            }
        } catch (SQLException e) {
            log.severe("Failed to load order: " + e.getMessage());
            Clients.showNotification("Database error", "error", null, NOTIFY_POS_CENTER, NOTIFY_DURATION_NORMAL);
            return;
        } finally {
            DB.close(rs, pstmt);
        }

        // Create Dialog
        final Window win = new Window();
        win.setTitle("Complete Order Confirmation");
        win.setPosition("center");
        win.setMode("highlighted");
        win.setWidth("450px");
        win.setHeight("300px");
        win.setBorder("normal");
        win.setClosable(true);

        Vbox vbox = new Vbox();
        vbox.setWidth("100%");
        vbox.setHeight("100%");
        vbox.setStyle("padding: 20px;");
        vbox.setAlign("center");
        win.appendChild(vbox);

        // Info
        Label lblOrder = new Label("Order: " + documentNo);
        lblOrder.setStyle("font-size: 18px; font-weight: bold; margin-bottom: 10px;");
        vbox.appendChild(lblOrder);

        Label lblQty = new Label("Target: " + qtyOrdered + " | Delivered: " + qtyDelivered);
        lblQty.setStyle("font-size: 16px; margin-bottom: 20px;");
        vbox.appendChild(lblQty);

        Label lblConfirm = new Label("Confirm order completion?");
        lblConfirm.setStyle("font-size: 14px; color: #666; margin-bottom: 20px;");
        vbox.appendChild(lblConfirm);

        // Notes (optional)
        Label lblNotes = new Label("Notes (Optional):");
        lblNotes.setStyle("align-self: flex-start; margin-bottom: 5px;");
        vbox.appendChild(lblNotes);

        final Textbox txtNotes = new Textbox();
        txtNotes.setRows(3);
        txtNotes.setWidth("95%");
        txtNotes.setStyle("font-size: 14px;");
        vbox.appendChild(txtNotes);

        vbox.appendChild(new Separator());

        // Buttons
        Hbox btnBox = new Hbox();
        btnBox.setStyle("margin-top: 15px;");
        btnBox.setAlign("center");
        btnBox.setPack("center");
        vbox.appendChild(btnBox);

        Button btnConfirm = new Button("✓ Confirm Complete");
        btnConfirm
                .setStyle("font-size: 16px; font-weight: bold; padding: 10px 20px; background: #28a745; color: white;");
        btnConfirm.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event e) {
                // Update DocStatus to Completed
                String notes = txtNotes.getValue();
                if (completeOrder(orderId, notes)) {
                    Clients.showNotification("✓ Order " + documentNo + " completed successfully!", "info", null,
                            NOTIFY_POS_TOP_RIGHT, NOTIFY_DURATION_EXTRA);
                    win.detach();
                } else {
                    Clients.showNotification("Failed to complete order", "error", null, NOTIFY_POS_CENTER,
                            NOTIFY_DURATION_NORMAL);
                }
            }
        });
        btnBox.appendChild(btnConfirm);

        btnBox.appendChild(new Space());

        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("font-size: 16px; padding: 10px 20px;");
        btnCancel.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event e) {
                win.detach();
            }
        });
        btnBox.appendChild(btnCancel);

        this.appendChild(win);
    }

    /**
     * Completes a production order
     */
    private boolean completeOrder(int orderId, String notes) {
        String trxName = Trx.createTrxName("CompleteOrder");
        Trx trx = null;

        try {
            trx = Trx.get(trxName, true);

            // Load M_Production using MProduction class (implements DocAction)
            org.compiere.model.MProduction production = new org.compiere.model.MProduction(Env.getCtx(), orderId,
                    trxName);

            if (production.get_ID() <= 0) {
                log.severe("M_Production not found: " + orderId);
                trx.rollback();
                trx.close();
                return false;
            }

            // Add notes if provided
            if (notes != null && !notes.trim().isEmpty()) {
                String currentDesc = production.getDescription();
                String newDesc = (currentDesc != null ? currentDesc + "\nCompletion Notes: " : "") + notes.trim();
                production.setDescription(newDesc);
            }

            // Check current status
            String currentStatus = production.getDocStatus();
            String documentNo = production.getDocumentNo();
            log.info("Current DocStatus: " + currentStatus + " for Production: " + documentNo);

            // Set DocAction to Complete
            production.setDocAction(org.compiere.process.DocAction.ACTION_Complete);

            // Process the document (this will trigger all validations and posting)
            if (!production.processIt(org.compiere.process.DocAction.ACTION_Complete)) {
                String errorMsg = production.getProcessMsg();
                log.severe("Failed to complete M_Production: " + errorMsg);
                trx.rollback();
                trx.close();
                Clients.showNotification("Failed to complete production: " + errorMsg, "error", null,
                        NOTIFY_POS_CENTER, NOTIFY_DURATION_LONG);
                return false;
            }

            // Save the production (saveEx throws exception on error)
            production.saveEx();

            // Commit transaction
            trx.commit();
            trx.close();

            log.info("Successfully completed M_Production: " + documentNo +
                    " (DocStatus: " + production.getDocStatus() + ")");

            // Update stage to Completed
            updateOrderStage(orderId, "Completed");

            // Publish EventQueue event
            try {
                int productId = production.getM_Product_ID();

                MESBroadcastUtil.publishMESUpdate(orderId, productId, 0);
            } catch (Exception ex) {
                log.warning("Failed to publish completion event: " + ex.getMessage());
            }

            refreshTimeline();
            return true;

        } catch (Exception e) {
            log.severe("Error completing production: " + e.getMessage());
            e.printStackTrace();
            if (trx != null) {
                trx.rollback();
                trx.close();
            }
            Clients.showNotification("Error: " + e.getMessage(), "error", null,
                    NOTIFY_POS_CENTER, NOTIFY_DURATION_LONG);
            return false;
        }
    }

    private String lastDivId = null; // Store ID to reuse

    private void refreshTimeline() {
        // Only clear if we don't have a container or if it was invalidated externally
        // But ZK doesn't guarantee DOM persistence if we don't manage it.
        // Strategy: Always try to find the DIV by stored ID using JS logic.

        String divId;
        if (lastDivId == null || timelineContainer.getChildren().isEmpty()) {
            timelineContainer.getChildren().clear();
            divId = "timeline_viz_" + System.currentTimeMillis();
            Html html = new Html("<div id='" + divId + "' style='height:100%; width:100%;'></div>");
            html.setHflex("1");
            html.setVflex("1");
            timelineContainer.appendChild(html);
            lastDivId = divId;
        } else {
            divId = lastDivId;
        }

        // Load Groups (Resources) and Items (PP_Orders)
        String groupJson = service.getGroupsJSON(Env.getAD_Client_ID(Env.getCtx()), null);
        String searchText = (txtSearch != null) ? txtSearch.getValue() : null;
        String itemJson = service.getItemsJSON(searchText);

        // Update KPIs
        String period = "Day";
        if (lstPeriod != null && lstPeriod.getSelectedItem() != null) {
            period = lstPeriod.getSelectedItem().getValue();
        }

        MESService.KPIStats stats = service.getKPIStats(period);
        if (lblTotal != null)
            lblTotal.setValue("Total: " + stats.total);
        if (lblCompleted != null)
            lblCompleted.setValue("Completed: " + stats.completed + " ("
                    + (stats.total > 0 ? (stats.completed * 100 / stats.total) : 0) + "%)");
        if (lblLate != null)
            lblLate.setValue("Late: " + stats.late);

        // Build Options JSON
        StringBuilder options = new StringBuilder();
        options.append("{");
        options.append(" 'groupOrder': 'content',");
        // Enable Editing
        options.append(" 'editable': { 'updateTime': true, 'updateGroup': true },");
        options.append(" 'stack': true,");
        options.append(" 'zoomMin': ").append(1000 * 60 * 60 * 24).append(",");
        options.append(" 'timeAxis': { 'scale': 'day', 'step': 1 },");
        options.append(" 'format': { 'minorLabels': { 'day': 'D' }, 'majorLabels': { 'day': 'ddd D MMMM' } },");

        // Dates logic
        if (chkTVMode != null && chkTVMode.isChecked()) {
            // TV Mode: 1 Week range starting from today 08:00
            options.append(" 'start': (function(){ var d = new Date(); d.setHours(8,0,0,0); return d; })(),");
            options.append(" 'end': (function(){ var d = new Date(); d.setDate(d.getDate()+7); return d; })(),");
        } else {
            // Normal Mode: 1 Week range starting from today 08:00
            options.append(" 'start': (function(){ var d = new Date(); d.setHours(8,0,0,0); return d; })(),");
            options.append(" 'end': (function(){ var d = new Date(); d.setDate(d.getDate()+7); return d; })(),");
        }

        options.append(" 'hiddenDates': [");
        options.append("   { 'start': '2026-01-01 18:00:00', 'end': '2026-01-02 08:00:00', 'repeat': 'daily' },");
        // options.append(" { 'start': '2026-01-25 00:00:00', 'end': '2026-01-26
        // 00:00:00', 'repeat': 'weekly' }");
        options.append(" ]");
        options.append("}");

        // Call renderOrUpdateMESTimeline with safety check
        String jsCall = "(function() {" +
                "  var waitForMES = function(count) {" +
                "    if (typeof window.renderOrUpdateMESTimeline !== 'undefined') {" +
                "      window.renderOrUpdateMESTimeline('" + divId + "', '" + timelineContainer.getUuid() + "', "
                + groupJson
                + ", " + itemJson + ", "
                + options.toString() + ");" +
                "    } else if (count < 20) {" +
                "      setTimeout(function() { waitForMES(count + 1); }, 100);" +
                "    } else {" +
                "      console.error('renderOrUpdateMESTimeline not defined from refreshTimeline');" +
                "    }" +
                "  };" +
                "  waitForMES(0);" +
                "})();";

        Clients.evalJavaScript(jsCall);
    }

    /**
     * Shows the KPI (Key Performance Indicator) dialog for a specific resource.
     * Displays daily production statistics including target qty, delivered qty, and
     * completion rate.
     * Supports real-time cross-browser updates via EventQueue.
     * 
     * @param resourceId ID of the resource (S_Resource_ID) to display KPI for
     */
    private void showResourceDialog(final int resourceId) {
        final int[] highlightId = { -1 };
        String resName = DB.getSQLValueString(null, "SELECT Name FROM S_Resource WHERE S_Resource_ID=?", resourceId);

        final org.zkoss.zul.Window kpiWindow = new org.zkoss.zul.Window();
        kpiWindow.setTitle("Daily KPI: " + resName);
        kpiWindow.setWidth(WINDOW_WIDTH_FULL);
        kpiWindow.setHeight(WINDOW_HEIGHT_FULL);
        kpiWindow.setBorder("normal");
        kpiWindow.setClosable(true);
        kpiWindow.setPage(this.getPage());

        org.zkoss.zul.Vbox mainContainer = new org.zkoss.zul.Vbox();
        mainContainer.setHflex("1");
        mainContainer.setVflex("1");
        mainContainer.setStyle("padding: " + SPACING_PADDING + ";");
        kpiWindow.appendChild(mainContainer);

        // Header with Date Selector
        final org.zkoss.zul.Datebox dateBox = new org.zkoss.zul.Datebox();
        org.zkoss.zul.Hbox header = createKPIDialogHeader(resName, dateBox);
        mainContainer.appendChild(header);

        mainContainer.appendChild(new org.zkoss.zul.Separator());

        // Container for Order Cards
        final org.zkoss.zul.Div orderCardsContainer = new org.zkoss.zul.Div();
        orderCardsContainer.setStyle(
                "display: flex; flex-wrap: wrap; gap: " + SPACING_GAP + "; justify-content: center; padding: "
                        + SPACING_MARGIN + "; overflow: auto; flex: 1;");
        orderCardsContainer.setWidth(WINDOW_WIDTH_FULL);
        mainContainer.appendChild(orderCardsContainer);

        // Bottom Bar with Close Button
        org.zkoss.zul.Hbox bottomBar = createKPIDialogBottomBar(kpiWindow);
        mainContainer.appendChild(bottomBar);

        // Refresh callback to reload KPI data
        final Runnable[] refreshListWrapper = new Runnable[1];
        refreshListWrapper[0] = new Runnable() {
            public void run() {
                orderCardsContainer.getChildren().clear();
                java.util.Date selDate = dateBox.getValue();
                Timestamp ts = (selDate != null) ? new Timestamp(selDate.getTime()) : null;

                java.util.List<MESService.TimelineItem> tasks = service.getResourceStats(resourceId, ts);

                // Set container style for full height vertical layout
                String containerStyle = "display: flex; flex-direction: column; height: 100%; width: 100%; padding: 20px; box-sizing: border-box; overflow: auto;";
                if (tasks.size() == 1) {
                    containerStyle += " justify-content: center; align-items: center;";
                }
                orderCardsContainer.setStyle(containerStyle);

                for (final MESService.TimelineItem task : tasks) { // Made final for anonymous inner class
                    // Card Box - Full Width, Flex Grow
                    org.zkoss.zul.Div card = new org.zkoss.zul.Div();
                    String borderStyle = ("Completed".equals(task.className)
                            || "status-completed".equals(task.className))
                                    ? "border: 4px solid #28a745; background: #e6ffed;"
                                    : "border: 2px solid #ccc; background: #fff;";

                    card.setStyle(borderStyle
                            + "border-radius: 12px; padding: 25px; flex: 1; margin-bottom: 20px; box-shadow: 4px 4px 10px rgba(0,0,0,0.2); display: flex; align-items: center; width: 100%; box-sizing: border-box; max-height: 48%; min-height: 350px;"); // Increased
                                                                                                                                                                                                                                                           // min-height
                                                                                                                                                                                                                                                           // for
                                                                                                                                                                                                                                                           // extra
                                                                                                                                                                                                                                                           // button

                    // 1. Column Left: Stage + Image (28%)
                    org.zkoss.zul.Div colLeft = new org.zkoss.zul.Div();
                    colLeft.setStyle(
                            "width: 28%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center;");
                    card.appendChild(colLeft);

                    // Add Stage Badge
                    String stage = extractStage(task.description);
                    org.zkoss.zul.Div stageBadge = createStageBadge(stage);
                    colLeft.appendChild(stageBadge);

                    org.zkoss.zul.Div imgDiv = new org.zkoss.zul.Div();
                    imgDiv.setStyle(
                            "width: 220px; height: 220px; display: flex; align-items: center; justify-content: center; background-color: #f0f0f0; border-radius: 12px; overflow: hidden; box-shadow: inset 0 0 5px rgba(0,0,0,0.1);");
                    colLeft.appendChild(imgDiv);

                    // Try to get Product Image
                    boolean imgFound = false;
                    if (task.productId > 0) {
                        MAttachment attachment = MAttachment.get(Env.getCtx(), 208, task.productId);
                        if (attachment != null && attachment.getEntryCount() > 0) {
                            MAttachmentEntry entry = attachment.getEntry(0);
                            byte[] imageData = entry.getData();
                            if (imageData != null && imageData.length > 0) {
                                try {
                                    org.zkoss.image.AImage aImage = new org.zkoss.image.AImage("prod", imageData);
                                    org.zkoss.zul.Image img = new org.zkoss.zul.Image();
                                    img.setContent(aImage);
                                    img.setStyle("max-width: 100%; max-height: 100%; object-fit: contain;");
                                    imgDiv.appendChild(img);
                                    imgFound = true;
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                    if (!imgFound) {
                        org.zkoss.zul.Label noImg = new org.zkoss.zul.Label("No Image");
                        noImg.setStyle("color: #999; font-size: 28px; font-weight: bold;");
                        imgDiv.appendChild(noImg);
                    }

                    // 2. Column Middle: Info (24%)
                    org.zkoss.zul.Vbox colMid = new org.zkoss.zul.Vbox();
                    colMid.setStyle(
                            "width: 24%; height: 100%; padding: 0 10px; display: flex; flex-direction: column; justify-content: center;");
                    card.appendChild(colMid);

                    org.zkoss.zul.Label lblOrder = new org.zkoss.zul.Label(
                            "Order: " + (task.documentNo != null ? task.documentNo : ""));
                    lblOrder.setStyle("display: block; font-size: 36px; font-weight: bold; color: #333;");
                    colMid.appendChild(lblOrder);

                    org.zkoss.zul.Label lblProdName = new org.zkoss.zul.Label(
                            (task.productName != null ? task.productName : ""));
                    lblProdName.setStyle(
                            "display: block; font-size: 32px; font-weight: bold; color: #2c3e50; margin-top: 10px;");
                    colMid.appendChild(lblProdName);

                    org.zkoss.zul.Label lblProdVal = new org.zkoss.zul.Label(
                            "Code: " + (task.productValue != null ? task.productValue : ""));
                    lblProdVal.setStyle("display: block; font-size: 28px; color: #7f8c8d; margin-top: 5px;");
                    colMid.appendChild(lblProdVal);

                    // Notice Column (20%)
                    org.zkoss.zul.Div colNotice = new org.zkoss.zul.Div();
                    colNotice.setStyle(
                            "width: 20%; height: 100%; padding: 0 5px; display: flex; flex-direction: column; align-items: center; justify-content: center;");
                    card.appendChild(colNotice);

                    if (task.notice != null && !task.notice.isEmpty()) {
                        org.zkoss.zul.Label lblNotice = new org.zkoss.zul.Label(task.notice);
                        lblNotice.setStyle(
                                "background: #fff3cd; color: #856404; padding: 10px; border: 1px solid #ffeeba; border-radius: 5px; font-size: 48px; font-weight: bold; line-height: 1.2; width: 100%; display: block; word-break: break-word; text-align: center;");
                        colNotice.appendChild(lblNotice);

                        // Flash Highlighting
                        if (task.id == highlightId[0]) {
                            lblNotice.setSclass("flash-highlight");
                        }
                    }

                    // 3. Column Right: Stats & Rate (28%) + Scan Button
                    org.zkoss.zul.Vbox colRight = new org.zkoss.zul.Vbox();
                    colRight.setStyle(
                            "width: 28%; height: 100%; padding: 0 10px; display: flex; flex-direction: column; justify-content: center; align-items: flex-end;"); // Align
                                                                                                                                                                  // items
                                                                                                                                                                  // to
                                                                                                                                                                  // flex-end
                                                                                                                                                                  // (right)
                    card.appendChild(colRight);

                    java.math.BigDecimal target = task.qtyOrdered != null ? task.qtyOrdered : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal delivered = task.qtyDelivered != null ? task.qtyDelivered
                            : java.math.BigDecimal.ZERO;

                    org.zkoss.zul.Label lblTarget = new org.zkoss.zul.Label("Target: " + target.intValue());
                    lblTarget.setStyle("display: block; font-size: 32px; color: #34495e; font-weight: bold;");
                    colRight.appendChild(lblTarget);

                    org.zkoss.zul.Label lblDelivered = new org.zkoss.zul.Label("Delivered: " + delivered.intValue());
                    lblDelivered.setStyle(
                            "display: block; font-size: 32px; color: #2980b9; font-weight: bold; margin-top: 5px;");
                    colRight.appendChild(lblDelivered);

                    int rate = 0;
                    if (target.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        rate = delivered.divide(target, 2, java.math.RoundingMode.HALF_UP)
                                .multiply(new java.math.BigDecimal(100)).intValue();
                    }

                    org.zkoss.zul.Label lblRate = new org.zkoss.zul.Label(rate + "%");
                    lblRate.setStyle("display: block; font-size: 84px; font-weight: bold; margin-top: 20px; color: "
                            + (rate >= 100 ? "#27ae60" : "#e74c3c") + ";");
                    colRight.appendChild(lblRate);

                    // Add Scan Button inside Card
                    org.zkoss.zul.Button btnScanOrder = new org.zkoss.zul.Button("Scan Barcode");
                    btnScanOrder.setStyle(
                            "font-size: 20px; font-weight: bold; height: 50px; width: 100%; max-width: 250px; margin-top: 25px; background: #007bff; color: white;");
                    btnScanOrder.addEventListener(org.zkoss.zk.ui.event.Events.ON_CLICK,
                            new org.zkoss.zk.ui.event.EventListener<org.zkoss.zk.ui.event.Event>() {
                                public void onEvent(org.zkoss.zk.ui.event.Event event) throws Exception {
                                    showPackingDialog(task.productId, task.documentNo, refreshListWrapper[0]);
                                }
                            });
                    colRight.appendChild(btnScanOrder);

                    // Add Complete Order Button (only if eligible)
                    if (delivered.compareTo(target) >= 0) {
                        // Check if order is not already completed
                        String docStatus = DB.getSQLValueString(null,
                                "SELECT DocStatus FROM M_Production WHERE DocumentNo=?", task.documentNo);

                        if (docStatus == null || !docStatus.equals("CO")) {
                            // Get Order ID from DocumentNo
                            final int orderId = DB.getSQLValue(null,
                                    "SELECT M_Production_ID FROM M_Production WHERE DocumentNo=?", task.documentNo);
                            org.zkoss.zul.Button btnComplete = new org.zkoss.zul.Button("✓ Complete Order");
                            btnComplete.setStyle(
                                    "font-size: 18px; font-weight: bold; height: 45px; width: 100%; max-width: 250px; margin-top: 15px; background: #28a745; color: white;");
                            btnComplete.addEventListener(org.zkoss.zk.ui.event.Events.ON_CLICK,
                                    new org.zkoss.zk.ui.event.EventListener<org.zkoss.zk.ui.event.Event>() {
                                        public void onEvent(org.zkoss.zk.ui.event.Event event) throws Exception {
                                            showCompleteOrderDialog(orderId);
                                        }
                                    });
                            colRight.appendChild(btnComplete);
                        }
                    }

                    orderCardsContainer.appendChild(card);
                }
            }
        };

        // Initial Load
        refreshListWrapper[0].run();

        // Listen for changes
        dateBox.addEventListener(Events.ON_CHANGE, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                refreshListWrapper[0].run();
            }
        });

        // Enable Server Push for Real-time Updates
        try {
            org.zkoss.zk.ui.Executions.getCurrent().getDesktop().enableServerPush(true);
            System.out.println("=== DEBUG: [KPI Dialog] Server Push Enabled for resource: " + resName);
        } catch (Exception ex) {
            System.err.println("=== DEBUG: [KPI Dialog] Server Push Enable Failed: " + ex.getMessage());
        }

        // Subscribe to Real-time Events
        final org.zkoss.zk.ui.Desktop kpiDesktop = org.zkoss.zk.ui.Executions.getCurrent().getDesktop();
        final EventHandler handler = subscribeKPIDialogToEvents(kpiDesktop, refreshListWrapper[0], highlightId,
                resName);

        kpiWindow.addEventListener("onDetach", new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                EventManager.getInstance().unregister(handler);
            }
        });

        kpiWindow.doModal();
    }

    // ==================== KPI Dialog Helper Methods ====================

    /**
     * Creates and configures the header section for KPI dialog.
     * 
     * @param resourceName Name of the resource to display in header
     * @param dateBox      Datebox component for date selection (will be configured
     *                     and added to header)
     * @return Configured Hbox containing resource label and date selector
     */
    private org.zkoss.zul.Hbox createKPIDialogHeader(String resourceName, org.zkoss.zul.Datebox dateBox) {
        org.zkoss.zul.Hbox header = new org.zkoss.zul.Hbox();
        header.setAlign("center");
        header.setPack("center");
        header.setWidth(WINDOW_WIDTH_FULL);

        // Resource Name Label
        org.zkoss.zul.Label lblResource = new org.zkoss.zul.Label("Daily KPI: " + resourceName);
        lblResource.setStyle("font-size: " + FONT_SIZE_LARGE + "; font-weight: bold; color: " + COLOR_DARK + ";");
        header.appendChild(lblResource);

        // Date Selector
        dateBox.setValue(new java.util.Date());
        dateBox.setFormat("yyyy-MM-dd");
        dateBox.setStyle("font-size: " + FONT_SIZE_NORMAL + "; height: 36px; margin-left: " + SPACING_MARGIN + ";");
        header.appendChild(dateBox);

        return header;
    }

    /**
     * Creates and configures the bottom bar with close button for KPI dialog.
     * 
     * @param win The window that will be closed when button is clicked
     * @return Configured Hbox containing centered close button
     */
    private org.zkoss.zul.Hbox createKPIDialogBottomBar(final org.zkoss.zul.Window win) {
        org.zkoss.zul.Hbox bottomBar = new org.zkoss.zul.Hbox();
        bottomBar.setStyle("padding: " + SPACING_MARGIN + "; background-color: " + COLOR_BG_NEUTRAL
                + "; border-top: 1px solid #ddd;");
        bottomBar.setAlign("center");
        bottomBar.setPack("center");
        bottomBar.setHeight("80px");
        bottomBar.setWidth(WINDOW_WIDTH_FULL);

        org.zkoss.zul.Button btnClose = new org.zkoss.zul.Button("Close");
        btnClose.setStyle("font-size: " + FONT_SIZE_NORMAL + "; font-weight: bold; height: " + SIZE_BUTTON_HEIGHT_LARGE
                + "; width: " + SIZE_BUTTON_WIDTH + ";");
        btnClose.addEventListener(org.zkoss.zk.ui.event.Events.ON_CLICK,
                new org.zkoss.zk.ui.event.EventListener<org.zkoss.zk.ui.event.Event>() {
                    public void onEvent(org.zkoss.zk.ui.event.Event event) throws Exception {
                        win.detach();
                    }
                });
        bottomBar.appendChild(btnClose);

        return bottomBar;
    }

    /**
     * Subscribes KPI dialog to real-time EventQueue updates.
     * Listens for MES_UPDATE events and automatically refreshes the KPI display
     * when production orders are scanned in other browser sessions.
     * 
     * @param kpiDesktop      Desktop instance for this KPI dialog
     * @param refreshCallback Runnable to execute when refresh is needed
     * @param resourceName    Name of resource (for logging purposes)
     */
    private EventHandler subscribeKPIDialogToEvents(final org.zkoss.zk.ui.Desktop kpiDesktop,
            final Runnable refreshCallback,
            final int[] highlightId, final String resourceName) {
        System.out.println("=== DEBUG: [KPI Dialog] Subscribing to OSGi EventAdmin for resource: " + resourceName);

        final EventHandler handler = new EventHandler() {
            @Override
            public void handleEvent(final org.osgi.service.event.Event event) {
                System.out.println("=== DEBUG: [KPI Dialog] *** EVENT RECEIVED *** Topic: " + event.getTopic());

                if (MESBroadcastUtil.TOPIC_MES_UPDATE.equals(event.getTopic())) {
                    final Integer orderId = (Integer) event.getProperty(MESBroadcastUtil.PROPERTY_ORDER_ID);
                    final Integer productId = (Integer) event.getProperty(MESBroadcastUtil.PROPERTY_PRODUCT_ID);

                    System.out.println(
                            "=== DEBUG: [KPI Dialog] Event data: orderId=" + orderId + ", productId=" + productId);

                    if (kpiDesktop.isAlive()) {
                        System.out.println("=== DEBUG: [KPI Dialog] Scheduling refresh...");

                        try {
                            org.zkoss.zk.ui.Executions.schedule(kpiDesktop,
                                    new EventListener<org.zkoss.zk.ui.event.Event>() {
                                        public void onEvent(org.zkoss.zk.ui.event.Event evt) throws Exception {
                                            System.out.println("=== DEBUG: [KPI Dialog] Executing refresh on Desktop: "
                                                    + kpiDesktop.getId());

                                            if (orderId != null) {
                                                highlightId[0] = orderId;
                                            }
                                            refreshCallback.run();

                                            // Show visual notification
                                            String docNo = DB.getSQLValueString(null,
                                                    "SELECT DocumentNo FROM M_Production WHERE M_Production_ID=?",
                                                    orderId);

                                            org.zkoss.zk.ui.util.Clients.showNotification(
                                                    "🔴 Remote Scan! Order " + docNo + " updated",
                                                    "info",
                                                    null,
                                                    NOTIFY_POS_TOP_RIGHT,
                                                    NOTIFY_DURATION_EXTRA);

                                            System.out.println("=== DEBUG: [KPI Dialog] Refresh completed!");
                                        }
                                    }, new org.zkoss.zk.ui.event.Event("updateKPI"));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        // Register with EventManager
        EventManager.getInstance().register(MESBroadcastUtil.TOPIC_MES_UPDATE, handler);

        return handler;
    }

    /**
     * Extracts stage name from PP_Order Description field.
     * 
     * @param description PP_Order.Description (e.g., "Stage: Sewing")
     * @return Stage name (e.g., "Sewing") or null if not found
     */
    private String extractStage(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }

        // Parse "Stage: Sewing" format
        if (description.contains("Stage: ")) {
            int startIndex = description.indexOf("Stage: ") + 7;
            int endIndex = description.indexOf("\n", startIndex);
            if (endIndex == -1) {
                endIndex = description.length();
            }
            return description.substring(startIndex, endIndex).trim();
        }

        return null;
    }

    /**
     * Creates stage badge component for KPI dialog order cards.
     * 
     * @param stage Stage name extracted from Description
     * @return Configured Div with stage badge (icon + label)
     */
    private org.zkoss.zul.Div createStageBadge(String stage) {
        org.zkoss.zul.Div badge = new org.zkoss.zul.Div();

        // Use centralized stage configuration
        tw.idempiere.mes.model.StageConfig stageConfig = tw.idempiere.mes.model.StageConfig.fromName(stage);
        String icon = stageConfig.getIcon();
        String label = stageConfig.getLabel();
        String bgColor = stageConfig.getColor();

        badge.setStyle(
                "background-color: " + bgColor + "; " +
                        "color: white; " +
                        "padding: 10px 15px; " +
                        "border-radius: 8px; " +
                        "font-size: 20px; " +
                        "font-weight: bold; " +
                        "text-align: center; " +
                        "margin-bottom: 15px; " +
                        "box-shadow: 0 2px 4px rgba(0,0,0,0.3); " +
                        "min-width: 180px;");

        org.zkoss.zul.Label badgeLabel = new org.zkoss.zul.Label(icon + " " + label);
        badgeLabel.setStyle("color: white; font-size: 18px;");
        badge.appendChild(badgeLabel);

        return badge;
    }

    /**
     * Subscribes Timeline view to real-time EventQueue updates.
     * Listens for MES_UPDATE events and automatically refreshes the Timeline
     * when production orders are scanned in any browser session.
     */
    private void subscribeTimelineToEvents() {
        System.out.println("=== DEBUG: [Timeline] Subscribing to OSGi EventAdmin");

        try {
            // Enable server push for Timeline desktop
            final org.zkoss.zk.ui.Desktop timelineDesktop = org.zkoss.zk.ui.Executions.getCurrent().getDesktop();
            timelineDesktop.enableServerPush(true);
            System.out.println("=== DEBUG: [Timeline] Server Push Enabled");

            final EventHandler handler = new EventHandler() {
                @Override
                public void handleEvent(final org.osgi.service.event.Event event) {
                    System.out.println("=== DEBUG: [Timeline] *** EVENT RECEIVED *** Topic: " + event.getTopic());

                    if (MESBroadcastUtil.TOPIC_MES_UPDATE.equals(event.getTopic())) {
                        final Integer orderId = (Integer) event.getProperty(MESBroadcastUtil.PROPERTY_ORDER_ID);

                        System.out.println(
                                "=== DEBUG: [Timeline] Event data: orderId=" + orderId);

                        if (timelineDesktop.isAlive()) {
                            System.out.println("=== DEBUG: [Timeline] Scheduling Timeline refresh...");

                            try {
                                org.zkoss.zk.ui.Executions.schedule(timelineDesktop,
                                        new EventListener<org.zkoss.zk.ui.event.Event>() {
                                            public void onEvent(org.zkoss.zk.ui.event.Event evt) throws Exception {
                                                System.out.println("=== DEBUG: [Timeline] Executing Timeline refresh");

                                                // Refresh the Timeline to show updated order status
                                                refreshTimeline();

                                                // Show notification
                                                String docNo = DB.getSQLValueString(null,
                                                        "SELECT DocumentNo FROM M_Production WHERE M_Production_ID=?",
                                                        orderId);

                                                org.zkoss.zk.ui.util.Clients.showNotification(
                                                        "🔄 Order " + docNo + " updated - Timeline refreshed",
                                                        "info",
                                                        null,
                                                        NOTIFY_POS_TOP_RIGHT,
                                                        NOTIFY_DURATION_EXTRA);

                                                System.out.println("=== DEBUG: [Timeline] Timeline refresh completed!");
                                            }
                                        }, new org.zkoss.zk.ui.event.Event("updateTimeline"));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            };

            EventManager.getInstance().register(MESBroadcastUtil.TOPIC_MES_UPDATE, handler);

            // Register cleanup on the timeline container or desktop if possible?
            // For Main Timeline, it stays alive usually.
            // Ideally we should hook into onDetach of the main component.
            this.addEventListener("onDetach",
                    new EventListener<org.zkoss.zk.ui.event.Event>() {
                        public void onEvent(org.zkoss.zk.ui.event.Event event) throws Exception {
                            EventManager.getInstance().unregister(handler);
                        }
                    });

            System.out.println("=== DEBUG: [Timeline] Subscription completed");

        } catch (Exception e) {
            System.err.println("=== DEBUG: [Timeline] EXCEPTION in subscription:");
            e.printStackTrace();
        }
    }

    /**
     * Shows the barcode scanning dialog for product delivery.
     * Allows operators to scan product barcodes and update delivery quantities.
     * Publishes real-time events to notify other browser sessions of updates.
     * 
     * @param productId       Expected product ID for validation
     * @param orderNo         Production order document number
     * @param onCloseCallback Callback to refresh parent UI after successful scan
     */
    private void showPackingDialog(final int productId, final String orderNo, final Runnable onCloseCallback) {
        final org.zkoss.zul.Window dialog = new org.zkoss.zul.Window();
        dialog.setTitle("Delivery Scan: " + orderNo);
        dialog.setWidth(DIALOG_WIDTH_SCAN);
        dialog.setHeight(DIALOG_HEIGHT_SCAN);
        dialog.setBorder("normal");
        dialog.setClosable(true);
        dialog.setPage(this.getPage());
        dialog.setSclass("popup-dialog");

        org.zkoss.zul.Vbox layout = new org.zkoss.zul.Vbox();
        layout.setHflex("1");
        layout.setVflex("1");
        layout.setStyle("padding: 20px; align-items: center;");
        dialog.appendChild(layout);

        // Instructions
        org.zkoss.zul.Label lblInstr = new org.zkoss.zul.Label("Scan Product Barcode or Enter Qty");
        lblInstr.setStyle("font-weight: bold; font-size: 16px; margin-bottom: 20px;");
        layout.appendChild(lblInstr);

        // Product Value Display
        String productValue = DB.getSQLValueString(null, "SELECT Value FROM M_Product WHERE M_Product_ID=?", productId);
        org.zkoss.zul.Hbox prodBox = new org.zkoss.zul.Hbox();
        prodBox.setAlign("center");
        prodBox.setWidth("100%");
        prodBox.setStyle("margin-bottom: 10px; background: #e9ecef; padding: 5px; border-radius: 4px;");

        org.zkoss.zul.Label lblProd = new org.zkoss.zul.Label("Product:");
        lblProd.setStyle("font-weight: bold; font-size: 14px; margin-right: 10px;");
        prodBox.appendChild(lblProd);

        org.zkoss.zul.Label lblProdValue = new org.zkoss.zul.Label(productValue);
        lblProdValue.setStyle("font-size: 16px; font-weight: bold; color: #0056b3;");
        prodBox.appendChild(lblProdValue);

        layout.appendChild(prodBox);

        // Barcode Input
        final org.zkoss.zul.Textbox txtBarcode = new org.zkoss.zul.Textbox();
        txtBarcode.setPlaceholder("Scan Barcode Here");
        txtBarcode.setStyle("height: 40px; font-size: 18px; width: 100%; margin-bottom: 15px;");
        txtBarcode.setFocus(true); // Focus for scanning
        layout.appendChild(txtBarcode);

        // Qty Input
        org.zkoss.zul.Hbox qtyBox = new org.zkoss.zul.Hbox();
        qtyBox.setAlign("center");
        qtyBox.setWidth("100%");

        org.zkoss.zul.Label lblQty = new org.zkoss.zul.Label("Delivered Qty:");
        lblQty.setStyle("font-size: 16px; margin-right: 10px;");
        qtyBox.appendChild(lblQty);

        final org.zkoss.zul.Intbox numQty = new org.zkoss.zul.Intbox(1); // Default 1
        numQty.setStyle("height: 40px; font-size: 18px; width: 100px;");
        qtyBox.appendChild(numQty);
        layout.appendChild(qtyBox);

        // Process Button
        final org.zkoss.zul.Button btnProcess = new org.zkoss.zul.Button("Confirm Delivery");
        btnProcess.setStyle(
                "margin-top: 20px; height: 50px; width: 100%; font-size: 18px; font-weight: bold; background: #28a745; color: white;");
        btnProcess.addEventListener(org.zkoss.zk.ui.event.Events.ON_CLICK,
                new org.zkoss.zk.ui.event.EventListener<org.zkoss.zk.ui.event.Event>() {
                    public void onEvent(org.zkoss.zk.ui.event.Event event) throws Exception {
                        String barcode = txtBarcode.getValue();
                        Integer qty = numQty.getValue();

                        if (qty == null || qty <= 0) {
                            org.zkoss.zk.ui.util.Clients.showNotification("Invalid Qty", "warning", null,
                                    NOTIFY_POS_CENTER, NOTIFY_DURATION_NORMAL);
                            return;
                        }

                        // 1. Validate Barcode
                        int scannedId = getProductIdFromBarcode(barcode);
                        if (scannedId <= 0) {
                            org.zkoss.zk.ui.util.Clients.showNotification("Product Not Found!", "error", null,
                                    NOTIFY_POS_CENTER, NOTIFY_DURATION_NORMAL);
                            txtBarcode.setFocus(true);
                            txtBarcode.setSelectionRange(0, barcode.length());
                            return;
                        }

                        if (scannedId != productId) {
                            org.zkoss.zk.ui.util.Clients.showNotification("Wrong Barcode!", "error", null,
                                    NOTIFY_POS_CENTER, NOTIFY_DURATION_NORMAL);
                            txtBarcode.setFocus(true);
                            txtBarcode.setSelectionRange(0, barcode.length());
                            return;
                        }

                        // 2. Actual Processing
                        boolean success = updateProductionOrderQty(orderNo, productId, qty);

                        if (success) {
                            org.zkoss.zk.ui.util.Clients.showNotification("Scanned!", "info", null, NOTIFY_POS_CENTER,
                                    NOTIFY_DURATION_SHORT);

                            // Get the Order ID for event publishing
                            int orderId = DB.getSQLValue(null,
                                    "SELECT M_Production_ID FROM M_Production WHERE DocumentNo=? AND M_Product_ID=?",
                                    orderNo, productId);

                            // Auto-update Stage to "Packing" if not already
                            try {
                                String currentStage = DB.getSQLValueString(null,
                                        "SELECT Description FROM M_Production WHERE M_Production_ID=?", orderId);
                                if (currentStage == null || !currentStage.contains("Stage: Packing")) {
                                    updateOrderStage(orderId, "Packing");
                                    System.out.println(
                                            "=== DEBUG: [Packing Dialog] Auto-updated stage to Packing for Order ID: "
                                                    + orderId);
                                }
                            } catch (Exception stageEx) {
                                log.warning("Failed to auto-update stage: " + stageEx.getMessage());
                            }

                            // Publish Event to notify all open dialogs
                            System.out.println("=== DEBUG: [Packing Dialog] *** PUBLISHING EVENT *** OrderID: "
                                    + orderId + ", ProductID: " + productId);

                            try {
                                MESBroadcastUtil.publishMESUpdate(orderId, productId, qty);
                                System.out.println("=== DEBUG: [Packing Dialog] *** EVENT PUBLISHED SUCCESSFULLY ***");

                            } catch (Exception pubEx) {
                                System.err.println("=== DEBUG: [Packing Dialog] EXCEPTION during publish:");
                                pubEx.printStackTrace();
                            }

                            // Clear and Refocus
                            txtBarcode.setValue("");
                            txtBarcode.setFocus(true);

                            if (onCloseCallback != null) {
                                onCloseCallback.run(); // Call the callback to refresh UI background
                            }
                        } else {
                            org.zkoss.zk.ui.util.Clients.showNotification("DB Error", "error", null, NOTIFY_POS_CENTER,
                                    NOTIFY_DURATION_LONG);
                        }
                    }
                });
        layout.appendChild(btnProcess);

        // Handle Enter key on Barcode field to jump to Process or Auto-submit
        txtBarcode.addEventListener(org.zkoss.zk.ui.event.Events.ON_OK,
                new org.zkoss.zk.ui.event.EventListener<org.zkoss.zk.ui.event.Event>() {
                    public void onEvent(org.zkoss.zk.ui.event.Event event) throws Exception {
                        org.zkoss.zk.ui.event.Events.postEvent(org.zkoss.zk.ui.event.Events.ON_CLICK, btnProcess, null);
                    }
                });

        dialog.doModal();
    }

    /**
     * Updates the delivered quantity for a production order.
     * 
     * @param documentNo Production order document number
     * @param productId  Product ID for validation
     * @param qtyToAdd   Quantity to add to current delivered amount
     * @return true if update was successful, false otherwise
     */
    private boolean updateProductionOrderQty(String documentNo, int productId, int qtyToAdd) {
        // 1. Update M_Production.QtyDelivered
        String sqlProd = "UPDATE M_Production " +
                "SET QtyDelivered = COALESCE(QtyDelivered, 0) + ? " +
                "WHERE DocumentNo = ? AND M_Product_ID = ?";
        int noProd = DB.executeUpdate(sqlProd, new Object[] { qtyToAdd, documentNo, productId }, false, null);

        // 2. Sync to M_ProductionLine.MovementQty (IsEndProduct='Y')
        // New Requirement: Overwrite MovementQty with actual delivered quantity
        String sqlLine = "UPDATE M_ProductionLine pl " +
                "SET MovementQty = COALESCE((SELECT QtyDelivered FROM M_Production WHERE DocumentNo = ? AND M_Product_ID = ?), 0) "
                +
                "WHERE pl.M_Production_ID = (" +
                "  SELECT M_Production_ID FROM M_Production " +
                "  WHERE DocumentNo = ? AND M_Product_ID = ?" +
                ") AND pl.IsEndProduct = 'Y' AND pl.M_Product_ID = ?";
        DB.executeUpdate(sqlLine, new Object[] { documentNo, productId, documentNo, productId, productId }, false,
                null);

        return noProd > 0;
    }

    /**
     * Looks up Product ID from barcode (M_Product.Value).
     * 
     * @param barcode Barcode string to search for
     * @return Product ID if found, -1 if not found
     */
    private int getProductIdFromBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty())
            return -1;
        String sql = "SELECT M_Product_ID FROM M_Product WHERE Value = ? AND IsActive='Y' AND AD_Client_ID = ?";
        return DB.getSQLValue(null, sql, barcode.trim(), Env.getAD_Client_ID(Env.getCtx()));
    }

    private void showRescheduleConfirmDialog(final int id, final int newResourceId, final String newStartStr,
            final String newEndStr) {
        // 1. Fetch Details
        String sql = "SELECT o.DocumentNo, p.Value, o.ProductionQty, r.Name, o.MovementDate " +
                "FROM M_Production o " +
                "INNER JOIN M_Product p ON (o.M_Product_ID=p.M_Product_ID) " +
                "INNER JOIN S_Resource r ON (o.S_Resource_ID=r.S_Resource_ID) " +
                "WHERE o.M_Production_ID=?";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String docNo = "";
        String prodValue = "";
        BigDecimal qty = BigDecimal.ZERO;
        String oldResource = "";
        Timestamp oldDate = null;

        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, id);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                docNo = rs.getString(1);
                prodValue = rs.getString(2);
                qty = rs.getBigDecimal(3);
                oldResource = rs.getString(4);
                oldDate = rs.getTimestamp(5);
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Error fetching order for reschedule confirm", e);
        } finally {
            DB.close(rs, pstmt);
        }

        String newResource = DB.getSQLValueString(null, "SELECT Name FROM S_Resource WHERE S_Resource_ID=?",
                newResourceId);

        // Parse New Date
        Timestamp newDate = null;
        try {
            // ISO 8601 parsing (Vis.js sends ISO strings)
            // Simple hack: Vis.js sends "2023-10-27T08:00:00.000Z"
            // Using simple date if possible or JodaTime/Java8 Time
            // Let's rely on simple string parsing or Timestamp.valueOf if format matches
            // But ISO format has T and Z.
            // Let's use Timestamp(long) from parsed Date
            // Assume standard ZK/Java env has access to basic parsing
            // Actually, `updateOrder` handled this via `DateStartSchedule` logic before,
            // but here I just need to show it.
            // Let's use a quick parser or substring if format is stable (Vis.js is stable)
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            java.util.Date d = sdf.parse(newStartStr);
            newDate = new Timestamp(d.getTime());
        } catch (Exception e) {
            // Fallback or ignore
            log.warning("Could not parse date: " + newStartStr);
        }

        final Window win = new Window();
        win.setTitle("Confirm Reschedule");
        win.setWidth("400px");
        win.setClosable(true);
        win.setBorder("normal");

        Vbox vbox = new Vbox();
        vbox.setWidth("100%");
        vbox.setSpacing("10px");
        win.appendChild(vbox);

        Grid grid = new Grid();
        Columns cols = new Columns();
        Column colLbl = new Column();
        colLbl.setWidth("120px");
        Column colVal = new Column();
        cols.appendChild(colLbl);
        cols.appendChild(colVal);
        grid.appendChild(cols);

        Rows rows = new Rows();
        grid.appendChild(rows);
        SimpleDateFormat sdfShort = new SimpleDateFormat("yyyy-MM-dd");
        addRow(rows, "DocumentNo:", docNo);
        addRow(rows, "Product:", prodValue);
        addRow(rows, "Qty:", qty + "");
        addRow(rows, "Resource:", oldResource + " -> " + newResource);
        addRow(rows, "Date:", sdfShort.format(oldDate) + " -> " + sdfShort.format(newDate));

        vbox.appendChild(grid);

        Hbox hbox = new Hbox();
        hbox.setPack("end");
        hbox.setWidth("100%");

        Button btnConfirm = new Button("Confirm");
        btnConfirm.addEventListener(Events.ON_CLICK, new org.zkoss.zk.ui.event.EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                updateOrder(id, newResourceId, newStartStr, newEndStr);
                win.detach();
            }
        });

        Button btnCancel = new Button("Cancel");
        btnCancel.addEventListener(Events.ON_CLICK, new org.zkoss.zk.ui.event.EventListener<Event>() {
            @Override
            public void onEvent(Event event) throws Exception {
                refreshTimeline();
                win.detach();
            }
        });

        hbox.appendChild(btnCancel);
        hbox.appendChild(new Space());
        hbox.appendChild(btnConfirm);

        vbox.appendChild(hbox);

        win.setPage(this.getPage());
        win.doModal();
    }

    private void addRow(Rows rows, String label, String value) {
        Row row = new Row();
        row.appendChild(new Label(label));
        Label valLbl = new Label(value);
        valLbl.setStyle("font-weight: bold;");
        row.appendChild(valLbl);
        rows.appendChild(row);
    }

    private void updateOrder(int id, int resourceId, String startStr, String endStr) {
        log.info("Moving Order " + id + " to Resource " + resourceId + " Start: " + startStr + " End: " + endStr);

        try {
            // 1. Cleaner Parsing
            String cleanStart = startStr.replace("T", " ").replace("Z", "");
            String cleanEnd = endStr.replace("T", " ").replace("Z", "");
            if (cleanStart.indexOf(".") > 0)
                cleanStart = cleanStart.substring(0, cleanStart.indexOf("."));
            if (cleanEnd.indexOf(".") > 0)
                cleanEnd = cleanEnd.substring(0, cleanEnd.indexOf("."));

            Timestamp startTime = Timestamp.valueOf(cleanStart);
            Timestamp endTime = Timestamp.valueOf(cleanEnd);

            // 2. Auto-Snap to Work Hours (0800 - 1700)
            Calendar cal = Calendar.getInstance();

            // Snap Start
            cal.setTime(startTime);

            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);

            startTime = new Timestamp(cal.getTimeInMillis());

            // Snap End
            cal.setTime(endTime);

            cal.set(Calendar.HOUR_OF_DAY, 17);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);

            endTime = new Timestamp(cal.getTimeInMillis());

            // Ensure End > Start
            if (endTime.before(startTime)) {
                cal.setTime(startTime);
                cal.add(Calendar.HOUR_OF_DAY, 1);
                endTime = new Timestamp(cal.getTimeInMillis());
            }

            // 3. Check for Resource Change & Notification
            String msg = "Saved";
            int oldResourceId = DB.getSQLValue(null, "SELECT S_Resource_ID FROM M_Production WHERE M_Production_ID=?",
                    id);

            if (oldResourceId != resourceId && oldResourceId > 0) {
                String oldName = DB.getSQLValueString(null, "SELECT Name FROM S_Resource WHERE S_Resource_ID=?",
                        oldResourceId);
                String newName = DB.getSQLValueString(null, "SELECT Name FROM S_Resource WHERE S_Resource_ID=?",
                        resourceId);
                msg = "Moved from " + oldName + " to " + newName;
                // Add explicit time info if desired, but user asked for Resource Name
            }

            // 4. Update
            String sql = "UPDATE M_Production SET S_Resource_ID=?, MovementDate=? WHERE M_Production_ID=?";
            int no = DB.executeUpdate(sql, new Object[] { resourceId, startTime, id }, false, null);

            if (no > 0) {
                Clients.showNotification(msg, "info", null, "middle_center", 3000);

                // Publish EventQueue event to notify KPI Dialogs
                try {
                    // Get Product ID for the event
                    int productId = DB.getSQLValue(null,
                            "SELECT M_Product_ID FROM M_Production WHERE M_Production_ID=?", id);

                    System.out.println("=== DEBUG: [Timeline Update] Publishing event for Order ID: " + id);

                    MESBroadcastUtil.publishMESUpdate(id, productId, 0);

                    System.out.println("=== DEBUG: [Timeline Update] Event published successfully");
                } catch (Exception ex) {
                    System.err.println("=== DEBUG: [Timeline Update] Failed to publish event: " + ex.getMessage());
                }
            } else {
                Clients.showNotification("Save Failed", "error", null, "middle_center", 2000);
                refreshTimeline(); // Revert
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Update Failed", e);
            Clients.showNotification("Error: " + e.getMessage(), "error", null, "middle_center", 2000);
            refreshTimeline(); // Revert
        }
    }

    private tw.idempiere.mes.service.MESService mesService = new tw.idempiere.mes.service.MESService();

    private void showGenerateDialog() {
        final Window win = new Window();
        win.setTitle("Generate Manufacturing Orders");
        win.setWidth("500px");
        win.setHeight("300px");
        win.setClosable(true);
        win.setBorder("normal");

        Panel panel = new Panel();
        win.appendChild(panel);
        Grid grid = GridFactory.newGridLayout();
        panel.appendChild(grid);
        Rows rows = new Rows();
        grid.appendChild(rows);

        // Order Line Picker
        Row row1 = new Row();
        rows.appendChild(row1);
        row1.appendChild(new Label("Order Line"));

        MLookup lookupOrderLine = null;
        int CL_OrderLine_ID = MColumn.getColumn_ID("C_OrderLine", "C_OrderLine_ID");
        try {
            lookupOrderLine = MLookupFactory.get(Env.getCtx(), 0, CL_OrderLine_ID, DisplayType.Search,
                    Env.getLanguage(Env.getCtx()), "C_OrderLine_ID", 0, false, "IsActive='Y'");
        } catch (Exception e) {
            log.log(Level.SEVERE, "OrderLine Lookup", e);
        }

        final WSearchEditor txtOrderLine = new WSearchEditor("C_OrderLine_ID", false, false, true, lookupOrderLine);
        row1.appendChild(txtOrderLine.getComponent());

        // Resource Picker
        Row row2 = new Row();
        rows.appendChild(row2);
        row2.appendChild(new Label("Resource"));

        int CL_Resource_ID = MColumn.getColumn_ID("S_Resource", "S_Resource_ID");
        MLookup lookupResource = null;
        try {
            lookupResource = MLookupFactory.get(Env.getCtx(), 0, CL_Resource_ID, DisplayType.Search,
                    Env.getLanguage(Env.getCtx()), "S_Resource_ID", 0, false, "IsActive='Y'");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Resource Lookup", e);
        }

        final WSearchEditor txtResource = new WSearchEditor("S_Resource_ID", false, false, true, lookupResource);
        row2.appendChild(txtResource.getComponent());

        // Buttons
        Hbox bbox = new Hbox();
        bbox.setStyle("margin-top: 10px; margin-bottom: 10px;");
        Button btnOk = new Button("OK");
        Button btnCancel = new Button("Cancel");
        bbox.appendChild(btnOk);
        bbox.appendChild(new Space());
        bbox.appendChild(btnCancel);

        ZKUpdateUtil.setHeight(bbox, "40px");

        Row rowBtn = new Row();
        org.zkoss.zul.Cell cell = new org.zkoss.zul.Cell();
        cell.setColspan(2);
        cell.appendChild(bbox);
        rowBtn.appendChild(cell);
        rows.appendChild(rowBtn);

        btnCancel.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                win.detach();
            }
        });

        btnOk.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                Object lineVal = txtOrderLine.getValue();
                Object resVal = txtResource.getValue();

                if (lineVal == null || resVal == null) {
                    FDialog.warn(0, "Warning", "Please select Order Line and Resource");
                    return;
                }

                int C_OrderLine_ID = (Integer) lineVal;
                int S_Resource_ID = (Integer) resVal;

                generateOrders(C_OrderLine_ID, S_Resource_ID, win);
            }
        });

        win.setPage(this.getPage());
        win.doModal();
    }

    private void generateOrders(int C_OrderLine_ID, int S_Resource_ID, Window win) {
        Trx trx = Trx.get(Trx.createTrxName("GenProd"), true);
        try {
            MOrderLine oLine = new MOrderLine(Env.getCtx(), C_OrderLine_ID, trx.getTrxName());
            MResource resource = new MResource(Env.getCtx(), S_Resource_ID, trx.getTrxName());

            BigDecimal qtyOrdered = oLine.getQtyOrdered();
            BigDecimal dailyCapacity = resource.getDailyCapacity();

            if (dailyCapacity == null || dailyCapacity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new AdempiereException("Resource has no Daily Capacity defined");
            }

            BigDecimal remaining = qtyOrdered;
            Timestamp scheduleDate = new Timestamp(System.currentTimeMillis());

            Calendar cal = Calendar.getInstance();
            cal.setTime(scheduleDate);
            cal.set(Calendar.HOUR_OF_DAY, 8); // Start 8 AM
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            // Find M_Product_BOM instead of PP_Product_BOM
            int M_Product_BOM_ID = DB.getSQLValue(trx.getTrxName(),
                    "SELECT M_Product_BOM_ID FROM M_Product_BOM WHERE M_Product_ID=? AND IsActive='Y' ORDER BY Created DESC",
                    oLine.getM_Product_ID());

            // Determine Warehouse from Resource, fallback to Order Line
            int M_Warehouse_ID = resource.getM_Warehouse_ID();
            if (M_Warehouse_ID == 0)
                M_Warehouse_ID = oLine.getM_Warehouse_ID();

            // 1. Try to find Locator with same name as Resource (in same Org)
            int M_Locator_ID = DB.getSQLValue(trx.getTrxName(),
                    "SELECT M_Locator_ID FROM M_Locator WHERE AD_Org_ID=? AND Value=? AND IsActive='Y' ORDER BY Created DESC",
                    oLine.getAD_Org_ID(), resource.getName());

            // 2. If not found by Name, try by Value (in same Org)
            if (M_Locator_ID <= 0) {
                M_Locator_ID = DB.getSQLValue(trx.getTrxName(),
                        "SELECT M_Locator_ID FROM M_Locator WHERE AD_Org_ID=? AND Value=? AND IsActive='Y' ORDER BY Created DESC",
                        oLine.getAD_Org_ID(), resource.getValue());
            }

            // 3. Fallback: Default locator for warehouse (Use Order Line Warehouse)
            if (M_Locator_ID <= 0) {
                M_Locator_ID = DB.getSQLValue(trx.getTrxName(),
                        "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND IsDefault='Y' AND IsActive='Y'",
                        oLine.getM_Warehouse_ID());
            }

            // 4. Fallback: Any locator in Order Line Warehouse
            if (M_Locator_ID <= 0) {
                M_Locator_ID = DB.getSQLValue(trx.getTrxName(),
                        "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND IsActive='Y' ORDER BY Created",
                        oLine.getM_Warehouse_ID());
            }

            int count = 0;
            while (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // Skip Sundays
                if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    continue;
                }
                BigDecimal daysProduction = dailyCapacity;
                if (remaining.compareTo(dailyCapacity) < 0) {
                    daysProduction = remaining;
                }

                // Create M_Production using MProduction class
                org.compiere.model.MProduction production = new org.compiere.model.MProduction(Env.getCtx(), 0,
                        trx.getTrxName());

                production.setAD_Org_ID(oLine.getAD_Org_ID());
                production.setM_Product_ID(oLine.getM_Product_ID());
                production.setProductionQty(daysProduction);
                production.setMovementDate(new Timestamp(cal.getTimeInMillis()));
                production.setM_Locator_ID(M_Locator_ID);

                // Set S_Resource_ID (custom column)
                production.set_ValueOfColumn("S_Resource_ID", S_Resource_ID);

                // Set C_OrderLine_ID (custom column)
                production.set_ValueOfColumn("C_OrderLine_ID", C_OrderLine_ID);

                // Initialize QtyDelivered to 0
                production.set_ValueOfColumn("QtyDelivered", BigDecimal.ZERO);

                // Set description with order line reference
                production.setDescription("Generated from Order Line: " + oLine.getParent().getDocumentNo() +
                        " - Line " + oLine.getLine());

                production.saveEx();

                // Generate Production Lines (BOM Explosion)
                // This is required to have valid production records for material
                int linesCreated = production.createLines(false);
                if (linesCreated == 0) {
                    log.warning("No Production Lines created for Product: " + oLine.getM_Product_ID());
                }

                count++;
                remaining = remaining.subtract(daysProduction);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            trx.commit();
            win.detach();
            refreshTimeline();
            FDialog.info(0, win, "Generated " + count + " Production Orders");

        } catch (Exception e) {
            trx.rollback();
            log.log(Level.SEVERE, "Generate Production Error", e);
            String msg = e.getMessage();
            if (msg == null)
                msg = e.toString();
            FDialog.error(0, win, msg);
        } finally {
            trx.close();
        }
    }

    @Override
    public ADForm getForm() {
        return this;
    }

    private void showContextMenu(final int orderId, int x, int y) {
        org.zkoss.zul.Menupopup popup = new org.zkoss.zul.Menupopup();
        popup.setPage(this.getPage());

        // 1. Open Window
        org.zkoss.zul.Menuitem itemOpen = new org.zkoss.zul.Menuitem("Open Order Window");
        if (ThemeManager.isUseFontIconForImage())
            itemOpen.setIconSclass("z-icon-Window");
        else
            itemOpen.setImage(ThemeManager.getThemeResource("images/Window16.png"));

        itemOpen.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                // Find Window by M_Production table
                int AD_Table_ID = MTable.getTable_ID("M_Production");
                int AD_Window_ID = DB.getSQLValue(null,
                        "SELECT w.AD_Window_ID " +
                                "FROM AD_Window w " +
                                "INNER JOIN AD_Tab t ON w.AD_Window_ID = t.AD_Window_ID " +
                                "WHERE t.AD_Table_ID=? AND w.IsActive='Y' AND t.IsActive='Y' " +
                                "ORDER BY t.SeqNo " +
                                "LIMIT 1",
                        AD_Table_ID);

                if (AD_Window_ID > 0) {
                    MQuery query = new MQuery("M_Production");
                    query.addRestriction("M_Production_ID", MQuery.EQUAL, orderId);
                    AEnv.zoom(AD_Window_ID, query);
                } else {
                    FDialog.warn(0, "Window Not Found", "Could not find Window for Production (M_Production)");
                }
            }
        });
        popup.appendChild(itemOpen);

        // --- New MES Features ---
        popup.appendChild(new org.zkoss.zul.Menuseparator());

        // 2. Process Status Submenu
        org.zkoss.zul.Menu processMenu = new org.zkoss.zul.Menu("Production Stage");
        org.zkoss.zul.Menupopup processPopup = new org.zkoss.zul.Menupopup();
        processMenu.appendChild(processPopup);

        String[] stages = { "Cutting", "Sewing", "Packing" };
        for (String stage : stages) {
            org.zkoss.zul.Menuitem itemStage = new org.zkoss.zul.Menuitem(stage);
            final String s = stage;
            itemStage.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    // Fire custom event to Self for handling
                    Events.postEvent("onProcessSet", WProductionSchedule.this, new Object[] { orderId, s });
                }
            });
            processPopup.appendChild(itemStage);
        }
        popup.appendChild(processMenu);

        // 3. Material Issue
        org.zkoss.zul.Menuitem itemMaterial = new org.zkoss.zul.Menuitem("Material Issue (Move)");
        if (ThemeManager.isUseFontIconForImage())
            itemMaterial.setIconSclass("z-icon-Move");
        itemMaterial.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                Events.postEvent("onMaterialIssue", WProductionSchedule.this, orderId);
            }
        });
        popup.appendChild(itemMaterial);

        // 4. Update Notice
        org.zkoss.zul.Menuitem itemNotice = new org.zkoss.zul.Menuitem("Notice");
        if (ThemeManager.isUseFontIconForImage())
            itemNotice.setIconSclass("z-icon-Message");
        itemNotice.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                // Fire custom event to Self for handling
                Events.postEvent("onNotice", WProductionSchedule.this, new org.zkoss.json.JSONObject() {
                    {
                        put("id", orderId);
                    }
                });
            }
        });
        popup.appendChild(itemNotice);

        popup.appendChild(new org.zkoss.zul.Menuseparator());
        // ------------------------

        org.zkoss.zul.Menuitem itemRefresh = new org.zkoss.zul.Menuitem("Refresh Board");
        if (ThemeManager.isUseFontIconForImage())
            itemRefresh.setIconSclass("z-icon-Refresh");
        else
            itemRefresh.setImage(ThemeManager.getThemeResource("images/Refresh16.png"));

        itemRefresh.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                refreshTimeline();
            }
        });
        popup.appendChild(itemRefresh);

        this.appendChild(popup);
        popup.open(x, y);
    }
}
