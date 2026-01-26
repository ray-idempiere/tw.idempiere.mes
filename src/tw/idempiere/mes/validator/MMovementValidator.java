package tw.idempiere.mes.validator;

import org.compiere.model.MClient;
import org.compiere.model.MMovement;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.DB;

/**
 * Model Validator for M_Movement
 * Automatically updates M_Production stage when Movement is completed
 * 
 * @author MES Team
 */
public class MMovementValidator implements ModelValidator {

    private static CLogger log = CLogger.getCLogger(MMovementValidator.class);
    private int m_AD_Client_ID = -1;

    @Override
    public void initialize(ModelValidationEngine engine, MClient client) {
        if (client != null) {
            m_AD_Client_ID = client.getAD_Client_ID();
            log.info("MMovementValidator initialized for client: " + client.getName());
        }

        // Register for M_Movement changes
        engine.addModelChange(MMovement.Table_Name, this);
        log.info("MMovementValidator registered for M_Movement table");
    }

    @Override
    public int getAD_Client_ID() {
        return m_AD_Client_ID;
    }

    @Override
    public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
        return null;
    }

    @Override
    public String modelChange(PO po, int type) throws Exception {
        if (!(po instanceof MMovement)) {
            return null;
        }

        MMovement movement = (MMovement) po;

        // Only process after change (when DocStatus is updated)
        if (type == TYPE_AFTER_CHANGE) {
            // Check if DocStatus changed to Completed
            if (movement.is_ValueChanged("DocStatus") &&
                    "CO".equals(movement.getDocStatus())) {

                log.info("Movement completed: " + movement.getDocumentNo());

                // Get M_Production_ID from Movement
                int productionId = movement.get_ValueAsInt("M_Production_ID");

                if (productionId > 0) {
                    log.info("Updating Production " + productionId + " stage to Cutting");
                    updateProductionStage(productionId, "Cutting");
                } else {
                    log.warning("Movement " + movement.getDocumentNo() +
                            " has no M_Production_ID, skipping stage update");
                }
            }
        }

        return null;
    }

    @Override
    public String docValidate(PO po, int timing) {
        return null;
    }

    /**
     * Update M_Production stage in Description field
     * 
     * @param productionId M_Production_ID
     * @param stage        Stage name (e.g., "Cutting")
     */
    private void updateProductionStage(int productionId, String stage) {
        try {
            // Get current Description
            String currentDesc = DB.getSQLValueString(null,
                    "SELECT Description FROM M_Production WHERE M_Production_ID=?",
                    productionId);

            if (currentDesc == null) {
                currentDesc = "";
            }

            // Update or append stage information
            String newDesc = currentDesc;
            if (newDesc.contains("Stage:")) {
                // Replace existing stage
                newDesc = newDesc.replaceAll("Stage: [^\\n]*", "Stage: " + stage);
            } else {
                // Append new stage
                if (!newDesc.isEmpty()) {
                    newDesc += "\n";
                }
                newDesc += "Stage: " + stage;
            }

            // Update database
            int updated = DB.executeUpdate(
                    "UPDATE M_Production SET Description=? WHERE M_Production_ID=?",
                    new Object[] { newDesc, productionId },
                    false, null);

            if (updated > 0) {
                log.info("Successfully updated Production " + productionId +
                        " stage to: " + stage);
            } else {
                log.warning("Failed to update Production " + productionId + " stage");
            }

        } catch (Exception e) {
            log.severe("Error updating Production stage: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
