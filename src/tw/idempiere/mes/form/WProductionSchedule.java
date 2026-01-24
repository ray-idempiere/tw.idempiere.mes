package tw.idempiere.mes.form;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.window.FDialog;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.component.Button;
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
import org.compiere.model.MColumn;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MOrderLine;
import org.compiere.model.MResource;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.compiere.model.MTable;
import org.compiere.model.MQuery;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.Div;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.North;
import org.zkoss.zul.Script;
import org.zkoss.zul.Separator;
import org.zkoss.zul.Style;
import org.zkoss.zul.Space;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listhead;
import org.zkoss.zul.Listheader;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Vbox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Image;
import org.zkoss.image.AImage;
import org.compiere.model.MAttachment;
import org.compiere.model.MAttachmentEntry;
import org.compiere.model.MProduct;

import com.google.gson.Gson;

import tw.idempiere.mes.service.MESService;

/**
 * Production Schedule Form with Timeline
 */
public class WProductionSchedule extends ADForm implements IFormController, EventListener<Event> {

    private static final long serialVersionUID = 1L;
    private static CLogger log = CLogger.getCLogger(WProductionSchedule.class);

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

    protected void initForm() {
        ZKUpdateUtil.setHeight(this, "100%");
        Borderlayout layout = new Borderlayout();
        this.appendChild(layout);

        // Toolbar
        North north = new North();
        layout.appendChild(north);
        Hbox toolbar = new Hbox();
        north.appendChild(toolbar);
        toolbar.setStyle("padding: 5px;");

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
        this.addEventListener("onMaterialIssue", this);

        // Inject Dependencies
        injectDependencies();
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
            + "            if (confirm('Confirm reschedule ' + item.content + '?')) { "
            + "                var wgt = zk.Widget.$('#' + listenerUuid); "
            + "                if (wgt) { "
            + "                    wgt.fire('onOrderMove', { "
            + "                        id: item.id, "
            + "                        start: item.start.toISOString(), "
            + "                        end: item.end.toISOString(), "
            + "                        group: item.group "
            + "                    }, {toServer: true}); "
            + "                    callback(item); "
            + "                } else { "
            + "                    console.error('Widget not found: ' + listenerUuid); "
            + "                    callback(null); "
            + "                } "
            + "            } else { "
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
            Clients.evalJavaScript("if(window.currentVisTimeline) { " +
                    "var t = new Date(); " +
                    "var e = new Date(); e.setMonth(t.getMonth() + 1); " +
                    "window.currentVisTimeline.setWindow(t, e); " +
                    "}");
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
            updateOrder(id, resourceId, startStr, endStr);
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
            updateOrderStage(orderId, "Material Issue");
            Clients.showNotification("Material Issue Requested", "info", null, "middle_center", 2000);
        }
    }

    private void handleItemSelect(int orderId) {
        String desc = DB.getSQLValueString(null, "SELECT Description FROM PP_Order WHERE PP_Order_ID=?", orderId);
        if (desc != null && desc.contains("Packing")) {
            showPackingDialog(orderId);
        }
    }

    private void showPackingDialog(final int orderId) {
        final org.zkoss.zul.Window win = new org.zkoss.zul.Window();
        win.setTitle("Packing Station Scanner");
        win.setWidth("400px");
        win.setHeight("300px");
        win.setBorder("normal");
        win.setClosable(true);
        win.setPage(this.getPage());

        org.zkoss.zul.Vbox vbox = new org.zkoss.zul.Vbox();
        vbox.setHflex("1");
        vbox.setVflex("1");
        vbox.setAlign("center");
        vbox.setPack("center");
        win.appendChild(vbox);

        // Fetch Data
        String sql = "SELECT p.Value, p.Name, o.QtyOrdered, o.QtyDelivered, o.DocumentNo " +
                "FROM PP_Order o INNER JOIN M_Product p ON o.M_Product_ID=p.M_Product_ID " +
                "WHERE o.PP_Order_ID=?";
        java.sql.PreparedStatement pstmt = null;
        java.sql.ResultSet rs = null;
        String productValue = "";
        String productName = "";
        String docNo = "";
        java.math.BigDecimal qtyOrdered = java.math.BigDecimal.ZERO;
        final java.math.BigDecimal[] qtyDelivered = { java.math.BigDecimal.ZERO }; // Array for final modification

        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, orderId);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                productValue = rs.getString(1);
                productName = rs.getString(2);
                qtyOrdered = rs.getBigDecimal(3);
                qtyDelivered[0] = rs.getBigDecimal(4);
                docNo = rs.getString(5);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DB.close(rs, pstmt);
        }

        // UI Components
        vbox.appendChild(new org.zkoss.zul.Label("Order: " + docNo));
        vbox.appendChild(new org.zkoss.zul.Separator());
        vbox.appendChild(new org.zkoss.zul.Label(productName));
        vbox.appendChild(new org.zkoss.zul.Label("Barcode: " + productValue));
        vbox.appendChild(new org.zkoss.zul.Separator());

        final org.zkoss.zul.Label lblQty = new org.zkoss.zul.Label();
        final java.math.BigDecimal fQtyOrdered = qtyOrdered;
        // Function to update label
        Runnable updateLabel = new Runnable() {
            public void run() {
                lblQty.setValue(qtyDelivered[0].intValue() + " / " + fQtyOrdered.intValue());
            }
        };
        updateLabel.run();
        lblQty.setStyle("font-size: 24px; font-weight: bold;");
        vbox.appendChild(lblQty);

        vbox.appendChild(new org.zkoss.zul.Separator());
        vbox.appendChild(new org.zkoss.zul.Label("Scan Barcode (Simulate):"));

        final org.zkoss.zul.Textbox txtScan = new org.zkoss.zul.Textbox();
        txtScan.setHflex("1");
        txtScan.setPlaceholder("Enter Product Value");
        // Focus logic
        txtScan.setFocus(true);

        final String fProductValue = productValue;
        txtScan.addEventListener(Events.ON_OK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                String input = txtScan.getValue().trim();
                txtScan.setValue(""); // Clear immediately

                if (input.equalsIgnoreCase(fProductValue)) {
                    // Valid Scan
                    // Update DB - Increment QtyDelivered
                    int count = DB.executeUpdate(
                            "UPDATE PP_Order SET QtyDelivered = COALESCE(QtyDelivered,0) + 1 WHERE PP_Order_ID=?",
                            orderId, null);
                    if (count > 0) {
                        qtyDelivered[0] = qtyDelivered[0].add(java.math.BigDecimal.ONE);
                        updateLabel.run();
                        Clients.showNotification("Scanned!", "info", null, "middle_center", 500);
                        refreshTimeline(); // Reflect on main screen
                    }
                } else {
                    Clients.showNotification("Wrong Barcode!", "error", null, "middle_center", 1000);
                    // Play sound?
                }
                txtScan.setFocus(true);
            }
        });
        vbox.appendChild(txtScan);

        win.doModal();
    }

    private void updateOrderStage(int orderId, String stage) {
        // Update Description to include Stage Info
        // Clean existing stage info if any "Stage: ..."
        String currentDesc = DB.getSQLValueString(null, "SELECT Description FROM PP_Order WHERE PP_Order_ID=?",
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

        DB.executeUpdate("UPDATE PP_Order SET Description=? WHERE PP_Order_ID=?", new Object[] { newDesc, orderId },
                false, null);
        refreshTimeline();
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
        String groupJson = getGroupsJSON();
        String itemJson = getItemsJSON();

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
        options.append(" 'stack': false,");
        options.append(" 'zoomMin': ").append(1000 * 60 * 60 * 24).append(",");
        options.append(" 'timeAxis': { 'scale': 'day', 'step': 1 },");
        options.append(" 'format': { 'minorLabels': { 'day': 'D' }, 'majorLabels': { 'day': 'ddd D MMMM' } },");

        // Dates logic
        if (chkTVMode != null && chkTVMode.isChecked()) {
            // TV Mode: 1 Week range
            options.append(" 'start': new Date(),");
            options.append(" 'end': (function(){ var d = new Date(); d.setDate(d.getDate()+7); return d; })(),");
        } else {
            // Normal Mode: 1 Month range
            options.append(" 'start': new Date(),");
            options.append(" 'end': (function(){ var d = new Date(); d.setMonth(d.getMonth()+1); return d; })(),");
        }

        options.append(" 'hiddenDates': [");
        options.append("   { 'start': '2026-01-01 18:00:00', 'end': '2026-01-02 08:00:00', 'repeat': 'daily' },");
        options.append("   { 'start': '2026-01-25 00:00:00', 'end': '2026-01-26 00:00:00', 'repeat': 'weekly' }");
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

    private void showResourceDialog(final int resourceId) {
        String resName = DB.getSQLValueString(null, "SELECT Name FROM S_Resource WHERE S_Resource_ID=?", resourceId);

        final org.zkoss.zul.Window win = new org.zkoss.zul.Window();
        win.setTitle("Daily KPI: " + resName);
        win.setWidth("100%");
        win.setHeight("100%");
        win.setBorder("normal");
        win.setClosable(true);
        win.setPage(this.getPage());

        org.zkoss.zul.Vbox vbox = new org.zkoss.zul.Vbox();
        vbox.setHflex("1");
        vbox.setVflex("1");
        vbox.setStyle("padding: 20px;");
        win.appendChild(vbox);

        // Header Info
        org.zkoss.zul.Hbox header = new org.zkoss.zul.Hbox();
        header.setAlign("center");
        header.setPack("center");
        header.setWidth("100%");
        vbox.appendChild(header);

        org.zkoss.zul.Label lblTitle = new org.zkoss.zul.Label("Daily Targets for: ");
        lblTitle.setStyle("font-size: 24px; font-weight: bold;");
        header.appendChild(lblTitle);

        // Date Selector
        final org.zkoss.zul.Datebox dateBox = new org.zkoss.zul.Datebox();
        dateBox.setValue(new java.util.Date());
        dateBox.setFormat("yyyy-MM-dd");
        dateBox.setStyle("font-size: 18px; margin-left: 10px;");
        header.appendChild(dateBox);

        vbox.appendChild(new org.zkoss.zul.Separator());

        // Container for Box Flow
        final org.zkoss.zul.Div cardContainer = new org.zkoss.zul.Div();
        cardContainer.setStyle("display: flex; flex-wrap: wrap; gap: 20px; justify-content: center; padding: 10px;");
        cardContainer.setWidth("100%");
        vbox.appendChild(cardContainer);

        // Function to refresh list
        final Runnable refreshList = new Runnable() {
            public void run() {
                cardContainer.getChildren().clear();
                java.util.Date selDate = dateBox.getValue();
                Timestamp ts = (selDate != null) ? new Timestamp(selDate.getTime()) : null;

                java.util.List<MESService.TimelineItem> tasks = service.getResourceStats(resourceId, ts);

                // Set container style for full height vertical layout
                String containerStyle = "display: flex; flex-direction: column; height: 100%; width: 100%; padding: 20px; box-sizing: border-box; overflow: auto;";
                if (tasks.size() == 1) {
                    containerStyle += " justify-content: center; align-items: center;";
                }
                cardContainer.setStyle(containerStyle);

                for (MESService.TimelineItem task : tasks) {
                    // Card Box - Full Width, Flex Grow
                    org.zkoss.zul.Div card = new org.zkoss.zul.Div();
                    String borderStyle = ("Completed".equals(task.className)
                            || "status-completed".equals(task.className))
                                    ? "border: 4px solid #28a745; background: #e6ffed;"
                                    : "border: 2px solid #ccc; background: #fff;";

                    card.setStyle(borderStyle
                            + "border-radius: 12px; padding: 25px; flex: 1; margin-bottom: 20px; box-shadow: 4px 4px 10px rgba(0,0,0,0.2); display: flex; align-items: center; width: 100%; box-sizing: border-box; max-height: 48%; min-height: 300px;");

                    // 1. Column Left: Image (33%)
                    org.zkoss.zul.Div colLeft = new org.zkoss.zul.Div();
                    colLeft.setStyle(
                            "width: 33%; height: 100%; display: flex; align-items: center; justify-content: center;");
                    card.appendChild(colLeft);

                    org.zkoss.zul.Div imgDiv = new org.zkoss.zul.Div();
                    imgDiv.setStyle(
                            "width: 250px; height: 250px; display: flex; align-items: center; justify-content: center; background-color: #f0f0f0; border-radius: 12px; overflow: hidden; box-shadow: inset 0 0 5px rgba(0,0,0,0.1);");
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

                    // 2. Column Middle: Info (33%)
                    org.zkoss.zul.Vbox colMid = new org.zkoss.zul.Vbox();
                    colMid.setStyle(
                            "width: 33%; height: 100%; padding: 0 20px; display: flex; flex-direction: column; justify-content: center;");
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

                    // 3. Column Right: Stats & Rate (33%)
                    org.zkoss.zul.Vbox colRight = new org.zkoss.zul.Vbox();
                    colRight.setStyle(
                            "width: 34%; height: 100%; padding: 0 20px; display: flex; flex-direction: column; justify-content: center; align-items: flex-end;");
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

                    cardContainer.appendChild(card);
                }
            }
        };

        // Initial Load
        refreshList.run();

        // Listen for changes
        dateBox.addEventListener(Events.ON_CHANGE, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                refreshList.run();
            }
        });

        win.doModal();
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
            int oldResourceId = DB.getSQLValue(null, "SELECT S_Resource_ID FROM PP_Order WHERE PP_Order_ID=?", id);

            if (oldResourceId != resourceId && oldResourceId > 0) {
                String oldName = DB.getSQLValueString(null, "SELECT Name FROM S_Resource WHERE S_Resource_ID=?",
                        oldResourceId);
                String newName = DB.getSQLValueString(null, "SELECT Name FROM S_Resource WHERE S_Resource_ID=?",
                        resourceId);
                msg = "Moved from " + oldName + " to " + newName;
                // Add explicit time info if desired, but user asked for Resource Name
            }

            // 4. Update
            String sql = "UPDATE PP_Order SET S_Resource_ID=?, DateStartSchedule=?, DateFinishSchedule=? WHERE PP_Order_ID=?";
            int no = DB.executeUpdate(sql, new Object[] { resourceId, startTime, endTime, id }, false, null);

            if (no > 0) {
                Clients.showNotification(msg, "info", null, "middle_center", 3000);
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

    private String getGroupsJSON() {
        return mesService.getGroupsJSON(Env.getAD_Client_ID(Env.getCtx()));
    }

    private String getItemsJSON() {
        return mesService.getItemsJSON();
    }

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
        Trx trx = Trx.get(Trx.createTrxName("GenPP"), true);
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

            int Table_ID = DB.getSQLValue(trx.getTrxName(),
                    "SELECT AD_Table_ID FROM AD_Table WHERE TableName='PP_Order'");
            if (Table_ID <= 0)
                throw new AdempiereException("PP_Order Table not found");

            org.compiere.model.MTable table = org.compiere.model.MTable.get(Env.getCtx(), Table_ID);

            // Find DocType
            int C_DocType_ID = DB.getSQLValue(trx.getTrxName(),
                    "SELECT C_DocType_ID FROM C_DocType WHERE DocBaseType='MO' AND IsActive='Y' ORDER BY IsDefault DESC");
            if (C_DocType_ID <= 0) {
                C_DocType_ID = DB.getSQLValue(trx.getTrxName(),
                        "SELECT C_DocType_ID FROM C_DocType WHERE IsActive='Y' AND Name LIKE '%Production%' AND AD_Client_ID=?",
                        Env.getAD_Client_ID(Env.getCtx()));
            }

            int AD_Workflow_ID = DB.getSQLValue(trx.getTrxName(),
                    "SELECT AD_Workflow_ID FROM AD_Workflow WHERE IsActive='Y' AND AD_Client_ID=? AND Value LIKE '%Process_Production%' ORDER BY IsDefault DESC",
                    Env.getAD_Client_ID(Env.getCtx()));
            if (AD_Workflow_ID <= 0) {
                AD_Workflow_ID = DB.getSQLValue(trx.getTrxName(),
                        "SELECT AD_Workflow_ID FROM AD_Workflow WHERE IsActive='Y' AND AD_Client_ID=? ORDER BY Created DESC",
                        Env.getAD_Client_ID(Env.getCtx()));
            }

            int PP_Product_BOM_ID = DB.getSQLValue(trx.getTrxName(),
                    "SELECT PP_Product_BOM_ID FROM PP_Product_BOM WHERE M_Product_ID=? AND IsActive='Y' ORDER BY Created DESC",
                    oLine.getM_Product_ID());

            // If no BOM found, handled by not setting or handle specific logic.
            // Constraint says not null, so if 0, we have an issue.
            if (PP_Product_BOM_ID <= 0) {
                // Try generic or create dummy? For now let's hope data exists.
                // Actually we can try to look for ANY BOM if specific product bom not exists?
                // NO.
                // Just warning if 0.
            }

            int lineNo = 10;
            while (remaining.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal daysProduction = dailyCapacity;
                if (remaining.compareTo(dailyCapacity) < 0) {
                    daysProduction = remaining;
                }

                PO ppOrder = table.getPO(0, trx.getTrxName());

                ppOrder.set_ValueNoCheck("AD_Org_ID", oLine.getAD_Org_ID());
                ppOrder.set_ValueNoCheck("Line", lineNo);
                lineNo += 10;
                ppOrder.set_ValueNoCheck("C_OrderLine_ID", C_OrderLine_ID);

                if (AD_Workflow_ID > 0)
                    ppOrder.set_ValueNoCheck("AD_Workflow_ID", AD_Workflow_ID);
                if (PP_Product_BOM_ID > 0)
                    ppOrder.set_ValueNoCheck("PP_Product_BOM_ID", PP_Product_BOM_ID);

                ppOrder.set_ValueNoCheck("PriorityRule", "5"); // Medium
                ppOrder.set_ValueNoCheck("S_Resource_ID", S_Resource_ID);
                ppOrder.set_ValueNoCheck("M_Product_ID", oLine.getM_Product_ID());
                ppOrder.set_ValueNoCheck("M_Warehouse_ID", oLine.getM_Warehouse_ID());
                ppOrder.set_ValueNoCheck("C_UOM_ID", oLine.getC_UOM_ID());
                ppOrder.set_ValueNoCheck("QtyOrdered", daysProduction);
                ppOrder.set_ValueNoCheck("DateOrdered", new Timestamp(System.currentTimeMillis()));
                ppOrder.set_ValueNoCheck("DateStartSchedule", new Timestamp(cal.getTimeInMillis()));

                Calendar endCal = (Calendar) cal.clone();
                endCal.set(Calendar.HOUR_OF_DAY, 17); // 5 PM
                ppOrder.set_ValueNoCheck("DateFinishSchedule", new Timestamp(endCal.getTimeInMillis()));
                ppOrder.set_ValueNoCheck("DatePromised", new Timestamp(endCal.getTimeInMillis()));

                ppOrder.set_ValueNoCheck("DocStatus", "DR");
                if (C_DocType_ID > 0)
                    ppOrder.set_ValueNoCheck("C_DocType_ID", C_DocType_ID);

                ppOrder.saveEx();

                remaining = remaining.subtract(daysProduction);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }

            trx.commit();
            win.detach();
            refreshTimeline();
            FDialog.info(0, win, "Generated Orders");

        } catch (Exception e) {
            trx.rollback();
            log.log(Level.SEVERE, "Gen Error", e);
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
                // Try finding Window by Name
                int AD_Window_ID = DB.getSQLValue(null,
                        "SELECT AD_Window_ID FROM AD_Window WHERE Name='Manufacturing Order' AND IsActive='Y'");

                if (AD_Window_ID <= 0) {
                    // Fallback: Try to find ANY window for PP_Order table
                    int AD_Table_ID = MTable.getTable_ID("PP_Order");
                    AD_Window_ID = DB.getSQLValue(null,
                            "SELECT AD_Window_ID FROM AD_Tab WHERE AD_Table_ID=? AND IsActive='Y' ORDER BY IsSortTab",
                            AD_Table_ID);
                }

                if (AD_Window_ID > 0) {
                    MQuery query = new MQuery("PP_Order");
                    query.addRestriction("PP_Order_ID", MQuery.EQUAL, orderId);
                    AEnv.zoom(AD_Window_ID, query);
                } else {
                    FDialog.warn(0, "Window Not Found", "Could not find Window for Manufacturing Order (PP_Order)");
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
