package com.aresstack.test;

import com.aresstack.Mermaid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Swing-Viewer fuer Mermaid-Diagramme mit Tabs, Zoom (Mausrad) und Pan (Drag).
 */
public class MermaidViewer extends JFrame {

    public MermaidViewer(Map<String, String> diagrams) {
        super("Mermaid Viewer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 800);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();

        for (Map.Entry<String, String> entry : diagrams.entrySet()) {
            String title = entry.getKey();
            String code = entry.getValue();

            System.out.println("Rendering: " + title + " ...");
            BufferedImage img = Mermaid.renderToImage(code, 2048);
            img = Mermaid.autoCrop(img);
            System.out.println("  -> " + img.getWidth() + " x " + img.getHeight());

            tabs.addTab(title, createDiagramPanel(img));
        }

        add(tabs, BorderLayout.CENTER);
    }

    private JPanel createDiagramPanel(BufferedImage image) {
        JPanel wrapper = new JPanel(new BorderLayout());

        double[] zoom = {1.0};
        int[] offset = {0, 0};
        Point[] dragStart = {null};

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                int w = (int) (image.getWidth() * zoom[0]);
                int h = (int) (image.getHeight() * zoom[0]);
                g2.drawImage(image, offset[0], offset[1], w, h, null);
            }
        };
        canvas.setBackground(Color.WHITE);

        // Zoom per Mausrad
        canvas.addMouseWheelListener(e -> {
            double old = zoom[0];
            zoom[0] *= (e.getWheelRotation() < 0) ? 1.15 : 1.0 / 1.15;
            zoom[0] = Math.max(0.05, Math.min(zoom[0], 20.0));
            int mx = e.getX(), my = e.getY();
            offset[0] = (int) (mx - (mx - offset[0]) * (zoom[0] / old));
            offset[1] = (int) (my - (my - offset[1]) * (zoom[0] / old));
            canvas.repaint();
        });

