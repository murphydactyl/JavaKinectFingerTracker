////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
//
//  FingerTrackerApp.java
//
//  Real-time finger tracking with Kinect
//  Using fast implementation of marching squares and libfreenect
//
//
//  AUTHOR:   Murphy Stein
//            New York University
//  CREATED:  Jan 2012
//
//  LICENSE:  BSD
//
//  Copyright (c) 2012 New York University.
//  All rights reserved.
//
//  Redistribution and use in source and binary forms are permitted
//  provided that the above copyright notice and this paragraph are
//  duplicated in all such forms and that any documentation,
//  advertising materials, and other materials related to such
//  distribution and use acknowledge that the software was developed
//  by New York Univserity.  The name of the
//  University may not be used to endorse or promote products derived
//  from this software without specific prior written permission.
//  THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR
//  IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED
//  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
import org.openkinect.freenect.*;
import canvasframe.*;
import imageprocessor.*;
import kinectdigitizer.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.event.*;
import java.util.Arrays;

public class FingerTrackerApp implements  Runnable, 
WindowListener, 
KeyListener, 
MouseListener, 
MouseMotionListener
{
	
	int w = 640;
	int h = 480;
	int[] pixDepth = new int[w * h];
	int[] pix = new int[w * h];
  
  ////////////////////////////////////////////////////
  // FingerTracker Instance
  ////////////////////////////////////////////////////
	FingerTracker fingers;	

	
  ////////////////////////////////////////////////////
  // Kinect Instance
  ////////////////////////////////////////////////////
  KinectDigitizer kd;

  ////////////////////////////////////////////////////
  // Fast Java/AWT Window for Visualization
  ////////////////////////////////////////////////////
  CanvasFrame view;	
	
  ////////////////////////////////////////////////////
  // ImageProcessor instance for utility methods
  ////////////////////////////////////////////////////
  ImageProcessor ip;
    
  ////////////////////////////////////////////////////
  // Handle to AWT Graphics for drawing with AWT
  ////////////////////////////////////////////////////
  Graphics2D g; 

  ////////////////////////////////////////////////////
  // Depth cutoff
  // libfreenect returns values in range 500-2047
  // with 2047 the point at infinity
  ////////////////////////////////////////////////////
  int depthcutoff = 625;


	public FingerTrackerApp() {
		kd = new KinectDigitizer(0);
		kd.start();
		view = new CanvasFrame(this);
		view.setLocation(0, 0);		
    g = view.getGraphics2D();     
		ip = new ImageProcessor(640, 480);
		fingers = new FingerTracker(640, 480);
    
	}
	
	public void run() {
    
		for (int frameCount = 0 ; true ; frameCount++) {
      
      ////////////////////////////////////////////////////
      // COPY depth image from Kinect
      ////////////////////////////////////////////////////      
      kd.getDepthFrame(pixDepth);
      ip.cvtFlipHorizontal(pixDepth, pixDepth);				
      
//      for (int i = 0; i < pixDepth.length; i++) {
//        if (pixDepth[i] < depthcutoff) {
//          pix[i] = 255;
//        } else {
//          pix[i] = 0;
//        }
//      }
      fingers.setThreshold(depthcutoff);
      fingers.update(pixDepth);
      ip.cvtDataToRGB(pixDepth);
      view.copy(pixDepth);
      

      ////////////////////////////////////////////////////
      // DRAW CONTOURS
      ////////////////////////////////////////////////////      
      FindIsolines fc = fingers.fc;
      int numcontours = fc.getNumContours();
      g.setColor(Color.green);
      for (int k = 0; k < numcontours; k++) {
          fc.drawContour(g, k);
      }

      ////////////////////////////////////////////////////
      // DRAW FINGERTIPS
      ////////////////////////////////////////////////////      
      int numfingers = fingers.getNumFingers();
      g.setColor(Color.yellow);
      for (int i = 0; i < numfingers; i++) {
        int x = (int)fingers.getFingerX(i);
        int y = (int)fingers.getFingerY(i);
        g.fillOval(x - 5, y - 5, 10, 10);
      }

      
			view.update();      
		}
	}
  
  ////////////////////////////////////////////////////
  // UTILITY METHODS
  ////////////////////////////////////////////////////      
  public void out(String msg) {
		System.out.println(msg);
	}
  
  ////////////////////////////////////////////////////
  // WINDOW EVENTS
  ////////////////////////////////////////////////////      
	public void windowClosing(WindowEvent e) {
		System.out.println("Window was closed.");
		if (kd != null) kd.shutdown();
	}
	public void windowDeactivated(WindowEvent e) {}
	public void windowActivated(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	
  ////////////////////////////////////////////////////
  // KEYBOARD EVENTS
  ////////////////////////////////////////////////////      	
	public void keyTyped(KeyEvent e) {}	
	public void keyReleased(KeyEvent e) {}
	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();
		switch( keyCode ) { 
			case KeyEvent.VK_Q: depthcutoff -= 1; out("depthcutoff: " + depthcutoff); break;
			case KeyEvent.VK_W: depthcutoff += 1; out("depthcutoff: " + depthcutoff); break;
			default: break;
		}
	}
	
  ////////////////////////////////////////////////////
  // MOUSE EVENTS
  ////////////////////////////////////////////////////      	
	public void mouseClicked(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseMoved(MouseEvent e) {}
	public void mouseDragged(MouseEvent e) {}
  
  
  ////////////////////////////////////////////////////
  // SETUP THE APP
  ////////////////////////////////////////////////////      	
	public static void main(String[] args) {
		FingerTrackerApp app = new FingerTrackerApp();      
		app.run();
	}
    
}

