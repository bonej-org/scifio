//
// OMEXMLReader.java
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

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import loci.formats.*;
import loci.formats.codec.Base64Codec;

/**
 * OMEXMLReader is the file format reader for OME-XML files.
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class OMEXMLReader extends FormatReader {

  // -- Constants --

  private static final String NO_OME_JAVA_MSG =
    "The Java OME-XML library is required to read OME-XML files. Please " +
    "obtain ome-java.jar from http://loci.wisc.edu/ome/formats.html";

  // -- Static fields --

  private static boolean noOME = false;

  static {
    try {
      Class.forName("org.openmicroscopy.xml.OMENode");
    }
    catch (Throwable t) {
      noOME = true;
    }
  }

  // -- Fields --

  /** Current file. */
  protected RandomAccessStream in;

  /** Flag indicating whether current file is little endian. */
  protected boolean[] littleEndian;

  /** Number of image planes in the file. */
  protected int[] numImages;

  /** Number of bits per pixel. */
  protected int[] bpp;

  /** Offset to each plane's data. */
  protected Vector[] offsets;

  /** String indicating the compression type. */
  protected String[] compression;

  // -- Constructor --

  /** Constructs a new OME-XML reader. */
  public OMEXMLReader() { super("OME-XML", "ome"); }

  // -- FormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */ 
  public boolean isThisType(byte[] block) {
    return new String(block, 0, 5).equals("<?xml");
  }

  /* @see loci.formats.IFormatReader#getSeriesCount(String) */ 
  public int getSeriesCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return core.sizeX.length; 
  }

  /* @see loci.formats.IFormatReader#getImageCount(String) */ 
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return numImages[series];
  }

  /* @see loci.formats.IFormatReader#isRGB(String) */ 
  public boolean isRGB(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#isLittleEndian(String) */ 
  public boolean isLittleEndian(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return littleEndian[series];
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
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= numImages[series]) {
      throw new FormatException("Invalid image number: " + no);
    }

    in.seek(((Integer) offsets[series].get(no)).intValue());

    byte[] buf;
    if (no < getImageCount(id) - 1) {
      buf = new byte[((Integer) offsets[series].get(no + 1)).intValue() -
        ((Integer) offsets[series].get(no)).intValue()];
    }
    else {
      buf = new byte[(int) (in.length() -
        ((Integer) offsets[series].get(no)).intValue())];
    }
    in.read(buf);
    String data = new String(buf);
    buf = null;

    // retrieve the compressed pixel data

    int dataStart = data.indexOf(">") + 1;
    String pix = data.substring(dataStart);
    if (pix.indexOf("<") > 0) {
      pix = pix.substring(0, pix.indexOf("<"));
    }
    data = null;

    //byte[] pixels = Compression.base64Decode(pix);
    Base64Codec e = new Base64Codec();
    byte[] pixels = e.base64Decode(pix);
    pix = null;

    if (compression[series].equals("bzip2")) {
      byte[] tempPixels = pixels;
      pixels = new byte[tempPixels.length - 2];
      System.arraycopy(tempPixels, 2, pixels, 0, pixels.length);

      ByteArrayInputStream bais = new ByteArrayInputStream(pixels);
      CBZip2InputStream bzip = new CBZip2InputStream(bais);
      pixels = new byte[core.sizeX[series] * core.sizeY[series] * bpp[series]];
      for (int i=0; i<pixels.length; i++) {
        pixels[i] = (byte) bzip.read();
      }
      tempPixels = null;
      bais.close();
      bais = null;
      bzip = null;
    }
    else if (compression[series].equals("zlib")) {
      try {
        Inflater decompressor = new Inflater();
        decompressor.setInput(pixels, 0, pixels.length);
        pixels = new byte[core.sizeX[series]*core.sizeY[series]*bpp[series]];
        decompressor.inflate(pixels);
        decompressor.end();
      }
      catch (DataFormatException dfe) {
        throw new FormatException("Error uncompressing zlib data.");
      }
    }
    return pixels;
  }

  /* @see loci.formats.IFormatReader#openImage(String, int) */ 
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    return ImageTools.makeImage(openBytes(id, no), core.sizeX[series],
      core.sizeY[series], 1, false, bpp[series], littleEndian[series]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws FormatException, IOException {
    if (fileOnly && in != null) in.close();
    else close();
  }

  /* @see loci.formats.IFormatReader#close() */ 
  public void close() throws FormatException, IOException {
    if (noOME) throw new FormatException(NO_OME_JAVA_MSG);
    if (in != null) in.close();
    in = null;
    currentId = null;
  }

  /** Initializes the given OME-XML file. */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("OMEXMLReader.initFile(" + id + ")");
    if (noOME) throw new FormatException(NO_OME_JAVA_MSG);
    super.initFile(id);

    in = new RandomAccessStream(id);
    ReflectedUniverse r = new ReflectedUniverse();
    try {
      r.exec("import loci.formats.ome.OMEXMLMetadataStore");
      r.exec("import org.openmicroscopy.xml.OMENode");
      r.exec("omexml = new OMEXMLMetadataStore()");
    }
    catch (ReflectException exc) {
      throw new FormatException(exc);
    }

    status("Determining endianness");

    in.skipBytes(200);

    int numDatasets = 0;
    Vector endianness = new Vector();
    Vector bigEndianPos = new Vector();

    byte[] buf = new byte[1];

    while (in.getFilePointer() < in.length()) {
      // read a block of 8192 characters, looking for the "BigEndian" pattern
      buf = new byte[8192];
      boolean found = false;
      while (!found) {
        if (in.getFilePointer() < in.length()) {
          int read = in.read(buf, 9, 8183);
          String test = new String(buf);

          int ndx = test.indexOf("BigEndian");
          if (ndx != -1) {
            found = true;
            String endian = test.substring(ndx + 11);
            endianness.add(new Boolean(!endian.toLowerCase().startsWith("t")));
            bigEndianPos.add(new Integer(in.getFilePointer() - read - 9 + ndx));
            numDatasets++;
          }
        }
        else if (numDatasets == 0) {
          throw new FormatException("Pixel data not found.");
        }
        else found = true;
      }
    }

    littleEndian = new boolean[numDatasets];
    offsets = new Vector[numDatasets];

    for (int i=0; i<littleEndian.length; i++) {
      littleEndian[i] = ((Boolean) endianness.get(i)).booleanValue();
      offsets[i] = new Vector();
    }

    status("Finding image offsets");

    // look for the first BinData element in each series

    for (int i=0; i<numDatasets; i++) {
      in.seek(((Integer) bigEndianPos.get(i)).intValue());
      boolean found = false;
      buf = new byte[8192];
      in.read(buf, 0, 14);

      while (!found) {
        if (in.getFilePointer() < in.length()) {
          int numRead = in.read(buf, 14, 8192-14);

          String test = new String(buf);

          int ndx = test.indexOf("<Bin");
          if (ndx == -1) {
            byte[] b = buf;
            System.arraycopy(b, 8192 - 15, buf, 0, 14);
          }
          else {
            while (!((ndx != -1) && (ndx != test.indexOf("<Bin:External")) &&
              (ndx != test.indexOf("<Bin:BinaryFile"))))
            {
              ndx = test.indexOf("<Bin", ndx+1);
            }
            found = true;
            numRead += 14;
            offsets[i].add(new Integer(
              (int) in.getFilePointer() - (numRead - ndx)));
          }
          test = null;
        }
        else {
          throw new FormatException("Pixel data not found");
        }
      }
    }

    in.seek(0);

    for (int i=0; i<numDatasets; i++) {
      if (i == 0) {
        buf = new byte[((Integer) offsets[i].get(0)).intValue()];
      }
      else {
        // look for the next Image element

        boolean found = false;
        buf = new byte[8192];
        in.read(buf, 0, 14);
        while (!found) {
          if (in.getFilePointer() < in.length()) {
            int numRead = in.read(buf, 14, 8192-14);

            String test = new String(buf);

            int ndx = test.indexOf("<Image ");
            if (ndx == -1) {
              byte[] b = buf;
              System.arraycopy(b, 8192 - 15, buf, 0, 14);
              b = null;
            }
            else {
              found = true;
              in.seek(in.getFilePointer() - (8192 - ndx));
            }
            test = null;
          }
          else {
            throw new FormatException("Pixel data not found");
          }
        }

        int bufSize = ((Integer) offsets[i].get(0)).intValue() -
          in.getFilePointer();
        buf = new byte[bufSize];
      }
      in.read(buf);
    }
    buf = null;

    status("Populating metadata");

    r.setVar("ome", null);
    try {
      File f = new File(Location.getMappedId(id));
      f = f.getAbsoluteFile();
      if (f.exists()) {
        r.setVar("f", f);
        r.exec("ome = new OMENode(f)");
      }
      else {
        byte[] b = new byte[(int) in.length()];
        long oldFp = in.getFilePointer();
        in.seek(0);
        in.read(b);
        in.seek(oldFp);
        r.setVar("s", new String(b));
        r.exec("ome = new OMENode(s)");
        b = null;
      }
    }
    catch (ReflectException exc) {
      throw new FormatException(exc);
    }
    try {
      r.exec("omexml.setRoot(ome)");
    }
    catch (ReflectException exc) {
      throw new FormatException(exc);
    }

    core = new CoreMetadata(numDatasets);

    numImages = new int[numDatasets];
    bpp = new int[numDatasets];
    compression = new String[numDatasets];

    int oldSeries = getSeries(currentId);

    try {
      r.exec("omexml.setRoot(ome)");
    }
    catch (ReflectException exc) {
      throw new FormatException(exc);
    }
    for (int i=0; i<numDatasets; i++) {
      setSeries(currentId, i);
      Integer ndx = new Integer(i);
      Integer w = null, h = null, t = null, z = null, c = null;
      String pixType = null, dimOrder = null;
      try {
        r.setVar("ndx", i);
        w = (Integer) r.exec("omexml.getSizeX(ndx)");
        h = (Integer) r.exec("omexml.getSizeY(ndx)");
        t = (Integer) r.exec("omexml.getSizeT(ndx)");
        z = (Integer) r.exec("omexml.getSizeZ(ndx)");
        c = (Integer) r.exec("omexml.getSizeC(ndx)");
        pixType = (String) r.exec("omexml.getPixelType(ndx)");
        core.currentOrder[i] = 
          (String) r.exec("omexml.getDimensionOrder(ndx)");
      }
      catch (ReflectException exc) {
        throw new FormatException(exc);
      }
      core.sizeX[i] = w.intValue();
      core.sizeY[i] = h.intValue();
      core.sizeT[i] = t.intValue();
      core.sizeZ[i] = z.intValue();
      core.sizeC[i] = c.intValue();

      String type = pixType.toLowerCase();
      if (type.endsWith("16")) {
        bpp[i] = 2;
        core.pixelType[i] = FormatTools.UINT16;
      }
      else if (type.endsWith("32")) {
        bpp[i] = 4;
        core.pixelType[i] = FormatTools.UINT32;
      }
      else if (type.equals("float")) {
        bpp[i] = 4;
        core.pixelType[i] = FormatTools.FLOAT;
      }
      else {
        bpp[i] = 1;
        core.pixelType[i] = FormatTools.UINT8;
      }

      // calculate the number of raw bytes of pixel data that we are expecting
      int expected = core.sizeX[i] * core.sizeY[i] * bpp[i];

      // find the compression type and adjust 'expected' accordingly
      in.seek(((Integer) offsets[i].get(0)).intValue());
      buf = new byte[256];
      in.read(buf);
      String data = new String(buf);

      int compressionStart = data.indexOf("Compression") + 13;
      int compressionEnd = data.indexOf("\"", compressionStart);
      if (compressionStart != -1 && compressionEnd != -1) {
        compression[i] = data.substring(compressionStart, compressionEnd);
      }
      else compression[i] = "none";

      expected /= 2;

      in.seek(((Integer) offsets[i].get(0)).intValue());

      int planes = core.sizeZ[i] * core.sizeC[i] * core.sizeT[i];

      searchForData(expected, planes);
      numImages[i] = offsets[i].size();
      if (numImages[i] < planes) {
        // hope this doesn't happen too often
        in.seek(((Integer) offsets[i].get(0)).intValue());
        searchForData(0, planes);
        numImages[i] = offsets[i].size();
      }
      buf = null;
    }
    setSeries(currentId, oldSeries);
    Arrays.fill(core.orderCertain, true);

    MetadataStore store = getMetadataStore(id);
    for (int i=0; i<core.sizeC.length; i++) {
      for (int j=0; j<core.sizeC[i]; j++) {
        store.setLogicalChannel(j, null, null, null, null, null,
          null, new Integer(i));
      }
    }

    System.gc();
  }

  // -- Helper methods --

  /** Searches for BinData elements, skipping 'safe' bytes in between. */
  private void searchForData(int safe, int numPlanes) throws IOException {
    int iteration = 0;
    boolean found = false;
    if (offsets[series].size() > 1) {
      Object zeroth = offsets[series].get(0);
      offsets[series].clear();
      offsets[series].add(zeroth);
    }

    in.skipBytes(1);
    while (((in.getFilePointer() + safe) < in.length()) &&
      (offsets[series].size() < numPlanes))
    {
      in.skipBytes(safe);

      // look for next BinData element
      found = false;
      byte[] buf = new byte[8192];
      while (!found) {
        if (in.getFilePointer() < in.length()) {
          int numRead = in.read(buf, 20, buf.length - 20);
          String test = new String(buf);

          // datasets with small planes could have multiple sets of pixel data
          // in this block
          int ndx = test.indexOf("<Bin");
          while (ndx != -1) {
            found = true;
            if (numRead == buf.length - 20) numRead = buf.length;
            offsets[series].add(new Integer(
              (int) in.getFilePointer() - (numRead - ndx)));
            ndx = test.indexOf("<Bin", ndx+1);
          }
          test = null;
        }
        else {
          found = true;
        }
      }
      buf = null;

      iteration++;
    }
  }

  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    new OMEXMLReader().testRead(args);
  }

}
