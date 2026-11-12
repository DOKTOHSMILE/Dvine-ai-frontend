package ai.ibiko.design;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko's own drawing board for designs: a brain writes the design as SVG (icons, logos, badges, diagrams, simple
 * layouts) and this draws it to a picture on the device, with no image service. The parts of SVG a design needs:
 * rect (rounded), circle, ellipse, line, polyline, polygon, path, text, groups with transforms, linear and radial
 * gradients, fill, stroke, opacity. Scripts, images and anything else are ignored.
 */
public final class SvgDraw {
    private SvgDraw() {
    }

    /** The SVG document inside a brain's reply (from "<svg" to "</svg>"), or null. */
    public static String extract(String reply) {
        if (reply == null) {
            return null;
        }
        int a = reply.indexOf("<svg");
        int b = reply.lastIndexOf("</svg>");
        return a < 0 || b < a ? null : reply.substring(a, b + 6);
    }

    /** Asks the brain for a design: an icon, logo, badge, diagram, chart, wireframe or other flat, vector work. */
    private static final Pattern DESIGNISH = Pattern.compile("(?i)\\b(icon|logo|logotype|badge|emblem|symbol|diagram|flow ?chart"
            + "|chart|graph|wireframe|mock-?up|layout|banner|button|vector|svg|flat|monogram|sticker|infographic|org ?chart"
            + "|mind ?map|favicon|app icon)s?\\b");

    /** True when this ask is best drawn in the app as a design rather than painted as a photo-like picture. */
    public static boolean designish(String what) {
        return what != null && DESIGNISH.matcher(what).find();
    }

    /** The instruction a brain gets to write a design Ibiko can draw. */
    public static final String SYSTEM = "You are Ibiko's designer. Draw what the user asks as one SVG document and nothing else:"
            + " start with <svg and end with </svg>, no words before or after, no markdown fence. Use viewBox=\"0 0 512 512\"."
            + " Use only rect (rx for rounded corners), circle, ellipse, line, polyline, polygon, path, text, g with transform,"
            + " and linearGradient or radialGradient inside defs. No images, scripts, filters, masks, clip paths or fonts."
            + " Make it look designed: a clear idea, balanced spacing, a small harmonious palette (2 to 4 colours), consistent"
            + " stroke widths, centred and filling the space with a little margin. Text only when asked for, short, large and"
            + " with text-anchor=\"middle\".";

    /** The ask as the brain gets it. */
    public static String brief(String what) {
        return "Design: " + what;
    }

    /**
     * Draws a brain's reply and saves both the design (.svg, editable later) and the picture (.png) in {@code dir}.
     * Returns the picture, or null when the reply held no drawable SVG.
     */
    public static java.io.File save(String reply, java.io.File dir, int size) {
        String svg = extract(reply);
        if (svg == null) {
            return null;
        }
        Bitmap bm = render(svg, size);
        if (bm == null) {
            return null;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        String name = "design-" + System.currentTimeMillis();
        java.io.File png = new java.io.File(dir, name + ".png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(png)) {
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (java.io.IOException e) {
            return null;
        } finally {
            bm.recycle();
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name + ".svg"))) {
            out.write(svg.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {
            // the picture is what was asked for; the .svg is a bonus
        }
        return png;
    }

    /** Draws the SVG to a square picture {@code size} pixels across; null when it can't be read. */
    public static Bitmap render(String svg, int size) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(false);
            Document d = f.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
            Element root = d.getDocumentElement();
            float[] vb = viewBox(root);
            Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            float scale = Math.min(size / vb[2], size / vb[3]);
            c.translate((size - vb[2] * scale) / 2f, (size - vb[3] * scale) / 2f);
            c.scale(scale, scale);
            c.translate(-vb[0], -vb[1]);
            Map<String, Element> defs = new HashMap<String, Element>();
            collectDefs(root, defs);
            draw(c, root, new Style(), defs);
            return bm;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    static float[] viewBox(Element root) {
        String v = root.getAttribute("viewBox");
        if (!v.isEmpty()) {
            String[] p = v.trim().split("[\\s,]+");
            if (p.length == 4) {
                return new float[] {num(p[0], 0), num(p[1], 0), Math.max(1, num(p[2], 512)), Math.max(1, num(p[3], 512))};
            }
        }
        return new float[] {0, 0, Math.max(1, num(root.getAttribute("width"), 512)), Math.max(1, num(root.getAttribute("height"), 512))};
    }

    /** What an element inherits from its group. */
    static final class Style {
        String fill = "#000000", stroke = "none";
        float strokeWidth = 1, opacity = 1, fillOpacity = 1, strokeOpacity = 1, fontSize = 16;
        String cap = "butt", join = "miter", anchor = "start", weight = "normal";

        Style copy() {
            Style s = new Style();
            s.fill = fill;
            s.stroke = stroke;
            s.strokeWidth = strokeWidth;
            s.opacity = opacity;
            s.fillOpacity = fillOpacity;
            s.strokeOpacity = strokeOpacity;
            s.fontSize = fontSize;
            s.cap = cap;
            s.join = join;
            s.anchor = anchor;
            s.weight = weight;
            return s;
        }
    }

    private static void collectDefs(Element e, Map<String, Element> defs) {
        if (!e.getAttribute("id").isEmpty()) {
            defs.put(e.getAttribute("id"), e);
        }
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element) {
                collectDefs((Element) kids.item(i), defs);
            }
        }
    }

