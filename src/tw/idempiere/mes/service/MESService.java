package tw.idempiere.mes.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;

import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;

import com.google.gson.Gson;

public class MESService {

    private static CLogger log = CLogger.getCLogger(MESService.class);

    // Inner classes for JSON structure
    public static class TimelineGroup {
        int id;
        String content;
        String value;

        public TimelineGroup(int id, String content) {
            this.id = id;
            this.content = content;
            this.value = content;
        }
    }

    public static class TimelineItem {
        public int id;
        public int group;
        public String content;
        public String start;
        public String end;
        public String title;
        public String className;

        // Detailed Info
        public String documentNo;
        public String productName;
        public String productValue;
        public BigDecimal qtyOrdered;
        public BigDecimal qtyDelivered;
        public int productId;
        public String description;
        public String notice;

        public TimelineItem(int id, int group, String content, Timestamp start, Timestamp end, String title,
                String className, String documentNo, String productName, String productValue, BigDecimal qtyOrdered,
                BigDecimal qtyDelivered, int productId, String description) {
            this.id = id;
            this.group = group;
            this.content = content;
            this.start = start.toString();
            this.end = end.toString();
            this.title = title;
            this.className = className;
            this.documentNo = documentNo;
            this.productName = productName;
            this.productValue = productValue;
            this.qtyOrdered = qtyOrdered;
            this.qtyDelivered = qtyDelivered;
            this.productId = productId;
            this.description = description;

            // Parse Notice: "Notice: ..."
            if (description != null && description.contains("Notice: ")) {
                int startIndex = description.indexOf("Notice: ") + 8; // Length of "Notice: "
                int endIndex = description.indexOf("\n", startIndex);
                if (endIndex == -1) {
                    endIndex = description.length();
                }
                this.notice = description.substring(startIndex, endIndex).trim();
            } else {
                this.notice = null;
            }
        }
    }

    public static class KPIStats {
        public int total = 0;
        public int completed = 0;
        public int late = 0;
    }

    /**
     * Get Resources as JSON for Vis.js
     */
    public String getGroupsJSON(int AD_Client_ID) {
        List<TimelineGroup> groups = new ArrayList<>();
        String sql = "SELECT S_Resource_ID, Name, Value FROM S_Resource WHERE IsActive='Y' AND IsManufacturingResource='Y' AND AD_Client_ID=?";
        // Fallback safety: Check if column exists
        String sqlCheck = "SELECT count(*) FROM AD_Column c INNER JOIN AD_Table t ON (c.AD_Table_ID=t.AD_Table_ID) WHERE t.TableName='S_Resource' AND c.ColumnName='IsManufacturingResource'";
        if (DB.getSQLValue(null, sqlCheck) <= 0) {
            sql = "SELECT S_Resource_ID, Name, Value FROM S_Resource WHERE IsActive='Y' AND AD_Client_ID=?";
        }

        PreparedStatement pstmt = null;
        ResultSet rs = null;

        // 1. Load Resources
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, AD_Client_ID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groups.add(new TimelineGroup(rs.getInt("S_Resource_ID"), rs.getString("Name")));
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to load resources", e);
        } finally {
            DB.close(rs, pstmt);
        }

        // 2. Load Product Aggregation
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        Timestamp dateFrom = new Timestamp(cal.getTimeInMillis());
        cal.add(Calendar.MONTH, 4);
        Timestamp dateTo = new Timestamp(cal.getTimeInMillis());

        String sqlProd = "SELECT DISTINCT o.S_Resource_ID, p.Value " +
                "FROM M_Production o " +
                "INNER JOIN M_Product p ON o.M_Product_ID = p.M_Product_ID " +
                "WHERE o.IsActive='Y' " +
                "AND o.MovementDate >= ? AND o.MovementDate <= ? " +
                "ORDER BY o.S_Resource_ID, p.Value";

        PreparedStatement pstmtProd = null;
        ResultSet rsProd = null;
        try {
            pstmtProd = DB.prepareStatement(sqlProd, null);
            pstmtProd.setTimestamp(1, dateFrom);
            pstmtProd.setTimestamp(2, dateTo);
            rsProd = pstmtProd.executeQuery();

            java.util.Map<Integer, java.util.Set<String>> resMap = new java.util.HashMap<>();
            while (rsProd.next()) {
                int resId = rsProd.getInt("S_Resource_ID");
                String val = rsProd.getString("Value");
                resMap.computeIfAbsent(resId, k -> new java.util.LinkedHashSet<>()).add(val);
            }

            for (TimelineGroup group : groups) {
                java.util.Set<String> prods = resMap.get(group.id);
                if (prods != null && !prods.isEmpty()) {
                    StringBuilder sb = new StringBuilder(group.content);
                    sb.append("<div style='font-size:0.75em; color:#666; line-height:1.1; margin-top:4px;'>");
                    for (String p : prods) {
                        sb.append(p).append("<br/>");
                    }
                    sb.append("</div>");
                    group.content = sb.toString();
                }
            }

        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to load resource product summary", e);
        } finally {
            DB.close(rsProd, pstmtProd);
        }

