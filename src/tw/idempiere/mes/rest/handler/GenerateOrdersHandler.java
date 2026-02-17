package tw.idempiere.mes.rest.handler;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * POST /mes/api/generate-orders
 * Body: {productId, dateFrom, dateTo, resourceId, qty}
 * Generates production orders from sales order lines.
 */
public class GenerateOrdersHandler {

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Read request body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();

        // TODO: Extract and call the generation logic from WProductionSchedule
        // This requires refactoring the generateProductionOrders method
        // from WProductionSchedule into MESService first.
        // For now, return a placeholder response.

        JsonObject result = new JsonObject();
        result.addProperty("created", 0);
        result.addProperty("message", "Generate orders endpoint ready - implementation pending");

        resp.getWriter().write(result.toString());
    }
}
