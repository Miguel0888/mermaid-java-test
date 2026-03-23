package com.aresstack.test;

import com.aresstack.Mermaid;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MermaidCoder: Links RSyntaxTextArea (Java-Editor), rechts Live-Mermaid-Klassendiagramm.
 * Start: gradlew runCoder
 */
public class MermaidCoder extends JFrame {

    private final RSyntaxTextArea editor;
    private final DiagramCanvas canvas;
    private final JLabel statusLabel;
    private final Timer debounceTimer;

    public MermaidCoder() {
        super("MermaidCoder - Java to Mermaid");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 850);
        setLocationRelativeTo(null);

        // ===== Left: Code Editor =====
        editor = new RSyntaxTextArea(40, 60);
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        editor.setCodeFoldingEnabled(true);
        editor.setAntiAliasingEnabled(true);
        editor.setTabSize(4);
        editor.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        applyDarkTheme(editor);

        RTextScrollPane editorScroll = new RTextScrollPane(editor);
        editorScroll.setMinimumSize(new Dimension(400, 0));

        // ===== Right: Diagram Panel =====
        canvas = new DiagramCanvas();
        JPanel rightPanel = new JPanel(new BorderLayout());

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton fitBtn = new JButton("Fit");
        fitBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { canvas.fitToWindow(); }
        });
        bar.add(fitBtn);

        JButton inBtn = new JButton("+");
        inBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { canvas.zoomIn(); }
        });
        bar.add(inBtn);

        JButton outBtn = new JButton("-");
        outBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { canvas.zoomOut(); }
        });
        bar.add(outBtn);

        JButton oneBtn = new JButton("1:1");
        oneBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { canvas.resetZoom(); }
        });
        bar.add(oneBtn);

        bar.addSeparator();

        JButton renderBtn = new JButton("Render");
        renderBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { renderDiagram(); }
        });
        bar.add(renderBtn);

        rightPanel.add(bar, BorderLayout.NORTH);
        rightPanel.add(canvas, BorderLayout.CENTER);

        // ===== Split Pane =====
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, rightPanel);
        split.setDividerLocation(580);
        split.setResizeWeight(0.4);

        // ===== Status Bar =====
        statusLabel = new JLabel("  Bereit - Tippe Java-Code ein...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        add(split, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        // ===== Auto-Render mit Debounce (800ms nach letzter Eingabe) =====
        debounceTimer = new Timer(800, new ActionListener() {
            public void actionPerformed(ActionEvent e) { renderDiagram(); }
        });
        debounceTimer.setRepeats(false);

        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { debounceTimer.restart(); }
            public void removeUpdate(DocumentEvent e)   { debounceTimer.restart(); }
            public void changedUpdate(DocumentEvent e)  { debounceTimer.restart(); }
        });

        // ===== Beispiel-Code laden =====
        editor.setText(SAMPLE_CODE);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() { renderDiagram(); }
        });
    }

    private void applyDarkTheme(RSyntaxTextArea textArea) {
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream(
                    "/org/fife/ui/rsyntaxtextarea/themes/monokai.xml"));
            theme.apply(textArea);
        } catch (IOException e) {
            // Fallback: Standard-Theme
        } catch (NullPointerException e) {
            // Theme-Ressource nicht gefunden
        }
    }

    private void renderDiagram() {
        String code = editor.getText();
        String mermaid = javaToMermaid(code);

        if (mermaid.trim().equals("classDiagram")) {
            statusLabel.setText("  Kein Diagramm - keine Klassen/Interfaces gefunden");
            canvas.clearImage();
            return;
        }

        statusLabel.setText("  Rendering...");

        final String mermaidCode = mermaid;
        new SwingWorker<BufferedImage, Void>() {
            protected BufferedImage doInBackground() {
                try {
                    BufferedImage img = Mermaid.renderToImage(mermaidCode, 2048);
                    return Mermaid.autoCrop(img);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return null;
                }
            }

            protected void done() {
                try {
                    BufferedImage img = get();
                    if (img != null) {
                        canvas.setImage(img);
                        statusLabel.setText("  OK - " + img.getWidth() + "x" + img.getHeight()
                                + " px  |  Mermaid: " + mermaidCode.split("\n").length + " Zeilen");
                    } else {
                        statusLabel.setText("  Render-Fehler - pruefe die Code-Syntax");
                    }
                } catch (Exception ex) {
                    statusLabel.setText("  Fehler: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ================================================================
    //  Java-Code -> Mermaid classDiagram  (einfacher Regex-Parser)
    // ================================================================

    static String javaToMermaid(String javaCode) {
        StringBuilder sb = new StringBuilder("classDiagram\n");
        List<ClassInfo> classes = parseClasses(javaCode);

        for (ClassInfo ci : classes) {
            sb.append("    class ").append(ci.name).append(" {\n");
            if (ci.isInterface) {
                sb.append("        <<interface>>\n");
            } else if (ci.isAbstract) {
                sb.append("        <<abstract>>\n");
            } else if (ci.isEnum) {
                sb.append("        <<enumeration>>\n");
            }

            for (String field : ci.fields) {
                sb.append("        ").append(field).append("\n");
            }
            for (String method : ci.methods) {
                sb.append("        ").append(method).append("\n");
            }
            sb.append("    }\n");

            if (ci.extendsClass != null) {
                sb.append("    ").append(ci.extendsClass).append(" <|-- ").append(ci.name).append("\n");
            }
            for (String iface : ci.implementsList) {
                sb.append("    ").append(iface).append(" <|.. ").append(ci.name).append("\n");
            }
        }

        // Assoziationen
        Set<String> knownNames = new HashSet<String>();
        for (ClassInfo ci : classes) {
            knownNames.add(ci.name);
        }

        for (ClassInfo ci : classes) {
            for (String rawField : ci.rawFieldTypes) {
                for (String known : knownNames) {
                    if (!known.equals(ci.name) && rawField.contains(known)) {
                        boolean isCollection = rawField.matches(".*(List|Set|Collection|Map).*");
                        if (isCollection) {
                            sb.append("    ").append(ci.name).append(" o-- \"*\" ").append(known).append("\n");
                        } else {
                            sb.append("    ").append(ci.name).append(" --> ").append(known).append("\n");
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    static List<ClassInfo> parseClasses(String code) {
        List<ClassInfo> result = new ArrayList<ClassInfo>();
        String cleaned = code.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("//.*", "");

        Pattern classPattern = Pattern.compile(
                "(public\\s+|private\\s+|protected\\s+)?" +
                "(abstract\\s+)?" +
                "(static\\s+)?" +
                "(class|interface|enum)\\s+" +
                "(\\w+)" +
                "(?:<[^>]*>)?" +
                "(?:\\s+extends\\s+(\\w+)(?:<[^>]*>)?)?" +
                "(?:\\s+implements\\s+([\\w,\\s<>]+))?" +
                "\\s*\\{"
        );

        Matcher cm = classPattern.matcher(cleaned);
        while (cm.find()) {
            ClassInfo ci = new ClassInfo();
            ci.isAbstract = cm.group(2) != null;
            String kind = cm.group(4);
            ci.isInterface = "interface".equals(kind);
            ci.isEnum = "enum".equals(kind);
            ci.name = cm.group(5);
            ci.extendsClass = cm.group(6);

            String implStr = cm.group(7);
            if (implStr != null) {
                for (String s : implStr.split(",")) {
                    String trimmed = s.trim().replaceAll("<.*>", "");
                    if (!trimmed.isEmpty()) {
                        ci.implementsList.add(trimmed);
                    }
                }
            }

            int braceStart = cm.end() - 1;
            String body = extractBody(cleaned, braceStart);
            parseMembers(body, ci);
            result.add(ci);
        }
        return result;
    }

    static String extractBody(String code, int openBrace) {
        int depth = 0;
        int start = openBrace + 1;
        for (int i = openBrace; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(start, i);
                }
            }
        }
        return code.substring(start);
    }

    static void parseMembers(String body, ClassInfo ci) {
        if (ci.isEnum) {
            Pattern enumConst = Pattern.compile("^\\s*(\\w+)\\s*[,;({]", Pattern.MULTILINE);
            Matcher em = enumConst.matcher(body);
            while (em.find()) {
                String name = em.group(1);
                if (!isKeyword(name)) {
                    ci.fields.add(name);
                }
            }
        }

        Pattern fieldPat = Pattern.compile(
                "^\\s*(public|private|protected)?\\s*(static\\s+)?(final\\s+)?" +
                "([A-Z]\\w*(?:<[^>]*>)?(?:\\[\\])?)\\s+(\\w+)\\s*[;=]",
                Pattern.MULTILINE
        );
        Matcher fm = fieldPat.matcher(body);
        while (fm.find()) {
            String vis = fm.group(1);
            String type = fm.group(4);
            String name = fm.group(5);
            ci.fields.add(visPrefix(vis) + type + " " + name);
            ci.rawFieldTypes.add(type);
        }

        Pattern primFieldPat = Pattern.compile(
                "^\\s*(public|private|protected)?\\s*(static\\s+)?(final\\s+)?" +
                "(int|long|double|float|boolean|char|byte|short)\\s+(\\w+)\\s*[;=]",
                Pattern.MULTILINE
        );
        Matcher pfm = primFieldPat.matcher(body);
        while (pfm.find()) {
            String vis = pfm.group(1);
            String type = pfm.group(4);
            String name = pfm.group(5);
            ci.fields.add(visPrefix(vis) + type + " " + name);
        }

        Pattern methodPat = Pattern.compile(
                "^\\s*(public|private|protected)?\\s*(static\\s+)?(abstract\\s+)?" +
                "(?:final\\s+)?(?:synchronized\\s+)?" +
                "([A-Za-z]\\w*(?:<[^>]*>)?(?:\\[\\])?)\\s+" +
                "(\\w+)\\s*\\(([^)]*)\\)",
                Pattern.MULTILINE
        );
        Matcher mm = methodPat.matcher(body);
        while (mm.find()) {
            String vis = mm.group(1);
            String returnType = mm.group(4);
            String name = mm.group(5);
            String params = mm.group(6).trim();

            if (isKeyword(name)) {
                continue;
            }

            String paramStr = shortenParams(params);
            String abs = mm.group(3) != null ? "*" : "";
            ci.methods.add(visPrefix(vis) + name + "(" + paramStr + ")" + abs + " " + returnType);
        }
    }

    static boolean isKeyword(String word) {
        return "if".equals(word) || "for".equals(word) || "while".equals(word)
                || "switch".equals(word) || "catch".equals(word) || "new".equals(word)
                || "return".equals(word) || "throw".equals(word) || "class".equals(word)
                || "public".equals(word) || "private".equals(word) || "protected".equals(word)
                || "static".equals(word) || "final".equals(word) || "abstract".equals(word)
                || "void".equals(word) || "int".equals(word) || "boolean".equals(word)
                || "String".equals(word);
    }

    static String visPrefix(String vis) {
        if (vis == null) return "~";
        switch (vis) {
            case "public":    return "+";
            case "private":   return "-";
            case "protected": return "#";
            default:          return "~";
        }
    }

    static String shortenParams(String params) {
        if (params.isEmpty()) return "";
        String[] parts = params.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            String[] tokens = p.split("\\s+");
            if (tokens.length >= 2) {
                sb.append(tokens[tokens.length - 2]);
            } else {
                sb.append(p);
            }
            if (i < parts.length - 1) sb.append(", ");
        }
        return sb.toString();
    }

    // ================================================================
    //  Diagram Canvas mit Zoom / Pan
    // ================================================================

    static class DiagramCanvas extends JPanel {
        private BufferedImage image;
        private double zoom = 1.0;
        private int offX = 0;
        private int offY = 0;
        private Point dragStart;

        DiagramCanvas() {
            setBackground(Color.WHITE);

            addMouseWheelListener(new MouseWheelListener() {
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (image == null) return;
                    double old = zoom;
                    if (e.getWheelRotation() < 0) {
                        zoom *= 1.15;
                    } else {
                        zoom /= 1.15;
                    }
                    zoom = Math.max(0.05, Math.min(zoom, 20.0));
                    int mx = e.getX();
                    int my = e.getY();
                    offX = (int) (mx - (mx - offX) * (zoom / old));
                    offY = (int) (my - (my - offY) * (zoom / old));
                    repaint();
                }
            });

            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                    setCursor(Cursor.getDefaultCursor());
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        offX += e.getX() - dragStart.x;
                        offY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        void setImage(BufferedImage img) {
            this.image = img;
            fitToWindow();
        }

        void clearImage() {
            this.image = null;
            repaint();
        }

        void fitToWindow() {
            if (image == null) return;
            int cw = getWidth();
            int ch = getHeight();
            if (cw <= 0 || ch <= 0) {
                repaint();
                return;
            }
            double sx = (double) cw / image.getWidth();
            double sy = (double) ch / image.getHeight();
            zoom = Math.min(sx, sy) * 0.95;
            int w = (int) (image.getWidth() * zoom);
            int h = (int) (image.getHeight() * zoom);
            offX = (cw - w) / 2;
            offY = (ch - h) / 2;
            repaint();
        }

        void zoomIn() {
            zoom *= 1.3;
            repaint();
        }

        void zoomOut() {
            zoom = Math.max(0.05, zoom / 1.3);
            repaint();
        }

        void resetZoom() {
            zoom = 1.0;
            offX = 0;
            offY = 0;
            repaint();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                g.setColor(Color.GRAY);
                g.setFont(new Font("SansSerif", Font.ITALIC, 16));
                g.drawString("Diagramm wird hier angezeigt...", 40, 60);
                return;
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = (int) (image.getWidth() * zoom);
            int h = (int) (image.getHeight() * zoom);
            g2.drawImage(image, offX, offY, w, h, null);
        }
    }

    // ================================================================
    //  Data class
    // ================================================================

    static class ClassInfo {
        String name;
        boolean isInterface;
        boolean isAbstract;
        boolean isEnum;
        String extendsClass;
        List<String> implementsList = new ArrayList<String>();
        List<String> fields = new ArrayList<String>();
        List<String> methods = new ArrayList<String>();
        List<String> rawFieldTypes = new ArrayList<String>();
    }

    // ================================================================
    //  Beispiel-Code (wird beim Start geladen)
    // ================================================================

    private static final String SAMPLE_CODE =
            "// Beispiel: Einfaches Bestellsystem\n" +
            "// Aendere den Code und das Diagramm aktualisiert sich live!\n" +
            "\n" +
            "public interface Payable {\n" +
            "    double calculateTotal();\n" +
            "    void processPayment(String method);\n" +
            "}\n" +
            "\n" +
            "public abstract class Person {\n" +
            "    protected String name;\n" +
            "    protected int age;\n" +
            "    private String email;\n" +
            "\n" +
            "    public abstract String getRole();\n" +
            "    public String getName() { return name; }\n" +
            "}\n" +
            "\n" +
            "public class Customer extends Person implements Payable {\n" +
            "    private String customerId;\n" +
            "    private Address address;\n" +
            "    private List<Order> orders;\n" +
            "\n" +
            "    public String getRole() { return \"Customer\"; }\n" +
            "    public double calculateTotal() { return 0; }\n" +
            "    public void processPayment(String method) {}\n" +
            "    public void placeOrder(Order order) {}\n" +
            "}\n" +
            "\n" +
            "public class Employee extends Person {\n" +
            "    private String department;\n" +
            "    private double salary;\n" +
            "\n" +
            "    public String getRole() { return \"Employee\"; }\n" +
            "    public void promote(String newDept) {}\n" +
            "}\n" +
            "\n" +
            "public class Address {\n" +
            "    private String street;\n" +
            "    private String city;\n" +
            "    private String zipCode;\n" +
            "\n" +
            "    public String getFullAddress() { return \"\"; }\n" +
            "}\n" +
            "\n" +
            "public class Order implements Payable {\n" +
            "    private String orderId;\n" +
            "    private List<Product> items;\n" +
            "    private Customer customer;\n" +
            "\n" +
            "    public double calculateTotal() { return 0; }\n" +
            "    public void processPayment(String method) {}\n" +
            "    public void addItem(Product p) {}\n" +
            "    public void removeItem(Product p) {}\n" +
            "}\n" +
            "\n" +
            "public class Product {\n" +
            "    private String productId;\n" +
            "    private String name;\n" +
            "    private double price;\n" +
            "    private int stock;\n" +
            "\n" +
            "    public boolean isAvailable() { return stock > 0; }\n" +
            "    public void restock(int amount) {}\n" +
            "}\n" +
            "\n" +
            "public enum OrderStatus {\n" +
            "    PENDING,\n" +
            "    CONFIRMED,\n" +
            "    SHIPPED,\n" +
            "    DELIVERED,\n" +
            "    CANCELLED;\n" +
            "}\n";

    // ================================================================
    //  Main
    // ================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new MermaidCoder().setVisible(true);
            }
        });
    }
}

