/*
 * @author   Raúl López
 * @version  1.0
 * @year     2020
 */

package com.rlopez.molecare.analysis;

import android.annotation.SuppressLint;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MoleModel {

    private String name;
    private Date date;
    private Double focusDistance;
    private Double focalLength;
    private Double sensorHeight;
    private Double dpi;
    private Double originalImageHeight;
    private Mat originalImage;
    private Mat binarySegmentedImage;
    private Mat colouredSegmentedImage;
    private String path;
    private double diameter;
    private double hue;
    private double shapeCorrelation;

    public MoleModel(String name, String focusDistance, String focalLength, String sensorHeight, String originalImageHeight, Double dpi, Mat originalImage, String path) {
        this.name = name;
        this.date = getDateFromName(name);
        this.focusDistance = Double.parseDouble(focusDistance);
        this.focalLength = Double.parseDouble(focalLength);
        this.sensorHeight = Double.parseDouble(sensorHeight);
        this.originalImageHeight = Double.parseDouble(originalImageHeight);
        this.dpi = dpi;
        this.originalImage = originalImage;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Double getFocusDistance() {
        return focusDistance;
    }

    public void setFocusDistance(Double focusDistance) {
        this.focusDistance = focusDistance;
    }

    public Double getFocalLength() {
        return focalLength;
    }

    public void setFocalLength(Double focalLength) {
        this.focalLength = focalLength;
    }

    public Double getDpi() {
        return dpi;
    }

    public void setDpi(Double dpi) {
        this.dpi = dpi;
    }

    public Mat getOriginalImage() {
        return originalImage;
    }

    public void setOriginalImage(Mat originalImage) {
        this.originalImage = originalImage;
    }

    public Mat getBinarySegmentedImage() {
        return binarySegmentedImage;
    }

    public void setBinarySegmentedImage(Mat binarySegmentedImage) {
        this.binarySegmentedImage = binarySegmentedImage;
    }

    public Mat getColouredSegmentedImage() {
        return colouredSegmentedImage;
    }

    public void setColouredSegmentedImage(Mat colouredSegmentedImage) {
        this.colouredSegmentedImage = colouredSegmentedImage;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public double getDiameter() {
        return diameter;
    }

    public void setDiameter(double diameter) {
        this.diameter = diameter;
    }

    public double getHue() {
        return hue;
    }

    public void setHue(double hue) {
        this.hue = hue;
    }

    public double getShapeCorrelation() {
        return shapeCorrelation;
    }

    public void setShapeCorrelation(double shapeCorrelation) {
        this.shapeCorrelation = shapeCorrelation;
    }

    public Double getSensorHeight() {
        return sensorHeight;
    }

    public void setSensorHeight(Double sensorHeight) {
        this.sensorHeight = sensorHeight;
    }

    public Double getOriginalImageHeight() {
        return originalImageHeight;
    }

    public void setOriginalImageHeight(Double originalImageHeight) {
        this.originalImageHeight = originalImageHeight;
    }

    @SuppressLint("SimpleDateFormat")
    private Date getDateFromName(String name) {
        String[] parts = name.split("_");
        String stringDate = parts[1];
        Date date = null;
        try {
            date = new SimpleDateFormat("yyyyMMdd").parse(stringDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date;
    }

    // Save binary and coloured segmented image in a subfolder
    public void saveSegmentedImages() {
        String parentFile = new File(getPath()).getParent();
        File binarySegmentedFile = new File(parentFile, "binary");
        File colouredSegmentedFile = new File(parentFile, "coloured");
        binarySegmentedFile.mkdirs();
        colouredSegmentedFile.mkdirs();
        Imgcodecs.imwrite(colouredSegmentedFile.getAbsolutePath() + File.separator + getName(), colouredSegmentedImage);
        Imgcodecs.imwrite(binarySegmentedFile.getAbsolutePath() + File.separator + getName(), binarySegmentedImage);
    }

}
