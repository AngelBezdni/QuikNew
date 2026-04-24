package org.example;

import org.example.ui.QuikDesktopFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new QuikDesktopFrame(args).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Ошибка запуска", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });
    }
}
