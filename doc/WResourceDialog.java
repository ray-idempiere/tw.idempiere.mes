package tw.idempiere.mes.form;

import org.compiere.util.CLogger;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.event.DialogEvents;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.theme.ThemeManager;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MColumn;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MTable;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.wf.MWFNode;
import org.eevolution.model.X_PP_WF_Node_Asset;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Center;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Separator;
import org.zkoss.zul.Vbox;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;

public class WResourceDialog extends Window implements EventListener<Event>, ValueChangeListener {
    private static final long serialVersionUID = 1L;
    private static CLogger log = CLogger.getCLogger(WResourceDialog.class);

    private MWFNode m_node;
    private Grid m_grid;
    private WSearchEditor m_assetEditor;
    private ConfirmPanel m_confirmPanel;

    public WResourceDialog(MWFNode node) {
        m_node = node;
        init();
    }

    private org.adempiere.webui.component.Label lblLiveInfo;

    private void init() {
        System.out.println("DEBUG: WResourceDialog.init() called");
        setTitle("Assign Tools/Assets to: " + m_node.getName());
        setBorder("normal");
        setWidth("600px");
        setHeight("450px");
        setClosable(true);

        Borderlayout mainLayout = new Borderlayout();
        this.appendChild(mainLayout);

        // Top: Add Asset
        Vbox topBox = new Vbox();
        topBox.setStyle("padding: 10px");

        // Enlarged Resource Name
        Label lblResource = new Label(m_node.getName());
        lblResource.setStyle("font-size: 32px; font-weight: bold; color: #333; margin-bottom: 5px; display: block;");
        topBox.appendChild(lblResource);

        // Live Info Label
        lblLiveInfo = new Label("Ready for Real-time Updates...");
        lblLiveInfo
                .setStyle("font-size: 14px; color: #007bff; font-weight: bold; margin-bottom: 10px; display: block;");
        topBox.appendChild(lblLiveInfo);

        Hbox inputLine = new Hbox();
        inputLine.setAlign("center");
        Label lblAdd = new Label("Add Asset: ");
        lblAdd.setStyle("font-size: 18px;");
        inputLine.appendChild(lblAdd);

        // Search Editor for A_Asset
        int columnID = MColumn.getColumn_ID("A_Asset", "A_Asset_ID");
        MLookup lookup = null;
        try {
            lookup = MLookupFactory.get(Env.getCtx(), 0, columnID, DisplayType.Search,
                    Env.getLanguage(Env.getCtx()), "A_Asset_ID", 0, false, "IsActive='Y'");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to create lookup", e);
        }

        if (lookup != null) {
            m_assetEditor = new WSearchEditor("A_Asset_ID", true, false, true, lookup);
            m_assetEditor.addValueChangeListener(this);
            m_assetEditor.getComponent().setStyle("height: 40px; font-size: 16px;"); // Enlarge editor slightly
            inputLine.appendChild(m_assetEditor.getComponent());
        }

        topBox.appendChild(inputLine);
        topBox.appendChild(new Separator());

        org.zkoss.zul.North north = new org.zkoss.zul.North();
        north.appendChild(topBox);
        mainLayout.appendChild(north);

        // Center: List
        m_grid = GridFactory.newGridLayout();
        Center center = new Center();
        center.setAutoscroll(true);
        center.appendChild(m_grid);
        mainLayout.appendChild(center);

        refreshGrid();

        // South: Scan Button & OK
        org.zkoss.zul.South south = new org.zkoss.zul.South();
        south.setHeight("140px"); // Implement enough height for big buttons

        Vbox southBox = new Vbox();
        southBox.setAlign("center");
        southBox.setPack("center");
        southBox.setHeight("100%");
        southBox.setWidth("100%");

        // Scan Barcode Button
        Button btnScan = new Button("Scan Barcode for Delivered");
        btnScan.setStyle(
                "font-size: 24px; font-weight: bold; height: 60px; width: 400px; margin-bottom: 15px; background: #007bff; color: white;");
        btnScan.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
            public void onEvent(Event event) {
                showPackingDialog();
            }
        });
        southBox.appendChild(btnScan);

        // OK (Close) Button
        m_confirmPanel = new ConfirmPanel(true); // OK only
        Button btnOK = m_confirmPanel.getButton(ConfirmPanel.A_OK);
        btnOK.setLabel("Close"); // Rename to Close as requested (or keep OK but enlarge)
        btnOK.setStyle("font-size: 24px; font-weight: bold; height: 50px; width: 200px;");

        btnOK.addEventListener(Events.ON_CLICK, this);
        m_confirmPanel.getButton(ConfirmPanel.A_CANCEL).setVisible(false);
        southBox.appendChild(m_confirmPanel);

        south.appendChild(southBox);
        mainLayout.appendChild(south);

        this.addEventListener(DialogEvents.ON_WINDOW_CLOSE, this);

        // Enable Server Push for Real-time Updates
        try {
            org.zkoss.zk.ui.Executions.getCurrent().getDesktop().enableServerPush(true);
            System.out.println("DEBUG: Server Push Enabled");
        } catch (Exception ex) {
            System.err.println("DEBUG: Server Push Enable Failed: " + ex.getMessage());
        }

        // Subscribe to Real-time Events
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        System.out.println("=== DEBUG: subscribeToEvents called for dialog: " + this);

        try {
            final org.zkoss.zk.ui.Desktop desktop = org.zkoss.zk.ui.Executions.getCurrent().getDesktop();
            System.out.println("=== DEBUG: Desktop captured: " + desktop + ", ID: " + desktop.getId() + ", isAlive: "
                    + desktop.isAlive());

            org.zkoss.zk.ui.event.EventQueue<Event> queue = org.zkoss.zk.ui.event.EventQueues.lookup(
                    "MesUpdateQueue",
                    org.zkoss.zk.ui.event.EventQueues.APPLICATION,
                    true);

            System.out.println("=== DEBUG: EventQueue retrieved: " + queue);

            queue.subscribe(new EventListener<Event>() {
                public void onEvent(Event event) throws Exception {
                    System.out.println("=== DEBUG: *** EVENT RECEIVED *** Name: " + event.getName() + ", Desktop: "
                            + desktop.getId());

                    if ("MES_UPDATE".equals(event.getName())) {
                        final Object[] data = (Object[]) event.getData();
                        System.out.println("=== DEBUG: Event data: orderId=" + data[0] + ", productId=" + data[1]
                                + ", qty=" + data[2]);

                        // Check desktop state
                        System.out.println("=== DEBUG: Desktop isAlive before schedule: " + desktop.isAlive()
                                + ", isServerPushEnabled: " + desktop.isServerPushEnabled());

                        if (desktop.isAlive()) {
                            System.out.println("=== DEBUG: Scheduling UI update...");

                            try {
                                org.zkoss.zk.ui.Executions.schedule(desktop, new EventListener<Event>() {
                                    public void onEvent(Event evt) throws Exception {
                                        System.out.println("=== DEBUG: *** UI UPDATE EXECUTING *** on Desktop: "
                                                + desktop.getId());

                                        try {
                                            int orderId = (Integer) data[0];
                                            int productId = (Integer) data[1];
                                            int qty = (Integer) data[2];

                                            String productName = DB.getSQLValueString(null,
                                                    "SELECT Name FROM M_Product WHERE M_Product_ID=?", productId);
                                            String docNo = DB.getSQLValueString(null,
                                                    "SELECT DocumentNo FROM PP_Order WHERE PP_Order_ID=?", orderId);
                                            int totalDelivered = DB.getSQLValue(null,
                                                    "SELECT QtyDelivered FROM PP_Order WHERE PP_Order_ID=?", orderId);

                                            System.out.println("=== DEBUG: Fetched data - DocNo: " + docNo + ", Total: "
                                                    + totalDelivered);

                                            // Update Live Info Label
                                            lblLiveInfo.setValue("🔴 LIVE: Order " + docNo + " (" + productName
                                                    + ") Scanned! Total: " + totalDelivered);
                                            lblLiveInfo.setStyle(
                                                    "font-size: 16px; color: #DC3545; font-weight: bold; margin-bottom: 10px; display: block; background: #FFEEEE; padding: 8px; border: 2px solid #DC3545; border-radius: 4px;");

                                            System.out.println("=== DEBUG: Label updated successfully!");

                                            // Visual confirmation popup
                                            org.zkoss.zk.ui.util.Clients.showNotification(
                                                    "🔴 Remote Scan Detected! Order " + docNo + " Total: "
                                                            + totalDelivered,
                                                    "info",
                                                    null,
                                                    "top_right",
                                                    3000);

                                        } catch (Exception e) {
                                            System.err.println("=== DEBUG: EXCEPTION in UI update:");
                                            e.printStackTrace();
                                        }
                                    }
                                }, new Event("updateUI"));

                                System.out.println("=== DEBUG: Schedule call completed");

                            } catch (Exception schedEx) {
                                System.err.println("=== DEBUG: EXCEPTION during schedule:");
                                schedEx.printStackTrace();
                            }

                        } else {
                            System.out.println("=== DEBUG: *** DESKTOP IS DEAD - SKIPPING UPDATE ***");
                        }
                    }
                }
            });

            System.out.println("=== DEBUG: Subscription completed successfully");

        } catch (Exception e) {
            System.err.println("=== DEBUG: EXCEPTION in subscribeToEvents:");
            e.printStackTrace();
        }
    }

    private void refreshGrid() {
        Rows rows = m_grid.getRows();
        if (rows == null) {
            rows = new Rows();
            m_grid.appendChild(rows);
        }
        rows.getChildren().clear();

        // Header
        Row header = new Row();
        header.setStyle("background-color: #F0F0F0; font-weight: bold;");
        header.appendChild(new Label("Asset / Tool"));
        header.appendChild(new Label("Action"));
        rows.appendChild(header);

        // Load Data
        String sql = "SELECT p.PP_WF_Node_Asset_ID, a.Name " +
                "FROM PP_WF_Node_Asset p " +
                "INNER JOIN A_Asset a ON (p.A_Asset_ID = a.A_Asset_ID) " +
                "WHERE p.AD_WF_Node_ID = ? AND p.IsActive='Y'";

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, m_node.getAD_WF_Node_ID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                final int id = rs.getInt(1);
                String name = rs.getString(2);

                Row row = new Row();
                row.appendChild(new Label(name));

                Button delBtn = new Button();
                if (ThemeManager.isUseFontIconForImage())
                    delBtn.setIconSclass("z-icon-Delete");
                else
                    delBtn.setImage(ThemeManager.getThemeResource("images/Delete16.png"));

                delBtn.setTooltiptext("Delete");
                delBtn.addEventListener(Events.ON_CLICK, new EventListener<Event>() {
                    public void onEvent(Event event) {
                        deleteAsset(id);
                    }
                });
                row.appendChild(delBtn);

                rows.appendChild(row);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, sql, e);
        } finally {
            DB.close(rs, pstmt);
        }
    }

    private void deleteAsset(int pp_WF_Node_Asset_ID) {
        X_PP_WF_Node_Asset record = new X_PP_WF_Node_Asset(Env.getCtx(), pp_WF_Node_Asset_ID, null);
        if (record.delete(true)) {
            refreshGrid();
        } else {
            // Show error?
        }
    }

    public void show() {
        this.doHighlighted();
    }

    @Override
    public void valueChange(ValueChangeEvent evt) {
        if (evt.getPropertyName().equals("A_Asset_ID")) {
            Object value = evt.getNewValue();
            if (value != null && value instanceof Integer) {
                int assetId = (Integer) value;
                createNodeAsset(assetId);
                m_assetEditor.setValue(null); // Clear
                refreshGrid();
            }
        }
    }

    private void createNodeAsset(int assetId) {
        X_PP_WF_Node_Asset record = new X_PP_WF_Node_Asset(Env.getCtx(), 0, null);
        record.setAD_WF_Node_ID(m_node.getAD_WF_Node_ID());
        record.setA_Asset_ID(assetId);
        record.saveEx();
    }

    private void showPackingDialog() {
        System.out.println("DEBUG: showPackingDialog called");
        final org.zkoss.zul.Window win = new org.zkoss.zul.Window();
        win.setTitle("Packing Scanner (Node: " + m_node.getName() + ")");
        win.setWidth("400px");
        win.setHeight("250px");
        win.setBorder("normal");
        win.setClosable(true);
        win.setPage(this.getPage());

        Vbox vbox = new Vbox();
        vbox.setHflex("1");
        vbox.setVflex("1");
        vbox.setAlign("center");
        vbox.setPack("center");
        win.appendChild(vbox);

        Label lblInstr = new Label("Scan Product Barcode:");
        lblInstr.setStyle("font-weight: bold; font-size: 16px; margin-bottom: 10px;");
        vbox.appendChild(lblInstr);

        final org.zkoss.zul.Textbox txtBarcode = new org.zkoss.zul.Textbox();
        txtBarcode.setPlaceholder("Scan Here");
        txtBarcode.setStyle("height: 40px; font-size: 18px; width: 80%;");
        txtBarcode.setFocus(true);
        vbox.appendChild(txtBarcode);

        txtBarcode.addEventListener(Events.ON_OK, new EventListener<Event>() {
            public void onEvent(Event event) throws Exception {
                System.out.println("DEBUG: ON_OK fired in Textbox");
                String barcode = txtBarcode.getValue().trim();
                txtBarcode.setValue("");
                if (barcode.isEmpty())
                    return;

                // Process Scan
                processScan(barcode);
                txtBarcode.setFocus(true);
            }
        });

        win.doModal();
    }

    private void processScan(String barcode) {
        System.out.println("DEBUG: processScan called for barcode: " + barcode);
        // 1. Find Product
        int M_Product_ID = DB.getSQLValue(null,
                "SELECT M_Product_ID FROM M_Product WHERE Value=? AND IsActive='Y' AND AD_Client_ID=?", barcode,
                Env.getAD_Client_ID(Env.getCtx()));
        if (M_Product_ID <= 0) {
            System.out.println("DEBUG: Product not found");
            org.zkoss.zk.ui.util.Clients.showNotification("Product Not Found", "error", null, "middle_center", 1000);
            return;
        }

        // 2. Find Active Order for this Product AND this Node's Resource
        int AD_WF_Node_ID = m_node.getAD_WF_Node_ID();
        int S_Resource_ID = DB.getSQLValue(null, "SELECT S_Resource_ID FROM AD_WF_Node WHERE AD_WF_Node_ID=?",
                AD_WF_Node_ID);

        // If Node has no resource, maybe try global match? But sticking to context is
        // safer.
        String where = "M_Product_ID=? AND DocStatus IN ('IP','DR')"; // In Progress or Draft
        Object[] params;
        if (S_Resource_ID > 0) {
            where += " AND S_Resource_ID=?";
            params = new Object[] { M_Product_ID, S_Resource_ID };
        } else {
            params = new Object[] { M_Product_ID };
        }

        int PP_Order_ID = DB.getSQLValue(null,
                "SELECT PP_Order_ID FROM PP_Order WHERE " + where + " ORDER BY DateOrdered DESC", params);

        if (PP_Order_ID <= 0) {
            System.out.println("DEBUG: No ACTIVE Order found");
            org.zkoss.zk.ui.util.Clients.showNotification("No Active Order Found for this Product & Station", "warning",
                    null, "middle_center", 2000);
            return;
        }

        // 3. Update DB
        int no = DB.executeUpdate("UPDATE PP_Order SET QtyDelivered = QtyDelivered + 1 WHERE PP_Order_ID=?",
                PP_Order_ID, null);

        if (no > 0) {
            org.zkoss.zk.ui.util.Clients.showNotification("Scanned! (+1)", "info", null, "middle_center", 500);

            // 4. Publish Event to all listeners
            System.out.println(
                    "=== DEBUG: *** PUBLISHING EVENT *** OrderID: " + PP_Order_ID + ", ProductID: " + M_Product_ID);

            try {
                org.zkoss.zk.ui.event.EventQueue<Event> queue = org.zkoss.zk.ui.event.EventQueues
                        .lookup("MesUpdateQueue", org.zkoss.zk.ui.event.EventQueues.APPLICATION, true);

                System.out.println("=== DEBUG: Queue retrieved for publish: " + queue);

                Event mesEvent = new Event("MES_UPDATE", null, new Object[] { PP_Order_ID, M_Product_ID, 1 });
                queue.publish(mesEvent);

                System.out.println("=== DEBUG: *** EVENT PUBLISHED SUCCESSFULLY ***");

            } catch (Exception pubEx) {
                System.err.println("=== DEBUG: EXCEPTION during publish:");
                pubEx.printStackTrace();
            }

        } else {
            org.zkoss.zk.ui.util.Clients.showNotification("Update Failed", "error", null, "middle_center", 2000);
        }
    }

    @Override
    public void onEvent(Event event) throws Exception {
        if (event.getTarget() == m_confirmPanel.getButton(ConfirmPanel.A_OK)) {
            this.onClose();
        } else if (event.getName().equals(DialogEvents.ON_WINDOW_CLOSE)) {
            // Clean up if needed
        }
    }
}
