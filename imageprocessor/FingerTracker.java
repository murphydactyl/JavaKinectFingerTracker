////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
//
//  FingerTracker.java
//
//  Uses marching squares to trace isolines of depth image
//  Then finds fingerpoints as points of high negative curvature
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
package imageprocessor;

import java.awt.*;
import java.util.Arrays;

public class FingerTracker {
  
	ImageProcessor ip;
	public FindIsolines fc;
	int w;
	int h;
	int numfingers;
	int[] tmp;
	int meltFactor = 20;
	double[] screenx;
	double[] screeny;
	double[] normalx;
	double[] normaly;
	int[] contour;
	boolean printVerbose = true;
	double FINGER_RADIUS = 15.0;				// perimeter of a fingertip
	double ROUNDNESS_THRESHOLD = -1.33;			// minimum allowable value for 
                                          // ratio of area / perimeter of fingertip
  
	public FingerTracker(int w, int h) {
		this.w = w;
		this.h = h;
		ip = new ImageProcessor(w, h);
		fc = new FindIsolines(w, h);
		setThreshold(128);
		
		screenx = new double[w * h];
		screeny = new double[w * h];
		normalx = new double[w * h];
		normaly = new double[w * h];
		contour = new int[w * h];
		tmp = new int[w * h];
		
	}
  
  public void setThreshold(int value) {
    fc.setThreshold(value);
  }
  
	public void setMeltFactor(int value) {
    meltFactor = value;
	}
  
	public void update(int[] pix) {

    ////////////////////////////////////////////////////
    // FIND contours in pix
    ////////////////////////////////////////////////////
		int numcontours = fc.find(pix);
    
    ////////////////////////////////////////////////////
    // MELT Contours
    ////////////////////////////////////////////////////
    for (int i = 0 ; i < meltFactor ; i++)
      fc.meltContours();
    

    ////////////////////////////////////////////////////
    // FIND Fingers on contours
    // Use 1D connected components (spans)
    // Assume fingers are centers of these 1-D spans
    ////////////////////////////////////////////////////
    numfingers = 0;    
    int spanoffset = 0;
    int window = (int)FINGER_RADIUS;
    double[] tips = fc.findRoundedCorners(window);
    numfingers = 0;
    for (int k = 0; k < numcontours; k++) {
      int l = fc.getContourLength(k);
      for (int i = 0; i < l; i++) {
        if (tips[fc.getValidIndex(k, i)] > ROUNDNESS_THRESHOLD) {
          spanoffset = i + 1;
          break;
        }
      }
      int span = 0;
      for (int i = spanoffset; i < l + spanoffset; i++) {
        double roundness = tips[fc.getValidIndex(k, i)];
        
        if (roundness <= ROUNDNESS_THRESHOLD) {
          span++;
        } else {
          if (span > 0) {
            int tip = (i - 1) - span/2;
            int lo = tip - window;
            int hi = tip + window;
            if (fc.measureDistance(k, lo, hi) < 2 * FINGER_RADIUS) {
              double cx = 0;
              double cy = 0;
              for (int j = lo; j <= hi; j++) {
                cx += fc.getContourX(k,j);
                cy += fc.getContourY(k,j);
              }
              cx = cx / (2 * window + 1);
              cy = cy / (2 * window + 1);
              screenx[numfingers] = cx;
              screeny[numfingers] = cy;
              contour[numfingers] = k;              
              numfingers++;
            }
            
          }
          span = 0;
        }
      }
    }
  }
  
  public double getFingerX(int i) {
    return screenx[i];
  }
  
  public double getFingerY(int i) {
    return screeny[i];
  }
    
  public int getContour(int i) {
    return contour[i];
  }
  
  public int getNumFingers() {
    return numfingers;
  }
  
  public void setPrintVerbose(boolean val) {
    printVerbose = val;
  }
  
  public void out(String s) {
    if (printVerbose) {
      System.out.println(s);
    }
  }
  
}