    private static Style styled(Element e, Style parent) {
        Style s = parent.copy();
        Map<String, String> a = new HashMap<String, String>();
        for (String k : new String[] {"fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity", "font-size",
                "stroke-linecap", "stroke-linejoin", "text-anchor", "font-weight"}) {
            if (e.hasAttribute(k)) {
                a.put(k, e.getAttribute(k));
            }
        }
        for (String decl : e.getAttribute("style").split(";")) {
            int c = decl.indexOf(':');
            if (c > 0) {
                a.put(decl.substring(0, c).trim(), decl.substring(c + 1).trim());
            }
        }
        if (a.containsKey("fill")) s.fill = a.get("fill");
        if (a.containsKey("stroke")) s.stroke = a.get("stroke");
        if (a.containsKey("stroke-width")) s.strokeWidth = num(a.get("stroke-width"), 1);
        if (a.containsKey("opacity")) s.opacity = parent.opacity * num(a.get("opacity"), 1);
        if (a.containsKey("fill-opacity")) s.fillOpacity = num(a.get("fill-opacity"), 1);
        if (a.containsKey("stroke-opacity")) s.strokeOpacity = num(a.get("stroke-opacity"), 1);
        if (a.containsKey("font-size")) s.fontSize = num(a.get("font-size"), 16);
        if (a.containsKey("stroke-linecap")) s.cap = a.get("stroke-linecap");
        if (a.containsKey("stroke-linejoin")) s.join = a.get("stroke-linejoin");
        if (a.containsKey("text-anchor")) s.anchor = a.get("text-anchor");
        if (a.containsKey("font-weight")) s.weight = a.get("font-weight");
        return s;
    }