        return new Gson().toJson(groups);
    }

    /**
     * Get Orders as JSON for Vis.js
     */
    public String getItemsJSON() {
        List<TimelineItem> items = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        Timestamp dateFrom = new Timestamp(cal.getTimeInMillis());

        cal.add(Calendar.MONTH, 4);
        Timestamp dateTo = new Timestamp(cal.getTimeInMillis());

        String sql = "SELECT o.M_Production_ID, o.DocumentNo, o.MovementDate, o.S_Resource_ID, "
                + "o.DocStatus, o.ProductionQty, o.QtyDelivered, o.Description, p.Value as ProductValue, p.Name as ProductName, p.M_Product_ID "
                +
                "FROM M_Production o " +
                "LEFT JOIN M_Product p ON o.M_Product_ID = p.M_Product_ID " +
                "WHERE o.IsActive='Y' " +
                "AND o.MovementDate >= ? AND o.MovementDate <= ?";

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setTimestamp(1, dateFrom);
            pstmt.setTimestamp(2, dateTo);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("M_Production_ID");
                int resId = rs.getInt("S_Resource_ID");
                String docNo = rs.getString("DocumentNo");
                Timestamp movementDate = rs.getTimestamp("MovementDate");
                String docStatus = rs.getString("DocStatus");
                String desc = rs.getString("Description"); // Fetch Description for Stage
                if (desc == null)
                    desc = "";

                BigDecimal qty = rs.getBigDecimal("ProductionQty");
                BigDecimal qtyDelivered = rs.getBigDecimal("QtyDelivered");
                if (qtyDelivered == null)
                    qtyDelivered = BigDecimal.ZERO;

                String prodValue = rs.getString("ProductValue");
                if (prodValue == null)
                    prodValue = "";
                String prodName = rs.getString("ProductName");
                int prodId = rs.getInt("M_Product_ID");

                // Calculate Timeline dates from MovementDate
                // DateFrom = MovementDate + 08:00
                // DateTo = MovementDate + 17:00
                Calendar calStart = Calendar.getInstance();
                calStart.setTime(movementDate);
                calStart.set(Calendar.HOUR_OF_DAY, 8);
                calStart.set(Calendar.MINUTE, 0);
                calStart.set(Calendar.SECOND, 0);
                calStart.set(Calendar.MILLISECOND, 0);
                Timestamp start = new Timestamp(calStart.getTimeInMillis());

                Calendar calEnd = Calendar.getInstance();
                calEnd.setTime(movementDate);
                calEnd.set(Calendar.HOUR_OF_DAY, 17);
                calEnd.set(Calendar.MINUTE, 0);
                calEnd.set(Calendar.SECOND, 0);
                calEnd.set(Calendar.MILLISECOND, 0);
                Timestamp end = new Timestamp(calEnd.getTimeInMillis());

                // Get stage icon from centralized config
                String stageName = extractStageName(desc);
                tw.idempiere.mes.model.StageConfig stageConfig = tw.idempiere.mes.model.StageConfig.fromName(stageName);
                String icon = stageConfig.getIcon() + " ";

                String content = icon + prodValue + " (" + qtyDelivered.intValue() + "/" + qty.intValue() + ")";

                StringBuilder title = new StringBuilder();
                title.append("Order: ").append(docNo).append("\n");
                title.append("Product: ").append(prodName).append("\n");
                title.append("Qty: ").append(qty).append("\n");
                title.append("Status: ").append(docStatus);

                String className = "status-draft";
                if ("CO".equals(docStatus) || "CL".equals(docStatus)) {
                    className = "status-completed";
                } else if ("IP".equals(docStatus)) {
                    className = "status-inprogress";
                } else if ("DR".equals(docStatus) || "NA".equals(docStatus)) {
                    className = "status-draft";
                } else {
                    className = "status-error";
                }

                if (start != null && end != null) {
                    items.add(new TimelineItem(id, resId, content, start, end, title.toString(), className, docNo,
                            prodName, prodValue, qty, qtyDelivered, prodId, desc));
                }
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to load productions: " + e.getMessage());
        } finally {
            DB.close(rs, pstmt);
        }
        return new Gson().toJson(items);
    }

    public KPIStats getKPIStats(String period) {
        KPIStats stats = new KPIStats();

        Calendar cal = Calendar.getInstance();
        // Reset time to start of day
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Timestamp dateFrom = new Timestamp(cal.getTimeInMillis());
        Timestamp dateTo;

        if ("Week".equalsIgnoreCase(period)) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            dateFrom = new Timestamp(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_WEEK, 7);
            dateTo = new Timestamp(cal.getTimeInMillis());
        } else if ("Month".equalsIgnoreCase(period)) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            dateFrom = new Timestamp(cal.getTimeInMillis());
            cal.add(Calendar.MONTH, 1);
            dateTo = new Timestamp(cal.getTimeInMillis());
        } else {
            // Default Day
            cal.add(Calendar.DAY_OF_MONTH, 1);
            dateTo = new Timestamp(cal.getTimeInMillis());
        }

        String sql = "SELECT COUNT(*) as Total, " +
                "SUM(CASE WHEN DocStatus IN ('CO', 'CL') THEN 1 ELSE 0 END) as Completed, " +
                "SUM(CASE WHEN MovementDate < ? AND DocStatus NOT IN ('CO', 'CL') THEN 1 ELSE 0 END) as Late "
                +
                "FROM M_Production o " +
                "WHERE o.IsActive='Y' " +
                "AND o.MovementDate >= ? AND o.MovementDate < ?";

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            pstmt.setTimestamp(2, dateFrom);
            pstmt.setTimestamp(3, dateTo);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                stats.total = rs.getInt("Total");
                stats.completed = rs.getInt("Completed");
                stats.late = rs.getInt("Late");
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to load KPI stats", e);
        } finally {
            DB.close(rs, pstmt);
        }
        return stats;
    }

    public List<TimelineItem> getResourceStats(int resourceId, Timestamp date) {
        List<TimelineItem> items = new ArrayList<>();
        // Tasks for resource on specific date
        Calendar cal = Calendar.getInstance();
        if (date != null) {
            cal.setTime(date);
        }
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp dateStart = new Timestamp(cal.getTimeInMillis());
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Timestamp dateEnd = new Timestamp(cal.getTimeInMillis());

        String sql = "SELECT o.M_Production_ID, o.DocumentNo, o.MovementDate, " +
                "o.MovementDate as DateFinishSchedule, o.S_Resource_ID, " +
                "o.DocStatus, o.ProductionQty as QtyOrdered, " +
                "COALESCE(o.QtyDelivered, 0) as QtyDelivered, " +
                "o.Description, p.Value as ProductValue, p.Name as ProductName, p.M_Product_ID " +
                "FROM M_Production o " +
                "LEFT JOIN M_Product p ON o.M_Product_ID = p.M_Product_ID " +
                "WHERE o.IsActive='Y' AND o.S_Resource_ID=? " +
                "AND o.MovementDate >= ? AND o.MovementDate < ?"; // Overlap target date

        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, resourceId);
            pstmt.setTimestamp(2, dateStart);
            pstmt.setTimestamp(3, dateEnd);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("M_Production_ID");
                String docNo = rs.getString("DocumentNo");
                Timestamp start = rs.getTimestamp("MovementDate");
                Timestamp end = rs.getTimestamp("DateFinishSchedule");

                BigDecimal qty = rs.getBigDecimal("QtyOrdered");
                BigDecimal qtyDelivered = rs.getBigDecimal("QtyDelivered");
                if (qtyDelivered == null)
                    qtyDelivered = BigDecimal.ZERO;

                String prodValue = rs.getString("ProductValue");
                String prodName = rs.getString("ProductName");
                int prodId = rs.getInt("M_Product_ID");
                String desc = rs.getString("Description");

                String content = prodName + " (" + qtyDelivered.intValue() + "/" + qty.intValue() + ")";
                String status = rs.getString("DocStatus");

                items.add(new TimelineItem(id, resourceId, content, start, end, docNo, status, docNo, prodName,
                        prodValue, qty, qtyDelivered, prodId, desc));
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Failed to load resource stats", e);
        } finally {
            DB.close(rs, pstmt);
        }
        return items;
    }

    /**
     * Extracts stage name from M_Production Description field.
     * Parses "Stage: Sewing" format and returns "Sewing".
     *
     * @param description M_Production Description field
     * @return Stage name or null if not found
     */
    private String extractStageName(String description) {
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
}
