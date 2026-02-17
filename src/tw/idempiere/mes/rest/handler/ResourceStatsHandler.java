package tw.idempiere.mes.rest.handler;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.compiere.util.DB;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import tw.idempiere.mes.service.MESService;

/**
 * GET /mes/api/resource/{id}/stats?date=2026-02-17
 * Returns resource name + daily order list.
 */
public class ResourceStatsHandler {

    private final MESService service = new MESService();
    private final Gson gson = new Gson();

    public void handle(HttpServletRequest req, HttpServletResponse resp, int resourceId) throws IOException {
        String dateStr = req.getParameter("date");
        Timestamp date = null;
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                date = new Timestamp(new SimpleDateFormat("yyyy-MM-dd").parse(dateStr).getTime());
            } catch (Exception e) {
                date = new Timestamp(System.currentTimeMillis());
            }
        }

        String resourceName = DB.getSQLValueString(null,
                "SELECT Name FROM S_Resource WHERE S_Resource_ID=?", resourceId);

        List<MESService.TimelineItem> items = service.getResourceStats(resourceId, date);

        JsonObject result = new JsonObject();
        result.addProperty("resourceName", resourceName != null ? resourceName : "");
        result.addProperty("resourceId", resourceId);
        result.add("items", gson.toJsonTree(items));

        resp.getWriter().write(result.toString());
    }
}
