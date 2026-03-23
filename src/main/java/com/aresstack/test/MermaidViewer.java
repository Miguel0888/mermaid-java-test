package com.aresstack.test;

import com.aresstack.Mermaid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Swing-Viewer für Mermaid-Diagramme mit Zoom (Mausrad) und Pan (Drag).
 */
public class MermaidViewer extends JFrame {

    private BufferedImage image;
    private double zoom = 1.0;
    private int offsetX = 0, offsetY = 0;
    private Point dragStart;

    public MermaidViewer(String mermaidCode) {
        super("Mermaid Viewer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);

        // Mermaid → BufferedImage (Breite 2048px für gute Qualität)
        System.out.println("Rendering Mermaid diagram...");
        image = Mermaid.renderToImage(mermaidCode, 2048);
        image = Mermaid.autoCrop(image);
        System.out.println("Image size: " + image.getWidth() + " x " + image.getHeight());

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);

                int w = (int) (image.getWidth() * zoom);
                int h = (int) (image.getHeight() * zoom);
                g2.drawImage(image, offsetX, offsetY, w, h, null);
            }
        };
        canvas.setBackground(Color.WHITE);

        // Zoom per Mausrad
        canvas.addMouseWheelListener(e -> {
            double oldZoom = zoom;
            if (e.getWheelRotation() < 0) {
                zoom *= 1.15;   // Rein-Zoomen
            } else {
                zoom /= 1.15;   // Raus-Zoomen
            }
            zoom = Math.max(0.05, Math.min(zoom, 20.0));

            // Zoom zentriert auf Mausposition
            int mx = e.getX();
            int my = e.getY();
            offsetX = (int) (mx - (mx - offsetX) * (zoom / oldZoom));
            offsetY = (int) (my - (my - offsetY) * (zoom / oldZoom));

            canvas.repaint();
        });

        // Pan per Drag
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                canvas.setCursor(Cursor.getDefaultCursor());
            }
        });
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    offsetX += e.getX() - dragStart.x;
                    offsetY += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                    canvas.repaint();
                }
            }
        });

        // Fit-to-Window beim Start
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                fitToWindow(canvas);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                // Optional: bei Resize neu einpassen
            }
        });

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton fitBtn = new JButton("Fit");
        fitBtn.setToolTipText("Bild einpassen");
        fitBtn.addActionListener(e -> fitToWindow(canvas));
        toolbar.add(fitBtn);

        JButton zoomInBtn = new JButton("+");
        zoomInBtn.setToolTipText("Zoom In");
        zoomInBtn.addActionListener(e -> {
            zoom *= 1.3;
            canvas.repaint();
        });
        toolbar.add(zoomInBtn);

        JButton zoomOutBtn = new JButton("−");
        zoomOutBtn.setToolTipText("Zoom Out");
        zoomOutBtn.addActionListener(e -> {
            zoom /= 1.3;
            zoom = Math.max(0.05, zoom);
            canvas.repaint();
        });
        toolbar.add(zoomOutBtn);

        JButton oneToOneBtn = new JButton("1:1");
        oneToOneBtn.setToolTipText("Originalgröße");
        oneToOneBtn.addActionListener(e -> {
            zoom = 1.0;
            offsetX = 0;
            offsetY = 0;
            canvas.repaint();
        });
        toolbar.add(oneToOneBtn);

        add(toolbar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        // Initiale Einpassung nach dem Sichtbarwerden
        SwingUtilities.invokeLater(() -> fitToWindow(canvas));
    }

    private void fitToWindow(JComponent canvas) {
        if (image == null) return;
        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        if (cw <= 0 || ch <= 0) return;

        double scaleX = (double) cw / image.getWidth();
        double scaleY = (double) ch / image.getHeight();
        zoom = Math.min(scaleX, scaleY) * 0.95; // 5% Padding

        int w = (int) (image.getWidth() * zoom);
        int h = (int) (image.getHeight() * zoom);
        offsetX = (cw - w) / 2;
        offsetY = (ch - h) / 2;
        canvas.repaint();
    }

    public static void main(String[] args) {
        String diagram = String.join("\n",
                "graph TD",
                "    A[Start] --> B{Entscheidung}",
                "    B -->|Ja| C[Aktion 1]",
                "    B -->|Nein| D[Aktion 2]",
                "    C --> E[Ende]",
                "    D --> E"
        );

        SwingUtilities.invokeLater(() -> {
            new MermaidViewer(diagram).setVisible(true);
        });
    }
}

