/*
 * Copyright (C) 2009 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ImageUtils
{
	/**
	 * Convenience method that returns a scaled instance of the
	 * provided {@code BufferedImage}.
	 * <p>
	 * This method was copied from:
	 * http://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html
	 *
	 * @param img the original image to be scaled
	 * @param targetWidth the desired width of the scaled instance,
	 *    in pixels
	 * @param targetHeight the desired height of the scaled instance,
	 *    in pixels
	 * @param hint one of the rendering hints that corresponds to
	 *    {@code RenderingHints.KEY_INTERPOLATION} (e.g.
	 *    {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
	 *    {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
	 *    {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
	 * @param higherQuality if true, this method will use a multi-step
	 *    scaling technique that provides higher quality than the usual
	 *    one-step technique (only useful in downscaling cases, where
	 *    {@code targetWidth} or {@code targetHeight} is
	 *    smaller than the original dimensions, and generally only when
	 *    the {@code BILINEAR} hint is specified)
	 * @return a scaled version of the original {@code BufferedImage}
	 */
	public static BufferedImage getScaledInstance(BufferedImage img,
										int targetWidth,
										int targetHeight,
										Object hint,
										boolean higherQuality)
	{
		int type = (img.getTransparency() == Transparency.OPAQUE) ?
			BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		BufferedImage ret = (BufferedImage)img;
		int w, h;
		if (higherQuality) {
			// Use multi-step technique: start with original size, then
			// scale down in multiple passes with drawImage()
			// until the target size is reached
			w = img.getWidth();
			h = img.getHeight();
		} else {
			// Use one-step technique: scale directly from original
			// size to target size with a single drawImage() call
			w = targetWidth;
			h = targetHeight;
		}

		do {
			if (higherQuality && w > targetWidth) {
				w /= 2;
				if (w < targetWidth) {
					w = targetWidth;
				}
			}

			if (higherQuality && h > targetHeight) {
				h /= 2;
				if (h < targetHeight) {
					h = targetHeight;
				}
			}

			BufferedImage tmp = new BufferedImage(w, h, type);
			Graphics2D g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
			g2.drawImage(ret, 0, 0, w, h, null);
			g2.dispose();

			ret = tmp;
		} while (w != targetWidth || h != targetHeight);

		return ret;
	}

	/**
	 * A more specialized wrapper for
	 * {@link #getScaledInstance(BufferedImage, int, int, Object, boolean)}.
	 * Performs cropping to preserve aspect ratio.
	 */
	public static byte[] getScaledInstance(byte[] img, int targetWidth, int targetHeight)
	{
		try {
			BufferedImage buf = ImageIO.read(new ByteArrayInputStream(img));

			if (targetWidth != targetHeight)
				throw new UnsupportedOperationException("TODO");

			/* Crop first. */
			int w = buf.getWidth();
			int h = buf.getHeight();

			BufferedImage cropped;
			if (w == h)
				cropped = buf;
			else
			{
				int x, y, minDimension;

				if (w > h)
				{
					x = (int)((w - h) / 2.0);
					y = 0;
				}
				else
				{
					x = 0;
					y = (int)((h - w) / 2.0);
				}

				minDimension = Math.min(w, h);
				cropped = buf.getSubimage(x, y, minDimension, minDimension);
			}

			BufferedImage scaledAndCropped = getScaledInstance(cropped, targetWidth, targetHeight,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);

			/* Export as a JPEG byte array. */
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				ImageIO.write(scaledAndCropped, "JPEG", out);
				return out.toByteArray();
			} finally {
				out.close();
			}
		} catch (IOException e) {
			return null;
		}
	}
}
