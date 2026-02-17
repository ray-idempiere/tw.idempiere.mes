package tw.idempiere.mes.rest;

import java.io.IOException;
import java.util.logging.Level;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.compiere.util.CLogger;
import org.compiere.util.Env;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.trekglobal.idempiere.rest.api.v1.jwt.LoginClaims;
import com.trekglobal.idempiere.rest.api.v1.jwt.TokenUtils;

/**
 * JWT authentication filter for MES REST endpoints.
 * Validates the same JWT token used by idempiere-rest.
 */
public class MESAuthFilter implements Filter {

    private static final CLogger log = CLogger.getCLogger(MESAuthFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // CORS preflight
        if ("OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
            setCorsHeaders(httpResp);
            httpResp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        setCorsHeaders(httpResp);

        String authHeader = httpReq.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResp.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7);

        try {
            Algorithm algorithm = Algorithm.HMAC512(TokenUtils.getTokenSecret());
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(TokenUtils.getTokenIssuer())
                    .build();
            DecodedJWT jwt = verifier.verify(token);

            // Set iDempiere Env context from JWT claims
            int clientId = jwt.getClaim(LoginClaims.AD_Client_ID.name()).asInt();
            int userId = jwt.getClaim(LoginClaims.AD_User_ID.name()).asInt();
            int roleId = jwt.getClaim(LoginClaims.AD_Role_ID.name()).asInt();
            int orgId = jwt.getClaim(LoginClaims.AD_Org_ID.name()).asInt();

            Env.setContext(Env.getCtx(), "#AD_Client_ID", clientId);
            Env.setContext(Env.getCtx(), "#AD_User_ID", userId);
            Env.setContext(Env.getCtx(), "#AD_Role_ID", roleId);
            Env.setContext(Env.getCtx(), "#AD_Org_ID", orgId);

            chain.doFilter(request, response);

        } catch (JWTVerificationException e) {
            log.log(Level.WARNING, "JWT verification failed: " + e.getMessage());
            httpResp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResp.getWriter().write("{\"error\":\"Invalid token\"}");
        }
    }

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    }

    @Override
    public void destroy() {}
}
