package com.freefood.servlet;

import com.freefood.dao.FoodEventDAO;
import com.freefood.util.DatabaseUtil;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.sql.SQLException;
import java.util.concurrent.*;

@WebListener
public class AppStartupListener implements ServletContextListener {
    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("══════════════════════════════════════");
        System.out.println("  AnnaDaan v2.0 — Starting Up");
        System.out.println("══════════════════════════════════════");
        try { DatabaseUtil.getConnection().close(); System.out.println("[Startup] DB ready"); }
        catch (SQLException e) { System.err.println("[Startup] DB error: " + e.getMessage()); }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try { new FoodEventDAO().deleteExpiredEvents(); }
            catch (Exception e) { System.err.println("[Scheduler] " + e.getMessage()); }
        }, 5, 15, TimeUnit.MINUTES);
        System.out.println("[Startup] Cleanup scheduler active (every 15 min)");
        System.out.println("[Startup] App ready!");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) scheduler.shutdown();
    }
}
