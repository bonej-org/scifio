
package io.scif.formats;

import io.scif.*;
import io.scif.config.SCIFIOConfig;
import io.scif.io.RandomAccessInputStream;
import io.scif.util.FormatTools;

import java.io.IOException;

import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;

import org.scijava.plugin.Plugin;

/**
 * @author Richard Domander
 */
@Plugin(type = Format.class, name = "Region of interest")
public class ROIFormat extends AbstractFormat {

	@Override
	protected String[] makeSuffixArray() {
		return new String[] { "roi" };
	}

	public static class Checker extends AbstractChecker {

	}

	public static class Metadata extends AbstractMetadata {

		@Override
		public void populateImageMetadata() {
			createImageMetadata(1);
			final ImageMetadata metadata = get(0);
			metadata.setAxes(new DefaultLinearAxis(Axes.X), new DefaultLinearAxis(
				Axes.Y));
		}
	}

	public static class Parser extends AbstractParser<Metadata> {

		@Override
		protected void typedParse(final RandomAccessInputStream stream,
			final Metadata meta, final SCIFIOConfig config) throws IOException,
			FormatException
		{

		}
	}

	public static class Reader extends ByteArrayReader<Metadata> {

		@Override
		protected String[] createDomainArray() {
			return new String[] { FormatTools.UNKNOWN_DOMAIN };
		}

		@Override
		public ByteArrayPlane openPlane(final int imageIndex, final long planeIndex,
			final ByteArrayPlane plane, final long[] planeMin, final long[] planeMax,
			final SCIFIOConfig config) throws FormatException, IOException
		{
            final RandomAccessInputStream stream = getStream();
            return readPlane(stream, imageIndex, planeMin, planeMax, plane);
		}
	}
}
