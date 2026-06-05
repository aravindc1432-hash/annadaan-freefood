package com.freefood.servlet;

import com.freefood.util.DatabaseUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;

/**
 * Serves uploaded food photos.
 * GET /api/photos/{filename}
 */
@WebServlet("/api/photos/*")
public class PhotoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) { resp.setStatus(404); return; }
        String filename = path.substring(1).replaceAll("[/\\\\]", ""); // sanitize
        File file = new File(DatabaseUtil.UPLOAD_DIR, filename);
        if (!file.exists() || !file.isFile()) { resp.setStatus(404); return; }

        String ct = getServletContext().getMimeType(filename);
        resp.setContentType(ct != null ? ct : "image/jpeg");
        resp.setContentLengthLong(file.length());
        // Cache for 1 hour
        resp.setHeader("Cache-Control", "public, max-age=3600");
        Files.copy(file.toPath(), resp.getOutputStream());
    }
}
