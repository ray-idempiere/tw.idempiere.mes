package tw.idempiere.mes.util;

import java.util.HashMap;
import java.util.Map;

import org.adempiere.base.event.EventManager;
import org.osgi.service.event.Event;

/**
 * Utility for broadcasting MES-related events using OSGi EventAdmin.
 * This replaces ZK EventQueues for backend-to-frontend communication,
 * providing a more robust and decoupled mechanism.
 */
public class MESBroadcastUtil {

    public static final String TOPIC_MES_UPDATE = "idempiere/mes/update";

    // Property keys
    public static final String PROPERTY_ORDER_ID = "M_Production_ID";
    public static final String PROPERTY_PRODUCT_ID = "M_Product_ID";
    public static final String PROPERTY_QTY = "Qty";

    /**
     * Publishes an MES update event.
     * 
     * @param orderId   The Production Order ID
     * @param productId The Product ID
     * @param qty       The scanned/processed quantity
     */
    public static void publishMESUpdate(int orderId, int productId, int qty) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_ORDER_ID, orderId);
        properties.put(PROPERTY_PRODUCT_ID, productId);
        properties.put(PROPERTY_QTY, qty);

        Event event = new Event(TOPIC_MES_UPDATE, properties);
        EventManager.getInstance().postEvent(event);
    }
}
