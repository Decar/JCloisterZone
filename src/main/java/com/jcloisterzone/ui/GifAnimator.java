package com.jcloisterzone.ui;

// 
//  GifSequenceWriter.java
//  
//  Created by Elliot Kroo on 2009-04-25.
//
// This work is licensed under the Creative Commons Attribution 3.0 Unported
// License. To view a copy of this license, visit
// http://creativecommons.org/licenses/by/3.0/ or send a letter to Creative
// Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GifAnimator {

    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    private static final boolean LOOP = true;
    private static final int TIME_BETWEEN_FRAMES_MS = 500;
    protected ImageWriter gifWriter;
    protected ImageWriteParam imageWriteParam;
    protected IIOMetadata imageMetaData;
    private ArrayList<String> images;
    private ArrayList<Point> points;
    private int ymax;
    private int xmax;
    private File file;
    private boolean stopProcessing;

    /**
     * Loosely based on Elliot Kroo's GifSequenceWriter
     * 
     */
    public GifAnimator() {
        String gifExt = ".gif";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        String fileName = "JCloisterZone_animation" + sdf.format(new Date())
                + gifExt;
        file = new File(fileName);
        images = new ArrayList<String>();
        points = new ArrayList<Point>();
    }

    public void setupGifWriter(ImageOutputStream outputStream, int imageType, int timeBetweenFramesMS, boolean loopContinuously) throws IOException {
        // my method to create a writer
        gifWriter = getWriter();
        imageWriteParam = gifWriter.getDefaultWriteParam();
        ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);

        imageMetaData = gifWriter.getDefaultImageMetadata(imageTypeSpecifier, imageWriteParam);

        String metaFormatName = imageMetaData.getNativeMetadataFormatName();

        IIOMetadataNode root = (IIOMetadataNode) imageMetaData.getAsTree(metaFormatName);

        IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");

        graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(timeBetweenFramesMS / 10));
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode appEntensionsNode = getNode(root, "ApplicationExtensions");

        IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");

        child.setAttribute("applicationID", "NETSCAPE");
        child.setAttribute("authenticationCode", "2.0");

        int loop = loopContinuously ? 0 : 1;

        child.setUserObject(new byte[] { 0x1, (byte) (loop & 0xFF),
                (byte) ((loop >> 8) & 0xFF) });
        appEntensionsNode.appendChild(child);

        imageMetaData.setFromTree(metaFormatName, root);

        gifWriter.setOutput(outputStream);

        gifWriter.prepareWriteSequence(null);
    }

    public void addToGif(BufferedImage img, int x, int y) {
        try {
            File tmpFile = File.createTempFile("JCZ", ".png");
            ImageOutputStream ios = new FileImageOutputStream(tmpFile);
            ImageIO.write(img, "PNG", ios);
            images.add(tmpFile.getAbsolutePath());
            points.add(new Point(x, y));
            setYmax(y);
            setXmax(x);
        } catch (IOException ioe) {
            logger.error("Error Adding Gif to Sequence", ioe);
        }
    }

    public void setYmax(int y) {
        if (y < ymax) {
            ymax = y;
        }
    }

    public void setXmax(int x) {
        if (x < xmax) {
            xmax = x;
        }
    }

    /**
     * Close this GifSequenceWriter object. This does not close the underlying
     * stream, just finishes off the GIF.
     * @param imageType 
     * @param loop 
     * @param outStream 
     * @param timeBetweenFramesMS 
     */
    public void close() {

        ProgressBar progressBar = createProgressBar();

        ImageOutputStream outStream;
        BufferedImage finalImg;
        try {
            outStream = new FileImageOutputStream(file);
            //get last (largest image)
            File tmpFile = new File(images.get(images.size() - 1));
            if (!tmpFile.exists()) {
                outStream.close();
                return;
            }
            ImageInputStream inStream = new FileImageInputStream(tmpFile);
            finalImg = ImageIO.read(inStream);
        } catch (IOException e) {
            logger.error("Failed to create Animated GIF", e);
            return;
        }

        try {
            BufferedImage initialImage = new BufferedImage(finalImg.getWidth(), finalImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D s = initialImage.createGraphics();
            s.setColor(new Color(240, 240, 240));
            s.fill(new Rectangle2D.Double(0, 0, finalImg.getWidth(), finalImg.getHeight()));
            setupGifWriter(outStream, finalImg.getType(), TIME_BETWEEN_FRAMES_MS, LOOP);

            gifWriter.writeToSequence(new IIOImage(initialImage, null, imageMetaData), imageWriteParam);
        } catch (IOException e) {
            //failed to setup gif writer or write first image
            logger.error("Failed to write first in sequence", e);
            return;
        }

        for (int i = 0; i < images.size(); i++) {
            progressBar.setValue(100 * i / (images.size() - 1), i + " of " + (images.size() - 1));
            File srcImgFile = new File(images.get(i));
            if (!srcImgFile.exists()) continue;
            if (!stopProcessing) {

                BufferedImage image;
                try {
                    FileImageInputStream fileImageInputStream = new FileImageInputStream(srcImgFile);
                    image = ImageIO.read(fileImageInputStream);
                } catch (IOException e) {
                    //failed to read saved tmp files. so try next
                    continue;
                }
                BufferedImage destImg = new BufferedImage(finalImg.getWidth(), finalImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D newGphx = destImg.createGraphics();

                Point p = points.get(i);
                //dest x1,y1 x2,y2 then source x1,y1 x2,y2
                int xOffset = (int) -(xmax - p.getX());
                int yOffset = (int) -(ymax - p.getY());
                newGphx.drawImage(image, xOffset, yOffset, 
                                         xOffset + image.getWidth() - 270, yOffset + image.getHeight(), 
                                         0, 0, 
                                         image.getWidth() - 270, image.getHeight(), null);

                newGphx.drawImage(image, destImg.getWidth() - 270, 0, 
                                         destImg.getWidth(), image.getHeight(), 
                                         image.getWidth() - 270, 0, 
                                         image.getWidth(), image.getHeight(), null);
                try {
                    gifWriter.writeToSequence(new IIOImage(destImg, null, imageMetaData), imageWriteParam);
                } catch (IOException e) {
                    //failed to write next image into sequence
                    logger.error("Failed to write image to Animated GIF", e);
                    continue;
                }
            }
            srcImgFile.delete();
        }

        try {
            gifWriter.writeToSequence(new IIOImage(finalImg, null, imageMetaData), imageWriteParam);
            gifWriter.writeToSequence(new IIOImage(finalImg, null, imageMetaData), imageWriteParam);
            gifWriter.endWriteSequence();
            outStream.close();
        } catch (IOException e) {
            //failed to write final two images;
            logger.error("Failed to complete Animated GIF", e);

        }
        progressBar.dispose();
    }

    public ProgressBar createProgressBar() {
        stopProcessing = false;
        ProgressBar progressBar = new ProgressBar(new WindowAdapter() {

            @Override
            public void windowClosed(WindowEvent e) {
                stopProcessing = true;
            }

        });
        progressBar.setVisible(true);
        return progressBar;
    }

    /**
     * Returns the first available GIF ImageWriter using 
     * ImageIO.getImageWritersBySuffix("gif").
     * 
     * @return a GIF ImageWriter object
     * @throws IIOException if no GIF image writers are returned
     */
    private static ImageWriter getWriter() throws IIOException {
        Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix("gif");
        if (!iter.hasNext()) {
            throw new IIOException("No GIF Image Writers Exist");
        } else {
            return iter.next();
        }
    }

    /**
     * Returns an existing child node, or creates and returns a new child node (if 
     * the requested node does not exist).
     * 
     * @param rootNode the <tt>IIOMetadataNode</tt> to search for the child node.
     * @param nodeName the name of the child node.
     * 
     * @return the child node, if found or a new node created with the given name.
     */
    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        int nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++) {
            if (rootNode.item(i).getNodeName().compareToIgnoreCase(nodeName) == 0) {
                return ((IIOMetadataNode) rootNode.item(i));
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return (node);
    }
}