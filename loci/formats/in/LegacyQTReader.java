//
// LegacyQTReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan,
Eric Kjellman and Brian Loranger.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageProducer;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;
import loci.formats.*;

/**
 * LegacyQTReader is a file format reader for QuickTime movie files.
 * To use it, QuickTime for Java must be installed.
 *
 * Much of this code was based on the QuickTime Movie Opener for ImageJ
 * (available at http://rsb.info.nih.gov/ij/plugins/movie-opener.html).
 */
public class LegacyQTReader extends FormatReader {

  // -- Fields --

  /** Instance of LegacyQTTools to handle QuickTime for Java detection. */
  protected LegacyQTTools tools;

  /** Reflection tool for QuickTime for Java calls. */
  protected ReflectedUniverse r;

  /** Number of images in current QuickTime movie. */
  protected int numImages;

  /** Time offset for each frame. */
  protected int[] times;

  /** Image containing current frame. */
  protected Image image;

  // -- Constructor --

  /** Constructs a new QT reader. */
  public LegacyQTReader() { super("QuickTime", "mov"); }

  /** Constructs a new QT reader with the given id mappings. */
  public LegacyQTReader(Hashtable idMap) {
    super("QuickTime", "mov");
  }

  // -- FormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */ 
  public boolean isThisType(byte[] block) { return false; }

  /* @see loci.formats.IFormatReader#getImageCount(String) */ 
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return numImages;
  }

  /* @see loci.formats.IFormatReader#isRGB(String) */ 
  public boolean isRGB(String id) throws FormatException, IOException {
    return true;
  }

  /* @see loci.formats.IFormatReader#isLittleEndian(String) */ 
  public boolean isLittleEndian(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#isInterleaved(String, int) */ 
  public boolean isInterleaved(String id, int subC)
    throws FormatException, IOException
  {
    return false;
  }

  /* @see loci.formats.IFormatReader#openBytes(String, int) */ 
  public byte[] openBytes(String id, int no)
    throws FormatException, IOException
  {
    return ImageTools.getBytes(openImage(id, no), false, 3);
  }

  /* @see loci.formats.IFormatReader#openImage(String, int) */ 
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= getImageCount(id)) {
      throw new FormatException("Invalid image number: " + no);
    }

    if (tools.isQTExpired()) {
      throw new FormatException(LegacyQTTools.EXPIRED_QT_MSG);
    }
    if (!tools.canDoQT()) throw new FormatException(LegacyQTTools.NO_QT_MSG);

    // paint frame into image
    try {
      r.setVar("time", times[no]);
      r.exec("moviePlayer.setTime(time)");
      r.exec("qtip.redraw(null)");
      r.exec("qtip.updateConsumers(null)");
    }
    catch (ReflectException re) {
      throw new FormatException("Open movie failed", re);
    }

    return ImageTools.makeBuffered(image);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws FormatException, IOException {
    if (fileOnly) {
      try {
        r.exec("openMovieFile.close()");
      }
      catch (ReflectException e) {
        throw new FormatException("Close movie failed", e);
      }
    }
    else close();
  }

  /* @see loci.formats.IFormatReader#close() */ 
  public void close() throws FormatException, IOException {
    if (currentId == null) return;

    try {
      r.exec("openMovieFile.close()");
      r.exec("QTSession.close()");
    }
    catch (ReflectException e) {
      throw new FormatException("Close movie failed", e);
    }
    currentId = null;
  }

  /** Initializes the given QuickTime file. */
  protected void initFile(String id)
    throws FormatException, IOException
  {
    if (debug) debug("LegacyQTReader.initFile(" + id + ")");
    
    status("Checking for QuickTime Java"); 
    
    if (tools == null) {
      tools = new LegacyQTTools();
      r = tools.getUniverse();
    }
    if (tools.isQTExpired()) {
      throw new FormatException(LegacyQTTools.EXPIRED_QT_MSG);
    }
    if (!tools.canDoQT()) throw new FormatException(LegacyQTTools.NO_QT_MSG);

    super.initFile(id);

    status("Reading movie dimensions");
    try {
      r.exec("QTSession.open()");

      // open movie file
      Location file = new Location(id);
      r.setVar("path", file.getAbsolutePath());
      r.exec("qtf = new QTFile(path)");
      r.exec("openMovieFile = OpenMovieFile.asRead(qtf)");
      r.exec("m = Movie.fromFile(openMovieFile)");

      int numTracks = ((Integer) r.exec("m.getTrackCount()")).intValue();
      int trackMostLikely = 0;
      int trackNum = 0;
      while (++trackNum <= numTracks && trackMostLikely == 0) {
        r.setVar("trackNum", trackNum);
        r.exec("imageTrack = m.getTrack(trackNum)");
        r.exec("d = imageTrack.getSize()");
        Integer w = (Integer) r.exec("d.getWidth()");
        if (w.intValue() > 0) trackMostLikely = trackNum;
      }

      r.setVar("trackMostLikely", trackMostLikely);
      r.exec("imageTrack = m.getTrack(trackMostLikely)");
      r.exec("d = imageTrack.getSize()");
      Integer w = (Integer) r.exec("d.getWidth()");
      Integer h = (Integer) r.exec("d.getHeight()");

      r.exec("moviePlayer = new MoviePlayer(m)");
      r.setVar("dim", new Dimension(w.intValue(), h.intValue()));
      ImageProducer qtip = (ImageProducer)
        r.exec("qtip = new QTImageProducer(moviePlayer, dim)");
      image = Toolkit.getDefaultToolkit().createImage(qtip);

      r.setVar("zero", 0);
      r.setVar("one", 1f);
      r.exec("timeInfo = new TimeInfo(zero, zero)");
      r.exec("moviePlayer.setTime(zero)");
      Vector v = new Vector();
      int time = 0;
      Integer q = new Integer(time);
      do {
        v.add(q);
        r.exec("timeInfo = imageTrack.getNextInterestingTime(" +
          "StdQTConstants.nextTimeMediaSample, timeInfo.time, one)");
        q = (Integer) r.getVar("timeInfo.time");
        time = q.intValue();
      }
      while (time >= 0);
      numImages = v.size();
      times = new int[numImages];
      for (int i=0; i<times.length; i++) {
        q = (Integer) v.elementAt(i);
        times[i] = q.intValue();
      }

      status("Populating metadata");

      BufferedImage img = ImageTools.makeBuffered(image);

      core.sizeX[0] = img.getWidth();
      core.sizeY[0] = img.getHeight();
      core.sizeZ[0] = 1;
      core.sizeC[0] = img.getRaster().getNumBands();
      core.sizeT[0] = numImages;
      core.pixelType[0] = ImageTools.getPixelType(img);
      core.currentOrder[0] = "XYCTZ";

      MetadataStore store = getMetadataStore(id);
      store.setPixels(new Integer(core.sizeX[0]), new Integer(core.sizeY[0]),
        new Integer(core.sizeZ[0]), new Integer(core.sizeC[0]), 
        new Integer(core.sizeT[0]), new Integer(core.pixelType[0]), 
        Boolean.TRUE, core.currentOrder[0], null, null);

      for (int i=0; i<core.sizeC[0]; i++) {
        store.setLogicalChannel(i, null, null, null, null, null, null, null);
      }
    }
    catch (Exception e) {
      // CTR TODO - eliminate catch-all exception handling
      throw new FormatException("Open movie failed", e);
    }
  }

}
