package tw.idempiere.mes.rest;

import java.io.IOException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.compiere.util.CLogger;

import tw.idempiere.mes.rest.handler.GenerateOrdersHandler;
import tw.idempiere.mes.rest.handler.MaterialIssueHandler;
import tw.idempiere.mes.rest.handler.ResourceStatsHandler;
import tw.idempiere.mes.rest.handler.TimelineHandler;

/**
 * MES REST Servlet — routes requests to handlers.
 * Mapped to /mes/api/* via web.xml.
 */
public class MESServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final CLogger log = CLogger.getCLogger(MESServlet.class);

    // Route patterns
    private static final Pattern TIMELINE_PATTERN = Pattern.compile("^/timeline$");
    private static final Pattern RESOURCE_STATS_PATTERN = Pattern.compile("^/resource/(\\d+)/stats$");
    private static final Pattern GENERATE_ORDERS_PATTERN = Pattern.compile("^/generate-orders$");
    private static final Pattern MATERIAL_ISSUE_PATTERN = Pattern.compile("^/production/(\\d+)/material-issue$");

    private final TimelineHandler timelineHandler = new TimelineHandler();
    private final ResourceStatsHandler resourceStatsHandler = new ResourceStatsHandler();
    private final GenerateOrdersHandler generateOrdersHandler = new GenerateOrdersHandler();
    private final MaterialIssueHandler materialIssueHandler = new MaterialIssueHandler();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        String path = req.getPathInfo();
        if (path == null) path = "";

        try {
            Matcher m;
            if ((m = TIMELINE_PATTERN.matcher(path)).matches()) {
                timelineHandler.handle(req, resp);
            } else if ((m = RESOURCE_STATS_PATTERN.matcher(path)).matches()) {
                int resourceId = Integer.parseInt(m.group(1));
                resourceStatsHandler.handle(req, resp, resourceId);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "MES GET error: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        String path = req.getPathInfo();
        if (path == null) path = "";

        try {
            Matcher m;
            if ((m = GENERATE_ORDERS_PATTERN.matcher(path)).matches()) {
                generateOrdersHandler.handle(req, resp);
            } else if ((m = MATERIAL_ISSUE_PATTERN.matcher(path)).matches()) {
                int productionId = Integer.parseInt(m.group(1));
                materialIssueHandler.handle(req, resp, productionId);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "MES POST error: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
