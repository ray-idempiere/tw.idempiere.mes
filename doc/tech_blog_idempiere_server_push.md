# iDempiere Technical: achieving Sub-Second Real-Time Updates with OSGi EventAdmin

**Category:** Technical Tutorial  
**Tags:** iDempiere, ZK, Server Push, OSGi, Real-Time, Performance  

---

In complex Manufacturing Execution Systems (MES) or high-frequency trading screens, the default "polling" mechanism often feels sluggish. Users expect instant feedback: just like a chat app, when a barcode is scanned in the warehouse, the dashboard in the office should blink *immediately*.

In this post, I will share how we solved a "Server Push Delay" issue in iDempiere by moving away from standard ZK EventQueues to a robust **OSGi EventAdmin** approach, achieving sub-second updates across browser sessions.

## The Problem: "It's too slow!"

We built a **Resource KPI Board** for our production floor. It displays real-time stats (Target vs. Delivered) for each machine.
*   **Role A (Operator):** Scans a product barcode on an industrial tablet.
*   **Role B (Manager):** Watches the KPI Dashboard on a large TV screen.

Initially, we used the standard ZK `EventQueues.APPLICATION`. However, users reported lags of 3-5 seconds or missed updates. ZK's default mechanism sometimes falls back to "piggyback" polling if not configured perfectly, or struggles when the EventQueue is managed strictly within the web container's context, separate from the business logic layer.

We extended this solution not just for scanning, but for **Timeline Rescheduling** and **Stage Changes**. When an operator drags a job on the timeline, the same OSGi event is fired, instantly updating all other views.

## The Solution: OSGi EventAdmin + Server Push

We looked at how iDempiere handles its own "Broadcast Messages" (the notifications that pop up when you log in). It uses **OSGi EventAdmin**. This is the native event bus of the OSGi framework, sitting at a lower, more "system-wide" level than the UI.

By coupling OSGi Events with ZK's `enableServerPush`, we created a lightning-fast update cycle.

### Architecture

1.  **Publisher (The Scanner):** Fires an OSGi Event (`dempiere/mes/update`) containing purely data IDs (OrderId, ProductId).
2.  **Subscriber (The Dashboard):** A ZK UI component registers an OSGi `EventHandler`.
3.  **Bridge:** When the OSGi event arrives, the handler uses `Executions.schedule()` to inject a task into the ZK Desktop's specific thread to update the UI safely.

## Implementation Guide

Here is the recipe to implement this in your own plugin.

### 1. The Utility Class
First, create a helper to handle the raw OSGi event posting. This keeps your code clean.

```java
package tw.idempiere.mes.util;

import java.util.HashMap;
import java.util.Map;
import org.adempiere.base.event.EventManager;
import org.osgi.service.event.Event;

public class MESBroadcastUtil {

    // Define a unique topic name
    public static final String TOPIC_MES_UPDATE = "idempiere/mes/update";
    
    public static final String PROPERTY_ORDER_ID = "M_Production_ID";
    public static final String PROPERTY_QTY = "Qty";

    /**
     * Publish an event to the OSGi system bus
     */
    public static void publishMESUpdate(int orderId, int qty) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_ORDER_ID, orderId);
        properties.put(PROPERTY_QTY, qty);

        Event event = new Event(TOPIC_MES_UPDATE, properties);
        EventManager.getInstance().postEvent(event);
    }
}
```

### 2. The Publisher (e.g., A Process or Dialog)
Wherever the business logic happens (e.g., after a successful `DB.executeUpdate`), fire the event.

```java
// ... Inside your scanning logic ...
if (updateSuccessful) {
   // Notify the world!
   MESBroadcastUtil.publishMESUpdate(currentOrderId, scannedQty);
}
```

### 3. The Subscriber (The ZK Dashboard)
This is the tricky part. You cannot touch ZK UI components directly from an OSGi thread. You must use `Executions.schedule`.

**Crucial Step:** Ensure you add `org.osgi.service.event` to your `Import-Package` in `MANIFEST.MF`.

```java
import org.adempiere.base.event.EventManager;
import org.osgi.service.event.EventHandler;

public class WKpiDashboard extends AdForm {
    
    @Override
    protected void initForm() {
        // 1. Enable Server Push for this Desktop
        this.getDesktop().enableServerPush(true);
        
        // 2. Subscribe
        subscribeToEvents();
    }

    private EventHandler subscriber;

    private void subscribeToEvents() {
        final Desktop myDesktop = this.getDesktop();

        // Create the OSGi Handler
        subscriber = new EventHandler() {
            @Override
            public void handleEvent(final org.osgi.service.event.Event event) {
                // Filter for our topic
                if (MESBroadcastUtil.TOPIC_MES_UPDATE.equals(event.getTopic())) {
                    
                    // Extract Data
                    final Integer orderId = (Integer) event.getProperty(MESBroadcastUtil.PROPERTY_ORDER_ID);

                    // CHECK: Is the desktop still alive?
                    if (myDesktop.isAlive()) {
                        
                        // SCHEDULE: Run UI update on the ZK Thread
                        Executions.schedule(myDesktop, new EventListener<Event>() {
                            public void onEvent(Event zEvent) {
                                // Now we are safe to touch UI components!
                                refreshDashboard(orderId);
                                Clients.showNotification("New Scan Received!");
                            }
                        }, new Event("onUpdate"));
                    }
                }
            }
        };

        // Register with iDempiere's EventManager
        EventManager.getInstance().register(MESBroadcastUtil.TOPIC_MES_UPDATE, subscriber);
        
        // CLEANUP: Very Important! Unregister when user closes tab
        this.addEventListener("onDetach", new EventListener<Event>() {
             public void onEvent(Event event) {
                 EventManager.getInstance().unregister(subscriber);
             }
        });
    }
}
```

## Why this is better?

1.  **Decoupling:** Your business logic (Publisher) doesn't need to know about ZK or the WebUI context. It just fires a system event.
2.  **Performance:** OSGi EventAdmin is highly optimized.
3.  **Cluster Ready:** If you implement `IMessageService`, this pattern can be extended to broadcast across multiple servers in a cluster (like iDempiere's login alerts).

## Conclusion

Real-time feedback creates a "premium" feel for end-users. By leveraging the power of OSGi already built into iDempiere, we can achieve responsive dashboards without complex third-party tools (like external WebSocket servers).

Happy Coding!
