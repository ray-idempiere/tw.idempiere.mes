package tw.idempiere.mes.rest.handler;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;

import com.google.gson.JsonObject;

/**
 * POST /mes/api/production/{id}/material-issue
 * Creates a draft M_Movement with BOM components.
 */
public class MaterialIssueHandler {

    public void handle(HttpServletRequest req, HttpServletResponse resp, int productionId) throws IOException {
        try {
            // Get Production info
            String sql = "SELECT DocumentNo, M_Product_ID, ProductionQty FROM M_Production WHERE M_Production_ID=?";
            PreparedStatement pstmt = null;
            ResultSet rs = null;

            String documentNo;
            int productId;
            BigDecimal qtyOrdered;

            try {
                pstmt = DB.prepareStatement(sql, null);
                pstmt.setInt(1, productionId);
                rs = pstmt.executeQuery();
                if (rs.next()) {
                    documentNo = rs.getString("DocumentNo");
                    productId = rs.getInt("M_Product_ID");
                    qtyOrdered = rs.getBigDecimal("ProductionQty");
                } else {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Production order not found\"}");
                    return;
                }
            } finally {
                DB.close(rs, pstmt);
            }

            // Check if Movement already exists
            int existingMovementId = DB.getSQLValue(null,
                    "SELECT M_Movement_ID FROM M_Movement WHERE M_Production_ID=? ORDER BY Created DESC",
                    productionId);

            if (existingMovementId > 0) {
                String movementDocStatus = DB.getSQLValueString(null,
                        "SELECT DocStatus FROM M_Movement WHERE M_Movement_ID=?", existingMovementId);
                JsonObject result = new JsonObject();
                result.addProperty("movementId", existingMovementId);
                result.addProperty("status", movementDocStatus);
                result.addProperty("message", "Existing movement found");
                resp.getWriter().write(result.toString());
                return;
            }

            // Create draft movement (logic from WProductionSchedule.createDraftMaterialMovement)
            int movementId = createDraftMovement(productionId, documentNo, productId, qtyOrdered);

            if (movementId <= 0) {
                resp.setStatus(500);
                resp.getWriter().write("{\"error\":\"Failed to create material movement\"}");
                return;
            }

            // Update stage to Material Issue
            String currentDesc = DB.getSQLValueString(null,
                    "SELECT Description FROM M_Production WHERE M_Production_ID=?", productionId);
            if (currentDesc == null) currentDesc = "";
            String newDesc = currentDesc.contains("Stage:")
                    ? currentDesc.replaceAll("Stage: [^\\n]*", "Stage: Material Issue")
                    : (currentDesc.isEmpty() ? "" : currentDesc + "\n") + "Stage: Material Issue";
            DB.executeUpdate("UPDATE M_Production SET Description=? WHERE M_Production_ID=?",
                    new Object[]{newDesc, productionId}, false, null);

            JsonObject result = new JsonObject();
            result.addProperty("movementId", movementId);
            result.addProperty("status", "DR");
            result.addProperty("message", "Draft movement created for " + documentNo);
            resp.getWriter().write(result.toString());

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private int createDraftMovement(int orderId, String orderDocNo, int productId, BigDecimal qtyOrdered) {
        String trxName = Trx.createTrxName("MatIssueDraft");
        Trx trx = null;
        try {
            trx = Trx.get(trxName, true);

            int warehouseId = Env.getContextAsInt(Env.getCtx(), "#M_Warehouse_ID");
            if (warehouseId <= 0) {
                warehouseId = DB.getSQLValue(null, "SELECT MIN(M_Warehouse_ID) FROM M_Warehouse WHERE IsActive='Y'");
            }
            if (warehouseId <= 0) { trx.close(); return -1; }

            int fromLocatorId = DB.getSQLValue(null,
                    "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND IsDefault='Y' ORDER BY M_Locator_ID",
                    warehouseId);

            String resourceName = DB.getSQLValueString(null,
                    "SELECT r.Name FROM S_Resource r JOIN M_Production o ON r.S_Resource_ID=o.S_Resource_ID WHERE o.M_Production_ID=?",
                    orderId);

            int toLocatorId = -1;
            if (resourceName != null && !resourceName.isEmpty()) {
                toLocatorId = DB.getSQLValue(null,
                        "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND Value=? AND IsActive='Y'",
                        warehouseId, resourceName);
            }
            if (toLocatorId <= 0) {
                toLocatorId = DB.getSQLValue(null,
                        "SELECT M_Locator_ID FROM M_Locator WHERE M_Warehouse_ID=? AND M_Locator_ID != ? ORDER BY M_Locator_ID",
                        warehouseId, fromLocatorId);
            }
            if (fromLocatorId <= 0 || toLocatorId <= 0 || fromLocatorId == toLocatorId) {
                trx.close(); return -1;
            }

            org.compiere.model.MMovement movement = new org.compiere.model.MMovement(Env.getCtx(), 0, trxName);
            movement.setMovementDate(new Timestamp(System.currentTimeMillis()));
            movement.setDescription("Material Issue for Order: " + orderDocNo);
            movement.set_ValueOfColumn("M_Production_ID", orderId);
            if (!movement.save()) { trx.rollback(); trx.close(); return -1; }

            // Add BOM component lines
            String bomSql = "SELECT M_ProductBOM_ID, BOMQty FROM M_Product_BOM WHERE M_Product_ID=? AND IsActive='Y'";
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                pstmt = DB.prepareStatement(bomSql, trxName);
                pstmt.setInt(1, productId);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    int componentId = rs.getInt("M_ProductBOM_ID");
                    BigDecimal bomQty = rs.getBigDecimal("BOMQty");
                    BigDecimal requiredQty = bomQty.multiply(qtyOrdered);

                    org.compiere.model.MMovementLine line = new org.compiere.model.MMovementLine(movement);
                    line.setM_Product_ID(componentId);
                    line.setM_Locator_ID(fromLocatorId);
                    line.setM_LocatorTo_ID(toLocatorId);
                    line.setMovementQty(requiredQty);
                    if (!line.save()) { trx.rollback(); trx.close(); return -1; }
                }
            } finally {
                DB.close(rs, pstmt);
            }

            movement.saveEx();
            trx.commit();
            trx.close();
            return movement.getM_Movement_ID();

        } catch (Exception e) {
            if (trx != null) { trx.rollback(); trx.close(); }
            return -1;
        }
    }
}
