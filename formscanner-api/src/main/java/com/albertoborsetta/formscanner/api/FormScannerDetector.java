/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.albertoborsetta.formscanner.api;

import com.albertoborsetta.formscanner.api.commons.Constants;
import java.awt.image.BufferedImage;
import java.util.HashMap;

/**
 *
 * @author Alberto Borsetta
 */
public abstract class FormScannerDetector {

    public static final int WHITE_PIXEL = 1;
    public static final int BLACK_PIXEL = 0;
    public static final int HALF_WINDOW_SIZE = 5;
    public static final int WINDOW_SIZE = (HALF_WINDOW_SIZE * 2) + 1;

    public final int threshold;
    public final int density;
    public final BufferedImage image;

    public final int height;
    public final int width;
    public final int subImageWidth;
    public final int subImageHeight;

    public final FormTemplate template;
    public final FormTemplate parent;

    protected FormScannerDetector(BufferedImage image, FormTemplate template) {
        this(0, 0, image, template);
    }

    protected FormScannerDetector(int threshold, int density, BufferedImage image, FormTemplate template) {
        this.image = image;

        height = image.getHeight();
        width = image.getWidth();

        subImageWidth = width / 2;
        subImageHeight = height / 2;
        
        this.template = template;
        parent = template != null ? template.getParentTemplate() : null;
        
        this.threshold = threshold;
        this.density = density;
    }
    
    protected FormScannerDetector(int threshold, int density, BufferedImage image) {
        this(threshold, density, image, null);
    }

    protected FormPoint calcResponsePoint(FormPoint responsePoint) {
        double[] h = getHomography();
        double x = responsePoint.getX();
        double y = responsePoint.getY();
        double w = (h[6] * x) + (h[7] * y) + h[8];
        double px = ((h[0] * x) + (h[1] * y) + h[2]) / w;
        double py = ((h[3] * x) + (h[4] * y) + h[5]) / w;
        return new FormPoint(px, py);
    }

    private double[] homography;

    private double[] getHomography() {
        if (homography == null) {
            homography = computeHomography(parent.getCorners(), template.getCorners());
        }
        return homography;
    }

    private static double[] computeHomography(HashMap<Constants.Corners, FormPoint> src,
            HashMap<Constants.Corners, FormPoint> dst) {
        double[][] m = new double[8][9];
        int row = 0;
        for (Constants.Corners corner : Constants.Corners.values()) {
            FormPoint s = src.get(corner);
            FormPoint d = dst.get(corner);
            fill(m, row, s.getX(), s.getY(), d.getX(), d.getY());
            row += 2;
        }
        return solve(m);
    }

    private static void fill(double[][] m, int row, double x, double y, double dx, double dy) {
        m[row][0] = x;
        m[row][1] = y;
        m[row][2] = 1;
        m[row][3] = 0;
        m[row][4] = 0;
        m[row][5] = 0;
        m[row][6] = -dx * x;
        m[row][7] = -dx * y;
        m[row][8] = dx;
        m[row + 1][0] = 0;
        m[row + 1][1] = 0;
        m[row + 1][2] = 0;
        m[row + 1][3] = x;
        m[row + 1][4] = y;
        m[row + 1][5] = 1;
        m[row + 1][6] = -dy * x;
        m[row + 1][7] = -dy * y;
        m[row + 1][8] = dy;
    }

    private static double[] solve(double[][] m) {
        for (int col = 0; col < 8; col++) {
            int pivot = col;
            for (int r = col + 1; r < 8; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) {
                    pivot = r;
                }
            }
            double[] tmp = m[pivot];
            m[pivot] = m[col];
            m[col] = tmp;
            for (int r = 0; r < 8; r++) {
                if (r != col && m[r][col] != 0) {
                    double f = m[r][col] / m[col][col];
                    for (int c = 0; c < 9; c++) {
                        m[r][c] -= f * m[col][c];
                    }
                }
            }
        }
        double[] h = new double[9];
        for (int i = 0; i < 8; i++) {
            h[i] = m[i][8] / m[i][i];
        }
        h[8] = 1;
        return h;
    }

    protected int isWhite(int xi, int yi, int[] rgbArray) {
        int blacks = 0;
        int total = WINDOW_SIZE * WINDOW_SIZE;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            for (int j = 0; j < WINDOW_SIZE; j++) {
                int xji = xi - HALF_WINDOW_SIZE + j;
                int yji = yi - HALF_WINDOW_SIZE + i;
                int index = (yji * subImageWidth) + xji;
                
                int red = (rgbArray[index] >> 16) & (0xFF);
                int green = (rgbArray[index] >> 8) & (0xFF);
                int blue = rgbArray[index] & (0xFF);                
                int pixel = Math.max(red, Math.max(green, blue));                
                if (pixel < threshold) {
                    blacks++;
                }
            }
        }
        if ((blacks / (double) total) >= (density / 100.0)) {
            return BLACK_PIXEL;
        }
        return WHITE_PIXEL;
    }
}