    private static void draw(Canvas c, Element e, Style parent, Map<String, Element> defs) {
        String tag = e.getTagName().toLowerCase(Locale.US).replaceAll("^.*:", "");
        if (tag.equals("defs") || tag.equals("script") || tag.equals("image") || tag.equals("style") || tag.equals("lineargradient")
                || tag.equals("radialgradient") || tag.equals("clippath") || tag.equals("mask") || tag.equals("title")) {
            return;
        }
        Style s = styled(e, parent);
        int saved = c.save();
        String tr = e.getAttribute("transform");
        if (!tr.isEmpty()) {
            c.concat(transform(tr));
        }
        if (tag.equals("svg") || tag.equals("g")) {
            NodeList kids = e.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                Node k = kids.item(i);
                if (k instanceof Element) {
                    draw(c, (Element) k, s, defs);
                }
            }
        } else if (tag.equals("text")) {
            text(c, e, s, defs);
        } else {
            Path p = shape(tag, e);
            if (p != null) {
                paint(c, p, s, defs);
            }
        }
        c.restoreToCount(saved);
    }

    // androidx's PathParser is marked internal to androidx (lint: RestrictedApi), but it ships inside this APK at the
    // pinned androidx.core version, so it can't change under us; Android's own android.util.PathParser is hidden.
    @android.annotation.SuppressLint("RestrictedApi")
    static Path shape(String tag, Element e) {
        Path p = new Path();
        switch (tag) {
            case "rect": {
                float x = num(e.getAttribute("x"), 0), y = num(e.getAttribute("y"), 0);
                float w = num(e.getAttribute("width"), 0), h = num(e.getAttribute("height"), 0);
                float rx = num(e.getAttribute("rx"), -1), ry = num(e.getAttribute("ry"), -1);
                if (rx < 0) rx = Math.max(0, ry);
                if (ry < 0) ry = rx;
                p.addRoundRect(new RectF(x, y, x + w, y + h), Math.min(rx, w / 2), Math.min(ry, h / 2), Path.Direction.CW);
                return p;
            }
            case "circle": {
                p.addCircle(num(e.getAttribute("cx"), 0), num(e.getAttribute("cy"), 0), num(e.getAttribute("r"), 0), Path.Direction.CW);
                return p;
            }
            case "ellipse": {
                float cx = num(e.getAttribute("cx"), 0), cy = num(e.getAttribute("cy"), 0);
                float rx = num(e.getAttribute("rx"), 0), ry = num(e.getAttribute("ry"), 0);
                p.addOval(new RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW);
                return p;
            }
            case "line": {
                p.moveTo(num(e.getAttribute("x1"), 0), num(e.getAttribute("y1"), 0));
                p.lineTo(num(e.getAttribute("x2"), 0), num(e.getAttribute("y2"), 0));
                return p;
            }
            case "polyline":
            case "polygon": {
                String[] n = e.getAttribute("points").trim().split("[\\s,]+");
                for (int i = 0; i + 1 < n.length; i += 2) {
                    if (i == 0) {
                        p.moveTo(num(n[i], 0), num(n[i + 1], 0));
                    } else {
                        p.lineTo(num(n[i], 0), num(n[i + 1], 0));
                    }
                }
                if (tag.equals("polygon")) {
                    p.close();
                }
                return p;
            }
            case "path": {
                try {
                    return androidx.core.graphics.PathParser.createPathFromPathData(e.getAttribute("d"));
                } catch (RuntimeException bad) {
                    return null;
                }
            }
            default:
                return null;
        }
    }

    private static void paint(Canvas c, Path p, Style s, Map<String, Element> defs) {
        if (!"none".equals(s.fill)) {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setStyle(Paint.Style.FILL);
            fillWith(f, s.fill, s.opacity * s.fillOpacity, p, defs);
            c.drawPath(p, f);
        }
        if (!"none".equals(s.stroke) && s.strokeWidth > 0) {
            Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
            st.setStyle(Paint.Style.STROKE);
            st.setStrokeWidth(s.strokeWidth);
            st.setStrokeCap("round".equals(s.cap) ? Paint.Cap.ROUND : "square".equals(s.cap) ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
            st.setStrokeJoin("round".equals(s.join) ? Paint.Join.ROUND : "bevel".equals(s.join) ? Paint.Join.BEVEL : Paint.Join.MITER);
            fillWith(st, s.stroke, s.opacity * s.strokeOpacity, p, defs);
            c.drawPath(p, st);
        }
    }

    private static void text(Canvas c, Element e, Style s, Map<String, Element> defs) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(s.fontSize);
        t.setFakeBoldText("bold".equals(s.weight) || num(s.weight, 400) >= 600);
        t.setTextAlign("middle".equals(s.anchor) ? Paint.Align.CENTER : "end".equals(s.anchor) ? Paint.Align.RIGHT : Paint.Align.LEFT);
        fillWith(t, "none".equals(s.fill) ? "#000" : s.fill, s.opacity * s.fillOpacity, null, defs);
        c.drawText(e.getTextContent().trim(), num(e.getAttribute("x"), 0), num(e.getAttribute("y"), 0), t);
    }

    private static final Pattern URL = Pattern.compile("url\\(\\s*#([^)\\s]+)\\s*\\)");

    private static void fillWith(Paint p, String value, float opacity, Path shape, Map<String, Element> defs) {
        Matcher m = URL.matcher(value);
        if (m.find() && defs.containsKey(m.group(1)) && shape != null) {
            Shader sh = gradient(defs.get(m.group(1)), shape, defs);
            if (sh != null) {
                p.setShader(sh);
                p.setAlpha((int) (255 * clamp(opacity)));
                return;
            }
        }
        int color = color(value);
        p.setColor(color);
        p.setAlpha((int) (((color >>> 24) & 0xff) * clamp(opacity)));
    }

    private static Shader gradient(Element g, Path shape, Map<String, Element> defs) {
        java.util.List<Integer> colors = new java.util.ArrayList<Integer>();
        java.util.List<Float> stops = new java.util.ArrayList<Float>();
        Element src = g;
        String href = g.hasAttribute("xlink:href") ? g.getAttribute("xlink:href") : g.getAttribute("href");
        if (g.getElementsByTagName("stop").getLength() == 0 && href.startsWith("#") && defs.containsKey(href.substring(1))) {
            src = defs.get(href.substring(1));
        }
        NodeList st = src.getElementsByTagName("stop");
        for (int i = 0; i < st.getLength(); i++) {
            Element s = (Element) st.item(i);
            String c = s.hasAttribute("stop-color") ? s.getAttribute("stop-color") : styleValue(s, "stop-color", "#000");
            float op = num(s.hasAttribute("stop-opacity") ? s.getAttribute("stop-opacity") : styleValue(s, "stop-opacity", "1"), 1);
            int col = color(c);
            colors.add((((int) (((col >>> 24) & 0xff) * clamp(op))) << 24) | (col & 0xffffff));
            String off = s.getAttribute("offset").trim();
            stops.add(off.endsWith("%") ? num(off.substring(0, off.length() - 1), 0) / 100f : num(off, 0));
        }
        if (colors.size() < 2) {
            return null;
        }
        int[] cs = new int[colors.size()];
        float[] ps = new float[stops.size()];
        for (int i = 0; i < cs.length; i++) {
            cs[i] = colors.get(i);
            ps[i] = clamp(stops.get(i));
        }
        RectF b = new RectF();
        shape.computeBounds(b, true);
        boolean user = "userSpaceOnUse".equals(g.getAttribute("gradientUnits"));
        if (g.getTagName().toLowerCase(Locale.US).endsWith("radialgradient")) {
            float cx = frac(g.getAttribute("cx"), 0.5f), cy = frac(g.getAttribute("cy"), 0.5f), r = frac(g.getAttribute("r"), 0.5f);
            float x = user ? cx : b.left + cx * b.width(), y = user ? cy : b.top + cy * b.height();
            float rad = Math.max(1, user ? r : r * Math.max(b.width(), b.height()));
            return new RadialGradient(x, y, rad, cs, ps, Shader.TileMode.CLAMP);
        }
        float x1 = frac(g.getAttribute("x1"), 0), y1 = frac(g.getAttribute("y1"), 0);
        float x2 = frac(g.getAttribute("x2"), 1), y2 = frac(g.getAttribute("y2"), 0);
        if (!user) {
            x1 = b.left + x1 * b.width();
            x2 = b.left + x2 * b.width();
            y1 = b.top + y1 * b.height();
            y2 = b.top + y2 * b.height();
        }
        return new LinearGradient(x1, y1, x2, y2, cs, ps, Shader.TileMode.CLAMP);
    }

    private static String styleValue(Element e, String key, String fallback) {
        for (String decl : e.getAttribute("style").split(";")) {
            int c = decl.indexOf(':');
            if (c > 0 && decl.substring(0, c).trim().equals(key)) {
                return decl.substring(c + 1).trim();
            }
        }
        return fallback;
    }

    private static float frac(String v, float fallback) {
        if (v == null || v.isEmpty()) {
            return fallback;
        }
        return v.endsWith("%") ? num(v.substring(0, v.length() - 1), fallback * 100) / 100f : num(v, fallback);
    }

    static Matrix transform(String t) {
        Matrix m = new Matrix();
        Matcher f = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)").matcher(t);
        while (f.find()) {
            String[] a = f.group(2).trim().split("[\\s,]+");
            float[] v = new float[a.length];
            for (int i = 0; i < a.length; i++) {
                v[i] = num(a[i], 0);
            }
            Matrix step = new Matrix();
            switch (f.group(1)) {
                case "translate":
                    step.setTranslate(v[0], v.length > 1 ? v[1] : 0);
                    break;
                case "scale":
                    step.setScale(v[0], v.length > 1 ? v[1] : v[0]);
                    break;
                case "rotate":
                    if (v.length >= 3) {
                        step.setRotate(v[0], v[1], v[2]);
                    } else {
                        step.setRotate(v[0]);
                    }
                    break;
                case "skewX":
                    step.setSkew((float) Math.tan(Math.toRadians(v[0])), 0);
                    break;
                case "skewY":
                    step.setSkew(0, (float) Math.tan(Math.toRadians(v[0])));
                    break;
                default:
                    if (v.length == 6) {
                        step.setValues(new float[] {v[0], v[2], v[4], v[1], v[3], v[5], 0, 0, 1});
                    }
            }
            m.preConcat(step);
        }
        return m;
    }

    static int color(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(Locale.US);
        try {
            if (s.startsWith("#")) {
                String h = s.substring(1);
                if (h.length() == 3) {
                    h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                }
                if (h.length() == 6) {
                    return 0xff000000 | Integer.parseInt(h, 16);
                }
                if (h.length() == 8) {
                    long rgba = Long.parseLong(h, 16);
                    return (int) (((rgba & 0xff) << 24) | (rgba >>> 8));
                }
            }
            Matcher rgb = Pattern.compile("rgba?\\(([^)]*)\\)").matcher(s);
            if (rgb.find()) {
                String[] p = rgb.group(1).split("[\\s,]+");
                int r = (int) num(p[0], 0), g = (int) num(p[1], 0), b = (int) num(p[2], 0);
                int a = p.length > 3 ? (int) (255 * clamp(num(p[3], 1))) : 255;
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
        } catch (RuntimeException ignored) {
            // falls to a named colour
        }
        Integer named = NAMED.get(s);
        return named != null ? named : 0xff000000;
    }

    static final Map<String, Integer> NAMED = new HashMap<String, Integer>();

    static {
        String[] n = {"black", "000000", "white", "ffffff", "red", "ff0000", "green", "008000", "blue", "0000ff", "yellow", "ffff00",
                "orange", "ffa500", "purple", "800080", "pink", "ffc0cb", "gray", "808080", "grey", "808080", "navy", "000080",
                "teal", "008080", "cyan", "00ffff", "magenta", "ff00ff", "lime", "00ff00", "gold", "ffd700", "silver", "c0c0c0",
                "brown", "a52a2a", "transparent", "00000000"};
        for (int i = 0; i < n.length; i += 2) {
            NAMED.put(n[i], n[i + 1].length() == 8 ? 0 : 0xff000000 | Integer.parseInt(n[i + 1], 16));
        }
    }

    private static final Pattern NUM = Pattern.compile("^\\s*([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)");

    static float num(String v, float fallback) {
        if (v == null) {
            return fallback;
        }
        Matcher m = NUM.matcher(v);
        return m.find() ? Float.parseFloat(m.group(1)) : fallback;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
