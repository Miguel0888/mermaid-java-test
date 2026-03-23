package com.aresstack.test;

import com.aresstack.Mermaid;

public class Main {

    public static void main(String[] args) {
        // Variante 1: Einfach über die Fassade
        String svg = Mermaid.render("graph TD; A-->B;");
        System.out.println(svg);
    }
}