        // Pan per Drag
        canvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                dragStart[0] = e.getPoint();
                canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            public void mouseReleased(MouseEvent e) {
                dragStart[0] = null;
                canvas.setCursor(Cursor.getDefaultCursor());
            }
        });
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (dragStart[0] != null) {
                    offset[0] += e.getX() - dragStart[0].x;
                    offset[1] += e.getY() - dragStart[0].y;
                    dragStart[0] = e.getPoint();
                    canvas.repaint();
                }
            }
        });

        // Toolbar
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton fitBtn = new JButton("Fit");
        fitBtn.addActionListener(e -> fitToWindow(canvas, image, zoom, offset));
        bar.add(fitBtn);

        JButton inBtn = new JButton("+");
        inBtn.addActionListener(e -> { zoom[0] *= 1.3; canvas.repaint(); });
        bar.add(inBtn);

        JButton outBtn = new JButton("-");
        outBtn.addActionListener(e -> { zoom[0] = Math.max(0.05, zoom[0] / 1.3); canvas.repaint(); });
        bar.add(outBtn);

        JButton oneBtn = new JButton("1:1");
        oneBtn.addActionListener(e -> { zoom[0] = 1.0; offset[0] = 0; offset[1] = 0; canvas.repaint(); });
        bar.add(oneBtn);

        wrapper.add(bar, BorderLayout.NORTH);
        wrapper.add(canvas, BorderLayout.CENTER);

        // Fit nach Anzeige
        SwingUtilities.invokeLater(() -> fitToWindow(canvas, image, zoom, offset));
        return wrapper;
    }

    private void fitToWindow(JComponent canvas, BufferedImage img, double[] zoom, int[] offset) {
        int cw = canvas.getWidth(), ch = canvas.getHeight();
        if (cw <= 0 || ch <= 0) return;
        zoom[0] = Math.min((double) cw / img.getWidth(), (double) ch / img.getHeight()) * 0.95;
        int w = (int) (img.getWidth() * zoom[0]);
        int h = (int) (img.getHeight() * zoom[0]);
        offset[0] = (cw - w) / 2;
        offset[1] = (ch - h) / 2;
        canvas.repaint();
    }

    public static void main(String[] args) {
        Map<String, String> diagrams = new LinkedHashMap<>();

        // Tab 1: Bunte, vollgestopfte KI-Mindmap
        diagrams.put("KI Mindmap", String.join("\n",
                "mindmap",
                "  root((Kuenstliche Intelligenz))",
                "    Machine Learning",
                "      Supervised Learning",
                "        Regression",
                "        Klassifikation",
                "        Random Forest",
                "        SVM",
                "      Unsupervised Learning",
                "        Clustering",
                "        K-Means",
                "        Dimensionsreduktion",
                "        PCA",
                "      Reinforcement Learning",
                "        Q-Learning",
                "        Policy Gradient",
                "        AlphaGo",
                "    Deep Learning",
                "      Neuronale Netze",
                "        CNN",
                "        RNN",
                "        Transformer",
                "      Frameworks",
                "        TensorFlow",
                "        PyTorch",
                "        JAX",
                "      Anwendungen",
                "        Bilderkennung",
                "        Sprachsynthese",
                "        Autonomes Fahren",
                "    NLP",
                "      Chatbots",
                "        ChatGPT",
                "        Claude",
                "        Gemini",
                "      Aufgaben",
                "        Uebersetzung",
                "        Zusammenfassung",
                "        Sentimentanalyse",
                "      Modelle",
                "        BERT",
                "        GPT-4",
                "        LLaMA",
                "    Computer Vision",
                "      Objekterkennung",
                "        YOLO",
                "        R-CNN",
                "      Gesichtserkennung",
                "      Bildgenerierung",
                "        DALL-E",
                "        Stable Diffusion",
                "        Midjourney",
                "    Robotik",
                "      Industrieroboter",
                "      Drohnen",
                "      Humanoide Roboter",
                "        Atlas",
                "        Optimus",
                "    Ethik und Gesellschaft",
                "      Bias und Fairness",
                "      Datenschutz",
                "      Arbeitsmarkt",
                "      Regulierung",
                "        EU AI Act",
                "        UNESCO Empfehlung"
        ));

        // Tab 2: Flowchart
        diagrams.put("Flowchart", String.join("\n",
                "graph TD",
                "    A[Start] --> B{Entscheidung}",
                "    B -->|Ja| C[Aktion 1]",
                "    B -->|Nein| D[Aktion 2]",
                "    C --> E[Ende]",
                "    D --> E"
        ));

        // Tab 3: Sequenzdiagramm
        diagrams.put("Sequenz", String.join("\n",
                "sequenceDiagram",
                "    participant Browser",
                "    participant Server",
                "    participant DB",
                "    Browser->>Server: GET /api/users",
                "    Server->>DB: SELECT * FROM users",
                "    DB-->>Server: ResultSet",
                "    Server-->>Browser: JSON Response",
                "    Browser->>Server: POST /api/login",
                "    Server->>DB: SELECT password WHERE user=?",
                "    DB-->>Server: Hash",
                "    Server-->>Browser: JWT Token"
        ));

        // Tab 4: Klassendiagramm
        diagrams.put("Klassen", String.join("\n",
                "classDiagram",
                "    class Animal {",
                "        +String name",
                "        +int age",
                "        +makeSound()",
                "    }",
                "    class Dog {",
                "        +fetch()",
                "    }",
                "    class Cat {",
                "        +purr()",
                "    }",
                "    Animal <|-- Dog",
                "    Animal <|-- Cat"
        ));

        // Tab 5: Gantt
        diagrams.put("Gantt", String.join("\n",
                "gantt",
                "    title Projektplan",
                "    dateFormat YYYY-MM-DD",
                "    section Design",
                "        Wireframes      :a1, 2025-01-01, 14d",
                "        Prototyp        :a2, after a1, 10d",
                "    section Entwicklung",
                "        Backend         :b1, after a2, 21d",
                "        Frontend        :b2, after a2, 28d",
                "    section Test",
                "        Integration     :c1, after b2, 7d",
                "        Release         :milestone, after c1, 0d"
        ));

        SwingUtilities.invokeLater(() ->
            new MermaidViewer(diagrams).setVisible(true)
        );
    }
}

