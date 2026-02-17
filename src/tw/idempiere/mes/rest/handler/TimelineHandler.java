package tw.idempiere.mes.rest.handler;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.compiere.util.Env;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import tw.idempiere.mes.service.MESService;

/**
 * GET /mes/api/timeline?search=&period=Day
 * Returns {groups, items, kpi} aggregated timeline data.
 */
public class TimelineHandler {

    private final MESService service = new MESService();

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String search = req.getParameter("search");
        String period = req.getParameter("period");
        if (period == null || period.isEmpty()) period = "Day";

        int clientId = Env.getAD_Client_ID(Env.getCtx());

        // Reuse existing MESService methods
        String groupsJson = service.getGroupsJSON(clientId, null);
        String itemsJson = (search != null && !search.isEmpty())
                ? service.getItemsJSON(search)
                : service.getItemsJSON();
        MESService.KPIStats stats = service.getKPIStats(period);

        // Build combined response
        JsonObject result = new JsonObject();
        result.add("groups", JsonParser.parseString(groupsJson));
        result.add("items", JsonParser.parseString(itemsJson));

        JsonObject kpi = new JsonObject();
        kpi.addProperty("total", stats.total);
        kpi.addProperty("completed", stats.completed);
        kpi.addProperty("late", stats.late);
        result.add("kpi", kpi);

        resp.getWriter().write(result.toString());
    }
}
