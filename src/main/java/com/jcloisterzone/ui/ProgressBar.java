package com.jcloisterzone.ui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.WindowListener;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JProgressBar;

public class ProgressBar extends JFrame {

    private JProgressBar progressBar;

    public ProgressBar(WindowListener listener){
        this.setLocation(200, 200);
        this.setTitle("Animating Game");
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        this.setPreferredSize(new Dimension(250, 45));
        Container content = this.getContentPane();
        this.progressBar = new JProgressBar(0,100);
        progressBar.setStringPainted(true);
        
        this.add(progressBar);
        this.pack();
        content.add(progressBar);
        this.setResizable(false);
        this.addWindowListener(listener);
    }

    public void setValue(int i, String string) {
        this.progressBar.setValue(i);
        this.progressBar.setString(string);
        this.progressBar.paintImmediately(0,0,progressBar.getWidth(), progressBar.getHeight());
    }
    
    @Override
    public void dispose() {
        // TODO Auto-generated method stub
        super.dispose();
    }
}
